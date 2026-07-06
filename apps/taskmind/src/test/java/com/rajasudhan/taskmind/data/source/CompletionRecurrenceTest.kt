package com.rajasudhan.taskmind.data.source

import com.rajasudhan.taskmind.testutil.FakeTaskMindDao
import com.rajasudhan.taskmind.testutil.aNote
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/** Completion-based recurrence roll-forward (#124 Part A). */
class CompletionRecurrenceTest {

    private val dao = FakeTaskMindDao()
    private val alarms = mockk<AlarmScheduler>(relaxed = true)
    private val handler = CompletionRecurrence(dao, alarms)
    private val now = LocalDateTime.of(2026, 6, 15, 12, 0)

    /** Epoch millis for noon on [date] in the default zone — round-trips back to the same LocalDate. */
    private fun at(date: String) =
        LocalDate.parse(date).atTime(12, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

    @Test
    fun completionBasedReminder_rollsForwardFromCompletion_notFromTheOverdueDueDate() = runTest {
        val id = dao.insertNote(
            aNote(type = "reminder", title = "Water plants", dueDate = "2026-06-10", dueTime = "09:00",
                recurrence = "daily", repeatFromCompletion = true)
        ).toInt()
        val note = dao.getNoteByIdNow(id)!!

        val rolled = handler.rollForwardIfCompletionBased(note, at("2026-06-15"), now)

        assertTrue(rolled)
        val after = dao.getNoteByIdNow(id)!!
        assertFalse(after.completed)               // rolled to the next occurrence, not left done
        assertEquals("2026-06-16", after.dueDate)  // day AFTER completion, not after the stale due date
        verify { alarms.schedule(id, "Water plants", "2026-06-16", "09:00", "daily") }
    }

    @Test
    fun dateBasedRepeat_isLeftAlone() = runTest {
        val id = dao.insertNote(
            aNote(type = "reminder", dueDate = "2026-06-10", dueTime = "09:00",
                recurrence = "daily", repeatFromCompletion = false)
        ).toInt()
        val note = dao.getNoteByIdNow(id)!!

        val rolled = handler.rollForwardIfCompletionBased(note, at("2026-06-15"), now)

        assertFalse(rolled)
        assertEquals("2026-06-10", dao.getNoteByIdNow(id)!!.dueDate) // unchanged
        verify(exactly = 0) { alarms.schedule(any(), any(), any(), any(), any()) }
    }

    @Test
    fun oneShot_isNotRolledForward() = runTest {
        val note = aNote(type = "reminder", recurrence = null, repeatFromCompletion = true)
        assertFalse(handler.rollForwardIfCompletionBased(note, at("2026-06-15"), now))
    }

    @Test
    fun archivedItem_isNotResurrected() = runTest {
        val note = aNote(type = "todo", recurrence = "weekly", repeatFromCompletion = true, archived = true)
        assertFalse(handler.rollForwardIfCompletionBased(note, at("2026-06-15"), now))
    }

    @Test
    fun monthlyCompletionBased_anchorsToTheCompletionDay() = runTest {
        val id = dao.insertNote(
            aNote(type = "todo", title = "Pay rent", dueDate = "2026-05-01",
                recurrence = "monthly", repeatFromCompletion = true) // no anchor yet
        ).toInt()
        val note = dao.getNoteByIdNow(id)!!

        val rolled = handler.rollForwardIfCompletionBased(note, at("2026-06-03"), now)

        assertTrue(rolled)
        val after = dao.getNoteByIdNow(id)!!
        assertEquals(3, after.recurrenceAnchorDay)  // captured from the completion day
        assertEquals("2026-07-03", after.dueDate)   // one month on from completion (a future slot)
    }
}
