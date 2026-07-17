package com.rajasudhan.taskmind.ui.ask

import com.rajasudhan.taskmind.data.source.embedding.SemanticIndex
import com.rajasudhan.taskmind.data.source.understanding.AskEngine
import com.rajasudhan.taskmind.data.source.understanding.AskIntent
import com.rajasudhan.taskmind.data.source.understanding.AskResult
import com.rajasudhan.taskmind.data.source.understanding.AskResultKind
import com.rajasudhan.taskmind.data.source.understanding.RoutingLlmProvider
import com.rajasudhan.taskmind.testutil.MainDispatcherRule
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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

    private fun vm() = AskViewModel(engine, routing, semanticIndex)

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
}
