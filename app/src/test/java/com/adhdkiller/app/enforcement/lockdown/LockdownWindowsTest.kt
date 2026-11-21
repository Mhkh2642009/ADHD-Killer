package com.adhdkiller.app.enforcement.lockdown

import com.adhdkiller.app.core.Time
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDateTime

/**
 * The lockdown window matcher decides whether the phone is locked. It has to be exactly right:
 * a window that ends an hour late is a bug the user notices every single day.
 */
class LockdownWindowsTest {

    private fun at(day: DayOfWeek, hour: Int, minute: Int = 0): LocalDateTime {
        // 2024-01-01 is a Monday.
        val monday = java.time.LocalDate.of(2024, 1, 1)
        val date = monday.plusDays((day.value - 1).toLong())
        return LocalDateTime.of(date, java.time.LocalTime.of(hour, minute))
    }

    @Test
    fun `window is active inside the range on an enabled day`() {
        val window = LockdownWindow(
            label = "Deep work",
            daysMask = Time.bit(DayOfWeek.MONDAY),
            startMinute = 9 * 60,
            endMinute = 12 * 60,
        )
        assertTrue(LockdownWindows.isActive(window, at(DayOfWeek.MONDAY, 10)))
        assertTrue(LockdownWindows.isActive(window, at(DayOfWeek.MONDAY, 9, 0)))
        assertFalse(LockdownWindows.isActive(window, at(DayOfWeek.MONDAY, 12, 0)))
        assertFalse(LockdownWindows.isActive(window, at(DayOfWeek.MONDAY, 8, 59)))
    }

    @Test
    fun `window is inactive on a day that is not selected`() {
        val window = LockdownWindow(
            label = "Deep work",
            daysMask = Time.bit(DayOfWeek.MONDAY) or Time.bit(DayOfWeek.WEDNESDAY),
            startMinute = 9 * 60,
            endMinute = 12 * 60,
        )
        assertFalse(LockdownWindows.isActive(window, at(DayOfWeek.TUESDAY, 10)))
        assertTrue(LockdownWindows.isActive(window, at(DayOfWeek.WEDNESDAY, 10)))
    }

    @Test
    fun `window that wraps midnight stays active after midnight`() {
        val window = LockdownWindow(
            label = "Night shift",
            daysMask = Time.bit(DayOfWeek.MONDAY),
            startMinute = 22 * 60,
            endMinute = 6 * 60,
        )
        // Monday 23:00 — the owning day is Monday.
        assertTrue(LockdownWindows.isActive(window, at(DayOfWeek.MONDAY, 23)))
        // Tuesday 02:00 — the owning day is still Monday.
        assertTrue(LockdownWindows.isActive(window, at(DayOfWeek.TUESDAY, 2)))
        // Tuesday 07:00 — the window is over.
        assertFalse(LockdownWindows.isActive(window, at(DayOfWeek.TUESDAY, 7)))
        // Sunday 23:00 — Monday has not started yet.
        assertFalse(LockdownWindows.isActive(window, at(DayOfWeek.SUNDAY, 23)))
    }

    @Test
    fun `all day window covers the whole selected day`() {
        val window = LockdownWindow(
            label = "Full day",
            daysMask = Time.bit(DayOfWeek.SATURDAY),
            startMinute = 0,
            endMinute = 0,
        )
        assertTrue(LockdownWindows.isActive(window, at(DayOfWeek.SATURDAY, 0, 0)))
        assertTrue(LockdownWindows.isActive(window, at(DayOfWeek.SATURDAY, 23, 59)))
        assertFalse(LockdownWindows.isActive(window, at(DayOfWeek.SUNDAY, 0, 0)))
    }

    @Test
    fun `end of an all day window is unknown`() {
        val window = LockdownWindow(label = "Full day", daysMask = 0b1111111, startMinute = 0, endMinute = 0)
        assertNull(LockdownWindows.endOf(window, at(DayOfWeek.MONDAY, 10)))
    }

