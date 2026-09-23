package eu.mymcd.shifts.util

import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

object TimeUtil {

    private val PARSER: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

    fun parseLocal(value: String?): LocalDateTime? {
        if (value.isNullOrBlank()) return null
        return try {
            LocalDateTime.parse(value, PARSER)
        } catch (_: Exception) {
            null
        }
    }

    fun shiftStart(shift: eu.mymcd.shifts.network.Shift): LocalDateTime? = parseLocal(shift.from)
    fun shiftEnd(shift: eu.mymcd.shifts.network.Shift): LocalDateTime? = parseLocal(shift.to)

    fun hoursBetween(shift: eu.mymcd.shifts.network.Shift): Double? {
        val a = shiftStart(shift) ?: return null
        val b = shiftEnd(shift) ?: return null
        if (!b.isAfter(a)) return 0.0
        return Duration.between(a, b).toMinutes() / 60.0
    }

    fun totalHours(shifts: List<eu.mymcd.shifts.network.Shift>): Double =
        shifts.sumOf { hoursBetween(it) ?: 0.0 }

    fun isFuture(shift: eu.mymcd.shifts.network.Shift, now: LocalDateTime = LocalDateTime.now()): Boolean {
        val start = shiftStart(shift) ?: return false
        return !start.isBefore(now)
    }

    fun formatHours(h: Double, locale: Locale): String =
        if (h == h.toLong().toDouble()) "${h.toLong()} h"
        else String.format(locale, "%.1f h", h)

    fun toEpochMillis(ldt: LocalDateTime, zone: ZoneId = ZoneId.systemDefault()): Long =
        ldt.atZone(zone).toInstant().toEpochMilli()

    fun weekdayShort(date: LocalDate, locale: Locale): String =
        date.format(DateTimeFormatter.ofPattern("EEE", locale))
}
