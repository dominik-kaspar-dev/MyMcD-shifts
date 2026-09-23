package eu.mymcd.shifts.widget

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import eu.mymcd.shifts.data.Repository
import eu.mymcd.shifts.notify.ReminderScheduler
import eu.mymcd.shifts.work.RefreshWorker

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED &&
            intent.action != Intent.ACTION_MY_PACKAGE_REPLACED
        ) return

        if (Repository.get(context).store.anyAccountConfigured()) {
            RefreshWorker.enqueueNow(context)
        } else {
            ShiftWidgetReceiver.updateWidgets(context)
        }
        try {
            ReminderScheduler.rescheduleAll(context)
        } catch (_: Throwable) {
        }
    }
}
