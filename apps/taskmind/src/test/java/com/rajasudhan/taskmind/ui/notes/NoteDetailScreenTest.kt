package com.rajasudhan.taskmind.ui.notes

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.lifecycle.SavedStateHandle
import com.rajasudhan.taskmind.data.model.Note
import com.rajasudhan.taskmind.data.source.AlarmScheduler
import com.rajasudhan.taskmind.data.source.GeofenceManager
import com.rajasudhan.taskmind.testutil.FakeTaskMindDao
import com.rajasudhan.taskmind.testutil.MainDispatcherRule
import com.rajasudhan.taskmind.testutil.aNote
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
 * Compose UI tests for the redesigned Note detail, on the JVM via Robolectric. Binds a fake-DAO-backed
 * [NoteDetailViewModel] to a seeded note id (via SavedStateHandle) and renders the real screen.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class NoteDetailScreenTest {

    private val mainRule = MainDispatcherRule()
    private val composeRule = createComposeRule()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(mainRule).around(composeRule)

    private val dao = FakeTaskMindDao()
    private val alarms = mockk<AlarmScheduler>(relaxed = true)
    private val geofence = mockk<GeofenceManager>(relaxed = true)
    private val onDeviceLlm = mockk<com.rajasudhan.taskmind.data.source.understanding.OnDeviceLlmProvider>(relaxed = true)
    private val llm = mockk<com.rajasudhan.taskmind.data.source.understanding.LlmProvider>(relaxed = true)
    private val settingsManager = mockk<com.rajasudhan.taskmind.data.source.SettingsManager>(relaxed = true)
    private val placeGeocoder = mockk<com.rajasudhan.taskmind.data.source.PlaceGeocoder>(relaxed = true)

    private fun vmFor(note: Note): NoteDetailViewModel {
        val id = runBlocking { dao.insertNote(note) }.toInt()
        return NoteDetailViewModel(dao, alarms, geofence, onDeviceLlm, llm, settingsManager, placeGeocoder, com.rajasudhan.taskmind.data.source.CompletionRecurrence(dao, alarms, mockk(relaxed = true)), mockk<com.rajasudhan.taskmind.data.source.CalendarMirror>(relaxed = true), SavedStateHandle(mapOf("noteId" to id)))
    }

    @Test
    fun rendersTitleAndSummaryOfTheNote() {
        val vm = vmFor(aNote(title = "Passport renewal", summary = "Bring old passport and photos", type = "note"))
        composeRule.setContent { TaskMindTheme { NoteDetailScreen(onBack = {}, viewModel = vm) } }

        composeRule.onNodeWithText("Passport renewal").assertIsDisplayed()
        composeRule.onNodeWithText("Bring old passport and photos").assertIsDisplayed()
    }

    @Test
    fun timedReminder_showsItsSchedule() {
        val vm = vmFor(
            aNote(title = "Pay rent", type = "reminder", dueDate = "2026-07-01", dueTime = "09:00")
        )
        composeRule.setContent { TaskMindTheme { NoteDetailScreen(onBack = {}, viewModel = vm) } }

        composeRule.onNodeWithText("Pay rent").assertIsDisplayed()
    }
}
