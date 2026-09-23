package eu.mymcd.shifts

import android.app.Application
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import eu.mymcd.shifts.legal.LegalAssets
import eu.mymcd.shifts.notify.Notifier
import eu.mymcd.shifts.notify.ReminderScheduler
import eu.mymcd.shifts.store.SettingsStore
import eu.mymcd.shifts.work.RefreshWorker
import java.util.concurrent.TimeUnit

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        LegalAssets.init(this)
        Notifier.ensureChannel(this)

        // Use user-configured interval (default 30 min).
        val minutes = SettingsStore(this).refreshIntervalMin.toLong().coerceAtLeast(5L)
        val wm = WorkManager.getInstance(this)
        val request = PeriodicWorkRequestBuilder<RefreshWorker>(minutes, TimeUnit.MINUTES).build()
        wm.enqueueUniquePeriodicWork(
            RefreshWorker.UNIQUE_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            request
        )

        // Restore any missed shift reminders after process death / reboot.
        try {
            ReminderScheduler.rescheduleAll(this)
        } catch (_: Throwable) {
        }
    }
}
