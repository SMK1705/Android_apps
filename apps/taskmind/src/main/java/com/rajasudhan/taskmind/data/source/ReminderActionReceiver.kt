package com.rajasudhan.taskmind.data.source

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.rajasudhan.taskmind.data.local.TaskMindDao
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import javax.inject.Inject

/**
 * Handles the Done/Snooze actions on a *fired* reminder or geofence notification — the note-side
 * counterpart of [NotificationActionReceiver] (which handles suggestion review). Done completes the
 * note and tears down its alarm + geofence, exactly like completing it in the app; Snooze re-fires
 * the notification later without touching a recurring reminder's already-scheduled next occurrence.
 */
@AndroidEntryPoint
class ReminderActionReceiver : BroadcastReceiver() {

    @Inject lateinit var dao: TaskMindDao
    @Inject lateinit var alarmScheduler: AlarmScheduler
    @Inject lateinit var geofenceManager: GeofenceManager
    @Inject lateinit var completionRecurrence: CompletionRecurrence
    @Inject lateinit var calendarMirror: CalendarMirror

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        val noteId = intent.getIntExtra(EXTRA_NOTE_ID, -1)
        if (noteId < 0) return
        val title = intent.getStringExtra(EXTRA_TITLE) ?: ""

        // Keep the receiver alive while the DB work runs.
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                handle(context, action, noteId, title)
            } finally {
                pendingResult.finish()
            }
        }
    }

    /**
     * Applies the notification action. Extracted from [onReceive] so it's unit-testable without the
     * broadcast/goAsync plumbing.
     */
    internal suspend fun handle(context: Context, action: String, noteId: Int, title: String) {
        when (action) {
            ACTION_DONE -> {
                val note = dao.getNoteByIdNow(noteId)
                val at = System.currentTimeMillis()
                dao.setNoteCompleted(noteId, true, at)
                // Completion-based recurrence (#124): roll forward from completion rather than tearing
                // the alarm down; a one-shot / date-based repeat still gets cancelled as before.
                if (!completionRecurrence.rollForwardIfCompletionBased(note, at)) {
                    alarmScheduler.cancel(noteId)
                    geofenceManager.remove(noteId)
                    // Remove the mirrored calendar event too (#119) — the task is done.
                    note?.calendarEventId?.let { calendarMirror.delete(it); dao.updateNoteCalendarEventId(noteId, null) }
                }
            }
            ACTION_SNOOZE -> snooze(dao.getNoteByIdNow(noteId), LocalDateTime.now().plusMinutes(SNOOZE_MINUTES))
            // "Tomorrow" = tomorrow morning at 09:00 — the deal-with-it-in-the-morning snooze.
            ACTION_SNOOZE_TOMORROW -> snooze(
                dao.getNoteByIdNow(noteId),
                LocalDateTime.now().plusDays(1).withHour(9).withMinute(0)
            )
        }
        // Dismiss whichever reminder surface(s) this note had showing (timed alarm and/or arrival).
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.cancel(AlarmReceiver.NOTIFICATION_ID_BASE + noteId)
        manager.cancel(GeofenceBroadcastReceiver.NOTIFICATION_ID_BASE + noteId)
    }

    /**
     * Snoozes a fired reminder until [at]. A one-shot's snooze is PERSISTED as the note's new due
     * slot — that's what the snooze means now, the list shows the honest next fire, and
     * [BootReceiver] re-arms it from dueDate/dueTime so it survives a reboot (a bare AlarmManager
     * alarm would not). A recurring note keeps its persisted chain (next occurrence already armed by
     * [AlarmReceiver]); its snooze is just a transient extra nudge via [AlarmScheduler.snoozeReminder],
     * which deliberately doesn't touch the chain.
     */
    private suspend fun snooze(note: com.rajasudhan.taskmind.data.model.Note?, at: LocalDateTime) {
        if (note == null) return // deleted meanwhile; nothing to snooze
        // The user deferred this fire, so end the current nag chain — it must not resume on reboot
        // before the snooze lands; the next fire re-arms it.
        if (note.nagFiring) dao.setNagFiring(note.id, false)
        if (note.recurrence.isNullOrBlank()) {
            val dueDate = at.toLocalDate().toString()
            val dueTime = "%02d:%02d".format(at.hour, at.minute)
            dao.updateNote(note.copy(dueDate = dueDate, dueTime = dueTime))
            alarmScheduler.schedule(note.id, note.title, dueDate, dueTime, null)
            // A snooze is a reschedule: move the mirrored calendar event to the new slot too (#119).
            note.calendarEventId?.let { calendarMirror.update(it, note.title, dueDate, dueTime) }
        } else {
            // Round UP to whole minutes: the target was computed from an earlier now(), so a plain
            // toMinutes() would land at 59 for a "1 hour" snooze.
            val minutes = ((java.time.Duration.between(LocalDateTime.now(), at).seconds + 59) / 60).coerceAtLeast(1)
            alarmScheduler.snoozeReminder(note.id, note.title, minutes)
        }
    }

    companion object {
        const val ACTION_DONE = "com.rajasudhan.taskmind.action.REMINDER_DONE"
        const val ACTION_SNOOZE = "com.rajasudhan.taskmind.action.REMINDER_SNOOZE"
        const val ACTION_SNOOZE_TOMORROW = "com.rajasudhan.taskmind.action.REMINDER_SNOOZE_TOMORROW"
        const val EXTRA_NOTE_ID = "note_id"
        const val EXTRA_TITLE = "note_title"
        const val SNOOZE_MINUTES = 60L

        // Request-code namespaces for this receiver's PendingIntents (and the notifications' tap
        // intent), kept clear of SuggestionNotifier's (1M/2M + id*2) and AlarmScheduler's (noteId,
        // 7M+noteId) so no two PendingIntents can ever collide.
        const val DONE_RC_BASE = 3_000_000
        const val SNOOZE_RC_BASE = 4_000_000
        const val OPEN_RC_BASE = 5_000_000
        const val TOMORROW_RC_BASE = 8_000_000
    }
}
