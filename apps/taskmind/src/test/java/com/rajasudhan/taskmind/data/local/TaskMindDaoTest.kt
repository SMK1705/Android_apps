package com.rajasudhan.taskmind.data.local

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.rajasudhan.taskmind.data.model.RejectedPattern
import com.rajasudhan.taskmind.testutil.aNote
import com.rajasudhan.taskmind.testutil.aSuggestion
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** DAO behavior against a real (in-memory, unencrypted) Room database. */
@RunWith(RobolectricTestRunner::class)
class TaskMindDaoTest {

    private lateinit var db: TaskMindDatabase
    private lateinit var dao: TaskMindDao

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

    @Test
    fun pendingSuggestions_onlyPending_orderedByConfidenceDesc() = runTest {
        dao.insertSuggestion(aSuggestion(confidence = 0.5, status = "pending", extractedTitle = "low"))
        dao.insertSuggestion(aSuggestion(confidence = 0.9, status = "pending", extractedTitle = "high"))
        dao.insertSuggestion(aSuggestion(confidence = 0.99, status = "approved", extractedTitle = "approved"))

        val pending = dao.getPendingSuggestions().first()

        assertEquals(listOf("high", "low"), pending.map { it.extractedTitle })
    }

    @Test
    fun activeVsCompleted_split_andToggle() = runTest {
        val id = dao.insertNote(aNote(title = "task", type = "todo")).toInt()

        assertEquals(listOf("task"), dao.getActiveNotes().first().map { it.title })
        assertTrue(dao.getCompletedNotes().first().isEmpty())

        dao.setNoteCompleted(id, completed = true, date = 123L)

        assertTrue(dao.getActiveNotes().first().isEmpty())
        assertEquals(listOf("task"), dao.getCompletedNotes().first().map { it.title })
    }

    @Test
    fun searchNotes_matchesTitleSummaryBody() = runTest {
        dao.insertNote(aNote(title = "Buy milk"))
        dao.insertNote(aNote(title = "Call mom", body = "remember the milk too"))
        dao.insertNote(aNote(title = "Pay rent", summary = "milk money"))
        dao.insertNote(aNote(title = "Walk dog"))

        val hits = dao.searchNotes("%milk%").first().map { it.title }.toSet()

        assertEquals(setOf("Buy milk", "Call mom", "Pay rent"), hits)
    }

    @Test
    fun reminderNotes_onlyActiveDatedReminders() = runTest {
        dao.insertNote(aNote(title = "rem", type = "reminder", dueDate = "2026-07-01", dueTime = "09:00"))
        dao.insertNote(aNote(title = "rem-no-time", type = "reminder", dueDate = "2026-07-01", dueTime = null))
        dao.insertNote(aNote(title = "todo-timed", type = "todo", dueDate = "2026-07-01", dueTime = "09:00"))
        val done = dao.insertNote(
            aNote(title = "rem-done", type = "reminder", dueDate = "2026-07-01", dueTime = "09:00")
        ).toInt()
        dao.setNoteCompleted(done, completed = true, date = 1L)

        assertEquals(listOf("rem"), dao.getReminderNotes().map { it.title })
    }

    @Test
    fun deleteNotesOlderThan_appliesRetentionCutoff() = runTest {
        dao.insertNote(aNote(title = "old", createdDate = 100L))
        dao.insertNote(aNote(title = "new", createdDate = 10_000L))

        dao.deleteNotesOlderThan(1_000L)

        assertEquals(listOf("new"), dao.getNotesList().map { it.title })
    }

    @Test
    fun deletePurgeableSuggestions_keepsOnlyPending() = runTest {
        dao.insertSuggestion(aSuggestion(status = "pending", extractedTitle = "keep"))
        dao.insertSuggestion(aSuggestion(status = "approved", extractedTitle = "gone1"))
        dao.insertSuggestion(aSuggestion(status = "rejected", extractedTitle = "gone2"))

        dao.deletePurgeableSuggestions()

        assertEquals(listOf("keep"), dao.getPendingSuggestions().first().map { it.extractedTitle })
    }

    @Test
    fun rejectedPatterns_upsertReplacesAndDeleteForgets() = runTest {
        dao.upsertRejectedPattern(RejectedPattern("sender", "spammer", 1, 1L))
        dao.upsertRejectedPattern(RejectedPattern("sender", "spammer", 3, 2L)) // REPLACE on PK

        assertEquals(3, dao.rejectedPatternFor("sender", "spammer")?.count)
        assertEquals(1, dao.allRejectedPatterns().size)

        dao.deleteRejectedPattern("sender", "spammer")

        assertNull(dao.rejectedPatternFor("sender", "spammer"))
    }

    @Test
    fun insertNote_returnsRowId_andDeleteByIdRemovesIt() = runTest {
        val id = dao.insertNote(aNote(title = "x")).toInt()

        assertTrue(id > 0)
        assertNotNull(dao.getNoteByIdNow(id))

        dao.deleteNoteById(id)

        assertNull(dao.getNoteByIdNow(id))
    }
}
