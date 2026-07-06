package com.rajasudhan.taskmind.data.source

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.testing.TestListenableWorkerBuilder
import com.rajasudhan.taskmind.data.local.TaskMindDao
import com.rajasudhan.taskmind.data.local.TaskMindDatabase
import com.rajasudhan.taskmind.data.model.RejectedPattern
import com.rajasudhan.taskmind.testutil.aNote
import com.rajasudhan.taskmind.testutil.aSuggestion
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.LocalDate

/** The auto-recurrence detector worker (#124 Part B): mines history, offers, and backs off. */
@RunWith(RobolectricTestRunner::class)
class RecurrenceDetectorWorkerTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private lateinit var db: TaskMindDatabase
    private lateinit var dao: TaskMindDao
    private val notifier = mockk<SuggestionNotifier>(relaxed = true)
    private val today = LocalDate.of(2026, 7, 1)

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(context, TaskMindDatabase::class.java)
            .allowMainThreadQueries().build()
        dao = db.taskMindDao()
    }

    @After
    fun tearDown() = db.close()

    private fun worker() = TestListenableWorkerBuilder<RecurrenceDetectorWorker>(context)
        .setWorkerFactory(object : WorkerFactory() {
            override fun createWorker(c: Context, name: String, params: WorkerParameters) =
                RecurrenceDetectorWorker(c, params, dao, notifier)
        })
        .build()

    private suspend fun seedMonthlyRent() {
        dao.insertNote(aNote(type = "todo", title = "Pay rent", dueDate = "2026-04-01"))
        dao.insertNote(aNote(type = "todo", title = "Pay rent", dueDate = "2026-05-01"))
        dao.insertNote(aNote(type = "todo", title = "Pay rent", dueDate = "2026-06-01"))
    }

    @Test
    fun offersAMonthlyCompletionBasedSuggestion() = runTest {
        seedMonthlyRent()

        assertTrue(worker().detectAndOffer(today))

        val s = dao.getPendingSuggestions().first().single()
        assertEquals(RecurrenceDetectorWorker.RECURRENCE_SOURCE, s.source)
        assertEquals("Pay rent", s.extractedTitle)
        assertEquals("monthly", s.recurrence)
        assertTrue(s.repeatFromCompletion)          // auto-offers reschedule-from-completion
        assertEquals("2026-07-01", s.dueDate)        // next expected occurrence
    }

    @Test
    fun doesNotReofferADismissedPattern() = runTest {
        seedMonthlyRent()
        dao.upsertRejectedPattern(
            RejectedPattern(RecurrenceDetectorWorker.REJECTED_KIND, RecurrencePattern.key("Pay rent"), 1, 0)
        )

        assertFalse(worker().detectAndOffer(today))
        assertTrue(dao.getPendingSuggestions().first().isEmpty())
    }

    @Test
    fun doesNotOfferWhatIsAlreadyRecurring() = runTest {
        seedMonthlyRent()
        dao.insertNote(aNote(type = "reminder", title = "Pay rent", dueDate = "2026-07-01", dueTime = "09:00", recurrence = "monthly"))

        assertFalse(worker().detectAndOffer(today))
    }

    @Test
    fun doesNotDuplicateAPendingOffer() = runTest {
        seedMonthlyRent()
        dao.insertSuggestion(aSuggestion(extractedTitle = "Pay rent", status = "pending"))

        assertFalse(worker().detectAndOffer(today))
        assertEquals(1, dao.getPendingSuggestions().first().size) // just the pre-existing one
    }
}
