package com.rajasudhan.taskmind.data.source

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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.LocalDateTime

/** The Done/Snooze actions on fired reminders, tested via the extracted [ReminderActionReceiver.handle]. */
@RunWith(RobolectricTestRunner::class)
class ReminderActionReceiverTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private lateinit var db: TaskMindDatabase
    private lateinit var dao: TaskMindDao
    private val alarms = mockk<AlarmScheduler>(relaxed = true)
    private val geofences = mockk<GeofenceManager>(relaxed = true)

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(context, TaskMindDatabase::class.java)
            .allowMainThreadQueries().build()
        dao = db.taskMindDao()
    }

    @After
    fun tearDown() = db.close()

    private fun receiver(): ReminderActionReceiver {
        val r = ReminderActionReceiver()
        r.dao = dao
        r.alarmScheduler = alarms
        r.geofenceManager = geofences
        return r
    }

    @Test
    fun done_completesTheNote_andTearsDownAlarmAndGeofence() = runTest {
        val id = dao.insertNote(
            aNote(type = "reminder", title = "Call back", dueDate = "2026-07-01", dueTime = "09:00")
        ).toInt()

        receiver().handle(context, ReminderActionReceiver.ACTION_DONE, id, "Call back")

        assertTrue(dao.getNoteByIdNow(id)!!.completed)
        verify { alarms.cancel(id) }
        verify { geofences.remove(id) }
    }

    @Test
    fun snoozeOnAOneShot_persistsTheNewDueSlot_soItSurvivesAReboot() = runTest {
        val id = dao.insertNote(
            aNote(type = "reminder", title = "Call back", dueDate = "2026-07-01", dueTime = "09:00")
        ).toInt()

        receiver().handle(context, ReminderActionReceiver.ACTION_SNOOZE, id, "Call back")

        // The note's own due slot moved forward (BootReceiver re-arms from it after a reboot) …
        val note = dao.getNoteByIdNow(id)!!
        val newDue = LocalDateTime.of(
            java.time.LocalDate.parse(note.dueDate), java.time.LocalTime.parse(note.dueTime)
        )
        assertTrue(newDue.isAfter(LocalDateTime.now()))
        assertFalse(note.completed)
        // … and the persisted-alarm path was used, not the transient re-fire.
        verify { alarms.schedule(id, "Call back", note.dueDate, note.dueTime, null) }
        verify(exactly = 0) { alarms.snoozeReminder(any(), any(), any()) }
        verify(exactly = 0) { alarms.cancel(any()) }
    }

    @Test
    fun snoozeOnARecurring_usesTheTransientReFire_andLeavesTheChainAlone() = runTest {
        val id = dao.insertNote(
            aNote(type = "reminder", title = "Standup", dueDate = "2026-07-08", dueTime = "09:00", recurrence = "weekly")
        ).toInt()

        receiver().handle(context, ReminderActionReceiver.ACTION_SNOOZE, id, "Standup")

        // The persisted chain (next occurrence) is untouched; only the extra nudge is armed.
        val note = dao.getNoteByIdNow(id)!!
        assertEquals("2026-07-08", note.dueDate)
        verify { alarms.snoozeReminder(id, "Standup", ReminderActionReceiver.SNOOZE_MINUTES) }
        verify(exactly = 0) { alarms.schedule(any(), any(), any(), any(), any()) }
    }

    @Test
    fun snoozeOnADeletedNote_isANoOp() = runTest {
        receiver().handle(context, ReminderActionReceiver.ACTION_SNOOZE, 999, "Gone")

        verify(exactly = 0) { alarms.snoozeReminder(any(), any(), any()) }
        verify(exactly = 0) { alarms.schedule(any(), any(), any(), any(), any()) }
    }
}
