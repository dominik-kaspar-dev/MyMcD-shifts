package eu.mymcd.shifts.util

import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.provider.CalendarContract
import eu.mymcd.shifts.network.Shift
import java.time.LocalDate
import java.time.format.DateTimeFormatter

object ShareUtil {

    fun shareShift(context: Context, shift: Shift) {
        val text = buildString {
            append(shift.date)
            append("  ")
            append(timeOnly(shift.from))
            append(" – ")
            append(timeOnly(shift.to))
            if (!shift.note.isNullOrBlank()) {
                append("\n")
                append(shift.note)
            }
            append("\n(MyMcD Shifts)")
        }
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }
        context.startActivity(Intent.createChooser(intent, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    fun addToCalendar(context: Context, shift: Shift): Boolean {
        return try {
            val start = TimeUtil.parseLocal(shift.from) ?: return false
            val end = TimeUtil.parseLocal(shift.to) ?: return false
            val intent = Intent(Intent.ACTION_INSERT)
                .setData(CalendarContract.Events.CONTENT_URI)
                .putExtra(CalendarContract.Events.TITLE, "McD shift")
                .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, TimeUtil.toEpochMillis(start))
                .putExtra(CalendarContract.EXTRA_EVENT_END_TIME, TimeUtil.toEpochMillis(end))
                .putExtra(
                    CalendarContract.Events.DESCRIPTION,
                    shift.note ?: "MyMcD Shifts"
                )
                .putExtra(CalendarContract.Events.EVENT_TIMEZONE, java.util.TimeZone.getDefault().id)
            context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            true
        } catch (_: Exception) {
            false
        }
    }

    private fun timeOnly(value: String): String =
        TimeUtil.parseLocal(value)?.format(DateTimeFormatter.ofPattern("HH:mm"))
            ?: value.takeLast(5)
}
