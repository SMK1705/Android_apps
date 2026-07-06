package com.rajasudhan.taskmind.ui.guide

import androidx.compose.ui.test.assertIsDisplayed
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

/**
 * Compose UI tests for the onboarding [GuideOverlay], on the JVM via Robolectric. The overlay is fully
 * stateless (just an onDismiss callback), so it's driven directly — first page, Skip, and Next paging.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class GuideOverlayTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun showsFirstPage() {
        compose.setContent { TaskMindTheme { GuideOverlay(onDismiss = {}, isOnDevice = true) } }

        compose.onNodeWithText("Welcome to TaskMind").assertIsDisplayed()
        compose.onNodeWithText("Skip").assertIsDisplayed()
    }

    @Test
    fun skip_invokesOnDismiss() {
        var dismissed = false
        compose.setContent { TaskMindTheme { GuideOverlay(onDismiss = { dismissed = true }, isOnDevice = true) } }

        compose.onNodeWithText("Skip").performClick()

        assertTrue(dismissed)
    }

    @Test
    fun next_advancesToTheSecondPage() {
        compose.setContent { TaskMindTheme { GuideOverlay(onDismiss = {}, isOnDevice = true) } }

        compose.onNodeWithText("Next").performClick()
        compose.waitForIdle()

        compose.onNodeWithText("Choose your sources").assertIsDisplayed()
    }

    @Test
    fun cloudEngine_welcomePageIsHonestAboutTheCloud() {
        // #197: with the cloud engine active, the welcome copy must not claim "on-device".
        compose.setContent { TaskMindTheme { GuideOverlay(onDismiss = {}, isOnDevice = false) } }

        compose.onNodeWithText("cloud engine", substring = true).assertIsDisplayed()
    }
}
