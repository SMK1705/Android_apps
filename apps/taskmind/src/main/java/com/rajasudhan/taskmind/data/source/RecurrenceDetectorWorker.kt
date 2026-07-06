package com.rajasudhan.taskmind.data.source

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.rajasudhan.taskmind.data.local.TaskMindDao
import com.rajasudhan.taskmind.data.model.Note
import com.rajasudhan.taskmind.data.model.Suggestion
import com.rajasudhan.taskmind.data.source.understanding.NearDuplicate
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Auto-detected recurrence (#124 Part B). Periodically mines the user's captured history for a task that
 * keeps recurring on a steady weekly/monthly cadence and drops a single "repeat automatically?" offer
 * into the Inbox. The offer is completion-based (#124 Part A): approving it makes the item reschedule
 * from each time it's finished — the model most people mean by "make it recurring", and one that also
 * works for an untimed to-do (which has no alarm to advance it).
 *
 * Fully on-device and deterministic ([RecurrencePattern]); reads only local rows. Conservative and
 * de-duplicated: it never re-offers a pattern that's already recurring, already pending, or one the user
 * has dismissed.
 */
@HiltWorker
class RecurrenceDetectorWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val dao: TaskMindDao,
    private val notifier: SuggestionNotifier,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result = try {
        if (detectAndOffer(LocalDate.now())) notifier.notifyPending()
        Result.success()
    } catch (e: Exception) {
        e.printStackTrace()
        Result.retry()
    }

    /**
     * Runs the miner and inserts any fresh offers; returns true if at least one was inserted (so the
     * caller can surface a review notification). Extracted from [doWork] so a test can drive it with a
     * fixed [today] and without the WorkManager plumbing.
     */
    internal suspend fun detectAndOffer(today: LocalDate): Boolean {
        val notes = dao.getNotesList()
        // Mine only one-shot, non-archived tasks/reminders — a row that already repeats isn't history,
        // it's the outcome we'd be proposing.
        val candidates = notes.filter {
            !it.archived && it.recurrence == null && (it.type == "todo" || it.type == "reminder")
        }
        if (candidates.size < RecurrencePattern.MIN_OCCURRENCES) return false

        val history = candidates.mapNotNull { n ->
            occurrenceDate(n)?.let { RecurrencePattern.Occurrence(n.title, it) }
        }
        val detected = RecurrencePattern.detect(history, today)
        if (detected.isEmpty()) return false

        val pending = dao.getPendingSuggestions().first()
        val activeRecurring = notes.filter { !it.completed && !it.archived && it.recurrence != null }

        var offered = false
        for (d in detected) {
            fun similar(text: String) = NearDuplicate.tokenOverlap(text, d.title) >= NearDuplicate.TOKEN_OVERLAP
            // Backoff: skip a pattern that's already recurring, already pending, or previously dismissed.
            if (activeRecurring.any { similar(it.title) }) continue
            if (pending.any { similar(it.extractedTitle) }) continue
            if (dao.rejectedPatternFor(REJECTED_KIND, RecurrencePattern.key(d.title)) != null) continue

            // Match the source rows to pick a type + a consistent time (if any): a timed reminder keeps
            // its alarm, anything else becomes a to-do that rolls forward on completion.
            val group = candidates.filter { similar(it.title) }
            val dueTime = group.mapNotNull { it.dueTime }.groupingBy { it }.eachCount().maxByOrNull { it.value }?.key
            val modalType = group.groupingBy { it.type }.eachCount().maxByOrNull { it.value }?.key
            val type = if (dueTime != null && modalType == "reminder") "reminder" else "todo"

            dao.insertSuggestion(
                Suggestion(
                    source = RECURRENCE_SOURCE,
                    rawSnippet = "You've saved “${d.title}” ${d.occurrences} times on a ${d.recurrence} rhythm.",
                    extractedTitle = d.title,
                    summary = "Repeat automatically — rescheduled each time you finish it.",
                    dueDate = d.nextDate.toString(),
                    dueTime = dueTime,
                    type = type,
                    confidence = d.confidence,
                    status = "pending",
                    recurrence = d.recurrence,
                    repeatFromCompletion = true,
                )
            )
            offered = true
        }
        return offered
    }

    /** The day a sighting counts on: its due date if set, else the day it was captured. */
    private fun occurrenceDate(note: Note): LocalDate? =
        note.dueDate?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
            ?: runCatching { Instant.ofEpochMilli(note.createdDate).atZone(ZoneId.systemDefault()).toLocalDate() }.getOrNull()

    companion object {
        const val RECURRENCE_SOURCE = "Recurring pattern"
        const val REJECTED_KIND = "recurrence"
    }
}
