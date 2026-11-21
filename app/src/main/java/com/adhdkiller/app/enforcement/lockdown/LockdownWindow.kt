package com.adhdkiller.app.enforcement.lockdown

import com.adhdkiller.app.core.Time
import com.adhdkiller.app.data.db.entity.LockdownScheduleEntity
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * A lockdown window: a label, the weekdays it applies to and a start/end time.
 * `startMinute == endMinute` means "all day".
 */
data class LockdownWindow(
    val id: Long = 0,
    val label: String,
    val daysMask: Int,
    val startMinute: Int,
    val endMinute: Int,
    val strict: Boolean = true,
) {
    val isAllDay: Boolean get() = startMinute == endMinute
    val wrapsMidnight: Boolean get() = !isAllDay && startMinute > endMinute
}

object LockdownWindows {

    /** Is this window active at [now]? Handles windows that wrap past midnight. */
    fun isActive(window: LockdownWindow, now: LocalDateTime): Boolean {
        val minute = Time.minuteOfDay(now.toLocalTime())
        return when {
            window.isAllDay -> Time.maskContains(window.daysMask, now.dayOfWeek)
            window.startMinute < window.endMinute ->
                Time.maskContains(window.daysMask, now.dayOfWeek) &&
                    minute in window.startMinute until window.endMinute
            else -> {
                // Wraps midnight: the "owning" day is today after the start, yesterday before the end.
                // Everything between the end and the start is outside the window.
                when {
                    minute >= window.startMinute ->
                        Time.maskContains(window.daysMask, now.dayOfWeek)
                    minute < window.endMinute -> {
                        val previousDay = now.toLocalDate().minusDays(1).dayOfWeek
                        Time.maskContains(window.daysMask, previousDay)
                    }
                    else -> false
                }
            }
        }
    }

    /** The first window that is active at [now]. */
    fun activeWindow(windows: List<LockdownWindow>, now: LocalDateTime = Time.now()): LockdownWindow? =
        windows.firstOrNull { isActive(it, now) }

    fun activeEntity(
        schedules: List<LockdownScheduleEntity>,
        now: LocalDateTime = Time.now(),
    ): LockdownScheduleEntity? = schedules.firstOrNull {
        isActive(
            LockdownWindow(it.id, it.label, it.daysMask, it.startMinute, it.endMinute, it.strict),
            now,
        )
    }

    /**
     * The moment the current window ends, in epoch millis.
     * Returns null for an all-day window.
     *
     * The end lands on the current day as long as the end of the window is still ahead of us —
     * for a window that wraps past midnight that means the small hours of the same calendar
     * day, for a normal window it means the same evening.
     */
    fun endOf(window: LockdownWindow, now: LocalDateTime = Time.now()): Long? {
        if (window.isAllDay) return null
        val date = now.toLocalDate()
        val endDate = if (Time.minuteOfDay(now.toLocalTime()) < window.endMinute) {
            date
        } else {
            date.plusDays(1)
        }
        return endDate.atTime(LocalTime.ofSecondOfDay(window.endMinute * 60L)).atZone(Time.zone).toInstant().toEpochMilli()
    }

    /**
     * The next start boundary strictly after [now], in epoch millis.
     * Looks ahead up to 8 days to cover every weekday configuration.
     */
    fun nextStart(windows: List<LockdownWindow>, now: LocalDateTime = Time.now()): Long? {
        val nowMillis = now.atZone(Time.zone).toInstant().toEpochMilli()
        var best: Long? = null
        for (window in windows) {
            if (!window.enabled()) continue
            for (offset in 0..7) {
                val day: LocalDate = now.toLocalDate().plusDays(offset.toLong())
                if (!Time.maskContains(window.daysMask, day.dayOfWeek)) continue
                val start = day.atTime(LocalTime.ofSecondOfDay(window.startMinute * 60L))
                    .atZone(Time.zone).toInstant().toEpochMilli()
                if (start > nowMillis && (best == null || start < best)) {
                    best = start
                }
            }
        }
        return best
    }

    /** The next end boundary strictly after [now], in epoch millis. */
    fun nextEnd(windows: List<LockdownWindow>, now: LocalDateTime = Time.now()): Long? {
        val nowMillis = now.atZone(Time.zone).toInstant().toEpochMilli()
        var best: Long? = null
        for (window in windows) {
            if (!window.enabled()) continue
            for (offset in 0..7) {
                val day: LocalDate = now.toLocalDate().plusDays(offset.toLong())
                if (!Time.maskContains(window.daysMask, day.dayOfWeek)) continue
                val end = day.atTime(LocalTime.ofSecondOfDay(window.endMinute * 60L))
                    .atZone(Time.zone).toInstant().toEpochMilli()
                if (end > nowMillis && (best == null || end < best)) {
                    best = end
                }
            }
        }
        return best
    }

    private fun LockdownWindow.enabled(): Boolean = true

    /** Human readable summary, e.g. "Mon–Fri · 09:00–17:00". */
    fun describe(window: LockdownWindow): String {
        val days = Time.maskLabels(window.daysMask)
        val dayText = when {
            days.isEmpty() -> "No days"
            days.size == 7 -> "Every day"
            else -> days.joinToString(", ")
        }
        val timeText = if (window.isAllDay) {
            "All day"
        } else {
            "${Time.formatMinute(window.startMinute)}–${Time.formatMinute(window.endMinute)}"
        }
        return "$dayText · $timeText"
    }

    fun dayOrder(day: DayOfWeek): Int = day.value
}
