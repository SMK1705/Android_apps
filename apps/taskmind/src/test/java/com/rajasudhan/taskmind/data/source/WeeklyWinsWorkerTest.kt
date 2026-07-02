package com.rajasudhan.taskmind.data.source

import android.app.NotificationManager
import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.testing.TestListenableWorkerBuilder
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

/** The weekly-recap worker: aggregates the last 7 days of completions, composes, and posts (or stays silent). */
@RunWith(RobolectricTestRunner::class)
class WeeklyWinsWorkerTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private lateinit var db: TaskMindDatabase
    private lateinit var dao: TaskMindDao

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(context, TaskMindDatabase::class.java)
            .allowMainThreadQueries().build()
        dao = db.taskMindDao()
        TaskMindForegroundService.ensureNotificationChannel(context)
    }

    @After
    fun tearDown() = db.close()

    private fun worker() = TestListenableWorkerBuilder<WeeklyWinsWorker>(context)
        .setWorkerFactory(object : WorkerFactory() {
            override fun createWorker(c: Context, name: String, params: WorkerParameters) =
                WeeklyWinsWorker(c, params, dao)
        })
        .build()

    private fun manager() = context.getSystemService(NotificationManager::class.java)

    @Test
    fun postsRecap_countingThisWeeksCompletions_andCreditingCaptures() = runTest {
        val now = System.currentTimeMillis()
        dao.insertNote(aNote(title = "Reply to Sam", source = "SMS from +1", completed = true, completedDate = now))
        dao.insertNote(aNote(title = "Buy milk", source = "Manual entry", completed = true, completedDate = now))

        val result = worker().doWork()

        assertEquals(ListenableWorker.Result.success(), result)
        val posted = shadowOf(manager()).allNotifications.single()
        assertEquals(TaskMindForegroundService.WEEKLY_WINS_CHANNEL_ID, posted.channelId)
        assertEquals("2 wins this week 🎉", posted.extras.getString("android.title"))
        val text = posted.extras.getString("android.bigText").orEmpty()
        assertTrue(text.contains("You finished 2 things this week."))
        assertTrue(text.contains("1 caught from SMS")) // the manual one isn't credited to capture
    }

    @Test
    fun ignoresCompletionsOlderThanAWeek() = runTest {
        val eightDaysAgo = System.currentTimeMillis() - 8L * 24 * 60 * 60 * 1000
        dao.insertNote(aNote(title = "Ancient win", source = "SMS", completed = true, completedDate = eightDaysAgo))

        val result = worker().doWork()

        assertEquals(ListenableWorker.Result.success(), result)
        assertTrue(shadowOf(manager()).allNotifications.isEmpty())
    }

    @Test
    fun staysSilent_whenNothingCompleted() = runTest {
        dao.insertNote(aNote(title = "Still open", type = "todo", completed = false))

        val result = worker().doWork()

        assertEquals(ListenableWorker.Result.success(), result)
        assertTrue(shadowOf(manager()).allNotifications.isEmpty())
    }
}
