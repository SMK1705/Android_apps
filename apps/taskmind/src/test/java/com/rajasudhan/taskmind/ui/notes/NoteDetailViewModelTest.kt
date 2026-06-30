package com.rajasudhan.taskmind.ui.notes

import androidx.lifecycle.SavedStateHandle
import com.rajasudhan.taskmind.data.model.Note
import com.rajasudhan.taskmind.data.source.AlarmScheduler
import com.rajasudhan.taskmind.data.source.GeofenceManager
import com.rajasudhan.taskmind.testutil.FakeTaskMindDao
import com.rajasudhan.taskmind.testutil.MainDispatcherRule
import com.rajasudhan.taskmind.testutil.aNote
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class NoteDetailViewModelTest {

    @get:Rule
    val mainRule = MainDispatcherRule()

    private val dao = FakeTaskMindDao()
    private val alarms = mockk<AlarmScheduler>(relaxed = true)
    private val geofence = mockk<GeofenceManager>(relaxed = true)

    /** Insert [note], build a VM bound to its id, and wait for the note flow to load. */
    private suspend fun vmFor(note: Note): Pair<NoteDetailViewModel, Int> {
        val id = dao.insertNote(note).toInt()
        val vm = NoteDetailViewModel(dao, alarms, geofence, SavedStateHandle(mapOf("noteId" to id)))
        vm.note.filterNotNull().first()
        return vm to id
    }

    @Test
    fun loadsNoteByNavArgId() = runTest {
        val (vm, _) = vmFor(aNote(title = "Detail"))
        assertEquals("Detail", vm.note.filterNotNull().first().title)
    }

    @Test
    fun updateTitle_reschedulesAlarmForTimedReminder() = runTest {
        val (vm, id) = vmFor(aNote(title = "Old", type = "reminder", dueDate = "2026-07-01", dueTime = "09:00"))

        vm.updateTitle("New")

        assertEquals("New", dao.getNoteByIdNow(id)!!.title)
        verify { alarms.schedule(id, "New", "2026-07-01", "09:00", null) }
    }

    @Test
    fun updateTitle_doesNotScheduleForAPlainNote() = runTest {
        val (vm, _) = vmFor(aNote(title = "Old", type = "note"))

        vm.updateTitle("New")

        verify(exactly = 0) { alarms.schedule(any(), any(), any(), any(), any()) }
    }

    @Test
    fun setCompleted_marksTheNoteDone() = runTest {
        val (vm, id) = vmFor(aNote(title = "Task", type = "todo"))

        vm.setCompleted(true)

        assertTrue(dao.getNoteByIdNow(id)!!.completed)
    }

    @Test
    fun updateRecurrence_persistsAndReschedules() = runTest {
        val (vm, id) = vmFor(aNote(title = "Rent", type = "reminder", dueDate = "2026-07-01", dueTime = "09:00"))

        vm.updateRecurrence("weekly")

        assertEquals("weekly", dao.getNoteByIdNow(id)!!.recurrence)
        verify { alarms.schedule(id, "Rent", "2026-07-01", "09:00", "weekly") }
    }

    @Test
    fun delete_tearsDownAlarmAndGeofence_thenRemovesNote_andCallsBack() = runTest {
        val (vm, id) = vmFor(aNote(title = "Gone", type = "reminder", dueDate = "2026-07-01", dueTime = "09:00"))
        var deleted = false

        vm.deleteNote { deleted = true }

        assertTrue(deleted)
        assertNull(dao.getNoteByIdNow(id))
        verify { alarms.cancel(id) }
        verify { geofence.remove(id) }
    }
}
