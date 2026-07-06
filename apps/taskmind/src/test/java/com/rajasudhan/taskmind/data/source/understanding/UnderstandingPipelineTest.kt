package com.rajasudhan.taskmind.data.source.understanding

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.rajasudhan.taskmind.data.local.TaskMindDao
import com.rajasudhan.taskmind.data.local.TaskMindDatabase
import com.rajasudhan.taskmind.data.model.RejectedPattern
import com.rajasudhan.taskmind.data.source.RejectionLearner
import com.rajasudhan.taskmind.data.source.SuggestionNotifier
import com.rajasudhan.taskmind.testutil.FakeLlmProvider
import com.rajasudhan.taskmind.testutil.llmItem
import com.rajasudhan.taskmind.testutil.llmResponse
import com.squareup.moshi.Moshi
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Integration tests for the extract pipeline: pre-filter → parse → score → dedup → insert + notify. */
@RunWith(RobolectricTestRunner::class)
class UnderstandingPipelineTest {

    private lateinit var db: TaskMindDatabase
    private lateinit var dao: TaskMindDao
    private val moshi = Moshi.Builder().build()
    private val notifier = mockk<SuggestionNotifier>(relaxed = true)

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            TaskMindDatabase::class.java,
        ).allowMainThreadQueries().build()
        dao = db.taskMindDao()
    }

    @After
    fun tearDown() = db.close()

    private fun pipeline(llm: FakeLlmProvider) =
        UnderstandingPipeline(llm, moshi, dao, notifier, RejectionLearner(dao))

    @Test
    fun noiseInput_isSkippedBeforeTheModelRuns() = runTest {
        val llm = FakeLlmProvider(llmResponse(llmItem(title = "should-not-insert", confidence = 0.9)))

        pipeline(llm).processText("SMS from +10000000000", "Your verification code is 123456. Do not share it.")

        assertEquals(0, llm.calls)
        assertTrue(dao.getPendingSuggestions().first().isEmpty())
        coVerify(exactly = 0) { notifier.notifyPending() }
    }

    @Test
    fun actionableItem_isInsertedAndNotifies() = runTest {
        val llm = FakeLlmProvider(
            llmResponse(llmItem(type = "reminder", title = "Pay rent", dueDate = "2026-07-01", dueTime = "09:00", confidence = 0.9))
        )

        pipeline(llm).processText("SMS from +10000000000", "Pay the rent tomorrow at 9am")

        val pending = dao.getPendingSuggestions().first()
        assertEquals(1, pending.size)
        with(pending.single()) {
            assertEquals("Pay rent", extractedTitle)
            assertEquals("reminder", type)
            assertEquals("2026-07-01", dueDate)
            assertEquals("09:00", dueTime)
            assertEquals("pending", status)
        }
        coVerify(exactly = 1) { notifier.notifyPending() }
    }

    @Test
    fun lowConfidenceItem_isDropped() = runTest {
        val llm = FakeLlmProvider(llmResponse(llmItem(title = "maybe", confidence = 0.5)))

        pipeline(llm).processText("SMS from +10000000000", "an ordinary message")

        assertTrue(dao.getPendingSuggestions().first().isEmpty())
        coVerify(exactly = 0) { notifier.notifyPending() }
    }

    @Test
    fun malformedDateTimeRecurrence_areSanitizedToNull() = runTest {
        val llm = FakeLlmProvider(
            llmResponse(llmItem(type = "reminder", title = "Fuzzy", dueDate = "31-07-2026", dueTime = "9am", recurrence = "fortnightly", confidence = 0.9))
        )

        pipeline(llm).processText("SMS from +10000000000", "do the thing sometime")

        with(dao.getPendingSuggestions().first().single()) {
            assertNull(dueDate)      // not yyyy-MM-dd
            assertNull(dueTime)      // not H:mm
            assertNull(recurrence)   // not daily/weekly/monthly
        }
    }

    @Test
    fun duplicateTitleAndDate_isNotInsertedTwice() = runTest {
        val json = llmResponse(llmItem(title = "Call mom", dueDate = "2026-07-02", confidence = 0.9))

        pipeline(FakeLlmProvider(json)).processText("SMS from +10000000000", "call mom on the 2nd")
        pipeline(FakeLlmProvider(json)).processText("SMS from +10000000000", "call mom on the 2nd again")

        assertEquals(1, dao.getPendingSuggestions().first().count { it.extractedTitle == "Call mom" })
    }

    @Test
    fun nonConformingDate_isSanitizedThenDeduped_notReinsertedOnRescan() = runTest {
        // "2026-6-1" (single-digit month) sanitizes to null and is stored with a null date. The dedup
        // must compare on that SANITIZED date, so the re-scan recognises the same item instead of
        // piling up a fresh copy every time (the raw-vs-sanitized mismatch bug).
        val json = llmResponse(llmItem(title = "Pay rent", dueDate = "2026-6-1", confidence = 0.9))

        pipeline(FakeLlmProvider(json)).processText("SMS from +10000000000", "pay rent")
        pipeline(FakeLlmProvider(json)).processText("SMS from +10000000000", "pay rent again")

        val rent = dao.getPendingSuggestions().first().filter { it.extractedTitle == "Pay rent" }
        assertEquals(1, rent.size)
        assertNull(rent.single().dueDate) // stored sanitized (the malformed date dropped)
    }

    @Test
    fun seededDate_overridesTheLlm_andDedupsOnTheStoredValue() = runTest {
        // #116: the deterministic parse ("tomorrow") overrides the LLM's (wrong) relative date, and the
        // dedup must key on that SEEDED date — what's actually stored — so a re-capture can't pile up a twin.
        val tomorrow = java.time.LocalDate.now().plusDays(1).toString()
        val json = llmResponse(llmItem(title = "Call mom", dueDate = "2000-01-01", confidence = 0.9))

        pipeline(FakeLlmProvider(json)).processText("Manual entry", "call mom tomorrow", seedSchedule = true)
        pipeline(FakeLlmProvider(json)).processText("Manual entry", "call mom tomorrow", seedSchedule = true)

        val calls = dao.getPendingSuggestions().first().filter { it.extractedTitle == "Call mom" }
        assertEquals(1, calls.size)                    // deduped on the seeded date, not re-inserted
        assertEquals(tomorrow, calls.single().dueDate) // the seeded date overrode the LLM's wrong one
    }

    @Test
    fun recurrence_isNotSeededOntoANote() = runTest {
        // #116: a "note" the model classified must not silently gain a recurrence from the parser — that
        // would later arm a surprise repeating alarm if the user adds a time. Recurrence seeds reminders only.
        val json = llmResponse(llmItem(title = "Standup ideas", type = "note", confidence = 0.9))

        pipeline(FakeLlmProvider(json)).processText("Manual entry", "standup ideas every friday", seedSchedule = true)

        val note = dao.getPendingSuggestions().first().single { it.extractedTitle == "Standup ideas" }
        assertNull(note.recurrence)
    }

    @Test
    fun extractorReturnsNothing_butDatedCapture_stillCreatesAReminder() = runTest {
        // #116: the extractor is unavailable (or saw no task) but the user typed a clear date — the
        // deterministic parse stands up the reminder on its own, so capture works with extraction disabled.
        val tomorrow = java.time.LocalDate.now().plusDays(1).toString()

        pipeline(FakeLlmProvider(llmResponse())) // {"items":[]}
            .processText("Manual entry", "call mom tomorrow 5pm", seedSchedule = true)

        val s = dao.getPendingSuggestions().first().single()
        assertEquals("reminder", s.type)
        assertEquals("call mom", s.extractedTitle) // the date phrase is stripped out of the title
        assertEquals(tomorrow, s.dueDate)
        assertEquals("17:00", s.dueTime)
    }

    @Test
    fun extractorReturnsNothing_andNoDate_createsNothing() = runTest {
        // No task from the model AND no anchoring date → nothing to auto-create (a bare time is too ambiguous).
        pipeline(FakeLlmProvider(llmResponse()))
            .processText("Manual entry", "just some random thoughts", seedSchedule = true)

        assertTrue(dao.getPendingSuggestions().first().isEmpty())
    }

    @Test
    fun passiveSource_withNoItems_neverSynthesizesAReminder() = runTest {
        // The fallback is opt-in (seedSchedule): a passive SMS the model ignored must NOT become a reminder
        // just because it contains the word "tomorrow".
        pipeline(FakeLlmProvider(llmResponse()))
            .processText("SMS from +10000000000", "see you tomorrow!") // seedSchedule defaults false

        assertTrue(dao.getPendingSuggestions().first().isEmpty())
    }

    @Test
    fun rejectionPenalty_dropsABorderlineItem() = runTest {
        dao.upsertRejectedPattern(RejectedPattern("sender", "+15551234567", 3, 1L))
        val llm = FakeLlmProvider(llmResponse(llmItem(title = "spammy", confidence = 0.8)))

        pipeline(llm).processText("SMS from +15551234567", "buy this thing today")

        // 0.8 - 0.3 penalty = 0.5, below the 0.6 acceptance bar.
        assertTrue(dao.getPendingSuggestions().first().isEmpty())
    }

    @Test
    fun rejectionPenalty_reducesStoredConfidence_whenStillAccepted() = runTest {
        dao.upsertRejectedPattern(RejectedPattern("sender", "+15551234567", 3, 1L))
        val llm = FakeLlmProvider(llmResponse(llmItem(title = "still ok", confidence = 0.95)))

        pipeline(llm).processText("SMS from +15551234567", "remember to do this")

        assertEquals(0.65, dao.getPendingSuggestions().first().single().confidence, 1e-6)
    }

    @Test
    fun unparseableReply_triggersOneRetryThenSucceeds() = runTest {
        val llm = FakeLlmProvider(
            "Sorry, I can't help with that.",
            llmResponse(llmItem(title = "Retried task", confidence = 0.9)),
        )

        pipeline(llm).processText("SMS from +10000000000", "please add a task")

        assertEquals(2, llm.calls)
        assertEquals(listOf("Retried task"), dao.getPendingSuggestions().first().map { it.extractedTitle })
    }

    @Test
    fun fencedReplyWithTrailingNewline_isParsedNotDropped() = runTest {
        // The model wrapped valid JSON in a ```json fence and appended a trailing newline — this used
        // to defeat fence-stripping and discard the whole extraction. It must parse on the first try.
        val fenced = "```json\n" + llmResponse(llmItem(title = "Fenced task", confidence = 0.9)) + "\n```\n"
        val llm = FakeLlmProvider(fenced)

        pipeline(llm).processText("SMS from +10000000000", "please add a task")

        assertEquals(1, llm.calls) // no retry needed
        assertEquals(listOf("Fenced task"), dao.getPendingSuggestions().first().map { it.extractedTitle })
    }

    @Test
    fun oneMalformedItem_isSkipped_butGoodItemsSurvive_withoutARetry() = runTest {
        // The strict whole-object parse throws on the null title; salvage keeps the well-formed item
        // instead of discarding the entire (otherwise-fine) extraction, so no retry is needed.
        val raw = """{"items":[{"type":"todo","title":"Good one","confidence":0.9},{"title":null,"confidence":0.9}]}"""
        val llm = FakeLlmProvider(raw)

        pipeline(llm).processText("SMS from +10000000000", "please add tasks")

        assertEquals(1, llm.calls)
        assertEquals(listOf("Good one"), dao.getPendingSuggestions().first().map { it.extractedTitle })
    }

    @Test
    fun salvageWithOnlyUnacceptableItems_stillTriggersTheRetry() = runTest {
        // Strict parse throws on the null title; salvage recovers only a low-confidence item, which
        // is unacceptable — so the retry must still fire (not be suppressed by an unusable salvage),
        // and the clean high-confidence retry reply is what gets captured.
        val first = """{"items":[{"type":"todo","title":"low conf","confidence":0.3},{"title":null,"confidence":0.9}]}"""
        val llm = FakeLlmProvider(first, llmResponse(llmItem(title = "Retried task", confidence = 0.9)))

        pipeline(llm).processText("SMS from +10000000000", "please add tasks")

        assertEquals(2, llm.calls)
        assertEquals(listOf("Retried task"), dao.getPendingSuggestions().first().map { it.extractedTitle })
    }

    @Test
    fun addCallback_insertsACallBackAndDedupes() = runTest {
        val pipeline = pipeline(FakeLlmProvider())

        pipeline.addCallback(displayName = "Amma", number = null)
        pipeline.addCallback(displayName = "Amma", number = null)

        val callbacks = dao.getPendingSuggestions().first().filter { it.extractedTitle == "Call back Amma" }
        assertEquals(1, callbacks.size)
        assertEquals("todo", callbacks.single().type)
        coVerify { notifier.notifyPending() }
    }
}
