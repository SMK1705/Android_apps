package com.rajasudhan.taskmind.data.source

import android.app.NotificationManager
import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.rajasudhan.taskmind.data.local.TaskMindDao
import com.rajasudhan.taskmind.data.local.TaskMindDatabase
import com.rajasudhan.taskmind.testutil.aNote
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

/** Geofence arrival, tested via the extracted [GeofenceBroadcastReceiver.notifyEntered]. */
@RunWith(RobolectricTestRunner::class)
class GeofenceBroadcastReceiverTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private lateinit var db: TaskMindDatabase
    private lateinit var dao: TaskMindDao

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(context, TaskMindDatabase::class.java)
            .allowMainThreadQueries().build()
        dao = db.taskMindDao()
    }

    @After
    fun tearDown() = db.close()

    private fun receiver(): GeofenceBroadcastReceiver {
        val r = GeofenceBroadcastReceiver()
        r.dao = dao
        return r
    }

    private fun notificationManager() = context.getSystemService(NotificationManager::class.java)

    @Test
    fun notifyEntered_postsAnArrivalNotificationForAKnownNote() = runTest {
        val id = dao.insertNote(aNote(title = "Pick up parcel", locationLabel = "Office")).toInt()

        receiver().notifyEntered(context, listOf(id))

        assertTrue(shadowOf(notificationManager()).allNotifications.isNotEmpty())
    }

    @Test
    fun notifyEntered_ignoresAnUnknownNote() = runTest {
        receiver().notifyEntered(context, listOf(999))

        assertEquals(0, shadowOf(notificationManager()).allNotifications.size)
    }
}
