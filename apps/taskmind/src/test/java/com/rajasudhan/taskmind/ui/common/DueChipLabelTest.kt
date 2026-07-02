package com.rajasudhan.taskmind.ui.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

/** The relative due label on suggestion/note chips. `today` is injected for determinism. */
class DueChipLabelTest {

    // 2026-07-01 is a Wednesday.
    private val today = LocalDate.of(2026, 7, 1)

    @Test
    fun relativeDays_readAsWords() {
        assertEquals("Today", dueChipLabel("2026-07-01", null, today))
        assertEquals("Tomorrow", dueChipLabel("2026-07-02", null, today))
        assertEquals("Yesterday", dueChipLabel("2026-06-30", null, today))
    }

    @Test
    fun withinTheWeek_isTheWeekday() {
        assertEquals("Fri", dueChipLabel("2026-07-03", null, today))   // +2 days
        assertEquals("Tue", dueChipLabel("2026-07-07", null, today))   // +6 days
    }

    @Test
    fun furtherOut_isDayMonth_withYearOnlyWhenDifferent() {
        assertEquals("8 Jul", dueChipLabel("2026-07-08", null, today))     // +7 days, same year
        assertEquals("25 Dec", dueChipLabel("2026-12-25", null, today))
        assertEquals("5 Jan 2027", dueChipLabel("2027-01-05", null, today))
    }

    @Test
    fun timeIsAppended_whenPresent() {
        assertEquals("Today · 14:00", dueChipLabel("2026-07-01", "14:00", today))
        assertEquals("Tomorrow · 09:00", dueChipLabel("2026-07-02", "09:00", today))
    }

    @Test
    fun missingOrMalformedDate_yieldsNull() {
        assertNull(dueChipLabel(null, "09:00", today))
        assertNull(dueChipLabel("not-a-date", null, today))
    }
}
