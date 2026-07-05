package com.rajasudhan.taskmind.data.source

import com.rajasudhan.taskmind.testutil.aNote
import com.rajasudhan.taskmind.ui.notes.Checklist
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The plaintext Markdown export (#121) — a pure renderer, so a plain JVM unit test covers it fully. */
class NotesMarkdownTest {

    @Test
    fun render_emptyList_saysNoNotes() {
        assertEquals("# TaskMind Notes\n\n_No notes._\n", NotesMarkdown.render(emptyList()))
    }

    @Test
    fun render_writesTitleAsHeading_withBodyAndMetadata() {
        val md = NotesMarkdown.render(
            listOf(
                aNote(
                    title = "Pay rent", body = "Transfer to landlord", type = "reminder",
                    dueDate = "2026-07-01", dueTime = "09:00", recurrence = "monthly", source = "SMS from +1"
                )
            )
        )
        assertTrue(md.contains("## Pay rent"))
        assertTrue(md.contains("Transfer to landlord"))
        assertTrue(md.contains("**Type:** reminder"))
        assertTrue(md.contains("**Due:** 2026-07-01 09:00"))
        assertTrue(md.contains("**Repeats:** monthly"))
        assertTrue(md.contains("**Source:** SMS from +1"))
    }

    @Test
    fun render_checklist_asMarkdownTaskItems() {
        val checklist = Checklist.encode(
            listOf(Checklist.Item("Buy milk", true), Checklist.Item("Walk dog", false))
        )
        val md = NotesMarkdown.render(listOf(aNote(title = "Errands", type = "todo", checklist = checklist)))
        assertTrue(md.contains("- [x] Buy milk"))
        assertTrue(md.contains("- [ ] Walk dog"))
    }

    @Test
    fun render_completedAndPriority_surfacedInMetadata() {
        val md = NotesMarkdown.render(listOf(aNote(title = "Done", completed = true, priority = "high")))
        assertTrue(md.contains("**Status:** completed"))
        assertTrue(md.contains("**Priority:** high"))
    }

    @Test
    fun render_normalPriority_isOmitted() {
        assertFalse(NotesMarkdown.render(listOf(aNote(title = "Plain", priority = "normal"))).contains("**Priority:**"))
    }

    @Test
    fun render_createdDate_formattedAsUtcCalendarDate() {
        // 1_700_000_000_000 ms == 2023-11-14T22:13:20Z → the UTC calendar date is 2023-11-14.
        val md = NotesMarkdown.render(listOf(aNote(title = "Stamped", createdDate = 1_700_000_000_000L)))
        assertTrue(md.contains("**Created:** 2023-11-14"))
    }

    @Test
    fun render_multiLineSummary_blockquotesEveryLine() {
        // A single "> " prefix would leave line two as a plain paragraph — every line must be quoted.
        val md = NotesMarkdown.render(listOf(aNote(title = "T", summary = "line one\nline two")))
        assertTrue(md.contains("> line one\n> line two"))
    }

    @Test
    fun render_multiLineTitle_isFlattenedToASingleHeadingLine() {
        val md = NotesMarkdown.render(listOf(aNote(title = "part A\npart B")))
        assertTrue(md.contains("## part A part B"))
    }
}
