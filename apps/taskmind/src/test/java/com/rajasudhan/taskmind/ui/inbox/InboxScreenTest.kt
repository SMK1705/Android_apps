package com.rajasudhan.taskmind.ui.inbox

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.rajasudhan.taskmind.data.source.RecentDataScanner
import com.rajasudhan.taskmind.data.source.RejectionLearner
import com.rajasudhan.taskmind.data.source.SuggestionApprover
import com.rajasudhan.taskmind.data.source.transcription.VoskTranscriber
import com.rajasudhan.taskmind.data.source.understanding.UnderstandingPipeline
import com.rajasudhan.taskmind.testutil.FakeTaskMindDao
import com.rajasudhan.taskmind.testutil.MainDispatcherRule
import com.rajasudhan.taskmind.testutil.aSuggestion
import com.rajasudhan.taskmind.ui.theme.TaskMindTheme
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode

/**
 * Compose UI tests for the redesigned Inbox, on the JVM via Robolectric. Renders the real screen with
 * a fake-DAO-backed [InboxViewModel] and drives it through the ViewModel's flows — covering the empty
 * "Inbox zero" state, a rendered suggestion card, and the snooze-hiding filter that the ViewModel unit
 * test deliberately leaves to this layer (its 30s "now" ticker deadlocks the coroutine-test scheduler).
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class InboxScreenTest {

    private val mainRule = MainDispatcherRule()
    private val composeRule = createComposeRule()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(mainRule).around(composeRule)

    private val dao = FakeTaskMindDao()
    private val scanner = mockk<RecentDataScanner>(relaxed = true)
    private val approver = mockk<SuggestionApprover>(relaxed = true)
    private val vosk = mockk<VoskTranscriber>(relaxed = true)
    private val pipeline = mockk<UnderstandingPipeline>(relaxed = true)
    private val notifier = mockk<com.rajasudhan.taskmind.data.source.SuggestionNotifier>(relaxed = true)
    private val suggestionEditor = mockk<com.rajasudhan.taskmind.data.source.understanding.SuggestionEditor>(relaxed = true)
    private val routing = mockk<com.rajasudhan.taskmind.data.source.understanding.RoutingLlmProvider>(relaxed = true)

    private fun viewModel() = InboxViewModel(dao, scanner, approver, RejectionLearner(dao), vosk, pipeline, suggestionEditor, notifier, routing)

    private fun seed(vararg suggestions: com.rajasudhan.taskmind.data.model.Suggestion) = runBlocking {
        suggestions.forEach { dao.insertSuggestion(it) }
    }

    @Test
    fun emptyState_showsInboxZero() {
        val vm = viewModel()
        composeRule.setContent { TaskMindTheme { InboxScreen(viewModel = vm) } }

        composeRule.onNodeWithText("Inbox zero").assertIsDisplayed()
    }

    @Test
    fun pendingSuggestion_rendersItsTitle() {
        seed(aSuggestion(extractedTitle = "Renew passport before August", status = "pending"))
        val vm = viewModel()
        composeRule.setContent { TaskMindTheme { InboxScreen(viewModel = vm) } }

        composeRule.onNodeWithText("Renew passport before August").assertIsDisplayed()
    }

    @Test
    fun snoozedSuggestion_isHiddenFromInbox() {
        val future = System.currentTimeMillis() + 60 * 60 * 1000
        seed(aSuggestion(extractedTitle = "Snoozed away", status = "pending", snoozedUntil = future))
        val vm = viewModel()
        composeRule.setContent { TaskMindTheme { InboxScreen(viewModel = vm) } }

        // Snoozed items are filtered out, so the empty state shows instead of the card.
        composeRule.onNodeWithText("Inbox zero").assertIsDisplayed()
    }
}
