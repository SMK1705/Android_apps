package com.rajasudhan.taskmind.ui.notes

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import com.rajasudhan.taskmind.data.source.AlarmScheduler
import com.rajasudhan.taskmind.data.source.SavedFilterStore
import com.rajasudhan.taskmind.testutil.FakeTaskMindDao
import com.rajasudhan.taskmind.testutil.MainDispatcherRule
import com.rajasudhan.taskmind.testutil.aNote
import com.rajasudhan.taskmind.ui.theme.TaskMindTheme
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode

/**
 * Compose UI tests for the redesigned Notes list, on the JVM via Robolectric. Renders the real screen
 * with a fake-DAO-backed [NotesViewModel]: the header, an active note card, and the empty state.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class NotesScreenTest {

    private val mainRule = MainDispatcherRule()
    private val composeRule = createComposeRule()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(mainRule).around(composeRule)

    private val dao = FakeTaskMindDao()
    private fun viewModel() = NotesViewModel(
        dao,
        com.rajasudhan.taskmind.data.source.embedding.SemanticIndex(
            com.rajasudhan.taskmind.data.source.embedding.HashingEmbedder(), dao
        ),
        mockk<SavedFilterStore>(relaxed = true).also { every { it.filters } returns flowOf(emptyList()) },
        com.rajasudhan.taskmind.data.source.NoteActions(
            dao, mockk<AlarmScheduler>(relaxed = true),
            mockk<com.rajasudhan.taskmind.data.source.CalendarMirror>(relaxed = true),
            com.rajasudhan.taskmind.data.source.CompletionRecurrence(dao, mockk(relaxed = true), mockk(relaxed = true)),
            mockk<com.rajasudhan.taskmind.data.source.SettingsManager>(relaxed = true),
        ),
    )

    @Test
    fun header_isShown() {
        composeRule.setContent { TaskMindTheme { NotesScreen(viewModel = viewModel()) } }

        // "Notes" now appears twice — the page title and the kind-filter chip; the title is first.
        composeRule.onAllNodesWithText("Notes").onFirst().assertIsDisplayed()
        composeRule.onNodeWithText("Approved · encrypted at rest").assertIsDisplayed()
    }

    @Test
    fun activeNote_isRendered() {
        runBlocking { dao.insertNote(aNote(title = "Buy oat milk", type = "todo")) }
        composeRule.setContent { TaskMindTheme { NotesScreen(viewModel = viewModel()) } }

        composeRule.onNodeWithText("Buy oat milk").assertIsDisplayed()
    }

    @Test
    fun emptyState_isShownWhenNoNotes() {
        composeRule.setContent { TaskMindTheme { NotesScreen(viewModel = viewModel()) } }

        composeRule.onNodeWithText("Nothing here yet").assertIsDisplayed()
    }
}
