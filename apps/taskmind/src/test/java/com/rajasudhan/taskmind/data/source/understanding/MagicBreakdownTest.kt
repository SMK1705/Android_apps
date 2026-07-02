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
    fun emptyOrGarbage_yieldsNothingUsable() {
        assertTrue(MagicBreakdown.parseSteps("").isEmpty())
        assertTrue(MagicBreakdown.parseSteps("   \n  ").isEmpty())
        // A single line isn't a checklist.
        assertEquals(listOf("Just one thing"), MagicBreakdown.parseSteps("Just one thing"))
    }
}
