package com.rajasudhan.taskmind.data.source.understanding

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** The multi-turn fold behind Ask (#318): a refinement accumulates slots; a topic change resets. */
class AskConversationTest {

    private fun q(type: String? = null, tag: String? = null, window: String? = null, status: String? = null, keyword: String? = null) =
        AskIntent(action = "query", type = type, tag = tag, window = window, status = status, keyword = keyword)

    @Test
    fun emptyConversation_hasNoContext() {
        assertNull(AskConversation().context())
    }

    @Test
    fun threeStepRefinement_keepsTheEarliestSlots() {
        val c = AskConversation()
        c.record(q(type = "todo", window = "overdue"))     // "anything overdue?"
        c.record(q(type = "todo", tag = "Work"))           // "just the Work ones" (model omits window)
        c.record(q(type = "todo", status = "done"))        // "which of those are done?" (omits window + tag)

        // The fold re-injects what later turns dropped: overdue (turn 1) and Work (turn 2) both survive.
        val ctx = c.context()!!
        assertEquals("todo", ctx.type)
        assertEquals("overdue", ctx.window)
        assertEquals("Work", ctx.tag)
        assertEquals("done", ctx.status)
    }

    @Test
    fun aLaterTurnOverridesTheSameSlot_mostRecentWins() {
        val c = AskConversation()
        c.record(q(type = "todo", window = "overdue"))
        c.record(q(type = "todo", window = "upcoming")) // "what about next week?"

        assertEquals("upcoming", c.context()!!.window) // narrowing the SAME slot is a refine, newest wins
    }

    @Test
    fun aTopicChange_resetsSoStaleSlotsDoNotCarry() {
        val c = AskConversation()
        c.record(q(type = "todo", window = "overdue"))
        c.record(q(type = "note", tag = "Health")) // "show my health notes" — different subject

        val ctx = c.context()!!
        assertEquals("note", ctx.type)
        assertEquals("Health", ctx.tag)
        assertNull(ctx.window) // the old overdue window must NOT leak into the new topic
    }

    @Test
    fun aDifferentTag_alsoCountsAsATopicChange() {
        val c = AskConversation()
        c.record(q(type = "todo", tag = "Work", window = "overdue"))
        c.record(q(type = "todo", tag = "Home")) // switched from Work to Home

        assertEquals("Home", c.context()!!.tag)
        assertNull(c.context()!!.window) // reset — not an accumulation onto the Work chain
    }

    @Test
    fun aNullIntent_search_resetsTheContext() {
        val c = AskConversation()
        c.record(q(type = "todo", window = "overdue"))
        c.record(null) // a keyword search resolves no structured intent — fresh topic

        assertNull(c.context())
    }

    @Test
    fun aCreateTurn_resetsTheContext() {
        val c = AskConversation()
        c.record(q(type = "todo", window = "overdue"))
        c.record(AskIntent(action = "create", text = "buy milk"))

        assertNull(c.context())
    }

    @Test
    fun theWindowIsBounded_soTheOldestTurnsFallOff() {
        val c = AskConversation(maxDepth = 2)
        c.record(q(type = "todo", window = "overdue"))  // this should fall off
        c.record(q(type = "todo", tag = "Work"))
        c.record(q(type = "todo", status = "done"))

        // Depth 2 keeps only the last two; the overdue window from the dropped first turn is gone.
        assertNull(c.context()!!.window)
        assertEquals("Work", c.context()!!.tag)
        assertEquals("done", c.context()!!.status)
    }

    @Test
    fun restore_rehydratesTheWindow_soMultiTurnSurvivesARestart() {
        val c = AskConversation()
        c.restore(listOf(q(type = "todo", window = "overdue"), q(type = "todo", tag = "Work")))

        val ctx = c.context()!!
        assertEquals("overdue", ctx.window)
        assertEquals("Work", ctx.tag)
    }

    @Test
    fun clear_emptiesTheContext() {
        val c = AskConversation()
        c.record(q(type = "todo", window = "overdue"))
        c.clear()

        assertNull(c.context())
    }
}
