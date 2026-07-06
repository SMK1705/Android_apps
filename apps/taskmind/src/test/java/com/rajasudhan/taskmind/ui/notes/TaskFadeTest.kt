package com.rajasudhan.taskmind.ui.notes

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Pure tests for the Task Fade staleness rule (#125). */
class TaskFadeTest {

    private val now = 1_000_000_000_000L
    private val dayMs = 24L * 60 * 60 * 1000
    private val old = now - (TaskFade.FADE_AFTER_DAYS + 1) * dayMs
    private val recent = now - 2 * dayMs

    private fun fading(
        type: String = "todo",
        dueDate: String? = null,
        completed: Boolean = false,
        archived: Boolean = false,
        created: Long = old,
    ) = TaskFade.isFading(type, dueDate, completed, archived, created, now)

    @Test fun undatedOldIncompleteTodo_isFading() = assertTrue(fading())

    @Test fun recentTodo_isNotFading() = assertFalse(fading(created = recent))

    @Test fun datedTodo_isNotFading() = assertFalse(fading(dueDate = "2026-08-01")) // scheduled/overdue elsewhere

    @Test fun completedTodo_isNotFading() = assertFalse(fading(completed = true))

    @Test fun archivedTodo_isNotFading() = assertFalse(fading(archived = true)) // already off the active list

    @Test fun referenceNote_neverFades() = assertFalse(fading(type = "note"))

    @Test fun reminder_neverFades() = assertFalse(fading(type = "reminder"))

    @Test fun justUnderTheThreshold_isNotFading() = assertFalse(fading(created = now - (TaskFade.FADE_AFTER_DAYS - 1) * dayMs))

    @Test fun atTheThreshold_isFading() = assertTrue(fading(created = now - TaskFade.FADE_AFTER_DAYS * dayMs))
}
