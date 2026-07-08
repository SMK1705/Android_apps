package com.rajasudhan.taskmind.data.source.wear

import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkManager
import com.rajasudhan.taskmind.data.local.TaskMindDao
import com.rajasudhan.taskmind.data.model.Note
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/** #245: the pure due-change projection that gates tile re-publishes, and the one-shot enqueue. */
class WearSyncSchedulerTest {

    private val workManager = mockk<WorkManager>(relaxed = true)
    private val dao = mockk<TaskMindDao>(relaxed = true)
    private val scheduler = WearSyncScheduler(workManager, dao)

    private fun note(
        id: Int,
        title: String = "Standup",
        dueDate: String? = "2026-07-07",
        dueTime: String? = "09:00",
        body: String = "b",
    ) = Note(
        id = id, title = title, body = body, dueDate = dueDate, dueTime = dueTime,
        source = "test", createdDate = 0L, type = "reminder",
    )

    @Test
    fun dueSignature_unchanged_whenOnlyBodyEdited() {
        // A body-only edit must NOT trigger a tile re-publish — the tile never shows the body.
        val before = WearSyncScheduler.dueSignature(listOf(note(1, body = "old")))
        val after = WearSyncScheduler.dueSignature(listOf(note(1, body = "a much longer, edited body")))
        assertEquals(before, after)
    }

    @Test
    fun dueSignature_changed_whenRescheduledToAnotherTime() {
        assertNotEquals(
            WearSyncScheduler.dueSignature(listOf(note(1, dueTime = "09:00"))),
            WearSyncScheduler.dueSignature(listOf(note(1, dueTime = "18:00"))),
        )
    }

    @Test
    fun dueSignature_changed_whenRescheduledToAnotherDay() {
        // A day-level reschedule (same time, today → tomorrow) must still re-publish — pins dueDate in the
        // projection so a regression dropping it can't leave the tile stale.
        assertNotEquals(
            WearSyncScheduler.dueSignature(listOf(note(1, dueDate = "2026-07-07"))),
            WearSyncScheduler.dueSignature(listOf(note(1, dueDate = "2026-07-08"))),
        )
    }

    @Test
    fun dueSignature_changed_whenTitleEdited() {
        assertNotEquals(
            WearSyncScheduler.dueSignature(listOf(note(1, title = "Standup"))),
            WearSyncScheduler.dueSignature(listOf(note(1, title = "Standup (moved)"))),
        )
    }

    @Test
    fun dueSignature_changed_whenItemLeavesActiveSet() {
        // complete / delete / archive drop the note out of getActiveNotes → shorter list → different signature.
        assertNotEquals(
            WearSyncScheduler.dueSignature(listOf(note(1), note(2))),
            WearSyncScheduler.dueSignature(listOf(note(1))),
        )
    }

    @Test
    fun syncNow_enqueuesUniqueOneShot_withReplace() {
        scheduler.syncNow()
        verify {
            workManager.enqueueUniqueWork(
                eq(WearSyncScheduler.ONESHOT_WORK_NAME),
                eq(ExistingWorkPolicy.REPLACE),
                any<OneTimeWorkRequest>(),
            )
        }
    }
}
