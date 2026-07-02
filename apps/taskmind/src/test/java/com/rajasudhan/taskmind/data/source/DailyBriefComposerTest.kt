package com.rajasudhan.taskmind.data.source

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The deterministic morning-brief wording. */
class DailyBriefComposerTest {

    @Test
    fun emptyDay_producesNoBrief() {
        assertNull(DailyBriefComposer.compose(overdue = 0, dueToday = 0, pending = 0, focus = emptyList()))
    }

    @Test
    fun titleLeadsWithOverdue_whenAny() {
        val b = DailyBriefComposer.compose(2, 1, 4, listOf("Pay rent"))!!
        assertEquals("Good morning — 2 overdue", b.title)
    }

    @Test
    fun titleLeadsWithDueToday_whenNoOverdue() {
        val b = DailyBriefComposer.compose(0, 3, 1, listOf("Standup"))!!
        assertEquals("Good morning — 3 due today", b.title)
    }

    @Test
    fun titleLeadsWithReviewQueue_whenNothingDated() {
        val b = DailyBriefComposer.compose(0, 0, 5, emptyList())!!
        assertEquals("Good morning — 5 to review", b.title)
    }

    @Test
    fun bodySummarisesOnlyNonZeroBuckets() {
        val b = DailyBriefComposer.compose(2, 0, 4, emptyList())!!
        assertTrue(b.body.startsWith("2 overdue · 4 to review"))
        // due-today omitted because it's zero
        assertTrue("2 overdue · 4 to review" == b.body || b.body.startsWith("2 overdue · 4 to review\n"))
    }

    @Test
    fun focusLine_namesTopThreeDatedItems_onlyWhenThereIsADayLoad() {
        val b = DailyBriefComposer.compose(1, 1, 0, listOf("Pay rent", "Call dentist", "Submit form", "Extra"))!!
        assertTrue(b.body.contains("Start with: Pay rent, Call dentist, Submit form"))
        assertTrue("caps at 3", !b.body.contains("Extra"))
    }

    @Test
    fun focusLine_omitted_whenOnlyReviewQueue() {
        val b = DailyBriefComposer.compose(0, 0, 3, listOf("Should not appear"))!!
        assertTrue(!b.body.contains("Start with"))
        assertTrue(!b.body.contains("Should not appear"))
    }
}
