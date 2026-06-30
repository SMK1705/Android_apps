package com.rajasudhan.taskmind.ui.bold

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.isSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.rajasudhan.taskmind.ui.theme.TaskMindTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode

/** Compose UI tests for the shared Bold design-system components, on the JVM via Robolectric. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class BoldKitTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun confidenceChip_showsAPercentageAtOrAbove50() {
        compose.setContent { TaskMindTheme { BoldConfidenceChip(0.9) } }
        compose.onNodeWithText("90%").assertIsDisplayed()
    }

    @Test
    fun confidenceChip_showsLikelyNoiseBelow50() {
        compose.setContent { TaskMindTheme { BoldConfidenceChip(0.3) } }
        compose.onNodeWithText("likely noise").assertIsDisplayed()
    }

    @Test
    fun filterChip_showsLabelAndCount_andInvokesOnClick() {
        var clicked = false
        compose.setContent {
            TaskMindTheme {
                BoldFilterChip(label = "Tasks", selected = false, onClick = { clicked = true }, count = 4)
            }
        }

        compose.onNodeWithText("Tasks").assertIsDisplayed()
        compose.onNodeWithText("4").assertIsDisplayed()

        compose.onNodeWithText("Tasks").performClick()
        assertTrue(clicked)
    }

    @Test
    fun pillButton_showsTextAndInvokesOnClick() {
        var clicked = false
        compose.setContent {
            TaskMindTheme { BoldPillButton(text = "Add item", onClick = { clicked = true }) }
        }

        compose.onNodeWithText("Add item").performClick()
        assertTrue(clicked)
    }

    @Test
    fun sourcePill_showsTheSourceLabel() {
        compose.setContent { TaskMindTheme { BoldSourcePill("SMS from Amma") } }
        compose.onNodeWithText("SMS from Amma").assertIsDisplayed()
    }

    @Test
    fun bottomNav_marksTheActiveTabAsSelectedForTalkBack() {
        compose.setContent { TaskMindTheme { BoldBottomNav(currentRoute = "notes", onSelect = {}) } }

        // Exactly one tab carries the selected state, and it's the active one (Notes).
        compose.onAllNodes(isSelected()).assertCountEquals(1)
        compose.onNode(isSelected()).assertTextContains("NOTES")
    }
}
