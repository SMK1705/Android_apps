package com.rajasudhan.taskmind.ui.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The date/time category helpers. The load-bearing case is a single-digit-hour due_time like "9:30"
 * (which the pipeline accepts and stores): strict LocalTime.parse used to throw on it, so an overdue
 * item read as not-overdue — no red badge, and dropped from the Daily Brief's overdue count.
 */
class CategoryTest {

    @Test
    fun isOverdue_honoursSingleDigitHourTimes() {
        assertTrue(isOverdue("2000-01-01", "9:30"))   // long past -> overdue
        assertFalse(isOverdue("2999-12-31", "9:30"))  // far future -> not
    }

    @Test
    fun isOverdue_twoDigitHourStillWorks() {
        assertTrue(isOverdue("2000-01-01", "09:30"))
        assertFalse(isOverdue("2999-12-31", "23:59"))
    }

    @Test
    fun isOverdue_dateOnly_usesEndOfDay() {
        assertTrue(isOverdue("2000-01-01", null))
        assertFalse(isOverdue("2999-12-31", null))
    }

    @Test
    fun isOverdue_nullOrUnparseableDate_isNotOverdue() {
        assertFalse(isOverdue(null, "9:30"))
        assertFalse(isOverdue("not-a-date", "9:30"))
        // An unparseable time on a valid date falls back to end-of-day, not a crash.
        assertTrue(isOverdue("2000-01-01", "quarter past nine"))
    }

    @Test
    fun overdueLabel_honoursSingleDigitHour() {
        assertNotNull(overdueLabel("2000-01-01", "9:30")) // past -> an "ago" label
        assertNull(overdueLabel("2999-12-31", "9:30"))    // future -> null
    }

    @Test
    fun categoryFor_escalatesAnOverdueReminderWithSingleDigitHour() {
        assertEquals(OverdueCategory, categoryFor("reminder", "2000-01-01", "9:30"))
        assertEquals(ReminderCategory, categoryFor("reminder", "2999-12-31", "9:30"))
    }
}
