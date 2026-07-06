package com.rajasudhan.taskmind.data.source.understanding

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Pure tests for the strict lexical guard behind the non-destructive near-duplicate flag (#145). */
class NearDuplicateTest {

    // ---- the items the issue says must NEVER be flagged (distinct, high semantic similarity) ----

    @Test
    fun distinctItemsAreNotFlagged() {
        assertFalse(dup("Book room A", "Book room B"))       // single-letter identifiers kept
        assertFalse(dup("Buy 2 tickets", "Buy 4 tickets"))   // digits kept
        assertFalse(dup("Schedule 1:1 with Alice", "Schedule 1:1 with Bob"))
        assertFalse(dup("Call Alice", "Call Bob"))
        assertFalse(dup("Send Alice the Q3 report", "Send Bob the Q4 report"))
    }

    // ---- genuine re-captures (reordered / re-punctuated / stopword-only diffs) ARE flagged ----

    @Test
    fun genuineRecapturesAreFlagged() {
        assertTrue(dup("Pay the rent", "Pay rent"))                       // stopword-only diff
        assertTrue(dup("Buy milk, eggs and bread", "Buy bread eggs milk")) // reordered
        assertTrue(dup("Call the plumber about the leak", "Call plumber leak"))
    }

    @Test
    fun datesGateTheFlag() {
        // Same text but conflicting explicit dates -> not the same occurrence.
        assertFalse(
            NearDuplicate.isLikelyDuplicate("Pay rent", "2026-07-01", "Pay rent", "2026-08-01")
        )
        // One side undated is compatible.
        assertTrue(
            NearDuplicate.isLikelyDuplicate("Pay rent", "2026-07-01", "Pay rent", null)
        )
    }

    @Test
    fun emptyOrPunctuationOnlyTextNeverFlags() {
        assertFalse(dup("", "anything"))
        assertFalse(dup("!!!", "Buy milk"))
    }

    private fun dup(a: String, b: String) = NearDuplicate.isLikelyDuplicate(a, null, b, null)
}
