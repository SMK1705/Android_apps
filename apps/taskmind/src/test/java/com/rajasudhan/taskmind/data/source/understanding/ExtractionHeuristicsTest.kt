package com.rajasudhan.taskmind.data.source.understanding

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Unit tests for the safety-critical filtering/sanitization logic that gates the Inbox. */
class ExtractionHeuristicsTest {

    // ---------------- isLikelyNoise ----------------

    @Test
    fun otpAndVerificationCodesAreNoise() {
        assertTrue(ExtractionHeuristics.isLikelyNoise("Your verification code is 472913"))
        assertTrue(ExtractionHeuristics.isLikelyNoise("123456 is your one-time code"))
        assertTrue(ExtractionHeuristics.isLikelyNoise("Your OTP code: 9988"))
        assertTrue(ExtractionHeuristics.isLikelyNoise("Login code 4821 - do not share"))
    }

    @Test
    fun marketingAndOptOutAreNoise() {
        assertTrue(ExtractionHeuristics.isLikelyNoise("Flat 50% off this weekend!"))
        assertTrue(ExtractionHeuristics.isLikelyNoise("Huge SALE on now"))
        assertTrue(ExtractionHeuristics.isLikelyNoise("Use coupon SAVE20 at checkout"))
        assertTrue(ExtractionHeuristics.isLikelyNoise("Reply STOP to unsubscribe"))
    }

    @Test
    fun genuineActionItemsAreNotNoise() {
        assertFalse(ExtractionHeuristics.isLikelyNoise("Dentist appointment tomorrow at 3pm"))
        assertFalse(ExtractionHeuristics.isLikelyNoise("Can you send me the report by Friday?"))
        assertFalse(ExtractionHeuristics.isLikelyNoise("Lunch with Sam on 2026-06-15"))
        assertFalse(ExtractionHeuristics.isLikelyNoise("Don't forget to call the plumber"))
    }

    @Test
    fun meetingInvitesSurviveTheirUnsubscribeFooter() {
        // Invites & LinkedIn-style mail/notifications carry an opt-out footer; they must still pass.
        assertFalse(ExtractionHeuristics.isLikelyNoise(
            "Alex invited you to a meeting on Friday at 3pm. Manage preferences or unsubscribe here."
        ))
        assertFalse(ExtractionHeuristics.isLikelyNoise(
            "LinkedIn: Priya wants to schedule a call. To stop these emails, unsubscribe."
        ))
        assertFalse(ExtractionHeuristics.isLikelyNoise(
            "Invitation: Q3 planning. When: Mon 10:00. Where: Zoom. Reply STOP to opt out."
        ))
        // A pure promo with no scheduling cue is still noise.
        assertTrue(ExtractionHeuristics.isLikelyNoise("50% off everything — unsubscribe to stop deals"))
    }

    // ---------------- date / time sanitization ----------------

    @Test
    fun sanitizeDateKeepsValidIsoDropsRest() {
        assertEquals("2026-06-11", ExtractionHeuristics.sanitizeDate("2026-06-11"))
        assertNull(ExtractionHeuristics.sanitizeDate("tomorrow"))
        assertNull(ExtractionHeuristics.sanitizeDate("11/06/2026"))
        assertNull(ExtractionHeuristics.sanitizeDate("2026-6-11")) // single-digit month
        assertNull(ExtractionHeuristics.sanitizeDate(null))
    }

    @Test
    fun sanitizeDateRejectsShapeValidButCalendarInvalidDates() {
        // The model can hallucinate a date that matches the shape but isn't a real day; it must be
        // dropped, else it persists on a reminder whose alarm silently never fires.
        assertNull(ExtractionHeuristics.sanitizeDate("2026-02-30")) // Feb 30
        assertNull(ExtractionHeuristics.sanitizeDate("2026-13-01")) // month 13
        assertNull(ExtractionHeuristics.sanitizeDate("2026-04-31")) // Apr has 30 days
        assertNull(ExtractionHeuristics.sanitizeDate("2026-00-10")) // month 0
        assertNull(ExtractionHeuristics.sanitizeDate("2026-06-00")) // day 0
        // Real dates — including a genuine leap day — survive.
        assertEquals("2026-02-28", ExtractionHeuristics.sanitizeDate("2026-02-28"))
        assertEquals("2028-02-29", ExtractionHeuristics.sanitizeDate("2028-02-29"))
    }

