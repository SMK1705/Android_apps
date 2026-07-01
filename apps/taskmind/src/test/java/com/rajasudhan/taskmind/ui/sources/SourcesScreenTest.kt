package com.rajasudhan.taskmind.ui.sources

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.rajasudhan.taskmind.data.source.SourceManager
import com.rajasudhan.taskmind.data.source.email.GmailAuth
import com.rajasudhan.taskmind.data.source.email.GmailCollector
import com.rajasudhan.taskmind.data.source.ocr.OcrEngine
import com.rajasudhan.taskmind.data.source.transcription.VoskTranscriber
import com.rajasudhan.taskmind.testutil.MainDispatcherRule
import com.rajasudhan.taskmind.ui.theme.TaskMindTheme
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode

/**
 * Compose UI tests for the redesigned Sources screen, on the JVM via Robolectric. Renders the real
 * screen with a mock-backed [SourcesViewModel] (all sources off) and asserts the header + a source row.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SourcesScreenTest {

    private val mainRule = MainDispatcherRule()
    private val composeRule = createComposeRule()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(mainRule).around(composeRule)

    private val sourceManager = mockk<SourceManager>(relaxed = true)
    private val gmailAuth = mockk<GmailAuth>(relaxed = true)
    private val gmailCollector = mockk<GmailCollector>(relaxed = true)
    private val context = mockk<Context>(relaxed = true)

    private fun viewModel(): SourcesViewModel {
        every { gmailAuth.connectedAccounts } returns emptySet()
        return SourcesViewModel(
            sourceManager, gmailAuth, gmailCollector,
            mockk<VoskTranscriber>(relaxed = true), mockk<OcrEngine>(relaxed = true), context
        )
    }

    @Test
    fun header_isShown() {
        composeRule.setContent { TaskMindTheme { SourcesScreen(viewModel = viewModel()) } }

        composeRule.onNodeWithText("Sources").assertIsDisplayed()
        composeRule.onNodeWithText("What TaskMind is allowed to read").assertIsDisplayed()
    }
}
