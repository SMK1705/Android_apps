package com.rajasudhan.taskmind.data.source

import android.app.NotificationManager
import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.rajasudhan.taskmind.data.local.TaskMindDao
import com.rajasudhan.taskmind.data.local.TaskMindDatabase
import com.rajasudhan.taskmind.testutil.aNote
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import java.time.LocalDateTime

/** Alarm firing, tested via the extracted [AlarmReceiver.handle]. */
@RunWith(RobolectricTestRunner::class)
class AlarmReceiverTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private lateinit var db: TaskMindDatabase
    private lateinit var dao: TaskMindDao
    private val alarms = mockk<AlarmScheduler>(relaxed = true)

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(context, TaskMindDatabase::class.java)
            .allowMainThreadQueries().build()
        dao = db.taskMindDao()
    }

    @After
    fun tearDown() = db.close()

    private fun receiver(): AlarmReceiver {
        val r = AlarmReceiver()
        r.dao = dao
        r.alarmScheduler = alarms
        return r
    }

    @Test
    fun staleNote_cancelsTheAlarm_andDoesNotReschedule() = runTest {
        // No note with id 7 exists.
        receiver().handle(context, 7, "Gone", "weekly", "2026-07-01", "09:00", LocalDateTime.of(2026, 6, 1, 0, 0))

        verify { alarms.cancel(7) }
        verify(exactly = 0) { alarms.schedule(any(), any(), any(), any(), any()) }
    }

    @Test
    fun recurringReminder_advancesDueDate_andReschedules() = runTest {
        val id = dao.insertNote(
            aNote(type = "reminder", title = "Rent", dueDate = "2026-06-01", dueTime = "09:00", recurrence = "weekly")
        ).toInt()

        receiver().handle(context, id, "Rent", "weekly", "2026-06-01", "09:00", LocalDateTime.of(2026, 6, 20, 12, 0))

        verify { alarms.schedule(id, "Rent", any(), "09:00", "weekly") }
        assertNotEquals("2026-06-01", dao.getNoteByIdNow(id)!!.dueDate)
    }

    @Test
    fun oneShotReminder_notifiesWithoutRescheduling() = runTest {
        val id = dao.insertNote(
            aNote(type = "reminder", title = "Once", dueDate = "2026-07-01", dueTime = "09:00")
        ).toInt()

        receiver().handle(context, id, "Once", null, "2026-07-01", "09:00")

        verify(exactly = 0) { alarms.schedule(any(), any(), any(), any(), any()) }
        verify(exactly = 0) { alarms.cancel(any()) }
    }

    @Test
    fun completedNote_isSkipped_andItsAlarmCancelled() = runTest {
        val id = dao.insertNote(
            aNote(type = "reminder", title = "Already done", dueDate = "2026-07-01", dueTime = "09:00", completed = true)
        ).toInt()

        receiver().handle(context, id, "Already done", null, "2026-07-01", "09:00")

        verify { alarms.cancel(id) }
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        assertTrue(shadowOf(manager).allNotifications.isEmpty())
    }

    @Test
    fun firedReminder_isHighChannel_andActionable() = runTest {
        val id = dao.insertNote(
            aNote(type = "reminder", title = "Once", dueDate = "2026-07-01", dueTime = "09:00")
        ).toInt()

        receiver().handle(context, id, "Once", null, "2026-07-01", "09:00")

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val posted = shadowOf(manager).allNotifications.single()
        // The dedicated HIGH-importance channel — posting to the LOW service channel made reminders silent.
        assertEquals(TaskMindForegroundService.REMINDER_CHANNEL_ID, posted.channelId)
        // Done + Snooze + Tomorrow, so a fired reminder isn't a dead-end.
        assertEquals(listOf("Done", "Snooze 1h", "Tomorrow"), posted.actions.map { it.title.toString() })
    }

    @Test
    fun nagNote_schedulesTheNextReFire_walkingTheEscalationLadder() = runTest {
        val id = dao.insertNote(
            aNote(type = "reminder", title = "Pills", dueDate = "2026-07-01", dueTime = "09:00", nag = true)
        ).toInt()

        // First fire: nagCount 0 -> next nudge after the first ladder step, carrying count 1.
        receiver().handle(context, id, "Pills", null, "2026-07-01", "09:00", nagCount = 0)
        verify { alarms.snoozeReminder(id, "Pills", AlarmReceiver.NAG_INTERVALS[0], 1) }

        // Deep into the chain: interval caps at the ladder's last step.
        receiver().handle(context, id, "Pills", null, "2026-07-01", "09:00", nagCount = 9)
        verify { alarms.snoozeReminder(id, "Pills", AlarmReceiver.NAG_INTERVALS.last(), 10) }
    }

    @Test
    fun nonNagNote_neverReFires() = runTest {
        val id = dao.insertNote(
            aNote(type = "reminder", title = "Once", dueDate = "2026-07-01", dueTime = "09:00")
        ).toInt()

        receiver().handle(context, id, "Once", null, "2026-07-01", "09:00")

        verify(exactly = 0) { alarms.snoozeReminder(any(), any(), any(), any()) }
    }

    @Test
    fun nagChain_stopsOnceTheNoteIsCompleted() = runTest {
        val id = dao.insertNote(
            aNote(type = "reminder", title = "Pills", dueDate = "2026-07-01", dueTime = "09:00", nag = true, completed = true)
        ).toInt()

        receiver().handle(context, id, "Pills", null, "2026-07-01", "09:00", nagCount = 3)

        // Completed guard eats the straggler re-fire and cancels everything outstanding.
        verify { alarms.cancel(id) }
        verify(exactly = 0) { alarms.snoozeReminder(any(), any(), any(), any()) }
    }
}
