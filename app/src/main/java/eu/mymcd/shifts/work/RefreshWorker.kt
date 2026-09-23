package eu.mymcd.shifts.work

import android.content.Context
import android.util.Log
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import eu.mymcd.shifts.data.Repository
import eu.mymcd.shifts.notify.Notifier
import eu.mymcd.shifts.notify.ReminderScheduler
import eu.mymcd.shifts.store.SettingsStore
import eu.mymcd.shifts.widget.ShiftWidgetReceiver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

class RefreshWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            Notifier.ensureChannel(applicationContext)
            val repo = Repository.get(applicationContext)

            if (!repo.store.anyAccountConfigured()) {
                ShiftWidgetReceiver.updateWidgets(applicationContext)
                return@withContext Result.success()
            }

            if (!SettingsStore(applicationContext).legalAccepted) {
                ShiftWidgetReceiver.updateWidgets(applicationContext)
                return@withContext Result.success()
            }

            // Re-login handled inside McDClient.refreshAll when session is invalid.
            repo.refreshAllAccounts(notify = true)
            ShiftWidgetReceiver.updateWidgets(applicationContext)
            ReminderScheduler.rescheduleAll(applicationContext)
            Result.success()
        } catch (t: Throwable) {
            Log.e(TAG, "refresh failed", t)
            try {
                ShiftWidgetReceiver.updateWidgets(applicationContext)
            } catch (_: Throwable) {
            }
            Result.retry()
        }
    }

    companion object {
        private const val TAG = "MyMcDWorker"
        const val UNIQUE_NAME = "mymcd_widget_refresh"
        private const val ONCE_NAME = "mymcd_once"

        fun enqueueNow(context: Context) {
            val req = OneTimeWorkRequestBuilder<RefreshWorker>().build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                ONCE_NAME,
                androidx.work.ExistingWorkPolicy.REPLACE,
                req
            )
        }

        /** (Re)schedule periodic refresh using the user-configured interval. */
        fun enqueuePeriodic(context: Context) {
            val minutes = SettingsStore(context).refreshIntervalMin.toLong().coerceAtLeast(5L)
            val request = PeriodicWorkRequestBuilder<RefreshWorker>(minutes, TimeUnit.MINUTES)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(androidx.work.NetworkType.CONNECTED)
                        .build()
                )
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                UNIQUE_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                request
            )
        }

        fun enqueuePeriodicSafe(context: Context) = enqueuePeriodic(context)
    }
}
