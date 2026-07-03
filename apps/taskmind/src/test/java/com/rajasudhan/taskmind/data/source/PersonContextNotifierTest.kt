package com.rajasudhan.taskmind.data.source

import android.app.NotificationManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.rajasudhan.taskmind.testutil.FakeTaskMindDao
import com.rajasudhan.taskmind.testutil.aNote
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

/** Surfacing open items the moment their counterparty gets in touch. */
@RunWith(RobolectricTestRunner::class)
class PersonContextNotifierTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val dao = FakeTaskMindDao()
    private val notifier = PersonContextNotifier(context, dao)

    private fun manager() = context.getSystemService(NotificationManager::class.java)

    @Test
    fun postsAReminder_whenAContactWithAnOpenItemGetsInTouch() = runTest {
        dao.insertNote(aNote(title = "Ask about the contract", type = "todo", counterparty = "Sarah"))

        notifier.notifyForContact("Sarah Connor") // an incoming message titled by sender

        val posted = shadowOf(manager()).allNotifications.single()
        assertEquals(PersonContextNotifier.CHANNEL_ID, posted.channelId)
        assertTrue(posted.extras.getString("android.title").orEmpty().contains("Sarah"))
        assertTrue(posted.extras.getString("android.bigText").orEmpty().contains("Ask about the contract"))
    }

    @Test
    fun staysSilent_forAnUnrelatedContact() = runTest {
        dao.insertNote(aNote(title = "Ask about the contract", type = "todo", counterparty = "Sarah"))

        notifier.notifyForContact("Amazon")

        assertTrue(shadowOf(manager()).allNotifications.isEmpty())
    }

    @Test
    fun ignoresWaitingOnItems_theyAutoResolveInstead() = runTest {
        // A waiting-on item is handled by WaitingOnResolver (mark done), not surfaced here.
        dao.insertNote(aNote(title = "Waiting on Sarah: the file", type = "waiting_on", counterparty = "Sarah"))

        notifier.notifyForContact("Sarah")

        assertTrue(shadowOf(manager()).allNotifications.isEmpty())
    }

    @Test
    fun ignoresCompletedItems() = runTest {
        dao.insertNote(aNote(title = "Old commitment", type = "todo", counterparty = "Sarah", completed = true))

        notifier.notifyForContact("Sarah")

        assertTrue(shadowOf(manager()).allNotifications.isEmpty())
    }

    @Test
    fun summarisesMultipleOpenItemsForThePerson() = runTest {
        dao.insertNote(aNote(title = "Send the deck", type = "todo", counterparty = "Alex"))
        dao.insertNote(aNote(title = "Return the book", type = "todo", counterparty = "Alex Kim"))

        notifier.notifyForContact("Alex Kim")

        val posted = shadowOf(manager()).allNotifications.single()
        val text = posted.extras.getString("android.bigText").orEmpty()
        assertTrue(text.contains("Send the deck"))
        assertTrue(text.contains("Return the book"))
    }
}
