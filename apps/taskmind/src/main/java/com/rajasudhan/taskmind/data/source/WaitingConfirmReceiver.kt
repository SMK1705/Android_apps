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
import javax.inject.Inject

/**
 * Applies the user's answer to a [WaitingConfirmNotifier] "did they deliver?" check.
 *
 *  - **Got it** — the awaited thing arrived: complete the note and tear down its follow-up alarm.
 *    This is exactly what the resolver used to do automatically, now gated behind an explicit tap so
 *    an unrelated message can never close a real open loop.
 *  - **Still waiting** — they got in touch but didn't deliver: leave the note open and its follow-up
 *    nudge armed, only clearing the "awaiting confirmation" flag so it drops out of the Ready-to-close
 *    filter until they next surface.
 */
@AndroidEntryPoint
class WaitingConfirmReceiver : BroadcastReceiver() {

    @Inject lateinit var dao: TaskMindDao
    @Inject lateinit var alarmScheduler: AlarmScheduler

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        val noteId = intent.getIntExtra(EXTRA_NOTE_ID, -1)
        if (noteId < 0) return
        val title = intent.getStringExtra(EXTRA_TITLE) ?: ""

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
     * Applies the action. Extracted from [onReceive] so it's unit-testable without the
     * broadcast/goAsync plumbing. [title] is carried for parity with the other receivers (and any
     * future "chase them" nudge) even though neither branch needs it today.
     */
    internal suspend fun handle(context: Context, action: String, noteId: Int, title: String) {
        when (action) {
            ACTION_GOT_IT -> {
                dao.setNoteCompleted(noteId, true, System.currentTimeMillis())
                dao.setPendingConfirm(noteId, null)
                alarmScheduler.cancel(noteId)
            }
            // Keep the loop open and its follow-up alarm untouched; just clear the pending-confirm flag.
            ACTION_STILL_WAITING -> dao.setPendingConfirm(noteId, null)
        }
        (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
            .cancel(WaitingConfirmNotifier.WAITING_CONFIRM_NOTIFICATION_ID_BASE + noteId)
    }

    companion object {
        const val ACTION_GOT_IT = "com.rajasudhan.taskmind.action.WAITING_GOT_IT"
        const val ACTION_STILL_WAITING = "com.rajasudhan.taskmind.action.WAITING_STILL_WAITING"
        const val EXTRA_NOTE_ID = "note_id"
        const val EXTRA_TITLE = "note_title"

        // Request-code namespaces for this receiver's action PendingIntents, kept clear of every
        // other lane (SuggestionNotifier 1M/2M/6M/9.5M, ReminderActionReceiver 3M–8M, AlarmScheduler
        // 7M, and the notifier's own body-tap 12M).
        const val GOT_IT_RC_BASE = 10_000_000
        const val STILL_WAITING_RC_BASE = 11_000_000
    }
}
