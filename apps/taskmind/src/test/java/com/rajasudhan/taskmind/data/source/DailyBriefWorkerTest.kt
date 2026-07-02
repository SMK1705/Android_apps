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
import com.rajasudhan.taskmind.testutil.aSuggestion
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import java.time.LocalDate

/** The morning-brief worker: counts today's load, composes, and posts (or stays silent). */
@RunWith(RobolectricTestRunner::class)
class DailyBriefWorkerTest {

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

    private fun worker() = TestListenableWorkerBuilder<DailyBriefWorker>(context)
        .setWorkerFactory(object : WorkerFactory() {
            override fun createWorker(c: Context, name: String, params: WorkerParameters) =
                DailyBriefWorker(c, params, dao)
        })
        .build()

    private fun manager() = context.getSystemService(NotificationManager::class.java)

    @Test
    fun postsABrief_countingOverdueDueTodayAndPending() = runTest {
        val yesterday = LocalDate.now().minusDays(1).toString()
        val today = LocalDate.now().toString()
        dao.insertNote(aNote(type = "reminder", title = "Pay rent", dueDate = yesterday, dueTime = "09:00")) // overdue
        dao.insertNote(aNote(type = "todo", title = "Submit form", dueDate = today))                        // due today (no time)
        dao.insertSuggestion(aSuggestion(extractedTitle = "New thing", status = "pending"))                 // to review

        val result = worker().doWork()

        assertEquals(ListenableWorker.Result.success(), result)
        val posted = shadowOf(manager()).allNotifications.single()
        assertEquals(TaskMindForegroundService.DAILY_BRIEF_CHANNEL_ID, posted.channelId)
        assertEquals("Good morning — 1 overdue", posted.extras.getString("android.title"))
        val text = posted.extras.getString("android.bigText").orEmpty()
        assertTrue(text.contains("1 overdue"))
        assertTrue(text.contains("1 due today"))
        assertTrue(text.contains("1 to review"))
        assertTrue(text.contains("Start with: Pay rent")) // overdue leads the focus line
    }

    @Test
    fun staysSilent_onAnEmptyDay() = runTest {
        // A completed note and a far-future note: nothing overdue, due today, or pending.
        dao.insertNote(aNote(type = "todo", title = "done", completed = true))
        dao.insertNote(aNote(type = "reminder", title = "later", dueDate = LocalDate.now().plusDays(30).toString(), dueTime = "09:00"))

        val result = worker().doWork()

        assertEquals(ListenableWorker.Result.success(), result)
        assertTrue(shadowOf(manager()).allNotifications.isEmpty())
    }
}