    @Test
    fun sanitizeTimeKeepsValidDropsDatetime() {
        assertEquals("9:30", ExtractionHeuristics.sanitizeTime("9:30"))
        assertEquals("14:05", ExtractionHeuristics.sanitizeTime("14:05"))
        // A datetime mistakenly stuffed into due_time must be rejected (matches() = whole-string).
        assertNull(ExtractionHeuristics.sanitizeTime("2026-06-11T09:30"))
        assertNull(ExtractionHeuristics.sanitizeTime("evening"))
        assertNull(ExtractionHeuristics.sanitizeTime(null))
    }

    @Test
    fun sanitizeRecurrenceKeepsKnownRepeatsDropsRest() {
        assertEquals("daily", ExtractionHeuristics.sanitizeRecurrence("daily"))
        assertEquals("weekly", ExtractionHeuristics.sanitizeRecurrence("Weekly")) // case-insensitive
        assertEquals("monthly", ExtractionHeuristics.sanitizeRecurrence("  MONTHLY  ")) // trimmed
        assertNull(ExtractionHeuristics.sanitizeRecurrence("yearly")) // unsupported repeat
        assertNull(ExtractionHeuristics.sanitizeRecurrence("none"))
        assertNull(ExtractionHeuristics.sanitizeRecurrence(""))
        assertNull(ExtractionHeuristics.sanitizeRecurrence(null))
    }

    @Test
    fun sanitizePriorityOnlyKeepsHighFloorsEverythingElseToNormal() {
        assertEquals("high", ExtractionHeuristics.sanitizePriority("high"))
        assertEquals("high", ExtractionHeuristics.sanitizePriority("  High  ")) // trimmed + case-insensitive
        assertEquals("normal", ExtractionHeuristics.sanitizePriority("normal"))
        assertEquals("normal", ExtractionHeuristics.sanitizePriority("low")) // never low from text
        assertEquals("normal", ExtractionHeuristics.sanitizePriority("urgent")) // unknown -> normal
        assertEquals("normal", ExtractionHeuristics.sanitizePriority(""))
        assertEquals("normal", ExtractionHeuristics.sanitizePriority(null))
    }

    // ---------------- JSON fence stripping ----------------

    @Test
    fun stripJsonFencesHandlesAllForms() {
        val expected = "{\"items\": []}"
        assertEquals(expected, ExtractionHeuristics.stripJsonFences("```json\n{\"items\": []}\n```"))
        assertEquals(expected, ExtractionHeuristics.stripJsonFences("```\n{\"items\": []}\n```"))
        assertEquals(expected, ExtractionHeuristics.stripJsonFences("{\"items\": []}"))
        assertEquals(expected, ExtractionHeuristics.stripJsonFences("  {\"items\": []}  "))
    }

    @Test
    fun stripJsonFencesHandlesATrailingNewlineAfterTheClosingFence() {
        // The regression: a trailing newline after ``` made removeSuffix("```") a no-op, leaving a
        // dangling fence that failed JSON parsing and silently dropped the whole extraction.
        val expected = "{\"items\": []}"
        assertEquals(expected, ExtractionHeuristics.stripJsonFences("```json\n{\"items\": []}\n```\n"))
        assertEquals(expected, ExtractionHeuristics.stripJsonFences("```json\n{\"items\": []}\n```  \n"))
        assertEquals(expected, ExtractionHeuristics.stripJsonFences("  ```\n{\"items\": []}\n```\n\n"))
    }

    // ---------------- acceptance threshold ----------------

    @Test
    fun isAcceptableRequiresTitleAndConfidence() {
        assertTrue(ExtractionHeuristics.isAcceptable(LlmItem(title = "Call mom", confidence = 0.9)))
        assertTrue(ExtractionHeuristics.isAcceptable(LlmItem(title = "x", confidence = 0.6))) // at threshold
        assertFalse(ExtractionHeuristics.isAcceptable(LlmItem(title = "", confidence = 0.9))) // no title
        assertFalse(ExtractionHeuristics.isAcceptable(LlmItem(title = "  ", confidence = 0.9))) // blank title
        assertFalse(ExtractionHeuristics.isAcceptable(LlmItem(title = "x", confidence = 0.59))) // below threshold
    }

    // ---------------- dedup predicate ----------------

    @Test
    fun isDuplicateMatchesOnTitleAndDate() {
        val existing = listOf("Call mom" to "2026-06-11", "Pay rent" to null)
        assertTrue(ExtractionHeuristics.isDuplicate("Call mom", "2026-06-11", existing))
        assertTrue(ExtractionHeuristics.isDuplicate("Pay rent", null, existing))
        assertFalse(ExtractionHeuristics.isDuplicate("Call mom", "2026-06-12", existing)) // different date
        assertFalse(ExtractionHeuristics.isDuplicate("Call dad", "2026-06-11", existing)) // different title
        assertFalse(ExtractionHeuristics.isDuplicate("Anything", null, emptyList()))
    }
}
