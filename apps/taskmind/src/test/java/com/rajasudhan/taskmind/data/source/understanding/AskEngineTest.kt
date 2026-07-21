package com.rajasudhan.taskmind.data.source.understanding

import com.rajasudhan.taskmind.data.source.embedding.HashingEmbedder
import com.rajasudhan.taskmind.data.source.embedding.SemanticIndex
import com.rajasudhan.taskmind.testutil.FakeLlmProvider
import com.rajasudhan.taskmind.testutil.FakeTaskMindDao
import com.rajasudhan.taskmind.testutil.aNote
import com.squareup.moshi.Moshi
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.LocalDateTime

/** Ask TaskMind engine (#128): intent -> execution. Robolectric only for real org.json parsing. */
@RunWith(RobolectricTestRunner::class)
class AskEngineTest {

    private val dao = FakeTaskMindDao()
    private val moshi = Moshi.Builder().build()
    private val pipeline = mockk<UnderstandingPipeline>(relaxed = true)
    private val cloudLlm = mockk<CloudLlmProvider>(relaxed = true)
    // Relaxed => askAnswersEnabled is false, i.e. the opt-in answer layer stays off unless a test says so.
    private val settings = mockk<com.rajasudhan.taskmind.data.source.SettingsManager>(relaxed = true)
    private val now = LocalDateTime.of(2026, 7, 8, 10, 0) // Wednesday

    private fun engine(llm: FakeLlmProvider) =
        AskEngine(llm, moshi, dao, pipeline, SemanticIndex(HashingEmbedder(), dao), cloudLlm, settings)

    @Test
    fun queryIntent_filtersByType() = runTest {
        dao.insertNote(aNote(title = "Buy milk", type = "todo"))
        dao.insertNote(aNote(title = "Standup", type = "reminder", dueDate = "2026-07-09", dueTime = "09:00"))

        val r = engine(FakeLlmProvider("""{"action":"query","type":"todo"}""")).ask("show my tasks", now)

        assertEquals(AskResultKind.RESULTS, r.kind)
        assertEquals(listOf("Buy milk"), r.notes.map { it.title })
    }

    @Test
    fun createIntent_routesToTheCapturePipeline() = runTest {
        val r = engine(FakeLlmProvider("""{"action":"create","text":"buy milk"}""")).ask("remind me to buy milk", now)

        assertEquals(AskResultKind.CREATED, r.kind)
        coVerify { pipeline.processText("Ask TaskMind", "buy milk", any()) }
    }

    @Test
    fun textlessCreateIntent_fallsThroughToSearch_notMatchAll() = runTest {
        dao.insertNote(aNote(title = "Buy milk", type = "todo"))
        dao.insertNote(aNote(title = "Pay rent", type = "todo"))

        // The model said "create" but dropped the text slot — a failed classification, NOT a
        // match-all query. It must degrade to search (one lexical hit), never list every note.
        val r = engine(FakeLlmProvider("""{"action":"create"}""")).ask("buy milk", now)

        assertTrue(r.kind != AskResultKind.CREATED)
        assertEquals(listOf("Buy milk"), r.notes.map { it.title })
    }

    @Test
    fun unparseableReply_fallsBackToKeywordSearch() = runTest {
        dao.insertNote(aNote(title = "Call the electrician about the quote", type = "todo"))

        val r = engine(FakeLlmProvider("sorry, I can't help with that")).ask("electrician", now)

        assertEquals(listOf("Call the electrician about the quote"), r.notes.map { it.title })
    }

    @Test
    fun slotlessQueryIntent_fallsThroughToSearch_notMatchAll() = runTest {
        dao.insertNote(aNote(title = "Call the electrician about the quote", type = "todo"))
        dao.insertNote(aNote(title = "Buy milk", type = "todo"))
        dao.insertNote(aNote(title = "Pay rent", type = "todo"))

        // A bare query intent with no slots (the EMPTY_INTENT fallback) for a content question must search
        // the utterance — a single lexical hit — not match every note via AskQuery.matches and dump the list.
        val r = engine(FakeLlmProvider("""{"action":"query"}""")).ask("electrician", now)

        assertEquals(listOf("Call the electrician about the quote"), r.notes.map { it.title })
    }

    // ---- the opt-in, cloud-only answer layer (content questions only) ----

    private suspend fun aQuote() = dao.insertNote(aNote(title = "Electrician quote", summary = "Quoted \$450 for the rewiring", type = "note"))
    private fun contentAsk() = FakeLlmProvider("""{"action":"query","keyword":"electrician"}""")

    @Test
    fun answerLayer_isOffByDefault_soNoNoteContentEverLeaves() = runTest {
        aQuote()

        val r = engine(contentAsk()).ask("what did the electrician quote?", now)

        assertFalse(r.answeredFromNotes)
        coVerify(exactly = 0) { cloudLlm.generateAnswer(any(), any()) }
    }