    @Test
    fun `end of a normal window lands on the same day`() {
        // The window runs 09:00–17:00 and it is 10:00, so the end is seven hours away — not
        // tomorrow evening. A five minute test lockdown used to report an end ~24 hours out.
        val window = LockdownWindow(
            label = "Deep work",
            daysMask = 0b1111111,
            startMinute = 9 * 60,
            endMinute = 17 * 60,
        )
        val end = LockdownWindows.endOf(window, at(DayOfWeek.MONDAY, 10))
        assertEquals(
            java.time.LocalDateTime.of(2024, 1, 1, 17, 0),
            java.time.Instant.ofEpochMilli(end!!).atZone(Time.zone).toLocalDateTime(),
        )
    }

    @Test
    fun `the remaining time of a normal window is the real remaining time`() {
        val window = LockdownWindow(
            label = "Deep work",
            daysMask = 0b1111111,
            startMinute = 9 * 60,
            endMinute = 17 * 60,
        )
        val now = at(DayOfWeek.MONDAY, 10)
        val end = LockdownWindows.endOf(window, now)!!
        val remainingMinutes = (end - now.atZone(Time.zone).toInstant().toEpochMilli()) / 60_000L
        assertEquals(7 * 60L, remainingMinutes)
    }

    @Test
    fun `a five minute window ends five minutes later`() {
        val window = LockdownWindow(
            label = "Test lockdown",
            daysMask = 0b1111111,
            startMinute = 22 * 60 + 10,
            endMinute = 22 * 60 + 15,
        )
        val now = at(DayOfWeek.MONDAY, 22, 11)
        val end = LockdownWindows.endOf(window, now)!!
        val remainingMinutes = (end - now.atZone(Time.zone).toInstant().toEpochMilli()) / 60_000L
        assertEquals(4L, remainingMinutes)
    }

    @Test
    fun `end of a wrapping window lands after midnight`() {
        val window = LockdownWindow(
            label = "Night shift",
            daysMask = 0b1111111,
            startMinute = 22 * 60,
            endMinute = 6 * 60,
        )
        val end = LockdownWindows.endOf(window, at(DayOfWeek.MONDAY, 23))
        assertEquals(
            java.time.LocalDateTime.of(2024, 1, 2, 6, 0),
            java.time.Instant.ofEpochMilli(end!!).atZone(Time.zone).toLocalDateTime(),
        )
    }

    @Test
    fun `next start is strictly in the future and on an enabled day`() {
        val window = LockdownWindow(
            label = "Deep work",
            daysMask = Time.bit(DayOfWeek.MONDAY) or Time.bit(DayOfWeek.WEDNESDAY),
            startMinute = 9 * 60,
            endMinute = 12 * 60,
        )
        val now = at(DayOfWeek.MONDAY, 10) // inside the window
        val next = LockdownWindows.nextStart(listOf(window), now)
        assertEquals(
            java.time.LocalDateTime.of(2024, 1, 3, 9, 0),
            java.time.Instant.ofEpochMilli(next!!).atZone(Time.zone).toLocalDateTime(),
        )
    }

    @Test
    fun `next start before the window opens is today`() {
        val window = LockdownWindow(
            label = "Deep work",
            daysMask = Time.bit(DayOfWeek.MONDAY),
            startMinute = 9 * 60,
            endMinute = 12 * 60,
        )
        val now = at(DayOfWeek.MONDAY, 7)
        val next = LockdownWindows.nextStart(listOf(window), now)
        assertEquals(
            java.time.LocalDateTime.of(2024, 1, 1, 9, 0),
            java.time.Instant.ofEpochMilli(next!!).atZone(Time.zone).toLocalDateTime(),
        )
    }

    @Test
    fun `describe summarises days and time`() {
        val window = LockdownWindow(
            label = "Deep work",
            daysMask = Time.bit(DayOfWeek.MONDAY) or Time.bit(DayOfWeek.FRIDAY),
            startMinute = 9 * 60,
            endMinute = 17 * 60,
        )
        val text = LockdownWindows.describe(window)
        assertTrue(text.contains("09:00–17:00"))
        assertTrue(text.contains("Mon"))
        assertTrue(text.contains("Fri"))
    }
}
