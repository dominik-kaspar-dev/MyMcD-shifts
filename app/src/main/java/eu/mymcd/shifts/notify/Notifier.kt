package eu.mymcd.shifts.notify

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import eu.mymcd.shifts.MainActivity
import eu.mymcd.shifts.R
import eu.mymcd.shifts.network.Shift

object Notifier {

    const val CHANNEL_SHIFTS = "shift_changes"

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val mgr = context.getSystemService(NotificationManager::class.java)
            val channel = NotificationChannel(
                CHANNEL_SHIFTS,
                context.getString(R.string.notif_channel_name),
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = context.getString(R.string.notif_channel_desc)
            }
            mgr.createNotificationChannel(channel)
        }
    }

    fun canNotify(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            androidx.core.content.ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.POST_NOTIFICATIONS
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED

    fun notifyShiftChange(
        context: Context,
        email: String,
        added: Int,
        removed: Int,
        changed: Int,
        next: Shift?
    ) {
        ensureChannel(context)
        if (!canNotify(context)) return

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pending = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val parts = mutableListOf<String>()
        if (added > 0) parts.add(context.getString(R.string.notif_added, added))
        if (removed > 0) parts.add(context.getString(R.string.notif_removed, removed))
        if (changed > 0) parts.add(context.getString(R.string.notif_changed, changed))
        val summary = parts.joinToString(" · ")
        val nextLine = next?.let {
            context.getString(R.string.notif_next, it.date, it.from.take(16), it.to.take(16))
        } ?: context.getString(R.string.no_upcoming_shifts)

        val notification = NotificationCompat.Builder(context, CHANNEL_SHIFTS)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.notif_title, email))
            .setContentText(summary.ifBlank { context.getString(R.string.notif_body_fallback) })
            .setStyle(NotificationCompat.BigTextStyle().bigText("$summary\n$nextLine"))
            .setContentIntent(pending)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

        try {
            NotificationManagerCompat.from(context)
                .notify(email.hashCode(), notification)
        } catch (_: SecurityException) {
        }
    }

    data class ShiftDiff(val added: Int, val removed: Int, val changed: Int) {
        val hasChange: Boolean get() = added > 0 || removed > 0 || changed > 0
    }

    fun diff(old: List<Shift>, new: List<Shift>): ShiftDiff {
        val oldById = old.associateBy { it.id }
        val newById = new.associateBy { it.id }
        var added = 0
        var removed = 0
        var changed = 0
        for ((id, s) in newById) {
            val prev = oldById[id]
            if (prev == null) added++
            else if (!prev.sameAs(s)) changed++
        }
        for (id in oldById.keys) {
            if (id !in newById) removed++
        }
        return ShiftDiff(added, removed, changed)
    }
}
