package com.rajasudhan.taskmind.ui.settings

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.test.core.app.ApplicationProvider
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.rajasudhan.taskmind.data.local.TaskMindDao
import com.rajasudhan.taskmind.data.source.BackupManager
import com.rajasudhan.taskmind.data.source.EgressEvent
import com.rajasudhan.taskmind.data.source.EgressLogger
import com.rajasudhan.taskmind.data.source.ModelDownloader
import com.rajasudhan.taskmind.data.source.SettingsManager
import com.rajasudhan.taskmind.data.source.ocr.OcrEngine
import com.rajasudhan.taskmind.data.source.transcription.VoskTranscriber
import com.rajasudhan.taskmind.data.source.understanding.OnDeviceLlmProvider
import com.rajasudhan.taskmind.data.source.understanding.UnderstandingPipeline
import com.rajasudhan.taskmind.testutil.MainDispatcherRule
import com.rajasudhan.taskmind.ui.theme.TaskMindTheme
import com.rajasudhan.taskmind.ui.theme.ThemeMode
import com.squareup.moshi.Moshi
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode

/**
 * Compose UI tests for the redesigned Privacy status board, on the JVM via Robolectric. With a
 * mock-backed [SettingsViewModel] (no egress events) the board should read as clean/on-device.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PrivacyScreenTest {

    private val mainRule = MainDispatcherRule()
    private val composeRule = createComposeRule()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(mainRule).around(composeRule)

    private val settingsManager = mockk<SettingsManager>(relaxed = true)
    private val egressLogger = mockk<EgressLogger>(relaxed = true)

    private fun viewModel(): SettingsViewModel {
        // egressEvents is a straight passthrough to egressLogger.events; a relaxed StateFlow mock would
        // yield a bare Object at .value and cast-fail in the composable, so hand it a real empty flow.
        every { egressLogger.events } returns MutableStateFlow(emptyList<EgressEvent>())
        every { settingsManager.themeModeFlow } returns MutableStateFlow(ThemeMode.SYSTEM)
        every { settingsManager.appLockEnabledFlow } returns MutableStateFlow(false)
        return SettingsViewModel(
            settingsManager,
            mockk<TaskMindDao>(relaxed = true),
            mockk<OnDeviceLlmProvider>(relaxed = true),
            mockk<UnderstandingPipeline>(relaxed = true),
            egressLogger,
            mockk<VoskTranscriber>(relaxed = true),
            mockk<OcrEngine>(relaxed = true),
            mockk<ModelDownloader>(relaxed = true),
            mockk<BackupManager>(relaxed = true),
            mockk<Moshi>(relaxed = true),
            mockk<com.rajasudhan.taskmind.data.source.DailyBriefScheduler>(relaxed = true),
            mockk<com.rajasudhan.taskmind.data.source.WeeklyWinsScheduler>(relaxed = true),
            ApplicationProvider.getApplicationContext<Context>(),
        )
    }

    @Test
    fun cleanBoard_readsNoDataHasLeftTheDevice() {
        composeRule.setContent { TaskMindTheme { PrivacyScreen(viewModel = viewModel()) } }

        composeRule.onNodeWithText("No data has left this device").assertIsDisplayed()
    }

    @Test
    fun statusRows_areShown() {
        composeRule.setContent { TaskMindTheme { PrivacyScreen(viewModel = viewModel()) } }

        composeRule.onNodeWithText("Privacy").assertIsDisplayed()
        composeRule.onNodeWithText("Encryption at rest").assertIsDisplayed()
    }
}
