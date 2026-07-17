package com.rajasudhan.taskmind.data.source

import com.rajasudhan.taskmind.testutil.FakeTaskMindDao
import com.rajasudhan.taskmind.testutil.aNote
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The add-to-calendar path is new here (#319); complete/reschedule are exercised through NotesViewModelTest,
 * which now routes through this class. These pin the on-demand mirror: it matches approval's semantics
 * (reminder timed, to-do all-day), never duplicates, and no-ops cleanly when there's nothing to place.
 */
class NoteActionsTest {

    private val dao = FakeTaskMindDao()
    private val calendarMirror = mockk<CalendarMirror>(relaxed = true)
    private val settings = mockk<SettingsManager>(relaxed = true).also { every { it.eventDurationMinutes } returns 45 }

    private fun actions() = NoteActions(
        dao,
        mockk<AlarmScheduler>(relaxed = true),
        calendarMirror,
        mockk<CompletionRecurrence>(relaxed = true),
        settings,
    )

    @Test
    fun addToCalendar_reminder_insertsATimedEvent_andStoresTheId() = runTest {
        val id = dao.insertNote(aNote(title = "Dentist", type = "reminder", dueDate = "2026-07-22", dueTime = "15:00")).toInt()
        coEvery { calendarMirror.insert("Dentist", any(), "2026-07-22", "15:00", 45) } returns 88L

        val event = actions().addToCalendar(dao.getNoteByIdNow(id)!!)

        assertEquals(88L, event)
        assertEquals(88L, dao.getNoteByIdNow(id)!!.calendarEventId) // persisted so later edits keep it in step
    }

    @Test
    fun addToCalendar_todo_insertsAnAllDayEvent_noTime() = runTest {
        val id = dao.insertNote(aNote(title = "File taxes", type = "todo", dueDate = "2026-07-31")).toInt()
        coEvery { calendarMirror.insert("File taxes", any(), "2026-07-31", null, 45) } returns 90L

        val event = actions().addToCalendar(dao.getNoteByIdNow(id)!!)

        assertEquals(90L, event)
        coVerify { calendarMirror.insert("File taxes", any(), "2026-07-31", null, 45) } // all-day, like approval
    }

    @Test
    fun addToCalendar_undatedItem_isANoOp() = runTest {
        val id = dao.insertNote(aNote(title = "Buy milk", type = "todo", dueDate = null)).toInt()

        assertNull(actions().addToCalendar(dao.getNoteByIdNow(id)!!))
        coVerify(exactly = 0) { calendarMirror.insert(any(), any(), any(), any(), any()) }
    }

    @Test
    fun addToCalendar_alreadyMirrored_doesNotCreateADuplicate() = runTest {
        val id = dao.insertNote(aNote(title = "Standup", type = "reminder", dueDate = "2026-07-22", dueTime = "09:00", calendarEventId = 12L)).toInt()

        assertEquals(12L, actions().addToCalendar(dao.getNoteByIdNow(id)!!)) // returns the existing id
        coVerify(exactly = 0) { calendarMirror.insert(any(), any(), any(), any(), any()) }
    }

    @Test
    fun addToCalendar_whenCalendarUnwritable_returnsNull_andStoresNothing() = runTest {
        val id = dao.insertNote(aNote(title = "Dentist", type = "reminder", dueDate = "2026-07-22", dueTime = "15:00")).toInt()
        coEvery { calendarMirror.insert(any(), any(), any(), any(), any()) } returns null // source off / no writable calendar

        assertNull(actions().addToCalendar(dao.getNoteByIdNow(id)!!))
        assertNull(dao.getNoteByIdNow(id)!!.calendarEventId)
    }

    @Test
    fun canAddToCalendar_onlyForADatedTodoOrReminderNotYetMirrored() = runTest {
        val a = actions()
        assertTrue(a.canAddToCalendar(aNote(type = "todo", dueDate = "2026-07-31")))
        assertTrue(a.canAddToCalendar(aNote(type = "reminder", dueDate = "2026-07-31", dueTime = "09:00")))
        assertFalse(a.canAddToCalendar(aNote(type = "todo", dueDate = null)))                       // undated
        assertFalse(a.canAddToCalendar(aNote(type = "note", dueDate = "2026-07-31")))               // reference note
        assertFalse(a.canAddToCalendar(aNote(type = "waiting_on", dueDate = "2026-07-31")))         // not attending anything
        assertFalse(a.canAddToCalendar(aNote(type = "todo", dueDate = "2026-07-31", calendarEventId = 5L))) // already mirrored
    }
}
