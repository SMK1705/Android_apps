package com.rajasudhan.taskmind.data.source

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.rajasudhan.taskmind.data.local.TaskMindDao
import com.rajasudhan.taskmind.data.local.TaskMindDatabase
import com.rajasudhan.taskmind.testutil.aNote
import com.rajasudhan.taskmind.testutil.aSuggestion
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.LocalDate
import java.time.LocalDateTime

/** Reboot re-arming, tested via the extracted [BootReceiver.rearm]. */
@RunWith(RobolectricTestRunner::class)
class BootReceiverTest {

    private lateinit var db: TaskMindDatabase
    private lateinit var dao: TaskMindDao
    private val alarms = mockk<AlarmScheduler>(relaxed = true)
    private val notifier = mockk<SuggestionNotifier>(relaxed = true)

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(), TaskMindDatabase::class.java,
        ).allowMainThreadQueries().build()
        dao = db.taskMindDao()
    }

    @After
    fun tearDown() = db.close()

    private fun receiver(): BootReceiver {
        val r = BootReceiver()
        r.dao = dao
        r.alarmScheduler = alarms
        r.notifier = notifier
        return r
    }

    @Test
    fun rearm_reschedulesAOneShotReminderAsStored() = runTest {
        dao.insertNote(aNote(type = "reminder", title = "Once", dueDate = "2026-07-01", dueTime = "09:00"))

        receiver().rearm(LocalDateTime.of(2026, 6, 1, 0, 0))

        verify { alarms.schedule(any(), "Once", "2026-07-01", "09:00", null) }
    }

    @Test
    fun rearm_advancesARecurringReminderToItsNextFutureSlot() = runTest {
        val id = dao.insertNote(
            aNote(type = "reminder", title = "Rent", dueDate = "2026-06-01", dueTime = "09:00", recurrence = "weekly")
        ).toInt()

        receiver().rearm(LocalDateTime.of(2026, 6, 20, 12, 0))

        verify { alarms.schedule(id, "Rent", any(), "09:00", "weekly") }
        val advanced = dao.getNoteByIdNow(id)!!.dueDate!!
        assertTrue(LocalDate.parse(advanced).isAfter(LocalDate.parse("2026-06-20")))
    }

    @Test
    fun rearm_skipsAReminderWithAnUnparseableTime() = runTest {
        dao.insertNote(aNote(type = "reminder", title = "Bad", dueDate = "2026-07-01", dueTime = "9am"))

        receiver().rearm()

        verify(exactly = 0) { alarms.schedule(any(), any(), any(), any(), any()) }
    }

    @Test
    fun rearm_restartsTheNagLoopForAFiredButUncompletedNagNote() = runTest {
        // A past-due, uncompleted nag note: its transient re-fire died with the reboot.
        val id = dao.insertNote(
            aNote(type = "reminder", title = "Pills", dueDate = "2026-06-01", dueTime = "09:00", nag = true)
        ).toInt()

        receiver().rearm(LocalDateTime.of(2026, 6, 20, 12, 0))

        verify { alarms.snoozeReminder(id, "Pills", AlarmReceiver.NAG_INTERVALS[0], 0) }
    }

    @Test
    fun rearm_doesNotRestartNagForAFutureNote_norACompletedOne() = runTest {
        dao.insertNote(aNote(type = "reminder", title = "Future", dueDate = "2026-07-01", dueTime = "09:00", nag = true))
        dao.insertNote(aNote(type = "reminder", title = "Done", dueDate = "2026-06-01", dueTime = "09:00", nag = true, completed = true))

        receiver().rearm(LocalDateTime.of(2026, 6, 20, 12, 0))

        // Future note re-arms normally (no nag restart); completed note does nothing.
        verify(exactly = 0) { alarms.snoozeReminder(any(), any(), any(), any()) }
    }

    @Test
    fun rearm_reArmsAFutureSnoozedSuggestion_andRepostsTheReviewNotification() = runTest {
        val until = System.currentTimeMillis() + 3_600_000
        dao.insertSuggestion(aSuggestion(extractedTitle = "Later", status = "pending", snoozedUntil = until))
        val id = dao.getSuggestionById(1)!!.id

        receiver().rearm()

        verify { notifier.scheduleResurface(id, until) }
        coVerify { notifier.notifyPending() }
    }

    @Test
    fun rearm_doesNotReArmAnExpiredSnooze_butStillReposts() = runTest {
        dao.insertSuggestion(
            aSuggestion(extractedTitle = "Due back", status = "pending", snoozedUntil = System.currentTimeMillis() - 1_000)
        )

        receiver().rearm()

        verify(exactly = 0) { notifier.scheduleResurface(any(), any()) }
        // notifyPending() itself surfaces the expired snooze immediately.
        coVerify { notifier.notifyPending() }
    }
}
