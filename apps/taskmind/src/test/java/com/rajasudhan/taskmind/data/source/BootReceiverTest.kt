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
import org.junit.Assert.assertEquals
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
    fun rearm_reschedulesADatedWaitingOnFollowup() = runTest {
        // A waiting-on follow-up nudge is a plain alarm too — it must survive a reboot like a reminder.
        dao.insertNote(
            aNote(type = "waiting_on", title = "Waiting on John: numbers", dueDate = "2026-07-01", dueTime = "09:00", counterparty = "John")
        )

        receiver().rearm(LocalDateTime.of(2026, 6, 1, 0, 0))

        verify { alarms.schedule(any(), "Waiting on John: numbers", "2026-07-01", "09:00", null) }
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
    fun rearm_restartsTheNagLoopForAFiringNagNote() = runTest {
        // A nag note whose chain was active (nagFiring) when the device went down: resume it.
        val id = dao.insertNote(
            aNote(type = "reminder", title = "Pills", dueDate = "2026-06-01", dueTime = "09:00", nag = true, nagFiring = true)
        ).toInt()

        receiver().rearm(LocalDateTime.of(2026, 6, 20, 12, 0))

        verify { alarms.snoozeReminder(id, "Pills", AlarmReceiver.NAG_INTERVALS[0], 0) }
    }

    @Test
    fun rearm_restartsTheNagLoopForAFiringRecurringNagNote() = runTest {
        // #180: a RECURRING nag note's date advances the moment it fires, so the old "slot is past"
        // heuristic never matched and its nag chain died on reboot. The nagFiring flag fixes it.
        val id = dao.insertNote(
            aNote(type = "reminder", title = "Take pills", dueDate = "2026-06-21", dueTime = "09:00",
                recurrence = "daily", nag = true, nagFiring = true) // date already advanced to the future
        ).toInt()

        receiver().rearm(LocalDateTime.of(2026, 6, 20, 12, 0))

        verify { alarms.snoozeReminder(id, "Take pills", AlarmReceiver.NAG_INTERVALS[0], 0) }
    }

    @Test
    fun rearm_restartsTheNagLoopForAFiringWaitingOnNagNote() = runTest {
        // A nag on a non-'reminder' (waiting_on) note used to never resume on reboot — issue #178.
        val id = dao.insertNote(
            aNote(type = "waiting_on", title = "Chase invoice", dueDate = "2026-06-01", dueTime = "09:00",
                nag = true, nagFiring = true, counterparty = "Acme")
        ).toInt()

        receiver().rearm(LocalDateTime.of(2026, 6, 20, 12, 0))

        verify { alarms.snoozeReminder(id, "Chase invoice", AlarmReceiver.NAG_INTERVALS[0], 0) }
    }

    @Test
    fun rearm_doesNotRestartNag_whenNotFiring_orCompleted_orNagOff() = runTest {
        dao.insertNote(aNote(type = "reminder", title = "NotFiring", dueDate = "2026-06-01", dueTime = "09:00", nag = true, nagFiring = false))
        dao.insertNote(aNote(type = "reminder", title = "Done", dueDate = "2026-06-01", dueTime = "09:00", nag = true, nagFiring = true, completed = true))
        dao.insertNote(aNote(type = "reminder", title = "NagOff", dueDate = "2026-06-01", dueTime = "09:00", nag = false, nagFiring = true))

        receiver().rearm(LocalDateTime.of(2026, 6, 20, 12, 0))

        verify(exactly = 0) { alarms.snoozeReminder(any(), any(), any(), any()) }
    }

    @Test
    fun rearm_forAClockChange_reArmsAlarmsButDoesNotResurfaceOrRepost() = runTest {
        // A timezone/clock change (reNotify = false): the wall-clock reminder alarm must be re-armed
        // for the new zone, but the snooze-resurface and review-notification re-post (reboot-only state
        // restore) must be skipped — they'd be pointless and noisy on every clock change.
        dao.insertNote(aNote(type = "reminder", title = "Standup", dueDate = "2026-07-01", dueTime = "09:00"))
        dao.insertSuggestion(
            aSuggestion(extractedTitle = "Later", status = "pending", snoozedUntil = System.currentTimeMillis() + 3_600_000)
        )

        receiver().rearm(LocalDateTime.of(2026, 6, 1, 0, 0), reNotify = false)

        verify { alarms.schedule(any(), "Standup", "2026-07-01", "09:00", null) } // reminder re-armed
        verify(exactly = 0) { notifier.scheduleResurface(any(), any()) }           // no snooze resurface
        coVerify(exactly = 0) { notifier.notifyPending() }                          // no notification re-post
    }

    @Test
    fun rearm_forAClockChange_reArmsRecurring_butDoesNotPersistAnAdvancedDate() = runTest {
        // A recurring reminder whose stored slot is behind `now`: on a clock change the alarm is re-armed,
        // but the stored date must NOT advance — a user's temporary clock set-forward-then-back would
        // otherwise stick a wrong date and skip an occurrence (a reboot DOES persist, tested elsewhere).
        val id = dao.insertNote(
            aNote(type = "reminder", title = "Rent", dueDate = "2026-06-01", dueTime = "09:00", recurrence = "weekly")
        ).toInt()

        receiver().rearm(LocalDateTime.of(2026, 6, 20, 12, 0), reNotify = false)

        verify { alarms.schedule(id, "Rent", any(), "09:00", "weekly") } // alarm still re-armed
        assertEquals("2026-06-01", dao.getNoteByIdNow(id)!!.dueDate)     // stored date NOT advanced/persisted
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
