package eu.mymcd.shifts.notify

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import eu.mymcd.shifts.data.Repository
import eu.mymcd.shifts.network.Shift
import eu.mymcd.shifts.util.TimeUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDateTime

class ReminderWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val accountId = inputData.getString(KEY_ACCOUNT) ?: return@withContext Result.success()
        val shiftId = inputData.getLong(KEY_SHIFT_ID, -1L)
        val dayBefore = inputData.getBoolean(KEY_DAY_BEFORE, false)
        if (shiftId < 0) return@withContext Result.success()

        try {
            Notifier.ensureChannel(applicationContext)
            val repo = Repository.get(applicationContext)
            val shift: Shift = repo.cachedShifts(accountId).firstOrNull { it.id == shiftId }
                ?: return@withContext Result.success()

            val now = LocalDateTime.now()
            // Don't fire if the shift already started (e.g. device was off past the time).
            if (!TimeUtil.isFuture(shift, now) && !dayBefore) {
                // Day-of reminder after start is useless; day-before after start also useless.
                return@withContext Result.success()
            }
            if (!TimeUtil.isFuture(shift, now)) return@withContext Result.success()

            Notifier.notifyShiftReminder(applicationContext, shift, dayBefore)
            Result.success()
        } catch (t: Throwable) {
            Result.retry()
        }
    }

    companion object {
        const val KEY_ACCOUNT = "account"
        const val KEY_SHIFT_ID = "shift_id"
        const val KEY_DAY_BEFORE = "day_before"
    }
}
