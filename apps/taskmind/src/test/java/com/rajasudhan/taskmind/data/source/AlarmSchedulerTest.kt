package com.rajasudhan.taskmind.data.source

import android.app.AlarmManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowAlarmManager
import java.time.LocalDate

/**
 * The alarm/re-fire namespace bookkeeping — the safety net behind nag mode. The main reminder alarm
 * (request code = noteId) and the transient snooze/nag re-fire (7M namespace) must be independently
 * cancellable, and (re)establishing the main schedule must clear a stale re-fire so a rescheduled or
 * snoozed nag note can't keep ringing on its old cadence. Asserted via ShadowAlarmManager counts.
 */
@RunWith(RobolectricTestRunner::class)
class AlarmSchedulerTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private lateinit var scheduler: AlarmScheduler
    private val am get() = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    // A due slot comfortably in the future so schedule() actually arms it.
    private val futureDate = LocalDate.now().plusDays(2).toString()

    @Before
    fun setUp() {
        ShadowAlarmManager.setCanScheduleExactAlarms(true)
        scheduler = AlarmScheduler(context)
    }

    @Test
    fun schedule_clearsAPendingReFire_soItDoesNotRingAlongsideTheNewSlot() {
        scheduler.snoozeReminder(1, "Pills", minutes = 5, nagCount = 3)
        assertEquals(1, shadowOf(am).scheduledAlarms.size)

        scheduler.schedule(1, "Pills", futureDate, "09:00", null)

        // If schedule() had NOT cancelled the re-fire, both would be armed (count 2). One proves the
        // stale nag re-fire was cleared and only the main alarm remains.
        assertEquals(1, shadowOf(am).scheduledAlarms.size)
    }

    @Test
    fun cancelRefire_removesTheReFire_butLeavesTheMainAlarm() {
        scheduler.schedule(1, "Pills", futureDate, "09:00", null)
        scheduler.snoozeReminder(1, "Pills", minutes = 5, nagCount = 0)
        assertEquals(2, shadowOf(am).scheduledAlarms.size)

        scheduler.cancelRefire(1)

        assertEquals(1, shadowOf(am).scheduledAlarms.size) // main survives
    }

    @Test
    fun cancel_clearsBothTheMainAlarmAndTheReFire() {
        scheduler.schedule(1, "Pills", futureDate, "09:00", null)
        scheduler.snoozeReminder(1, "Pills", minutes = 5, nagCount = 0)
        assertEquals(2, shadowOf(am).scheduledAlarms.size)

        scheduler.cancel(1)

        assertEquals(0, shadowOf(am).scheduledAlarms.size)
    }

    @Test
    fun reFiresForDifferentNotesAreIndependent() {
        scheduler.snoozeReminder(1, "A", minutes = 5, nagCount = 0)
        scheduler.snoozeReminder(2, "B", minutes = 5, nagCount = 0)
        assertEquals(2, shadowOf(am).scheduledAlarms.size)

        scheduler.cancelRefire(1)

        assertEquals(1, shadowOf(am).scheduledAlarms.size) // note 2's re-fire untouched
    }
}
