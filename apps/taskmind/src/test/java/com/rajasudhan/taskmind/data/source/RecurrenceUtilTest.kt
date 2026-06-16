package com.rajasudhan.taskmind.data.source

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

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
}
