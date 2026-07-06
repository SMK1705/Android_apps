package com.rajasudhan.taskmind.data.source.understanding

import com.rajasudhan.taskmind.testutil.FakeLlmProvider
import com.rajasudhan.taskmind.testutil.aSuggestion
import com.squareup.moshi.Moshi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime

/** The NL-edit orchestrator (#115): LLM patch + deterministic date parse, merged and applied. */
class SuggestionEditorTest {

    private val moshi = Moshi.Builder().build() // Map<String,Any?> parsing needs no Kotlin factory
    private val now = LocalDateTime.of(2026, 6, 9, 12, 0) // Tuesday

    @Test
    fun appliesLlmPatch_mergedWithDeterministicDates() = runTest {
        val editor = SuggestionEditor(FakeLlmProvider("""{"priority":"high"}"""), moshi)
        val base = aSuggestion(extractedTitle = "Rent", type = "reminder", dueDate = "2026-06-01", priority = "normal")

        val r = editor.edit(base, "make it friday 6pm and high priority", now)

        assertEquals("high", r.updated.priority)                                      // from the LLM patch
        assertEquals("18:00", r.updated.dueTime)                                      // from the deterministic parse
        assertEquals(DayOfWeek.FRIDAY, LocalDate.parse(r.updated.dueDate).dayOfWeek)  // deterministic Friday
        assertTrue(r.hasChanges)
    }

    @Test
    fun llmReturnsUnparseableText_deterministicDatesStillApply() = runTest {
        val editor = SuggestionEditor(FakeLlmProvider("sorry, I can't do that"), moshi) // → empty patch
        val base = aSuggestion(extractedTitle = "Standup", type = "reminder", dueDate = "2026-06-01")

        val r = editor.edit(base, "move it to tomorrow", now)

        assertEquals(now.toLocalDate().plusDays(1).toString(), r.updated.dueDate) // parser resolved "tomorrow"
    }

    @Test
    fun unwrapsCloudSchemaWrappedPatch() = runTest {
        // The cloud backend pins the extraction schema, echoing the item under {"items":[…]}; the editor
        // must unwrap it and apply the changed field (unchanged fields equal current → no spurious change).
        val wrapped = """{"items":[{"type":"reminder","title":"Rent","priority":"high","confidence":0.9}]}"""
        val editor = SuggestionEditor(FakeLlmProvider(wrapped), moshi)
        val base = aSuggestion(extractedTitle = "Rent", type = "reminder", priority = "normal")

        val r = editor.edit(base, "make it high priority", now)

        assertEquals("high", r.updated.priority)
        assertEquals(listOf("Priority"), r.changes.map { it.label }) // only priority, not a full replace
    }

    @Test
    fun blankInstruction_isANoOp() = runTest {
        val editor = SuggestionEditor(FakeLlmProvider("""{"priority":"high"}"""), moshi)
        val r = editor.edit(aSuggestion(extractedTitle = "X", priority = "normal"), "   ", now)
        assertFalse(r.hasChanges)
    }
}
