package com.adhdkiller.app.core

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.abs

/**
 * Small time helpers shared by the schedule, lockdown and statistics code.
 *
 * Weekday bit masks: bit 0 = Monday … bit 6 = Sunday (ISO order).
 */
object Time {

    val zone: ZoneId get() = ZoneId.systemDefault()

    fun nowMillis(): Long = System.currentTimeMillis()

    fun today(): LocalDate = LocalDate.now(zone)

    fun now(): LocalDateTime = LocalDateTime.now(zone)

    fun epochDay(date: LocalDate): Long = date.toEpochDay()

    fun dateOf(epochDay: Long): LocalDate = LocalDate.ofEpochDay(epochDay)

    fun minuteOfDay(time: LocalTime): Int = time.hour * 60 + time.minute

    fun timeOfDay(minute: Int): LocalTime =
        LocalTime.of((minute / 60).coerceIn(0, 23), (minute % 60).coerceIn(0, 59))

    /** "22:30" */
    fun formatMinute(minute: Int): String {
        val m = ((minute % 1440) + 1440) % 1440
        return String.format(Locale.US, "%02d:%02d", m / 60, m % 60)
    }

    /** "1h 25m", "12m", "45s" */
    fun formatDuration(millis: Long): String {
        val totalMinutes = millis / 60_000
        val hours = totalMinutes / 60
        val minutes = totalMinutes % 60
        return when {
            hours > 0 -> "${hours}h ${minutes}m"
            minutes > 0 -> "${minutes}m"
            else -> "${(millis / 1000).coerceAtLeast(0)}s"
        }
    }

    /** "2h 05m" style countdown used by the lockdown screen. */
    fun formatCountdown(millis: Long): String {
        val total = (millis / 1000).coerceAtLeast(0)
        val h = total / 3600
        val m = (total % 3600) / 60
        val s = total % 60
        return if (h > 0) String.format(Locale.US, "%d:%02d:%02d", h, m, s)
        else String.format(Locale.US, "%02d:%02d", m, s)
    }

    fun formatDate(millis: Long): String =
        Instant.ofEpochMilli(millis).atZone(zone).toLocalDate()
            .format(DateTimeFormatter.ofPattern("d MMM yyyy", Locale.getDefault()))

    fun formatDateTime(millis: Long): String =
        Instant.ofEpochMilli(millis).atZone(zone).format(DateTimeFormatter.ofPattern("d MMM · HH:mm", Locale.getDefault()))

    fun dayName(day: DayOfWeek): String = day.getDisplayName(TextStyle.SHORT, Locale.getDefault())

    fun dayNameFull(day: DayOfWeek): String = day.getDisplayName(TextStyle.FULL, Locale.getDefault())

    /** Bit for the given weekday. */
    fun bit(day: DayOfWeek): Int = 1 shl (day.value - 1)

    fun maskContains(mask: Int, day: DayOfWeek): Boolean = (mask and bit(day)) != 0

    fun maskCount(mask: Int): Int = Integer.bitCount(mask)

    /** Short label list for a weekday mask, e.g. "Mon, Wed, Fri". */
    fun maskLabels(mask: Int): List<String> =
        DayOfWeek.entries.filter { maskContains(mask, it) }.map { dayName(it) }

    /** ISO day-of-week (1 = Monday … 7 = Sunday) → bit index. */
    fun bitForIsoDay(isoDay: Int): Int = 1 shl (isoDay - 1)

    fun isoDayOf(date: LocalDate): Int = date.dayOfWeek.value

    /** Start of the given day in epoch millis. */
    fun startOfDayMillis(date: LocalDate): Long =
        date.atStartOfDay(zone).toInstant().toEpochMilli()

    fun startOfTodayMillis(): Long = startOfDayMillis(today())

    fun daysBetween(from: Long, to: Long): Int = abs(to - from).toInt()
}
