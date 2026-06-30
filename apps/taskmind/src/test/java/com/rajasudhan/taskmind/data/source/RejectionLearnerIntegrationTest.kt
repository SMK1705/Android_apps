package com.rajasudhan.taskmind.data.source

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.rajasudhan.taskmind.data.local.TaskMindDao
import com.rajasudhan.taskmind.data.local.TaskMindDatabase
import com.rajasudhan.taskmind.testutil.aSuggestion
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Rejection learning against a real DB: accumulation, the penalty threshold, and approval recovery. */
@RunWith(RobolectricTestRunner::class)
class RejectionLearnerIntegrationTest {

    private lateinit var db: TaskMindDatabase
    private lateinit var dao: TaskMindDao
    private lateinit var learner: RejectionLearner
    private val source = "SMS from +15551234567"

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            TaskMindDatabase::class.java,
        ).allowMainThreadQueries().build()
        dao = db.taskMindDao()
        learner = RejectionLearner(dao)
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun rejectionsAccumulate_andPenaltyAppliesAtTheThreshold() = runTest {
        val s = aSuggestion(source = source)

        learner.recordRejection(s)
        assertEquals(0.0, learner.confidencePenalty(source), 0.0) // count 1
        learner.recordRejection(s)
        assertEquals(0.0, learner.confidencePenalty(source), 0.0) // count 2
        learner.recordRejection(s)                                // count 3 == threshold

        assertEquals(RejectionLearner.PENALTY, learner.confidencePenalty(source), 0.0)
        assertEquals(3, dao.rejectedPatternFor("sender", "+15551234567")!!.count)
    }

    @Test
    fun approvalRecovers_clearsPenaltyBelowThreshold_andDropsRowAtZero() = runTest {
        val s = aSuggestion(source = source)
        repeat(3) { learner.recordRejection(s) } // penalised at count 3

        learner.recordApproval(s)                // 3 -> 2, below threshold
        assertEquals(0.0, learner.confidencePenalty(source), 0.0)
        assertEquals(2, dao.rejectedPatternFor("sender", "+15551234567")!!.count)

        learner.recordApproval(s)                // 2 -> 1
        learner.recordApproval(s)                // 1 -> 0, row dropped
        assertNull(dao.rejectedPatternFor("sender", "+15551234567"))
    }

    @Test
    fun sourcesWithoutASender_areNeverPenalised() = runTest {
        learner.recordRejection(aSuggestion(source = "Manual entry"))

        assertTrue(dao.allRejectedPatterns().isEmpty())
        assertEquals(0.0, learner.confidencePenalty("Manual entry"), 0.0)
    }
}
