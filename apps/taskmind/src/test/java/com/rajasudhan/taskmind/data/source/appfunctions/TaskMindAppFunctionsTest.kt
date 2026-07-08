package com.rajasudhan.taskmind.data.source.appfunctions

import com.rajasudhan.taskmind.data.source.AlarmScheduler
import com.rajasudhan.taskmind.data.source.CalendarMirror
import com.rajasudhan.taskmind.data.source.understanding.UnderstandingPipeline
import com.rajasudhan.taskmind.testutil.FakeTaskMindDao
import com.rajasudhan.taskmind.testutil.aNote
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/** The behaviour behind the AppFunctions surface (#127). */
class TaskMindAppFunctionsTest {

    private val pipeline = mockk<UnderstandingPipeline>(relaxed = true)
    private val dao = FakeTaskMindDao()
    private val alarms = mockk<AlarmScheduler>(relaxed = true)
    private val calendarMirror = mockk<CalendarMirror>(relaxed = true)
    private val fns = TaskMindAppFunctions(pipeline, dao, alarms, calendarMirror)
    private val today = LocalDate.of(2026, 7, 8)

    // ---- createTask ----

    @Test
    fun createTask_proposesThroughTheInboxPipeline_preservingCuration() = runTest {
        coEvery { pipeline.captureFromAgent(any(), any(), any(), any(), any(), any()) } returns true

        val r = fns.createTask(CreateTaskRequest(title = "Call the dentist", dueDate = "2026-07-10", dueTime = "09:00", type = "reminder"))

        assertTrue(r.success)
        coVerify { pipeline.captureFromAgent("Call the dentist", any(), "2026-07-10", "09:00", "reminder", "Gemini") }
    }

    @Test
    fun createTask_reportsWhenNothingWasAdded() = runTest {
        coEvery { pipeline.captureFromAgent(any(), any(), any(), any(), any(), any()) } returns false
        assertFalse(fns.createTask(CreateTaskRequest(title = "already have it")).success)
    }

    @Test
    fun createTask_rejectsABlankTitle_withoutTouchingThePipeline() = runTest {
        assertFalse(fns.createTask(CreateTaskRequest(title = "   ")).success)
        coVerify(exactly = 0) { pipeline.captureFromAgent(any(), any(), any(), any(), any(), any()) }
    }

    // ---- getItemsDueToday ----

    @Test
    fun getItemsDueToday_returnsTodaysActiveItems_soonestFirst() = runTest {
        dao.insertNote(aNote(title = "Standup", type = "reminder", dueDate = "2026-07-08", dueTime = "09:00"))
        dao.insertNote(aNote(title = "Lunch", type = "reminder", dueDate = "2026-07-08", dueTime = "12:30"))
        dao.insertNote(aNote(title = "Tomorrow", type = "todo", dueDate = "2026-07-09"))
        dao.insertNote(aNote(title = "Done today", type = "todo", dueDate = "2026-07-08", completed = true))

        val r = fns.getItemsDueToday(today)

        assertEquals(listOf("Standup", "Lunch"), r.items.map { it.title })
        assertEquals(2, r.count)
    }

    @Test
    fun getItemsDueToday_ordersSingleDigitHoursChronologically() = runTest {
        // Stored times can be unpadded ("9:00"), which a string sort would wrongly place after "10:00".
        dao.insertNote(aNote(title = "Ten", type = "reminder", dueDate = "2026-07-08", dueTime = "10:00"))
        dao.insertNote(aNote(title = "Nine", type = "reminder", dueDate = "2026-07-08", dueTime = "9:00"))

        val r = fns.getItemsDueToday(today)

        assertEquals(listOf("Nine", "Ten"), r.items.map { it.title })
    }

    // ---- snoozeItem ----

    @Test
    fun snoozeItem_movesTheItem_reArmsTheAlarm_andMirrorsTheCalendar() = runTest {
        val id = dao.insertNote(
            aNote(type = "reminder", title = "Dentist", dueDate = "2026-07-08", dueTime = "14:00", calendarEventId = 5L)
        ).toInt()

        val r = fns.snoozeItem(SnoozeRequest(id = id, dueDate = "2026-07-09", dueTime = "09:00"))

        assertTrue(r.success)
        val note = dao.getNoteByIdNow(id)!!
        assertEquals("2026-07-09", note.dueDate)
        assertEquals("09:00", note.dueTime)
        verify { alarms.schedule(id, "Dentist", "2026-07-09", "09:00", null) }
        verify { calendarMirror.update(5L, "Dentist", "2026-07-09", "09:00") }
    }

    @Test
    fun snoozeItem_monthlyReminder_reAnchorsToTheSnoozedDay_andArmsWithThatAnchor() = runTest {
        // A monthly reminder anchored on the 15th, snoozed to the 20th: the anchor must move to 20 so the
        // NEXT occurrence follows the snoozed day, instead of reverting to the 15th on the next fire.
        val id = dao.insertNote(
            aNote(type = "reminder", title = "Rent", dueDate = "2026-07-15", dueTime = "09:00",
                recurrence = "monthly", recurrenceAnchorDay = 15)
        ).toInt()

        val r = fns.snoozeItem(SnoozeRequest(id = id, dueDate = "2026-07-20", dueTime = "09:00"))

        assertTrue(r.success)
        assertEquals(20, dao.getNoteByIdNow(id)!!.recurrenceAnchorDay) // re-anchored to the snoozed day
        verify { alarms.schedule(id, "Rent", "2026-07-20", "09:00", "monthly", 20) } // armed with the new anchor
    }

    @Test
    fun snoozeItem_failsForAnUnknownId() = runTest {
        assertFalse(fns.snoozeItem(SnoozeRequest(id = 999, dueDate = "2026-07-09")).success)
    }

    @Test
    fun snoozeItem_rejectsAnInvalidDate() = runTest {
        val id = dao.insertNote(aNote(type = "reminder", title = "X", dueDate = "2026-07-08", dueTime = "14:00")).toInt()
        assertFalse(fns.snoozeItem(SnoozeRequest(id = id, dueDate = "not-a-date")).success)
    }
}
