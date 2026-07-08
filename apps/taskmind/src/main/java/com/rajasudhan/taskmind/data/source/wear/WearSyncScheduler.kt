package com.rajasudhan.taskmind.data.source.wear

import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.rajasudhan.taskmind.data.local.TaskMindDao
import com.rajasudhan.taskmind.data.model.Note
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Keeps the watch's next-due tile fresh (#216). [schedule] runs the periodic [WearSyncWorker] every 30
 * minutes — the battery-cheap backstop — and, since #245, [observeDueChanges] also re-publishes the tile
 * the moment the due set actually changes, instead of leaving it stale for up to 30 min.
 *
 * Rather than hook every mutation site (approve / complete / snooze / reschedule / delete), it observes the
 * single choke-point they all flow through — the active-notes table — projected to only the due-relevant
 * fields (so a body-only edit doesn't republish), debounced, and coalesced into one [WearSyncWorker] run.
 */
@Singleton
class WearSyncScheduler @Inject constructor(
    private val workManager: WorkManager,
    private val dao: TaskMindDao,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    fun schedule() {
        val request = PeriodicWorkRequestBuilder<WearSyncWorker>(30, TimeUnit.MINUTES).build()
        workManager.enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
    }

    /**
     * Enqueue a one-shot next-due re-publish now. [ExistingWorkPolicy.REPLACE] under a unique name collapses
     * a burst of due-set changes (and any still-pending publish) into a single run.
     */
    fun syncNow() {
        val request = OneTimeWorkRequestBuilder<WearSyncWorker>().build()
        workManager.enqueueUniqueWork(ONESHOT_WORK_NAME, ExistingWorkPolicy.REPLACE, request)
    }

    /**
     * Start observing the due set and [syncNow] on each change (#245). Long-lived (the app-process lifetime),
     * like [schedule]. [drop] skips the initial snapshot — launch already publishes once via [schedule]'s
     * periodic run — and [debounce] coalesces a burst (e.g. Approve-All) into one publish.
     */
    fun observeDueChanges() {
        scope.launch {
            dao.getActiveNotes()
                .map(::dueSignature)
                .distinctUntilChanged()
                .drop(1)
                .debounce(DEBOUNCE_MS)
                .collect { syncNow() }
        }
    }

    companion object {
        const val WORK_NAME = "taskmind_wear_sync"
        const val ONESHOT_WORK_NAME = "taskmind_wear_sync_now"
        private const val DEBOUNCE_MS = 1500L

        /**
         * The due-relevant projection of the active notes: the tile's next-due can only change when a note's
         * id/title/date/time changes, or one enters/leaves the active set (complete/delete/archive drop out of
         * [TaskMindDao.getActiveNotes]) — NOT when its body is edited. Pure, so the change-detection is unit-tested.
         */
        internal fun dueSignature(notes: List<Note>): List<DueKey> =
            notes.map { DueKey(it.id, it.title, it.dueDate, it.dueTime) }

        /** A single note's contribution to what the tile shows. */
        internal data class DueKey(val id: Int, val title: String, val dueDate: String?, val dueTime: String?)
    }
}
