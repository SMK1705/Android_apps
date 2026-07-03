package com.rajasudhan.taskmind.data.source.understanding

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The tolerant parser that turns a small model's messy output into clean checklist steps. */
class MagicBreakdownTest {

    @Test
    fun parsesACleanJsonArray() {
        val steps = MagicBreakdown.parseSteps("""["Gather documents", "Fill the form", "Submit online"]""")
        assertEquals(listOf("Gather documents", "Fill the form", "Submit online"), steps)
    }

    @Test
    fun stripsCodeFencesAroundJson() {
        val steps = MagicBreakdown.parseSteps("```json\n[\"Book flights\", \"Reserve hotel\"]\n```")
        assertEquals(listOf("Book flights", "Reserve hotel"), steps)
    }

    @Test
    fun fallsBackToNumberedLines_whenNotJson() {
        val steps = MagicBreakdown.parseSteps("1. Wash rice\n2. Boil water\n3. Add rice\n")
        assertEquals(listOf("Wash rice", "Boil water", "Add rice"), steps)
    }

    @Test
    fun fallsBackToBulletedLines() {
        val steps = MagicBreakdown.parseSteps("- Call plumber\n• Get quote\n* Book slot")
        assertEquals(listOf("Call plumber", "Get quote", "Book slot"), steps)
    }

    @Test
    fun dedupesCaseInsensitively_andCapsAtSix() {
        val steps = MagicBreakdown.parseSteps("""["Step A","step a","B","C","D","E","F","G"]""")
        assertEquals(listOf("Step A", "B", "C", "D", "E", "F"), steps) // "step a" dropped, capped at 6
    }

    @Test
    fun handlesEscapedQuotesInsideAStep_withoutSplittingIt() {
        val steps = MagicBreakdown.parseSteps("""["Say \"hi\" to Bob", "Wave goodbye"]""")
        assertEquals(listOf("""Say "hi" to Bob""", "Wave goodbye"), steps)
    }

    @Test
    fun capsOverlyLongSteps() {
        val long = "x".repeat(200)
        val steps = MagicBreakdown.parseSteps("""["$long","short"]""")
        assertTrue(steps[0].length <= 80)
        assertEquals("short", steps[1])
    }

    @Test
    fun truncatedOpenArray_salvagesTheCompleteQuotedItems_withoutBracketJunk() {
        // MAX_TOKENS cut the array off mid-item (no closing ']'); the complete quoted steps survive
        // and the leading '[' never leaks into the first step.
        val steps = MagicBreakdown.parseSteps("""["Gather documents", "Fill the form", "Submit onl""")
        assertEquals(listOf("Gather documents", "Fill the form"), steps)
    }

    @Test
    fun closedButUnquotedArray_stripsStrayBracketsFromFirstAndLastStep() {
        // A small model emits a bracketed comma list without quotes; '[' and ']' must not cling to
        // the first/last step.
        val steps = MagicBreakdown.parseSteps("[wash rice, boil water, add rice]")
        assertEquals(listOf("wash rice", "boil water", "add rice"), steps)
    }

    @Test
    fun preservesLegitimateBracketsInsideAStep() {
        // Bracket cleanup is only for stray array delimiters — a step whose text legitimately begins
        // or ends with a bracket must survive intact (regression guard for the per-item trim removal).
        assertEquals(
            listOf("Water plants [balcony]", "Call bank"),
            MagicBreakdown.parseSteps("""["Water plants [balcony]", "Call bank"]"""),
        )
        assertEquals(
            listOf("[urgent] Review PR", "Merge branch"),
            MagicBreakdown.parseSteps("""["[urgent] Review PR", "Merge branch"]"""),
        )
        assertEquals(
            listOf("Review PR [urgent]", "Merge branch"),
            MagicBreakdown.parseSteps("1. Review PR [urgent]\n2. Merge branch"),
        )
    }

    @Test
    fun stripsCodeFencesWithATrailingNewline() {
        val steps = MagicBreakdown.parseSteps("```json\n[\"Book flights\", \"Reserve hotel\"]\n```\n")
        assertEquals(listOf("Book flights", "Reserve hotel"), steps)
    }

    @Test
    fun emptyOrGarbage_yieldsNothingUsable() {
        assertTrue(MagicBreakdown.parseSteps("").isEmpty())
        assertTrue(MagicBreakdown.parseSteps("   \n  ").isEmpty())
        // A single line isn't a checklist.
        assertEquals(listOf("Just one thing"), MagicBreakdown.parseSteps("Just one thing"))
    }
}
