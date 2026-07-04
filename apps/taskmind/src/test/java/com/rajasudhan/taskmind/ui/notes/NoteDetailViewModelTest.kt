package com.rajasudhan.taskmind.ui.notes

import androidx.lifecycle.SavedStateHandle
import com.rajasudhan.taskmind.data.model.Note
import com.rajasudhan.taskmind.data.source.AlarmScheduler
import com.rajasudhan.taskmind.data.source.GeofenceManager
import com.rajasudhan.taskmind.data.source.SettingsManager
import com.rajasudhan.taskmind.data.source.understanding.LlmProvider
import com.rajasudhan.taskmind.data.source.understanding.OnDeviceLlmProvider
import com.rajasudhan.taskmind.testutil.FakeTaskMindDao
import com.rajasudhan.taskmind.testutil.MainDispatcherRule
import com.rajasudhan.taskmind.testutil.aNote
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
    private val onDeviceLlm = mockk<OnDeviceLlmProvider>(relaxed = true)
    private val llm = mockk<LlmProvider>(relaxed = true)
    private val settingsManager = mockk<SettingsManager>(relaxed = true)

    /** Insert [note], build a VM bound to its id, and wait for the note flow to load. */
    private suspend fun vmFor(note: Note): Pair<NoteDetailViewModel, Int> {
        val id = dao.insertNote(note).toInt()
        val vm = NoteDetailViewModel(dao, alarms, geofence, onDeviceLlm, llm, settingsManager, SavedStateHandle(mapOf("noteId" to id)))
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
    fun breakDown_onDevice_writesTheModelSteps_asAChecklist() = runTest {
        every { settingsManager.useOnDeviceLlm } returns true
        every { onDeviceLlm.isModelPresent() } returns true
        coEvery { llm.generateList(any(), any()) } returns """["Gather documents", "Fill the form", "Submit online"]"""
        val (vm, id) = vmFor(aNote(title = "Renew passport", type = "todo"))
        var msg = ""

        vm.breakDown { msg = it }

        assertEquals("Broke it into 3 steps.", msg)
        val items = com.rajasudhan.taskmind.ui.notes.Checklist.decode(dao.getNoteByIdNow(id)!!.checklist!!)
        assertEquals(listOf("Gather documents", "Fill the form", "Submit online"), items.map { it.text })
        assertFalse(vm.breakingDown.first())
    }

    @Test
    fun breakDown_onCloud_writesTheSteps_viaTheRoutedProvider() = runTest {
        // Cloud mode with a key: no on-device model needed — the router uses the cloud breakdown.
        every { settingsManager.useOnDeviceLlm } returns false
        every { settingsManager.llmApiKey } returns "key-123"
        every { onDeviceLlm.isModelPresent() } returns false
        coEvery { llm.generateList(any(), any()) } returns """["Book flights", "Reserve hotel"]"""
        val (vm, id) = vmFor(aNote(title = "Plan trip", type = "todo"))
        var msg = ""

        vm.breakDown { msg = it }

        assertEquals("Broke it into 2 steps.", msg)
        val items = com.rajasudhan.taskmind.ui.notes.Checklist.decode(dao.getNoteByIdNow(id)!!.checklist!!)
        assertEquals(listOf("Book flights", "Reserve hotel"), items.map { it.text })
    }

    @Test
    fun breakDown_onDeviceMode_noModel_noKey_nudgesToAddTheModel() = runTest {
        every { settingsManager.useOnDeviceLlm } returns true
        every { onDeviceLlm.isModelPresent() } returns false
        every { settingsManager.llmApiKey } returns ""
        val (vm, id) = vmFor(aNote(title = "Vague task", type = "todo"))
        var msg = ""

        vm.breakDown { msg = it }

        assertTrue(msg.contains("Add the on-device model"))
        assertNull(dao.getNoteByIdNow(id)!!.checklist)
    }

    @Test
    fun breakDown_cloudMode_noKey_nudgesToAddACloudKey() = runTest {
        every { settingsManager.useOnDeviceLlm } returns false
        every { settingsManager.llmApiKey } returns ""
        val (vm, id) = vmFor(aNote(title = "Vague task", type = "todo"))
        var msg = ""

        vm.breakDown { msg = it }

        assertTrue(msg.contains("Add your Cloud API key"))
        assertNull(dao.getNoteByIdNow(id)!!.checklist)
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
    fun updateRecurrence_persistsTheAdvancedDate_whenSchedulerReschedulesPastAStaleSlot() = runTest {
        val (vm, id) = vmFor(aNote(title = "Rent", type = "reminder", dueDate = "2026-07-01", dueTime = "09:00"))
        // The scheduler advanced the stale (past) slot to the next weekly occurrence and reports it back.
        every { alarms.schedule(id, "Rent", "2026-07-01", "09:00", "weekly") } returns "2026-07-08"

        vm.updateRecurrence("weekly")

        // The VM persists that advanced date so the stored dueDate matches when it will actually fire.
        assertEquals("2026-07-08", dao.getNoteByIdNow(id)!!.dueDate)
    }

    @Test
    fun setNag_on_persistsWithoutTouchingAlarms() = runTest {
        val (vm, id) = vmFor(aNote(title = "Pills", type = "reminder", dueDate = "2026-07-01", dueTime = "09:00", nag = false))

        vm.setNag(true)

        assertTrue(dao.getNoteByIdNow(id)!!.nag)
        verify(exactly = 0) { alarms.cancelRefire(any()) }
    }

    @Test
    fun setNag_off_cancelsTheInFlightReFire_butNotTheReminder() = runTest {
        val (vm, id) = vmFor(aNote(title = "Pills", type = "reminder", dueDate = "2026-07-01", dueTime = "09:00", nag = true))

        vm.setNag(false)

        assertFalse(dao.getNoteByIdNow(id)!!.nag)
        // Only the nag re-fire is silenced; the note's own reminder alarm is left intact.
        verify { alarms.cancelRefire(id) }
        verify(exactly = 0) { alarms.cancel(any()) }
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
