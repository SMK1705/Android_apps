package com.rajasudhan.taskmind.ui.common

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.rajasudhan.taskmind.ui.bold.BoldPillButton
import com.rajasudhan.taskmind.ui.theme.TaskMindTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode

/** Compose UI tests for the shared empty/zero state, on the JVM via Robolectric. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class EmptyStateTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun showsTitleAndSubtitle() {
        compose.setContent {
            TaskMindTheme {
                EmptyState(
                    icon = Icons.Outlined.Inbox,
                    title = "All clear.",
                    subtitle = "You've triaged everything.",
                )
            }
        }

        compose.onNodeWithText("All clear.").assertIsDisplayed()
        compose.onNodeWithText("You've triaged everything.").assertIsDisplayed()
    }

    @Test
    fun rendersActions_andForwardsClicks() {
        var clicked = false
        compose.setContent {
            TaskMindTheme {
                EmptyState(
                    icon = Icons.Outlined.Inbox,
                    title = "Nothing here",
                    actions = { BoldPillButton(text = "Refresh", onClick = { clicked = true }) },
                )
            }
        }

        compose.onNodeWithText("Refresh").performClick()
        assertTrue(clicked)
    }
}
