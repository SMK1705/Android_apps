package com.rajasudhan.taskmind.data.source

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.rajasudhan.taskmind.MainActivity
import com.rajasudhan.taskmind.R
import com.rajasudhan.taskmind.data.model.Note
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Posts the "did they deliver?" check when a `waiting_on` counterparty gets back in touch. This
 * deliberately never closes the note itself — the counterparty resurfacing is only a signal that the
 * *moment* to act has arrived, not proof the awaited thing actually landed ("here's the report" reads
 * the same as "are you free at 3?" to the app). So instead of silently completing the item (the old
 * behaviour, which lost real open loops when the person messaged about anything else), it hands the
 * one call the app can't make — did they deliver? — back to the user as a single tap.
 *
 * [WaitingConfirmReceiver] applies the answer: Got it completes + tears down the alarm; Still waiting
 * leaves the loop open with its follow-up nudge intact. Ignoring it changes nothing.
 */
@Singleton
class WaitingConfirmNotifier @Inject constructor(
    @ApplicationContext private val context: Context
) {
    /**
     * (Re)posts the check for [note] on the quiet check-in channel. [message] is the incoming
     * message's text (if any) — shown as a short quoted snippet so the user can judge at a glance;
     * a missed call or empty body simply omits the snippet. Stable per-note id, so a second message
     * from the same person refreshes the existing prompt instead of stacking a new one.
     */
    fun prompt(note: Note, message: String?) {
        val who = note.counterparty?.trim()?.takeIf { it.isNotBlank() } ?: "Someone"
        val subject = subjectOf(note.title)
        val question = "Waiting on: $subject — did they deliver it?"
        val snippet = message?.lineSequence()
            ?.map { it.trim() }
            ?.lastOrNull { it.isNotBlank() }
            ?.take(160)

        val tapIntent = Intent(context, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            putExtra(MainActivity.EXTRA_OPEN_NOTE_ID, note.id)
        }
        val tapPending = PendingIntent.getActivity(
            context, OPEN_RC_BASE + note.id, tapIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(context, TaskMindForegroundService.WAITING_CONFIRM_CHANNEL_ID)
            .setContentTitle("$who got in touch")
            .setContentText(question)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(tapPending)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
        if (!snippet.isNullOrBlank()) {
            builder.setStyle(NotificationCompat.BigTextStyle().bigText("“$snippet”\n\n$question"))
        }
        builder
            .addAction(
                android.R.drawable.checkbox_on_background, "Got it",
                action(WaitingConfirmReceiver.ACTION_GOT_IT, WaitingConfirmReceiver.GOT_IT_RC_BASE + note.id, note.id, note.title)
            )
            .addAction(
                android.R.drawable.ic_popup_reminder, "Still waiting",
                action(WaitingConfirmReceiver.ACTION_STILL_WAITING, WaitingConfirmReceiver.STILL_WAITING_RC_BASE + note.id, note.id, note.title)
            )

        (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
            .notify(WAITING_CONFIRM_NOTIFICATION_ID_BASE + note.id, builder.build())
    }

    /** The awaited thing, parsed from the note title ("Waiting on David: sales report" → "sales report"). */
    private fun subjectOf(title: String): String {
        val idx = title.indexOf(": ")
        if (idx < 0) return title
        return title.substring(idx + 2).trim().ifBlank { title }
    }

    private fun action(action: String, rc: Int, noteId: Int, title: String): PendingIntent {
        val intent = Intent(context, WaitingConfirmReceiver::class.java).apply {
            this.action = action
            putExtra(WaitingConfirmReceiver.EXTRA_NOTE_ID, noteId)
            putExtra(WaitingConfirmReceiver.EXTRA_TITLE, title)
        }
        return PendingIntent.getBroadcast(
            context, rc, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    companion object {
        // Stable id per note, kept clear of the foreground-service (1) / review (42) / geofence
        // (100_000 + id) / fired-reminder (200_000 + id) / bounce-back (500_000) namespaces.
        const val WAITING_CONFIRM_NOTIFICATION_ID_BASE = 300_000

        // The prompt's body-tap PendingIntent request-code namespace — clear of every action lane
        // (SuggestionNotifier 1M/2M/6M/9.5M, ReminderActionReceiver 3M–8M, AlarmScheduler 7M, and
        // WaitingConfirmReceiver's own 10M/11M).
        private const val OPEN_RC_BASE = 12_000_000
    }
}
