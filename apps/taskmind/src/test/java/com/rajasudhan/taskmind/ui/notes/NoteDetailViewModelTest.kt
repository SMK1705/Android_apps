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
    private val placeGeocoder = mockk<com.rajasudhan.taskmind.data.source.PlaceGeocoder>()

    /** Insert [note], build a VM bound to its id, and wait for the note flow to load. */
    private suspend fun vmFor(note: Note): Pair<NoteDetailViewModel, Int> {
        val id = dao.insertNote(note).toInt()
        val vm = NoteDetailViewModel(dao, alarms, geofence, onDeviceLlm, llm, settingsManager, placeGeocoder, com.rajasudhan.taskmind.data.source.CompletionRecurrence(dao, alarms), SavedStateHandle(mapOf("noteId" to id)))
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
    fun updateRecurrence_monthly_capturesTheDayOfMonthAnchor_andClearsItWhenSwitchingAway() = runTest {
        val (vm, id) = vmFor(aNote(title = "Rent", type = "reminder", dueDate = "2026-01-31", dueTime = "09:00"))

        vm.updateRecurrence("Monthly")
        assertEquals(31, dao.getNoteByIdNow(id)!!.recurrenceAnchorDay) // the 31st is anchored

        vm.updateRecurrence("Weekly")
        assertNull(dao.getNoteByIdNow(id)!!.recurrenceAnchorDay) // no longer monthly → anchor cleared
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

    // A nag reminder that was mid-chain (nagFiring = true) is rescheduled/renamed/repeat-changed. Each
    // path re-arms via schedule() (or cancel()), which cancels the live nag re-fire — so the persisted
    // nagFiring flag is now stale and must be cleared, or BootReceiver would resurrect the dead chain on
    // the next reboot, firing a nag the user never asked to keep. #180 follow-up.
    @Test
    fun updateSchedule_clearsAStaleNagFiringFlag() = runTest {
        val (vm, id) = vmFor(aNote(title = "Pills", type = "reminder", dueDate = "2026-07-01", dueTime = "09:00", nag = true, nagFiring = true))

        vm.updateSchedule("2026-07-02", "15:00")

        assertFalse(dao.getNoteByIdNow(id)!!.nagFiring)
    }

    @Test
    fun updateSchedule_clearingTheTime_alsoClearsAStaleNagFiringFlag() = runTest {
        // Removing the time cancels the alarm (and its re-fire) outright — the flag must clear here too.
        val (vm, id) = vmFor(aNote(title = "Pills", type = "reminder", dueDate = "2026-07-01", dueTime = "09:00", nag = true, nagFiring = true))

        vm.updateSchedule("2026-07-01", null)

        assertFalse(dao.getNoteByIdNow(id)!!.nagFiring)
    }

    @Test
    fun updateRecurrence_clearsAStaleNagFiringFlag() = runTest {
        val (vm, id) = vmFor(aNote(title = "Pills", type = "reminder", dueDate = "2026-07-01", dueTime = "09:00", nag = true, nagFiring = true))

        vm.updateRecurrence("weekly")

        assertFalse(dao.getNoteByIdNow(id)!!.nagFiring)
    }

    @Test
    fun updateTitle_clearsAStaleNagFiringFlag_forATimedReminder() = runTest {
        val (vm, id) = vmFor(aNote(title = "Old", type = "reminder", dueDate = "2026-07-01", dueTime = "09:00", nag = true, nagFiring = true))

        vm.updateTitle("New")

        assertFalse(dao.getNoteByIdNow(id)!!.nagFiring)
    }

    // #132: a place reminder can be set from a TYPED place name, not just the current location.
    @Test
    fun setLocationReminderByPlace_geocodesTheName_persistsCoords_andArmsTheGeofence() = runTest {
        val (vm, id) = vmFor(aNote(title = "Lunch", type = "reminder"))
        coEvery { placeGeocoder.geocode("Panda Express, Dunwoody") } returns (10.0 to 20.0)
        var resolved: Boolean? = null

        vm.setLocationReminderByPlace("Panda Express, Dunwoody", "Lunch spot") { resolved = it }

        assertEquals(true, resolved)
        val note = dao.getNoteByIdNow(id)!!
        assertEquals(10.0, note.locationLat!!, 0.0001)
        assertEquals(20.0, note.locationLng!!, 0.0001)
        assertEquals("Lunch spot", note.locationLabel)
        verify { geofence.add(id, 10.0, 20.0, any()) }
    }

    @Test
    fun setLocationReminderByPlace_reportsFailure_andChangesNothing_whenUnresolvable() = runTest {
        val (vm, id) = vmFor(aNote(title = "Lunch", type = "reminder"))
        coEvery { placeGeocoder.geocode(any()) } returns null
        var resolved: Boolean? = null

        vm.setLocationReminderByPlace("nowhere at all", "x") { resolved = it }

        assertEquals(false, resolved)
        assertNull(dao.getNoteByIdNow(id)!!.locationLat)
        verify(exactly = 0) { geofence.add(any(), any(), any(), any()) }
    }

    @Test
    fun setLocationReminderByPlace_reportsFailure_whenTheGeocoderThrows() = runTest {
        // The geocoder does IO; a thrown error must surface as onResult(false), not a hung coroutine.
        val (vm, id) = vmFor(aNote(title = "Lunch", type = "reminder"))
        coEvery { placeGeocoder.geocode(any()) } throws java.io.IOException("geocoder offline")
        var resolved: Boolean? = null

        vm.setLocationReminderByPlace("somewhere", "x") { resolved = it }

        assertEquals(false, resolved)
        assertNull(dao.getNoteByIdNow(id)!!.locationLat)
        verify(exactly = 0) { geofence.add(any(), any(), any(), any()) }
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
