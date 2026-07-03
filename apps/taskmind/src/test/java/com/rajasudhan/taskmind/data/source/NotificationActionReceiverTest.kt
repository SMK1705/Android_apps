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
    fun resurface_ofASnoozedPendingItem_bouncesBackTheOriginalMessage() = runTest {
        // Bounce-Back: the snooze timer fired, so re-post the original captured message itself.
        dao.insertSuggestion(
            aSuggestion(status = "pending", snoozedUntil = 1_000L, source = "Notification from Alex", rawSnippet = "can we move dinner to friday")
        )
        val s = dao.getSuggestionById(1)!!

        receiver().handle(NotificationActionReceiver.ACTION_RESURFACE, s.id)

        assertEquals("pending", dao.getSuggestionById(s.id)!!.status)
        coVerify { notifier.notifyBounceBack(match { it.id == s.id }) }
        coVerify(exactly = 0) { notifier.notifyPending() }
    }

    @Test
    fun resurface_ofAnUnsnoozedItem_justReconcilesThePrompt_withoutBouncing() = runTest {
        // No snoozedUntil (e.g. the snooze was undone) → nothing to bounce; just refresh the prompt.
        dao.insertSuggestion(aSuggestion(status = "pending", snoozedUntil = null))
        val s = dao.getPendingSuggestions().first().single()

        receiver().handle(NotificationActionReceiver.ACTION_RESURFACE, s.id)

        assertEquals("pending", dao.getSuggestionById(s.id)!!.status)
        coVerify(exactly = 0) { notifier.notifyBounceBack(any()) }
        coVerify { notifier.notifyPending() }
    }

    @Test
    fun resurface_ofAnAlreadyHandledItem_doesNotBounce() = runTest {
        // Approved/rejected before the timer fired → the user dealt with it; don't bounce it back.
        dao.insertSuggestion(aSuggestion(status = "approved", snoozedUntil = 1_000L))
        val s = dao.getSuggestionById(1)!!

        receiver().handle(NotificationActionReceiver.ACTION_RESURFACE, s.id)

        coVerify(exactly = 0) { notifier.notifyBounceBack(any()) }
        coVerify { notifier.notifyPending() }
    }
}
