package com.rajasudhan.taskmind.data.source.embedding

import com.rajasudhan.taskmind.testutil.FakeTaskMindDao
import com.rajasudhan.taskmind.testutil.aNote
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The semantic layer over the fake DAO with the real hashing embedder. */
class SemanticIndexTest {

    private val dao = FakeTaskMindDao()
    private val index = SemanticIndex(HashingEmbedder(), dao)

    @Test
    fun scores_ranksRelatedNotesAboveUnrelatedOnes() = runTest {
        index.index(1, "Fix the kitchen tap", "leaking under the sink")
        index.index(2, "Buy birthday gift for mom", "")

        val scores = index.scores("kitchen tap repair", SemanticIndex.SEARCH_FLOOR)
        assertTrue("related note should clear the floor", scores.containsKey(1))
        assertFalse("unrelated note should be below the floor", scores.containsKey(2))
    }

    @Test
    fun backfill_embedsNotesThatLackAVector() = runTest {
        dao.insertNote(aNote(id = 0, title = "Renew passport", summary = "before August"))
        dao.insertNote(aNote(id = 0, title = "Call the plumber", summary = ""))

        index.backfill()

        assertTrue(dao.getAllEmbeddings().size == 2)
        // A second backfill is a no-op (both already embedded).
        index.backfill()
        assertTrue(dao.getAllEmbeddings().size == 2)
    }
}
