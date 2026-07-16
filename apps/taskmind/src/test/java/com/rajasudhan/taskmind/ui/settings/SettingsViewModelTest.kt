package com.rajasudhan.taskmind.ui.settings

import android.content.Context
import com.rajasudhan.taskmind.data.local.TaskMindDao
import com.rajasudhan.taskmind.data.source.BackupManager
import com.rajasudhan.taskmind.data.source.EgressLogger
import com.rajasudhan.taskmind.data.source.ModelDownloader
import com.rajasudhan.taskmind.data.source.SettingsManager
import com.rajasudhan.taskmind.data.source.ocr.OcrEngine
import com.rajasudhan.taskmind.data.source.transcription.VoskTranscriber
import com.rajasudhan.taskmind.data.source.transcription.WhisperTranscriber
import com.rajasudhan.taskmind.data.source.understanding.OnDeviceLlmProvider
import com.rajasudhan.taskmind.data.source.understanding.UnderstandingPipeline
import com.rajasudhan.taskmind.testutil.MainDispatcherRule
import com.rajasudhan.taskmind.ui.theme.ThemeMode
import com.squareup.moshi.Moshi
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/**
 * Light coverage of the config setters: each persists to SettingsManager and mirrors the value into
 * its exposed StateFlow. The IO/Android-bound methods (backup, export, model downloads) are covered
 * by the manual plan / later phases.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {

    @get:Rule
    val mainRule = MainDispatcherRule()

    private val settingsManager = mockk<SettingsManager>(relaxed = true)

    private fun vm(): SettingsViewModel = SettingsViewModel(
        settingsManager,
        mockk<TaskMindDao>(relaxed = true),
        mockk<OnDeviceLlmProvider>(relaxed = true),
        mockk<com.rajasudhan.taskmind.data.source.understanding.CloudLlmProvider>(relaxed = true),
        mockk<UnderstandingPipeline>(relaxed = true),
        mockk<EgressLogger>(relaxed = true),
        mockk<VoskTranscriber>(relaxed = true),
        mockk<WhisperTranscriber>(relaxed = true),
        mockk<OcrEngine>(relaxed = true),
        mockk<ModelDownloader>(relaxed = true),
        mockk<BackupManager>(relaxed = true),
        mockk<com.rajasudhan.taskmind.data.source.SnapshotManager>(relaxed = true),
        mockk<Moshi>(relaxed = true),
            mockk<com.rajasudhan.taskmind.data.source.DailyBriefScheduler>(relaxed = true),
        mockk<com.rajasudhan.taskmind.data.source.WeeklyWinsScheduler>(relaxed = true),
        mockk<Context>(relaxed = true),
    )

    @Test
    fun updateUseOnDeviceLlm_persistsAndMirrors() = runTest {
        val vm = vm()
        vm.updateUseOnDeviceLlm(true)
        verify { settingsManager.useOnDeviceLlm = true }
        assertEquals(true, vm.useOnDeviceLlm.value)
    }

    @Test
    fun updateRetentionDays_persistsAndMirrors() = runTest {
        val vm = vm()
        vm.updateRetentionDays(30)
        verify { settingsManager.retentionDays = 30 }
        assertEquals(30, vm.retentionDays.value)
    }

    @Test
    fun updateEventDurationMinutes_persistsAndMirrors() = runTest {
        val vm = vm()
        vm.updateEventDurationMinutes(45)
        verify { settingsManager.eventDurationMinutes = 45 }
        assertEquals(45, vm.eventDurationMinutes.value)
    }

    @Test
    fun updateThemeMode_persistsToSettingsManager() = runTest {
        vm().updateThemeMode(ThemeMode.DARK)
        verify { settingsManager.themeMode = ThemeMode.DARK }
    }
}