    @Test
    fun answerLayer_whenOnWithAKey_answersFromTheNotesAndKeepsTheCardsAsCitations() = runTest {
        aQuote()
        every { settings.askAnswersEnabled } returns true
        every { settings.llmApiKey } returns "k"
        coEvery { cloudLlm.generateAnswer(any(), any()) } returns "The electrician quoted \$450 for the rewiring."

        val r = engine(contentAsk()).ask("what did the electrician quote?", now)

        assertEquals("The electrician quoted \$450 for the rewiring.", r.answer)
        assertTrue(r.answeredFromNotes) // labelled, so the user knows a model read their notes
        assertTrue(r.notes.isNotEmpty()) // the cards stay on as citations
    }

    @Test
    fun answerLayer_withoutACloudKey_staysDeterministic() = runTest {
        aQuote()
        every { settings.askAnswersEnabled } returns true
        every { settings.llmApiKey } returns "" // cloud-only: the 1B on-device model can't ground reliably

        val r = engine(contentAsk()).ask("what did the electrician quote?", now)

        assertFalse(r.answeredFromNotes)
        coVerify(exactly = 0) { cloudLlm.generateAnswer(any(), any()) }
    }

    @Test
    fun answerLayer_whenTheCloudCallFails_fallsBackToTheDeterministicAnswer() = runTest {
        aQuote()
        every { settings.askAnswersEnabled } returns true
        every { settings.llmApiKey } returns "k"
        coEvery { cloudLlm.generateAnswer(any(), any()) } returns null

        val r = engine(contentAsk()).ask("what did the electrician quote?", now)

        // The floor is exactly today's behaviour: a generic lead-in plus the cards, never an error.
        assertFalse(r.answeredFromNotes)
        assertTrue(r.notes.isNotEmpty())
    }

    @Test
    fun answerLayer_leavesAStructuredCountAlone_soNoContentLeavesForADateQuestion() = runTest {
        dao.insertNote(aNote(title = "Standup", type = "reminder", dueDate = "2026-07-08", dueTime = "09:00"))
        every { settings.askAnswersEnabled } returns true
        every { settings.llmApiKey } returns "k"

        // "what's due today" — a count is already the best answer; note content must not be sent.
        val r = engine(FakeLlmProvider("""{"action":"query","window":"today"}""")).ask("what's due today", now)

        assertFalse(r.answeredFromNotes)
        coVerify(exactly = 0) { cloudLlm.generateAnswer(any(), any()) }
    }

    // ---- recall spans DONE items (a thing closed by mistake must still be findable) ----

    @Test
    fun search_findsACompletedNote_soRecallSurvivesCompletion() = runTest {
        dao.insertNote(aNote(title = "Electrician quote for rewiring", type = "todo", completed = true, completedDate = 100))

        // Content recall via the unparseable/search path — the note is done, but the info is still true.
        val r = engine(FakeLlmProvider("sorry, can't help")).ask("electrician", now)

        assertEquals(listOf("Electrician quote for rewiring"), r.notes.map { it.title })
        assertTrue(r.answer.contains("marked done", ignoreCase = true)) // labelled, not silently returned
    }

    @Test
    fun search_ranksAnActiveHitAboveACompletedOne_atEqualRelevance() = runTest {
        dao.insertNote(aNote(title = "Call the electrician back", type = "todo", completed = true, completedDate = 100))
        dao.insertNote(aNote(title = "Call the electrician today", type = "todo")) // active

        val r = engine(FakeLlmProvider("{}")).ask("electrician", now)

        assertEquals("Call the electrician today", r.notes.first().title) // the live one leads
    }

    @Test
    fun contentQuery_withOnlyADoneMatch_recallsItLabelled_notAFalseAllClear() = runTest {
        dao.insertNote(aNote(title = "Buy the air filter", type = "todo", completed = true, completedDate = 100))

        // A keyword query whose only match is completed — must recall it labelled, not "all clear".
        val r = engine(FakeLlmProvider("""{"action":"query","type":"todo","keyword":"filter"}""")).ask("did I get the air filter?", now)

        assertEquals(listOf("Buy the air filter"), r.notes.map { it.title })
        assertTrue(r.answer.contains("marked done", ignoreCase = true))
        assertEquals(AskResultKind.RESULTS, r.kind)
    }

    @Test
    fun contentQuery_surfacesADoneItem_evenWhenAnUnrelatedActiveNoteAlsoMatchesTheKeyword() = runTest {
        // The real bug found on-device: "water bill" matched the active "Split Google Fi bill" on the word
        // "bill" and returned early, hiding the completed water bill. Recall must span done from the start.
        dao.insertNote(aNote(title = "Split Google Fi bill", type = "todo"))
        dao.insertNote(aNote(title = "Pay the water bill", type = "todo", completed = true, completedDate = 100))

        val r = engine(FakeLlmProvider("""{"action":"query","type":"todo","keyword":"bill"}""")).ask("water bill", now)

        assertTrue(r.notes.any { it.title == "Pay the water bill" && it.completed }) // the done one is no longer masked
    }

