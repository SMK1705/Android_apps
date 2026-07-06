package com.rajasudhan.taskmind.data.source

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.rajasudhan.taskmind.data.local.TaskMindDao
import com.rajasudhan.taskmind.data.local.TaskMindDatabase
import com.rajasudhan.taskmind.data.model.RejectedPattern
import com.rajasudhan.taskmind.data.model.Suggestion
import com.rajasudhan.taskmind.testutil.aSuggestion
import com.rajasudhan.taskmind.ui.notes.Checklist
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Integration tests for the single approval path. Uses a real in-memory DB + real RejectionLearner;
 * the AlarmManager/geocoder/geofence/settings collaborators are mocked, and calendar writes self-gate
 * off because WRITE_CALENDAR isn't granted under Robolectric.
 */
@RunWith(RobolectricTestRunner::class)
class SuggestionApproverTest {

    private lateinit var db: TaskMindDatabase
    private lateinit var dao: TaskMindDao
    private val settings = mockk<SettingsManager>(relaxed = true)
    private val alarms = mockk<AlarmScheduler>(relaxed = true)
    private val geocoder = mockk<PlaceGeocoder>(relaxed = true)
    private val geofence = mockk<GeofenceManager>(relaxed = true)
    private lateinit var approver: SuggestionApprover

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            TaskMindDatabase::class.java,
        ).allowMainThreadQueries().build()
        dao = db.taskMindDao()
        approver = SuggestionApprover(
            dao, settings, alarms, geocoder, geofence, RejectionLearner(dao),
            com.rajasudhan.taskmind.data.source.embedding.SemanticIndex(
                com.rajasudhan.taskmind.data.source.embedding.HashingEmbedder(), dao
            ),
            ApplicationProvider.getApplicationContext<Context>(),
        )
    }

    @After
    fun tearDown() = db.close()

    /** Insert a pending suggestion and read it back with its assigned row id. */
    private suspend fun insertPending(s: Suggestion): Suggestion {
        dao.insertSuggestion(s)
        return dao.getPendingSuggestions().first().first { it.extractedTitle == s.extractedTitle }
    }

    @Test
    fun approveReminder_createsNote_marksApproved_andSchedulesAlarm() = runTest {
        val s = insertPending(aSuggestion(extractedTitle = "Standup", type = "reminder", dueDate = "2026-07-01", dueTime = "09:00"))

        val noteId = approver.approve(s)

        assertTrue(noteId > 0)
        val note = dao.getNoteByIdNow(noteId.toInt())!!
        assertEquals("Standup", note.title)
        assertEquals("reminder", note.type)
        assertEquals("2026-07-01", note.dueDate)
        assertEquals("approved", dao.getSuggestionById(s.id)!!.status)
        verify(exactly = 1) { alarms.schedule(noteId.toInt(), "Standup", "2026-07-01", "09:00", null) }
    }

    @Test
    fun approveNote_createsNote_withoutSchedulingAnAlarm() = runTest {
        val s = insertPending(aSuggestion(extractedTitle = "Idea", type = "note"))

        val noteId = approver.approve(s)

        assertEquals("note", dao.getNoteByIdNow(noteId.toInt())!!.type)
        verify(exactly = 0) { alarms.schedule(any(), any(), any(), any(), any()) }
    }

    @Test
    fun approveTodoWithListSummary_buildsAChecklist() = runTest {
        val s = insertPending(aSuggestion(extractedTitle = "Groceries", type = "todo", summary = "Milk, eggs, bread"))

        val noteId = approver.approve(s)

        val note = dao.getNoteByIdNow(noteId.toInt())!!
        assertNotNull(note.checklist)
        assertEquals(3, Checklist.decode(note.checklist!!).size)
        verify(exactly = 0) { alarms.schedule(any(), any(), any(), any(), any()) }
    }

    @Test
    fun approve_walksThisSendersRejectionPenaltyDown() = runTest {
        dao.upsertRejectedPattern(RejectedPattern("sender", "+15551234567", 3, 1L))
        val s = insertPending(aSuggestion(source = "SMS from +15551234567", extractedTitle = "Hello", type = "note"))

        approver.approve(s)

        assertEquals(2, dao.rejectedPatternFor("sender", "+15551234567")!!.count)
    }

    @Test
    fun approve_carriesAutoTagsOntoTheNote() = runTest {
        val s = insertPending(aSuggestion(extractedTitle = "Pay rent", type = "todo", tags = "Money,Home"))

        val noteId = approver.approve(s)

        assertEquals("Money,Home", dao.getNoteByIdNow(noteId.toInt())!!.tags)
    }

    @Test
    fun reminderWithoutATime_isNotScheduled() = runTest {
        val s = insertPending(aSuggestion(extractedTitle = "Someday", type = "reminder", dueDate = "2026-07-01", dueTime = null))

        val noteId = approver.approve(s)

        assertNotNull(dao.getNoteByIdNow(noteId.toInt()))
        verify(exactly = 0) { alarms.schedule(any(), any(), any(), any(), any()) }
    }
}
