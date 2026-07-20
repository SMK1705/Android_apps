package com.rajasudhan.taskmind.ui.ask

import com.rajasudhan.taskmind.data.source.AskConversationStore
import com.rajasudhan.taskmind.data.source.NoteActions
import com.rajasudhan.taskmind.data.source.embedding.SemanticIndex
import com.rajasudhan.taskmind.data.source.understanding.AskEngine
import com.rajasudhan.taskmind.data.source.understanding.AskIntent
import com.rajasudhan.taskmind.data.source.understanding.AskResult
import com.rajasudhan.taskmind.data.source.understanding.AskResultKind
import com.rajasudhan.taskmind.data.source.understanding.RoutingLlmProvider
import com.rajasudhan.taskmind.testutil.FakeTaskMindDao
import com.rajasudhan.taskmind.testutil.MainDispatcherRule
import com.rajasudhan.taskmind.testutil.aNote
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * The Ask chat's turn bookkeeping — untested until now. Covers the accumulation of user/assistant
 * turns, the blank guard, the exception path (a thrown engine must still answer and release the
 * `thinking` latch, or the input stays disabled forever), and the index backfill that keeps semantic
 * recall from silently degrading to lexical-only for a user who never opens Notes.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AskViewModelTest {

    @get:Rule
    val mainRule = MainDispatcherRule()

    private val engine = mockk<AskEngine>()
    private val routing = mockk<RoutingLlmProvider>(relaxed = true)
    private val semanticIndex = mockk<SemanticIndex>(relaxed = true)
    private val noteActions = mockk<NoteActions>(relaxed = true)
    private val store = mockk<AskConversationStore>(relaxed = true)
    private val dao = FakeTaskMindDao()

    init { every { store.load() } returns AskConversationStore.StoredConversation() } // empty thread by default

    private fun vm() = AskViewModel(engine, routing, semanticIndex, noteActions, store, dao)

    /** Seed the thread with one answer carrying [note] as a result card, so an action has a card to hit. */
    private fun kotlinx.coroutines.test.TestScope.vmWithResult(note: com.rajasudhan.taskmind.data.model.Note): AskViewModel {
        coEvery { engine.ask(any(), any(), any()) } returns AskResult("Here's what I found:", listOf(note))
        return vm().also { it.ask("find it"); advanceUntilIdle() }
    }

    private fun AskViewModel.card() = messages.value.last().result!!.notes.single()

    @Test
    fun ask_appendsTheUserTurnThenTheAnswer() = runTest {
        coEvery { engine.ask(any(), any(), any()) } returns AskResult("Found 2 tasks due today.", emptyList())

        val vm = vm()
        vm.ask("what's due today?")
        advanceUntilIdle()

        val msgs = vm.messages.value
        assertEquals(2, msgs.size)
        assertTrue(msgs[0].fromUser)
        assertEquals("what's due today?", msgs[0].text)
        assertFalse(msgs[1].fromUser)
        assertEquals("Found 2 tasks due today.", msgs[1].text)
        assertFalse(vm.thinking.value)
    }

    @Test
    fun ask_blankUtterance_isIgnored() = runTest {
        val vm = vm()
        vm.ask("   ")
        advanceUntilIdle()

        assertTrue(vm.messages.value.isEmpty())
        assertFalse(vm.thinking.value)
    }

    @Test
    fun ask_whenTheEngineThrows_stillAnswersAndReleasesThinking() = runTest {
        coEvery { engine.ask(any(), any(), any()) } throws IllegalStateException("db unavailable")

        val vm = vm()
        vm.ask("anything overdue?")
        advanceUntilIdle()

        val last = vm.messages.value.last()
        assertFalse(last.fromUser)
        assertEquals(AskResultKind.EMPTY, last.result?.kind)
        // The latch MUST clear, otherwise the input box stays disabled and Ask is bricked.
        assertFalse(vm.thinking.value)
    }

    @Test
    fun secondTurn_carriesTheFirstAnswersIntentAsContext() = runTest {
        val first = AskIntent(action = "query", type = "todo", window = "overdue")
        coEvery { engine.ask(any(), any(), any()) } returns AskResult("Found 2.", emptyList(), intent = first)

        val vm = vm()
        vm.ask("anything overdue?")
        advanceUntilIdle()
        vm.ask("what about next week?")
        advanceUntilIdle()

        // The opening question classifies blind; the follow-up MUST carry the previous slots, or
        // "what about next week?" is meaningless on its own.
        coVerify { engine.ask("anything overdue?", any(), null) }
        coVerify { engine.ask("what about next week?", any(), first) }
    }

    @Test
    fun aSearchTurn_clearsTheCarriedIntent_soStaleSlotsDoNotSkewTheNext() = runTest {
        // A keyword search resolves no structured intent (result.intent == null) — the next question
        // must start clean rather than inherit slots from two turns ago.
        coEvery { engine.ask(any(), any(), any()) } returns AskResult("Here's what I found:", emptyList())

        val vm = vm()
        vm.ask("electrician")
        advanceUntilIdle()
        vm.ask("what's due today?")
        advanceUntilIdle()

        coVerify(exactly = 2) { engine.ask(any(), any(), null) }
    }

    @Test
    fun init_backfillsTheSemanticIndex_soRecallIsNotLexicalOnly() = runTest {
        vm()
        advanceUntilIdle()

        coVerify { semanticIndex.backfill() }
    }

    // ---- acting on a result card (#319) ----

    @Test
    fun completeResult_marksItDone_andPatchesTheCardInThread() = runTest {
        val vm = vmWithResult(aNote(id = 7, title = "Pay rent", type = "todo"))

        vm.completeResult(vm.card())
        advanceUntilIdle()

        coVerify { noteActions.setCompleted(match { it.id == 7 }, true) }
        assertTrue(vm.card().completed) // the card reflects it without re-asking the model
    }

    @Test
    fun completeResult_onAReferenceNote_isANoOp_soThereIsNothingToComplete() = runTest {
        val vm = vmWithResult(aNote(id = 3, title = "Gate code 4471", type = "note"))

        vm.completeResult(vm.card())
        advanceUntilIdle()

        coVerify(exactly = 0) { noteActions.setCompleted(any(), any()) }
        assertFalse(vm.card().completed)
    }

    @Test
    fun rescheduleResult_bumpsTheDate_andReflectsWhereTheAlarmLanded() = runTest {
        val vm = vmWithResult(aNote(id = 9, title = "Standup", type = "reminder", dueDate = "2026-07-01"))
        coEvery { noteActions.reschedule(any(), any()) } returns "2026-08-30" // e.g. a recurring advance

        vm.rescheduleResult(vm.card(), 7)
        advanceUntilIdle()

        coVerify { noteActions.reschedule(match { it.id == 9 }, any()) }
        assertEquals("2026-08-30", vm.card().dueDate) // the landed date, not the requested one
    }

    @Test
    fun rescheduleResult_onAnUndatedItem_isANoOp() = runTest {
        val vm = vmWithResult(aNote(id = 5, title = "Buy milk", type = "todo", dueDate = null))

        vm.rescheduleResult(vm.card(), 1)
        advanceUntilIdle()

        coVerify(exactly = 0) { noteActions.reschedule(any(), any()) }
    }

    @Test
    fun addResultToCalendar_whenAllowed_mirrorsAndShowsTheEventOnTheCard() = runTest {
        val vm = vmWithResult(aNote(id = 4, title = "Dentist", type = "reminder", dueDate = "2026-07-22"))
        every { noteActions.canAddToCalendar(any()) } returns true
        coEvery { noteActions.addToCalendar(any()) } returns 55L

        vm.addResultToCalendar(vm.card())
        advanceUntilIdle()

        coVerify { noteActions.addToCalendar(match { it.id == 4 }) }
        assertEquals(55L, vm.card().calendarEventId)
    }

    @Test
    fun addResultToCalendar_whenNotAllowed_doesNothing() = runTest {
        val vm = vmWithResult(aNote(id = 6, title = "Gate code", type = "note"))
        every { noteActions.canAddToCalendar(any()) } returns false

        vm.addResultToCalendar(vm.card())
        advanceUntilIdle()

        coVerify(exactly = 0) { noteActions.addToCalendar(any()) }
        assertNull(vm.card().calendarEventId)
    }

    // ---- persistence (#317) ----

    @Test
    fun onLaunch_restoresTheSavedThread_rehydratingCardsFromRoom() = runTest {
        val id = dao.insertNote(aNote(title = "Ship the deck", type = "todo")).toInt()
        every { store.load() } returns AskConversationStore.StoredConversation(
            turns = listOf(
                AskConversationStore.StoredTurn(fromUser = true, text = "what's due?"),
                AskConversationStore.StoredTurn(fromUser = false, text = "Found 1.", kind = "RESULTS", noteIds = listOf(id)),
            ),
            intentHistory = listOf(AskIntent(action = "query", type = "todo", window = "overdue")),
        )

        val vm = vm()
        advanceUntilIdle()

        val msgs = vm.messages.value
        assertEquals(2, msgs.size)
        assertEquals("what's due?", msgs[0].text)
        // The card is re-read from Room, not copied from the blob — so it reflects the note's current state.
        assertEquals(listOf("Ship the deck"), msgs[1].result?.notes?.map { it.title })
    }

    @Test
    fun restore_dropsACardWhoseNoteWasDeleted() = runTest {
        // id 99 was cited in a saved turn but the note is gone now — it must simply not appear, not crash.
        every { store.load() } returns AskConversationStore.StoredConversation(
            turns = listOf(AskConversationStore.StoredTurn(fromUser = false, text = "Found 1.", noteIds = listOf(99))),
        )

        val vm = vm()
        advanceUntilIdle()

        assertTrue(vm.messages.value.single().result!!.notes.isEmpty())
    }

    @Test
    fun eachTurn_persistsTheThread() = runTest {
        coEvery { engine.ask(any(), any(), any()) } returns AskResult("Found 2.", emptyList())

        val vm = vm()
        vm.ask("what's due today?")
        advanceUntilIdle()

        val saved = slot<AskConversationStore.StoredConversation>()
        verify { store.save(capture(saved)) }
        assertEquals(listOf("what's due today?", "Found 2."), saved.captured.turns.map { it.text })
    }

    @Test
    fun restoredContext_isCarriedIntoTheNextQuestion_soAFollowUpWorksAfterARestart() = runTest {
        val prior = AskIntent(action = "query", type = "todo", window = "overdue")
        every { store.load() } returns AskConversationStore.StoredConversation(
            turns = listOf(AskConversationStore.StoredTurn(fromUser = false, text = "Found 3.")),
            intentHistory = listOf(prior),
        )
        coEvery { engine.ask(any(), any(), any()) } returns AskResult("Found 1.", emptyList())

        val vm = vm()
        advanceUntilIdle()
        vm.ask("what about next week?")
        advanceUntilIdle()

        // The restored fold window means the follow-up refines the pre-restart question, not a blank one.
        coVerify { engine.ask("what about next week?", any(), prior) }
    }

    @Test
    fun clearConversation_wipesTheThreadAndTheStore() = runTest {
        coEvery { engine.ask(any(), any(), any()) } returns AskResult("Found 2.", emptyList())
        val vm = vm()
        vm.ask("what's due today?")
        advanceUntilIdle()

        vm.clearConversation()

        assertTrue(vm.messages.value.isEmpty())
        verify { store.clear() }
    }

    @Test
    fun clearConversation_thenAsk_startsWithNoCarriedContext() = runTest {
        val prior = AskIntent(action = "query", type = "todo", window = "overdue")
        coEvery { engine.ask(any(), any(), any()) } returns AskResult("Found 3.", emptyList(), intent = prior)
        val vm = vm()
        vm.ask("anything overdue?")
        advanceUntilIdle()

        vm.clearConversation()
        vm.ask("what's due today?")
        advanceUntilIdle()

        // After a clear the next question classifies blind — the overdue slot must not linger.
        coVerify { engine.ask("what's due today?", any(), null) }
    }
}
