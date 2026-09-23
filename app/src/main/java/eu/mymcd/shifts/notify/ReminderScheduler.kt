package eu.mymcd.shifts.notify

import android.content.Context
import android.util.Log
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import eu.mymcd.shifts.data.Repository
import eu.mymcd.shifts.network.Shift
import eu.mymcd.shifts.store.SettingsStore
import eu.mymcd.shifts.util.TimeUtil
import java.time.Duration
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit

/**
 * Schedules day-before / day-of shift reminders via WorkManager one-shot jobs.
 * Re-run after every data refresh and when notification settings change.
 */
object ReminderScheduler {
    // Preference checks are done in rescheduleAll; early-out helper exists for callers.

    private const val TAG = "MyMcDReminders"
    private const val WORK_TAG = "shift_reminder"

    fun rescheduleAll(context: Context) {
        try {
            val wm = WorkManager.getInstance(context)
            wm.cancelAllWorkByTag(WORK_TAG)

            val settings = SettingsStore(context)
            if (!settings.legalAccepted) return
            if (!settings.notifyDayOf && !settings.notifyDayBefore) return

            val repo = Repository.get(context)
            val now = LocalDateTime.now()
            val shifts = collectUpcoming(repo, now)
            if (shifts.isEmpty()) return

            for ((accountId, shift) in shifts) {
                scheduleIfNeeded(context, accountId, shift, dayBefore = true, settings, now)
                scheduleIfNeeded(context, accountId, shift, dayBefore = false, settings, now)
            }
        } catch (t: Throwable) {
            Log.e(TAG, "rescheduleAll failed", t)
        }
    }

    private fun collectUpcoming(repo: Repository, now: LocalDateTime): List<Pair<String, Shift>> {
        val out = mutableListOf<Pair<String, Shift>>()
        for (id in repo.store.accountIds()) {
            for (s in repo.cachedShifts(id)) {
                if (TimeUtil.isFuture(s, now)) out.add(id to s)
            }
        }
        return out
    }

    private fun scheduleIfNeeded(
        context: Context,
        accountId: String,
        shift: Shift,
        dayBefore: Boolean,
        settings: SettingsStore,
        now: LocalDateTime
    ) {
        val start = TimeUtil.shiftStart(shift) ?: return
        if (!TimeUtil.isFuture(shift, now)) return

        val fireAt: LocalDateTime = if (dayBefore) {
            if (!settings.notifyDayBefore) return
            start.minusDays(1).withHour(settings.dayBeforeHour).withMinute(0).withSecond(0).withNano(0)
        } else {
            if (!settings.notifyDayOf) return
            start.toLocalDate().atTime(settings.dayOfHour, 0, 0, 0)
        }

        if (!fireAt.isAfter(now)) return
        val delayMs = Duration.between(now, fireAt).toMillis().coerceAtLeast(1_000L)
        val unique = "rem_${accountId}_${shift.id}_${if (dayBefore) "b" else "d"}"

        val req = OneTimeWorkRequestBuilder<ReminderWorker>()
            .setInitialDelay(delayMs, TimeUnit.MILLISECONDS)
            .setInputData(
                workDataOf(
                    ReminderWorker.KEY_ACCOUNT to accountId,
                    ReminderWorker.KEY_SHIFT_ID to shift.id,
                    ReminderWorker.KEY_DAY_BEFORE to dayBefore
                )
            )
            .addTag(WORK_TAG)
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(unique, ExistingWorkPolicy.REPLACE, req)
    }
}
