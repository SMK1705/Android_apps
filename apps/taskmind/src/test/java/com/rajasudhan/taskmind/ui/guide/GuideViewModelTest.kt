package com.rajasudhan.taskmind.ui.guide

import app.cash.turbine.test
import com.rajasudhan.taskmind.data.source.SourceManager
import com.rajasudhan.taskmind.testutil.MainDispatcherRule
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class GuideViewModelTest {

    @get:Rule
    val mainRule = MainDispatcherRule()

    private fun vmWith(seen: MutableStateFlow<Boolean>): GuideViewModel {
        val sm = mockk<SourceManager>()
        every { sm.hasSeenGuide } returns seen
        coEvery { sm.setHasSeenGuide(any()) } answers { seen.value = firstArg<Boolean>() }
        val routing = mockk<com.rajasudhan.taskmind.data.source.understanding.RoutingLlmProvider>(relaxed = true)
        return GuideViewModel(sm, routing)
    }

    @Test
    fun shownOnFirstRun_thenHiddenAfterDismiss() = runTest {
        val seen = MutableStateFlow(false)
        val vm = vmWith(seen)
        vm.showGuide.test {
            assertEquals(true, expectMostRecentItem())
            vm.dismiss()
            assertEquals(false, awaitItem())
        }
    }

    @Test
    fun hiddenWhenAlreadySeen_butReopensOnDemand() = runTest {
        val seen = MutableStateFlow(true)
        val vm = vmWith(seen)
        vm.showGuide.test {
            assertEquals(false, expectMostRecentItem())
            vm.open()
            assertEquals(true, awaitItem())
        }
    }
}
