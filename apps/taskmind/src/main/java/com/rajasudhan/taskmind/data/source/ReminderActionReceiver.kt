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
                dao.setNoteCompleted(noteId, true, System.currentTimeMillis())
                alarmScheduler.cancel(noteId)
                geofenceManager.remove(noteId)
            }
            ACTION_SNOOZE -> snooze(dao.getNoteByIdNow(noteId))
        }
        // Dismiss whichever reminder surface(s) this note had showing (timed alarm and/or arrival).
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.cancel(AlarmReceiver.NOTIFICATION_ID_BASE + noteId)
        manager.cancel(GeofenceBroadcastReceiver.NOTIFICATION_ID_BASE + noteId)
    }

    /**
     * Snoozes a fired reminder [SNOOZE_MINUTES] out. A one-shot's snooze is PERSISTED as the note's
     * new due slot — that's what the snooze means now, the list shows the honest next fire, and
     * [BootReceiver] re-arms it from dueDate/dueTime so it survives a reboot (a bare AlarmManager
     * alarm would not). A recurring note keeps its persisted chain (next occurrence already armed by
     * [AlarmReceiver]); its snooze is just a transient extra nudge via [AlarmScheduler.snoozeReminder],
     * which deliberately doesn't touch the chain.
     */
    private suspend fun snooze(note: com.rajasudhan.taskmind.data.model.Note?) {
        if (note == null) return // deleted meanwhile; nothing to snooze
        if (note.recurrence.isNullOrBlank()) {
            val at = LocalDateTime.now().plusMinutes(SNOOZE_MINUTES)
            val dueDate = at.toLocalDate().toString()
            val dueTime = "%02d:%02d".format(at.hour, at.minute)
            dao.updateNote(note.copy(dueDate = dueDate, dueTime = dueTime))
            alarmScheduler.schedule(note.id, note.title, dueDate, dueTime, null)
        } else {
            alarmScheduler.snoozeReminder(note.id, note.title, SNOOZE_MINUTES)
        }
    }

    companion object {
        const val ACTION_DONE = "com.rajasudhan.taskmind.action.REMINDER_DONE"
        const val ACTION_SNOOZE = "com.rajasudhan.taskmind.action.REMINDER_SNOOZE"
        const val EXTRA_NOTE_ID = "note_id"
        const val EXTRA_TITLE = "note_title"
        const val SNOOZE_MINUTES = 60L

        // Request-code namespaces for this receiver's PendingIntents (and the notifications' tap
        // intent), kept clear of SuggestionNotifier's (1M/2M + id*2) and AlarmScheduler's (noteId,
        // 7M+noteId) so no two PendingIntents can ever collide.
        const val DONE_RC_BASE = 3_000_000
        const val SNOOZE_RC_BASE = 4_000_000
        const val OPEN_RC_BASE = 5_000_000
    }
}
