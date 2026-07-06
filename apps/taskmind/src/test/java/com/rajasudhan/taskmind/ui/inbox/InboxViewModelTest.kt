package com.rajasudhan.taskmind.ui.inbox

import com.rajasudhan.taskmind.data.source.RecentDataScanner
import com.rajasudhan.taskmind.data.source.RejectionLearner
import com.rajasudhan.taskmind.data.source.SuggestionApprover
import com.rajasudhan.taskmind.data.source.SuggestionNotifier
import com.rajasudhan.taskmind.data.source.transcription.VoskTranscriber
import com.rajasudhan.taskmind.data.source.understanding.RoutingLlmProvider
import com.rajasudhan.taskmind.data.source.understanding.UnderstandingPipeline
import com.rajasudhan.taskmind.testutil.FakeTaskMindDao
import com.rajasudhan.taskmind.testutil.MainDispatcherRule
import com.rajasudhan.taskmind.testutil.aSuggestion
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * Inbox triage logic, asserted through the DAO. We avoid collecting `pendingSuggestions` here: it
 * combines the DB with a 30s "now" ticker that never completes, which deadlocks the coroutine-test
 * scheduler. Its snooze-hiding filter and the bulk approve/reject/sweep actions are exercised by the
 * Compose UI tests (Phase 5); here we verify the per-item state transitions and undo.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class InboxViewModelTest {

    @get:Rule
    val mainRule = MainDispatcherRule()

    private val dao = FakeTaskMindDao()
    private val scanner = mockk<RecentDataScanner>(relaxed = true)
    private val approver = mockk<SuggestionApprover>(relaxed = true)
    private val vosk = mockk<VoskTranscriber>(relaxed = true)
    private val pipeline = mockk<UnderstandingPipeline>(relaxed = true)
    private val suggestionEditor = mockk<com.rajasudhan.taskmind.data.source.understanding.SuggestionEditor>(relaxed = true)
    private val notifier = mockk<SuggestionNotifier>(relaxed = true)
    private val routing = mockk<RoutingLlmProvider>(relaxed = true)
    private lateinit var vm: InboxViewModel

    @Before
    fun setUp() {
        vm = InboxViewModel(dao, scanner, approver, RejectionLearner(dao), vosk, pipeline, suggestionEditor, notifier, routing)
    }

    private suspend fun pending() = dao.getPendingSuggestions().first()

    @Test
    fun onDeviceEngine_reflectsRouting_andRefreshUpdatesIt() {
        // #197: the Inbox/quick-capture label derives from the effective route, not a hardcoded value.
        every { routing.isOnDeviceEffective() } returns false
        vm.refreshEngine()
        assertFalse(vm.onDeviceEngine.value) // engine set to cloud -> label must say "cloud"

        every { routing.isOnDeviceEffective() } returns true
        vm.refreshEngine()
        assertTrue(vm.onDeviceEngine.value)
    }

    @Test
    fun reject_marksRejected_recordsRejection_andUndoRepends() = runTest {
        dao.insertSuggestion(aSuggestion(source = "SMS from +15551234567", extractedTitle = "Spam", status = "pending"))
        val s = pending().single()

        vm.rejectSuggestion(s)
        assertTrue(pending().isEmpty())
        assertEquals(1, dao.rejectedPatternFor("sender", "+15551234567")!!.count)

        vm.undoLast()
        assertEquals(listOf("Spam"), pending().map { it.extractedTitle })
    }

    @Test
    fun snooze_setsSnoozedUntil_andUndoClearsIt() = runTest {
        dao.insertSuggestion(aSuggestion(extractedTitle = "Later", status = "pending"))
        val s = pending().single()
        val until = System.currentTimeMillis() + 1_000_000

        vm.snooze(s, until)
        assertEquals(until, dao.getSuggestionById(s.id)!!.snoozedUntil)

        vm.undoLast()
        assertNull(dao.getSuggestionById(s.id)!!.snoozedUntil)
    }

    @Test
    fun snooze_armsTheResurfaceAlarm_andRefreshesTheNotification() = runTest {
        dao.insertSuggestion(aSuggestion(extractedTitle = "Later", status = "pending"))
        val s = pending().single()
        val until = System.currentTimeMillis() + 1_000_000

        vm.snooze(s, until)

        // The alarm announces the snooze's return; the immediate refresh drops it from the shade now.
        coVerify { notifier.scheduleResurface(s.id, until) }
        coVerify(exactly = 1) { notifier.notifyPending() }

        // Undo brings the item straight back to the shade too — not just the in-app list.
        vm.undoLast()
        coVerify(exactly = 2) { notifier.notifyPending() }
    }

    @Test
    fun approve_delegatesToTheApprover() = runTest {
        coEvery { approver.approve(any()) } returns 5L
        dao.insertSuggestion(aSuggestion(extractedTitle = "Task", status = "pending"))
        val s = pending().single()

        vm.approveSuggestion(s)

        coVerify { approver.approve(s) }
    }

    @Test
    fun updateSuggestion_persistsTheEdit() = runTest {
        dao.insertSuggestion(aSuggestion(extractedTitle = "Orig", status = "pending"))
        val s = pending().single()

        vm.updateSuggestion(s.copy(extractedTitle = "Edited"))

        assertEquals("Edited", dao.getSuggestionById(s.id)!!.extractedTitle)
    }
}
