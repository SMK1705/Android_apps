package com.rajasudhan.taskmind.data.source

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.rajasudhan.taskmind.data.local.TaskMindDao
import com.rajasudhan.taskmind.data.local.TaskMindDatabase
import com.rajasudhan.taskmind.testutil.aSuggestion
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** The notification Approve/Reject handler, tested via the extracted [NotificationActionReceiver.handle]. */
@RunWith(RobolectricTestRunner::class)
class NotificationActionReceiverTest {

    private lateinit var db: TaskMindDatabase
    private lateinit var dao: TaskMindDao
    private val approver = mockk<SuggestionApprover>(relaxed = true)
    private val notifier = mockk<SuggestionNotifier>(relaxed = true)

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(), TaskMindDatabase::class.java,
        ).allowMainThreadQueries().build()
        dao = db.taskMindDao()
    }

    @After
    fun tearDown() = db.close()

    private fun receiver(): NotificationActionReceiver {
        val r = NotificationActionReceiver()
        r.dao = dao
        r.approver = approver
        r.notifier = notifier
        r.rejectionLearner = RejectionLearner(dao)
        return r
    }

    @Test
    fun reject_marksRejected_recordsRejection_andRefreshes() = runTest {
        dao.insertSuggestion(aSuggestion(source = "SMS from +15551234567", status = "pending"))
        val s = dao.getPendingSuggestions().first().single()

        receiver().handle(NotificationActionReceiver.ACTION_REJECT, s.id)

        assertEquals("rejected", dao.getSuggestionById(s.id)!!.status)
        assertEquals(1, dao.rejectedPatternFor("sender", "+15551234567")!!.count)
        coVerify { notifier.notifyPending() }
    }

    @Test
    fun approve_callsApprover_andRefreshes() = runTest {
        coEvery { approver.approve(any()) } returns 1L
        dao.insertSuggestion(aSuggestion(status = "pending"))
        val s = dao.getPendingSuggestions().first().single()

        receiver().handle(NotificationActionReceiver.ACTION_APPROVE, s.id)

        coVerify { approver.approve(s) }
        coVerify { notifier.notifyPending() }
    }

    @Test
    fun nonPendingSuggestion_isLeftAlone_butStillRefreshes() = runTest {
        dao.insertSuggestion(aSuggestion(status = "approved"))
        val s = dao.getSuggestionById(1)!!

        receiver().handle(NotificationActionReceiver.ACTION_REJECT, s.id)

        assertEquals("approved", dao.getSuggestionById(s.id)!!.status)
        coVerify(exactly = 0) { approver.approve(any()) }
        coVerify { notifier.notifyPending() }
    }

    @Test
    fun resurface_mutatesNothing_andRepostsTheReviewNotification() = runTest {
        dao.insertSuggestion(aSuggestion(status = "pending"))
        val s = dao.getPendingSuggestions().first().single()

        receiver().handle(NotificationActionReceiver.ACTION_RESURFACE, s.id)

        assertEquals("pending", dao.getSuggestionById(s.id)!!.status)
        coVerify(exactly = 0) { approver.approve(any()) }
        coVerify { notifier.notifyPending() }
    }
}
