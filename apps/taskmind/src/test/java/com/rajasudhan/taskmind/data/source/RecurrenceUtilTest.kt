package com.rajasudhan.taskmind.data.source

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDateTime
import java.time.LocalTime

/** Unit tests for the pure recurrence date maths. */
class RecurrenceUtilTest {

    @Test
    fun dailyAddsOneDay() {
        assertEquals("2026-06-16", RecurrenceUtil.next("2026-06-15", "Daily"))
    }

    @Test
    fun weeklyAddsSevenDays() {
        assertEquals("2026-06-22", RecurrenceUtil.next("2026-06-15", "weekly"))
    }

    @Test
    fun monthlyAddsOneMonthAndHandlesRollover() {
        assertEquals("2026-07-15", RecurrenceUtil.next("2026-06-15", "Monthly"))
        // Jan 31 + 1 month clamps to Feb 28 (2026 is not a leap year).
        assertEquals("2026-02-28", RecurrenceUtil.next("2026-01-31", "monthly"))
    }

    @Test
    fun noneOrBadInputReturnsNull() {
        assertNull(RecurrenceUtil.next("2026-06-15", "None"))
        assertNull(RecurrenceUtil.next("2026-06-15", null))
        assertNull(RecurrenceUtil.next("not-a-date", "Daily"))
    }

    // ---------------- firstFutureOccurrence (late-fire / reboot catch-up) ----------------

    private val now = LocalDateTime.of(2026, 6, 15, 12, 0) // Mon 2026-06-15, noon

    @Test
    fun futureOccurrenceIsReturnedUnchanged() {
        // A slot already in the future is the next one — don't skip it.
        assertEquals("2026-06-20", RecurrenceUtil.firstFutureOccurrence("2026-06-20", "09:00", "daily", now))
        // Later today, before now passes it, also stays put.
        assertEquals("2026-06-15", RecurrenceUtil.firstFutureOccurrence("2026-06-15", "18:00", "daily", now))
    }

    @Test
    fun dailyCatchesUpPastTodaysElapsedTime() {
        // 09:00 today already passed (now is noon) → next day.
        assertEquals("2026-06-16", RecurrenceUtil.firstFutureOccurrence("2026-06-15", "09:00", "daily", now))
        // Stale by days (device was off) → first future day, not the original.
        assertEquals("2026-06-16", RecurrenceUtil.firstFutureOccurrence("2026-06-10", "09:00", "daily", now))
    }

    @Test
    fun weeklyAndMonthlyCatchUp() {
        // 06-01,06-08,06-15(09:00<=noon) → 06-22.
        assertEquals("2026-06-22", RecurrenceUtil.firstFutureOccurrence("2026-06-01", "09:00", "weekly", now))
        // 03-15,04-15,05-15,06-15(09:00<=noon) → 07-15.
        assertEquals("2026-07-15", RecurrenceUtil.firstFutureOccurrence("2026-03-15", "09:00", "monthly", now))
    }

    @Test
    fun singleDigitHourParses() {
        // "9:30" (no leading zero) must parse; 09:30 is after 09:00 here.
        val nine = LocalDateTime.of(2026, 6, 15, 9, 0)
        assertEquals("2026-06-15", RecurrenceUtil.firstFutureOccurrence("2026-06-15", "9:30", "daily", nine))
    }

    @Test
    fun missingOrBadTimeFallsBackToMidnight() {
        // No time → treat as midnight, so "today" has already passed by noon → tomorrow.
        assertEquals("2026-06-16", RecurrenceUtil.firstFutureOccurrence("2026-06-15", null, "daily", now))
        // Malformed time → midnight fallback, same result (never throws).
        assertEquals("2026-06-16", RecurrenceUtil.firstFutureOccurrence("2026-06-15", "99:99", "daily", now))
    }

    @Test
    fun nonRepeatingOrBadInputReturnsNull() {
        assertNull(RecurrenceUtil.firstFutureOccurrence("2026-06-20", "09:00", "none", now))
        assertNull(RecurrenceUtil.firstFutureOccurrence("2026-06-20", "09:00", null, now))
        assertNull(RecurrenceUtil.firstFutureOccurrence("not-a-date", "09:00", "daily", now))
    }

    // ---------------- parseTime (shared tolerant parser used to arm alarms/calendar) ----------------

    @Test
    fun parseTimeAcceptsSingleAndDoubleDigitHours() {
        // Both forms occur in stored data (sanitizeTime keeps the raw \d{1,2}:\d{2} match). A strict
        // HH parser would reject "9:30" and silently drop the alarm — the bug this guards against.
        assertEquals(LocalTime.of(9, 30), RecurrenceUtil.parseTime("9:30"))
        assertEquals(LocalTime.of(9, 30), RecurrenceUtil.parseTime("09:30"))
        assertEquals(LocalTime.of(14, 5), RecurrenceUtil.parseTime("14:05"))
        assertEquals(LocalTime.of(0, 0), RecurrenceUtil.parseTime(" 0:00 ")) // trimmed
    }

    @Test
    fun parseTimeRejectsJunk() {
        assertNull(RecurrenceUtil.parseTime(null))
        assertNull(RecurrenceUtil.parseTime(""))
        assertNull(RecurrenceUtil.parseTime("99:99")) // out of range
        assertNull(RecurrenceUtil.parseTime("evening"))
        assertNull(RecurrenceUtil.parseTime("9-30"))
        assertNull(RecurrenceUtil.parseTime("2026-06-11T09:30")) // a datetime, not a time
    }
}
