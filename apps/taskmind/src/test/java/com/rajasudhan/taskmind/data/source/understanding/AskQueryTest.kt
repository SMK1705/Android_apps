package com.rajasudhan.taskmind.data.source.understanding

import com.rajasudhan.taskmind.testutil.aNote
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/** Pure tests for the Ask TaskMind slot -> note filter (#128). */
class AskQueryTest {

    private val today = LocalDate.of(2026, 7, 8) // a Wednesday

    private fun intent(type: String? = null, tag: String? = null, window: String? = null, keyword: String? = null) =
        AskIntent(type = type, tag = tag, window = window, keyword = keyword)

    @Test
    fun typeFilter_canonicalisesSynonyms() {
        assertTrue(AskQuery.matches(aNote(type = "todo"), intent(type = "todo"), today))
        assertTrue(AskQuery.matches(aNote(type = "todo"), intent(type = "tasks"), today)) // "tasks" -> "todo"
        assertFalse(AskQuery.matches(aNote(type = "note"), intent(type = "todo"), today))
    }

    @Test
    fun tagFilter_isCaseInsensitive() {
        assertTrue(AskQuery.matches(aNote(tags = "Work,Money"), intent(tag = "money"), today))
        assertFalse(AskQuery.matches(aNote(tags = "Work"), intent(tag = "Money"), today))
    }

    @Test
    fun keyword_matchesTitleSummaryOrBody() {
        assertTrue(AskQuery.matches(aNote(title = "Call the electrician"), intent(keyword = "electrician"), today))
        assertTrue(AskQuery.matches(aNote(title = "x", summary = "quote from the electrician"), intent(keyword = "electrician"), today))
        assertFalse(AskQuery.matches(aNote(title = "Call plumber", summary = "", body = ""), intent(keyword = "electrician"), today))
    }

    @Test
    fun dateWindows() {
        assertTrue(AskQuery.inWindow("2026-07-08", "today", today))
        assertFalse(AskQuery.inWindow("2026-07-09", "today", today))
        assertTrue(AskQuery.inWindow("2026-07-09", "tomorrow", today))
        assertTrue(AskQuery.inWindow("2026-07-01", "overdue", today))  // before today
        assertFalse(AskQuery.inWindow("2026-07-08", "overdue", today)) // today is not overdue
        assertTrue(AskQuery.inWindow("2026-07-10", "upcoming", today))
        assertTrue(AskQuery.inWindow("2026-07-14", "this_week", today))  // within 7 days
        assertFalse(AskQuery.inWindow("2026-07-20", "this_week", today)) // beyond 7 days
    }

    @Test
    fun weekendWindow_isTheUpcomingSatAndSun() {
        // today = Wed 2026-07-08 -> weekend = Sat 2026-07-11, Sun 2026-07-12
        assertTrue(AskQuery.inWindow("2026-07-11", "this_weekend", today))
        assertTrue(AskQuery.inWindow("2026-07-12", "this_weekend", today))
        assertFalse(AskQuery.inWindow("2026-07-10", "this_weekend", today)) // Friday
    }

    @Test
    fun undatedItem_failsDateWindows_butUnknownWindowNeverFilters() {
        assertFalse(AskQuery.inWindow(null, "today", today))
        assertFalse(AskQuery.inWindow(null, "overdue", today))
        assertTrue(AskQuery.inWindow(null, "something_else", today))   // unknown window -> no constraint
        assertTrue(AskQuery.inWindow("2026-07-08", "something_else", today))
    }
}
