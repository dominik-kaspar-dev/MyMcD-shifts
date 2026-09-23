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
import eu.mymcd.shifts.store.SecureStore
import eu.mymcd.shifts.store.SettingsStore
import eu.mymcd.shifts.util.LocaleUtil
import eu.mymcd.shifts.util.TimeUtil
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

object Notifier {

    const val CHANNEL_SHIFTS = "shift_changes"
    const val CHANNEL_REMINDERS = "shift_reminders"

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val mgr = context.getSystemService(NotificationManager::class.java)
            mgr.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_SHIFTS,
                    context.getString(R.string.notif_channel_name),
                    NotificationManager.IMPORTANCE_DEFAULT
                ).apply {
                    description = context.getString(R.string.notif_channel_desc)
                }
            )
            mgr.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_REMINDERS,
                    context.getString(R.string.notif_reminder_channel_name),
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = context.getString(R.string.notif_reminder_channel_desc)
                }
            )
        }
    }

    fun canNotify(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            androidx.core.content.ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.POST_NOTIFICATIONS
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED

    private fun openAppPending(context: Context): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

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
        if (!SettingsStore(context).legalAccepted) return

        val settings = SettingsStore(context)
        val parts = mutableListOf<String>()
        if (added > 0 && settings.notifyOnAdd) {
            parts.add(context.getString(R.string.notif_added, added))
        }
        if (removed > 0 && settings.notifyOnRemove) {
            parts.add(context.getString(R.string.notif_removed, removed))
        }
        if (changed > 0 && settings.notifyOnChange) {
            parts.add(context.getString(R.string.notif_changed, changed))
        }
        if (parts.isEmpty()) return

        val summary = parts.joinToString(" · ")
        val nextLine = next?.let {
            context.getString(R.string.notif_next, it.date, it.from.take(16), it.to.take(16))
        } ?: context.getString(R.string.no_upcoming_shifts)

        val notification = NotificationCompat.Builder(context, CHANNEL_SHIFTS)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.notif_title, email))
            .setContentText(summary)
            .setStyle(NotificationCompat.BigTextStyle().bigText("$summary\n$nextLine"))
            .setContentIntent(openAppPending(context))
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

        try {
            NotificationManagerCompat.from(context)
                .notify(("plan_$email").hashCode(), notification)
        } catch (_: SecurityException) {
        }
    }

    fun notifyShiftReminder(
        context: Context,
        shift: Shift,
        isDayBefore: Boolean
    ) {
        ensureChannel(context)
        if (!canNotify(context)) return
        if (!SettingsStore(context).legalAccepted) return

        val settings = SettingsStore(context)
        if (isDayBefore && !settings.notifyDayBefore) return
        if (!isDayBefore && !settings.notifyDayOf) return

        val lang = LocaleUtil.resolveLanguage(
            SecureStore(context).language,
            context.resources.configuration.locales[0]
        )
        val locCtx = LocaleUtil.localizedContext(context, lang)
        val locale = locCtx.resources.configuration.locales[0]
        val hm = DateTimeFormatter.ofPattern("HH:mm")

        val title = locCtx.getString(
            if (isDayBefore) R.string.remind_before_title else R.string.remind_dayof_title
        )
        val hours = TimeUtil.hoursBetween(shift)
        val hoursPart = hours?.let { TimeUtil.formatHours(it, locale) } ?: ""
        val body = locCtx.getString(
            R.string.remind_body,
            shift.date,
            TimeUtil.parseLocal(shift.from)?.format(hm) ?: shift.from.takeLast(5),
            TimeUtil.parseLocal(shift.to)?.format(hm) ?: shift.to.takeLast(5),
            hoursPart
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_REMINDERS)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(openAppPending(context))
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()

        try {
            NotificationManagerCompat.from(context)
                .notify(
                    ("remind_${shift.id}_${if (isDayBefore) "b" else "d"}").hashCode(),
                    notification
                )
        } catch (_: SecurityException) {
        }
    }

    data class ShiftDiff(val added: Int, val removed: Int, val changed: Int) {
        val hasChange: Boolean get() = added > 0 || removed > 0 || changed > 0
    }

    /**
     * Diff old vs new plan.
     * - "removed" only counts shifts that are still in the future when they disappear.
     *   A past shift aging out of the next-10 window is NOT a removal.
     * - added/changed only count upcoming (or were-upcoming) shifts.
     */
    fun diff(
        old: List<Shift>,
        new: List<Shift>,
        now: LocalDateTime = LocalDateTime.now()
    ): ShiftDiff {
        val oldById = old.associateBy { it.id }
        val newById = new.associateBy { it.id }
        var added = 0
        var removed = 0
        var changed = 0

        for ((id, s) in newById) {
            val prev = oldById[id]
            if (prev == null) {
                if (TimeUtil.isFuture(s, now)) added++
            } else if (!prev.sameAs(s)) {
                val stillFuture = TimeUtil.isFuture(s, now)
                val wasFuture = TimeUtil.isFuture(prev, now)
                if (stillFuture || wasFuture) changed++
            }
        }

        for ((id, s) in oldById) {
            if (id !in newById && TimeUtil.isFuture(s, now)) {
                removed++
            }
        }

        return ShiftDiff(added, removed, changed)
    }
}
