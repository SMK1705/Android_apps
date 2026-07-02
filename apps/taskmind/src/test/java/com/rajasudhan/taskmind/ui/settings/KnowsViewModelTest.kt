package com.rajasudhan.taskmind.ui.settings

import com.rajasudhan.taskmind.data.model.RejectedPattern
import com.rajasudhan.taskmind.data.source.RejectionLearner
import com.rajasudhan.taskmind.testutil.FakeTaskMindDao
import com.rajasudhan.taskmind.testutil.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class KnowsViewModelTest {

    @get:Rule
    val mainRule = MainDispatcherRule()

    private val dao = FakeTaskMindDao()

    private suspend fun seed(value: String, count: Int) =
        dao.upsertRejectedPattern(RejectedPattern("sender", value, count, 0L))

    @Test
    fun loadsLearnedSenders_mostPenalisedFirst() = runTest {
        seed("alice", 1)
        seed("+91 99999", 5)
        seed("promo bot", 3)

        val vm = KnowsViewModel(dao)
        vm.loaded.first { it }

        val order = vm.senders.first().map { it.value }
        assertEquals(listOf("+91 99999", "promo bot", "alice"), order)
    }

    @Test
    fun downRanked_reflectsTheLearnerThreshold() = runTest {
        seed("below", RejectionLearner.REJECT_THRESHOLD - 1)
        seed("at", RejectionLearner.REJECT_THRESHOLD)

        val vm = KnowsViewModel(dao)
        vm.loaded.first { it }

        val byValue = vm.senders.first().associateBy { it.value }
        assertFalse(byValue.getValue("below").downRanked)
        assertTrue(byValue.getValue("at").downRanked)
    }

    @Test
    fun forget_removesOneSender_andLeavesTheRest() = runTest {
        seed("keep", 2)
        seed("drop", 4)
        val vm = KnowsViewModel(dao)
        vm.loaded.first { it }

        val drop = vm.senders.first().first { it.value == "drop" }
        vm.forget(drop)
        vm.senders.first { list -> list.none { it.value == "drop" } }

        assertEquals(listOf("keep"), vm.senders.first().map { it.value })
        assertEquals(null, dao.rejectedPatternFor("sender", "drop"))
    }

    @Test
    fun forgetAll_clearsEverything() = runTest {
        seed("a", 2)
        seed("b", 3)
        val vm = KnowsViewModel(dao)
        vm.loaded.first { it }

        vm.forgetAll()
        vm.senders.first { it.isEmpty() }

        assertTrue(dao.allRejectedPatterns().isEmpty())
    }
}
