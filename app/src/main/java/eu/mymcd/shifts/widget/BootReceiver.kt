package eu.mymcd.shifts.widget

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import eu.mymcd.shifts.data.Repository
import eu.mymcd.shifts.work.RefreshWorker

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        if (Repository.get(context).store.anyAccountConfigured()) {
            RefreshWorker.enqueueNow(context)
        } else {
            ShiftWidgetReceiver.updateWidgets(context)
        }
    }
}
