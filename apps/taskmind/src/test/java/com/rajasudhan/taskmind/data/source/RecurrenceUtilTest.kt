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

    // ---------------- monthly anchor (#177: no drift to the 28th) ----------------

    @Test
    fun monthlyWithoutAnchor_stillDriftsAfterAShortMonth() {
        // Legacy behaviour (no anchor): a 31st reminder clamps to Feb 28, then stays stuck on the 28th.
        assertEquals("2026-02-28", RecurrenceUtil.next("2026-01-31", "monthly"))
        assertEquals("2026-03-28", RecurrenceUtil.next("2026-02-28", "monthly")) // the drift
    }

    @Test
    fun monthlyWithAnchor_keepsTheIntendedDayOfMonth() {
        // Anchored to the 31st: clamps only where the month is short, then returns to 31/30.
        assertEquals("2026-02-28", RecurrenceUtil.next("2026-01-31", "monthly", anchorDay = 31))
        assertEquals("2026-03-31", RecurrenceUtil.next("2026-02-28", "monthly", anchorDay = 31)) // NOT the 28th
        assertEquals("2026-04-30", RecurrenceUtil.next("2026-03-31", "monthly", anchorDay = 31))
        assertEquals("2026-05-31", RecurrenceUtil.next("2026-04-30", "monthly", anchorDay = 31))
    }

    @Test
    fun monthlyAnchor_day30_neverBecomes31() {
        assertEquals("2026-02-28", RecurrenceUtil.next("2026-01-30", "monthly", anchorDay = 30))
        assertEquals("2026-03-30", RecurrenceUtil.next("2026-02-28", "monthly", anchorDay = 30)) // 30, not 31
    }

    @Test
    fun firstFutureOccurrence_monthlyAnchor_landsOnTheAnchoredDay() {
        val now = LocalDateTime.of(2026, 4, 15, 12, 0)
        // A 31st reminder from Jan 31, caught up to April: April has 30 days -> the 30th, not the 28th.
        assertEquals("2026-04-30", RecurrenceUtil.firstFutureOccurrence("2026-01-31", "09:00", "monthly", now, anchorDay = 31))
    }

    @Test
    fun dayOfMonth_extractsOrNull() {
        assertEquals(31, RecurrenceUtil.dayOfMonth("2026-01-31"))
        assertEquals(1, RecurrenceUtil.dayOfMonth("2026-06-01"))
        assertNull(RecurrenceUtil.dayOfMonth(null))
        assertNull(RecurrenceUtil.dayOfMonth("not-a-date"))
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

    // ---------------- nextFromCompletion (#124: completion-based recurrence) ----------------

    @Test
    fun nextFromCompletion_advancesFromWhenItWasFinished_notTheDueDate() {
        // Finished today (noon) → the next daily/weekly slot is measured from the completion day.
        assertEquals("2026-06-16", RecurrenceUtil.nextFromCompletion("2026-06-15", "09:00", "daily", now))
        assertEquals("2026-06-22", RecurrenceUtil.nextFromCompletion("2026-06-15", "09:00", "weekly", now))
        assertEquals("2026-07-15", RecurrenceUtil.nextFromCompletion("2026-06-15", "09:00", "monthly", now, anchorDay = 15))
    }

    @Test
    fun nextFromCompletion_stillSkipsToAFutureSlot_whenCompletionIsOld() {
        // A stale completion date (monthly, anchor 31, finished end of May) rolls to the first slot after
        // now (mid-June): June has 30 days → the 30th.
        assertEquals("2026-06-30", RecurrenceUtil.nextFromCompletion("2026-05-31", "09:00", "monthly", now, anchorDay = 31))
    }

    @Test
    fun nextFromCompletion_nullForNonRepeating() {
        assertNull(RecurrenceUtil.nextFromCompletion("2026-06-15", "09:00", "none", now))
        assertNull(RecurrenceUtil.nextFromCompletion("2026-06-15", "09:00", null, now))
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
