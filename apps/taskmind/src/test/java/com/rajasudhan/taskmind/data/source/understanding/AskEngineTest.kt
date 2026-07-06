package com.rajasudhan.taskmind.data.source.understanding

import com.rajasudhan.taskmind.data.source.embedding.HashingEmbedder
import com.rajasudhan.taskmind.data.source.embedding.SemanticIndex
import com.rajasudhan.taskmind.testutil.FakeLlmProvider
import com.rajasudhan.taskmind.testutil.FakeTaskMindDao
import com.rajasudhan.taskmind.testutil.aNote
import com.squareup.moshi.Moshi
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
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
    private val now = LocalDateTime.of(2026, 7, 8, 10, 0) // Wednesday

    private fun engine(llm: FakeLlmProvider) =
        AskEngine(llm, moshi, dao, pipeline, SemanticIndex(HashingEmbedder(), dao))

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
    fun structuredQueryWithNoMatch_reportsClear_ratherThanFuzzyResults() = runTest {
        dao.insertNote(aNote(title = "Buy milk", type = "todo")) // undated -> not "due today"

        val r = engine(FakeLlmProvider("""{"action":"query","window":"today"}""")).ask("what's due today", now)

        assertEquals(AskResultKind.EMPTY, r.kind)
        assertTrue(r.notes.isEmpty())
        assertTrue(r.answer.contains("clear", ignoreCase = true))
    }
}
