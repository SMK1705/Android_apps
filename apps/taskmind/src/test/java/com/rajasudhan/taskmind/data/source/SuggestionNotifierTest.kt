package com.rajasudhan.taskmind.data.source

import android.app.NotificationManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.rajasudhan.taskmind.data.local.TaskMindDao
import com.rajasudhan.taskmind.testutil.aSuggestion
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

/** Bounce-Back re-posts a snoozed suggestion's ORIGINAL message as its own notification. */
@RunWith(RobolectricTestRunner::class)
class SuggestionNotifierTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val dao = mockk<TaskMindDao>(relaxed = true)
    private val notifier = SuggestionNotifier(context, dao)

    private fun manager() = context.getSystemService(NotificationManager::class.java)

    @Test
    fun bounceBack_postsTheOriginalMessage_titledBySender_onItsOwnChannel() {
        notifier.notifyBounceBack(
            aSuggestion(id = 5, source = "Notification from Alex", rawSnippet = "can we move dinner to friday")
        )

        val posted = shadowOf(manager()).allNotifications.single()
        assertEquals(SuggestionNotifier.BOUNCE_BACK_CHANNEL_ID, posted.channelId)
        assertEquals("Alex", posted.extras.getString("android.title"))
        assertTrue(posted.extras.getString("android.bigText").orEmpty().contains("move dinner"))
    }

    @Test
    fun bounceBack_fallsBackToTheTitle_whenRawSnippetIsBlank() {
        notifier.notifyBounceBack(
            aSuggestion(id = 1, source = "Voice note", rawSnippet = "   ", extractedTitle = "Buy milk")
        )

        val posted = shadowOf(manager()).allNotifications.single()
        assertEquals("Voice note", posted.extras.getString("android.title"))
        assertTrue(posted.extras.getString("android.bigText").orEmpty().contains("Buy milk"))
    }
}