    @Test
    fun contentQuery_withAWindow_staysActiveOnly_soADoneItemIsNotShownAsOverdue() = runTest {
        // "overdue electrician" is time-scoped; a completed note with a past date isn't overdue any more.
        dao.insertNote(aNote(title = "Call the electrician", type = "todo", dueDate = "2026-07-01", completed = true, completedDate = 100))

        val r = engine(FakeLlmProvider("""{"action":"query","keyword":"electrician","window":"overdue"}""")).ask("overdue electrician", now)

        assertTrue(r.notes.none { it.completed })
    }

    @Test
    fun structuredDateQuery_staysActiveOnly_soDoneItemsDoNotClutterWhatsDue() = runTest {
        dao.insertNote(aNote(title = "Standup", type = "reminder", dueDate = "2026-07-08", dueTime = "09:00", completed = true, completedDate = 100))

        // "what's due today" is planning, not recall — a completed item must NOT resurface here.
        val r = engine(FakeLlmProvider("""{"action":"query","window":"today"}""")).ask("what's due today", now)

        assertEquals(AskResultKind.EMPTY, r.kind)
        assertTrue(r.notes.isEmpty())
    }

    @Test
    fun followUp_handsThePreviousIntentToTheClassifierAndReturnsTheResolvedOne() = runTest {
        dao.insertNote(aNote(title = "Ship the deck", type = "todo", dueDate = "2026-07-14"))
        val llm = FakeLlmProvider("""{"action":"query","type":"todo","window":"upcoming"}""")
        val previous = AskIntent(action = "query", type = "todo", window = "overdue")

        val r = engine(llm).ask("what about next week?", now, previous = previous)

        // The model can't resolve a bare "what about next week?" without the last question's slots.
        val sent = llm.userMessages.single()
        assertTrue(sent.contains("Previous question intent"))
        assertTrue(sent.contains("\"window\":\"overdue\""))
        assertTrue(sent.contains("New question: what about next week?"))
        // The resolved intent comes back so the NEXT turn can refine this one in turn.
        assertEquals("upcoming", r.intent?.window)
    }

    @Test
    fun firstTurn_sendsTheBareUtterance_withNoPreviousIntentLine() = runTest {
        dao.insertNote(aNote(title = "Buy milk", type = "todo"))
        val llm = FakeLlmProvider("""{"action":"query","type":"todo"}""")

        engine(llm).ask("show my tasks", now)

        assertEquals("show my tasks", llm.userMessages.single())
    }

    @Test
    fun emptyStructuredResult_stillCarriesItsIntent_soAFollowUpCanRefineIt() = runTest {
        dao.insertNote(aNote(title = "Buy milk", type = "todo")) // undated -> not "due today"

        val r = engine(FakeLlmProvider("""{"action":"query","window":"today"}""")).ask("what's due today", now)

        // "Nothing due today" -> "what about tomorrow?" must be able to refine the window.
        assertEquals(AskResultKind.EMPTY, r.kind)
        assertEquals("today", r.intent?.window)
    }

    @Test
    fun structuredQuery_overTheCardLimit_saysItIsShowingOnlyTheFirstSlice() = runTest {
        repeat(15) { dao.insertNote(aNote(title = "Task $it", type = "todo")) }

        val r = engine(FakeLlmProvider("""{"action":"query","type":"todo"}""")).ask("show my tasks", now)

        // The count must stay truthful (15) while the cards are capped — and say so, rather than
        // silently dropping 3 items the user asked for.
        assertTrue(r.answer.contains("Found 15"))
        assertTrue(r.answer.contains("Showing the first 12"))
        assertEquals(12, r.notes.size)
    }

    @Test
    fun structuredQuery_ordersChronologically_withUndatedLast() = runTest {
        dao.insertNote(aNote(title = "Later", type = "todo", dueDate = "2026-07-10"))
        dao.insertNote(aNote(title = "Undated", type = "todo"))
        dao.insertNote(aNote(title = "Sooner", type = "todo", dueDate = "2026-07-09"))

        val r = engine(FakeLlmProvider("""{"action":"query","type":"todo"}""")).ask("show my tasks", now)

        // Insertion (DAO) order would be Later, Undated, Sooner — meaningless to the user. A date-shaped
        // ask reads soonest-first, with undated items trailing.
        assertEquals(listOf("Sooner", "Later", "Undated"), r.notes.map { it.title })
    }

    @Test
    fun structuredQueryWithNoMatch_reportsClear_ratherThanFuzzyResults() = runTest {
        dao.insertNote(aNote(title = "Buy milk", type = "todo")) // undated -> not "due today"

        val r = engine(FakeLlmProvider("""{"action":"query","window":"today"}""")).ask("what's due today", now)

        assertEquals(AskResultKind.EMPTY, r.kind)
        assertTrue(r.notes.isEmpty())
        assertTrue(r.answer.contains("clear", ignoreCase = true))
    }
}
