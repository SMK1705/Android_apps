package com.rajasudhan.taskmind.data.source

import android.app.AlarmManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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

    // ---- recurring auto-advance (#1/#6) ----

    @Test
    fun schedule_recurringReminderWithPastSlot_advancesToFutureAndArms() {
        val pastDate = LocalDate.now().minusDays(3).toString()

        // Previously the past slot was silently dropped (nothing armed); now it advances to the next
        // future occurrence and arms, so a "Daily" reminder created/edited from a stale date still fires.
        val armed = scheduler.schedule(1, "Pills", pastDate, "09:00", "daily")

        assertEquals(1, shadowOf(am).scheduledAlarms.size)
        assertNotEquals(pastDate, armed)
        assertTrue("armed date must be today or later", !LocalDate.parse(armed).isBefore(LocalDate.now()))
    }

    @Test
    fun schedule_recurringReminderWithFutureSlot_armsUnchanged() {
        val armed = scheduler.schedule(1, "Standup", futureDate, "09:00", "weekly")

        assertEquals(1, shadowOf(am).scheduledAlarms.size)
        assertEquals(futureDate, armed) // already future -> no advance
    }

    @Test
    fun schedule_oneShotWithPastSlot_isDroppedAndReturnsNull() {
        val pastDate = LocalDate.now().minusDays(1).toString()

        val armed = scheduler.schedule(1, "Pills", pastDate, "09:00", null)

        assertEquals(0, shadowOf(am).scheduledAlarms.size) // a one-shot in the past correctly never fires
        assertNull(armed)
    }

    // ---- exact-alarm fallback (#5) ----

    @Test
    fun schedule_fallsBackToInexact_whenExactAlarmsNotPermitted() {
        ShadowAlarmManager.setCanScheduleExactAlarms(false)

        val armed = scheduler.schedule(1, "Pills", futureDate, "09:00", null)

        // No exact-alarm permission must NOT silently drop the reminder — an inexact alarm still arms.
        assertEquals(1, shadowOf(am).scheduledAlarms.size)
        assertEquals(futureDate, armed)
    }
}
