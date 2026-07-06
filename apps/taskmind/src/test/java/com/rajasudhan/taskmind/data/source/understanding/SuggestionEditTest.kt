package com.rajasudhan.taskmind.data.source.understanding

import com.rajasudhan.taskmind.data.source.ParsedSchedule
import com.rajasudhan.taskmind.testutil.aSuggestion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

/** The pure edit-patch apply (#115): set / clear / reject / leave-unchanged, driven by key presence. */
class SuggestionEditTest {

    private val base = aSuggestion(
        extractedTitle = "Call mom", type = "reminder",
        dueDate = "2026-06-10", dueTime = "09:00", priority = "normal", location = null, recurrence = null,
    )

    @Test
    fun setsOneField_leavingTheRestUntouched() {
        val r = SuggestionEdit.apply(base, mapOf("priority" to "high"))
        assertEquals("high", r.updated.priority)
        assertEquals("Call mom", r.updated.extractedTitle) // untouched
        assertEquals("2026-06-10", r.updated.dueDate)       // untouched
        assertEquals(listOf(FieldChange("Priority", "normal", "high")), r.changes)
    }

    @Test
    fun absentKey_isNotAClear() {
        val r = SuggestionEdit.apply(base, mapOf("title" to "Ring mum"))
        assertEquals("2026-06-10", r.updated.dueDate) // date not present in patch → kept
        assertEquals("Ring mum", r.updated.extractedTitle)
    }

    @Test
    fun presentNull_clearsANullableField() {
        val r = SuggestionEdit.apply(base, mapOf("due_date" to null))
        assertNull(r.updated.dueDate)
        assertEquals(listOf(FieldChange("Date", "2026-06-10", null)), r.changes)
    }

    @Test
    fun invalidValue_isRejected_notApplied_andNeverClears() {
        val r = SuggestionEdit.apply(base, mapOf("due_date" to "next friday", "type" to "gibberish"))
        assertEquals("2026-06-10", r.updated.dueDate) // garbled date must not wipe a good one
        assertEquals("reminder", r.updated.type)
        assertFalse(r.hasChanges)
    }

    @Test
    fun setsLocationAndRecurrence_normalisingCase() {
        val r = SuggestionEdit.apply(base, mapOf("location" to "Indiranagar branch", "recurrence" to "Weekly"))
        assertEquals("Indiranagar branch", r.updated.location)
        assertEquals("weekly", r.updated.recurrence)
        assertEquals(2, r.changes.size)
    }

    @Test
    fun noOpPatch_reportsNoChanges() {
        val r = SuggestionEdit.apply(base, mapOf("priority" to "normal", "type" to "reminder"))
        assertFalse(r.hasChanges)
    }

    @Test
    fun withDeterministicDates_mergesTheParsersDateTimeRecurrence() {
        val parsed = ParsedSchedule(date = LocalDate.of(2026, 6, 12), time = LocalTime.of(18, 0), recurrence = "weekly")
        val merged = SuggestionEdit.withDeterministicDates(mapOf("priority" to "high"), parsed)
        assertEquals("2026-06-12", merged["due_date"])
        assertEquals("18:00", merged["due_time"])
        assertEquals("weekly", merged["recurrence"])
        assertEquals("high", merged["priority"]) // the LLM's non-date change is preserved
    }

    @Test
    fun withDeterministicDates_emptyParse_returnsPatchUnchanged() {
        val patch = mapOf("priority" to "high")
        assertEquals(patch, SuggestionEdit.withDeterministicDates(patch, ParsedSchedule()))
    }

    // ---- review hardening ----

    @Test
    fun nonStringValue_isRejected_neverClears() {
        // The model returned a bare number for the time and an object for location — a failed cast must
        // NOT be treated as an intentional clear.
        val r = SuggestionEdit.apply(base, mapOf("due_time" to 1800.0, "location" to mapOf("name" to "X")))
        assertEquals("09:00", r.updated.dueTime) // unchanged, not wiped
        assertFalse(r.hasChanges)
    }

    @Test
    fun calendarInvalidDate_isRejected() {
        val r = SuggestionEdit.apply(base, mapOf("due_date" to "2026-02-30")) // shape-valid, not a real date
        assertEquals("2026-06-10", r.updated.dueDate)
        assertFalse(r.hasChanges)
    }

    @Test
    fun singleDigitHourTime_isAcceptedAndZeroPadded() {
        assertEquals("09:30", SuggestionEdit.apply(base, mapOf("due_time" to "9:30")).updated.dueTime)
    }

    @Test
    fun withDeterministicDates_doesNotOverrideAnExplicitClear() {
        val parsed = ParsedSchedule(date = LocalDate.of(2026, 7, 5)) // a stray "today" token in a clearing instruction
        val merged = SuggestionEdit.withDeterministicDates(mapOf("due_date" to null), parsed)
        assertTrue(merged.containsKey("due_date"))
        assertNull(merged["due_date"]) // the explicit clear survives
    }
}
