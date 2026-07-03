package com.rajasudhan.taskmind.data.source

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.rajasudhan.taskmind.MainActivity
import com.rajasudhan.taskmind.R
import com.rajasudhan.taskmind.data.local.TaskMindDao
import com.rajasudhan.taskmind.data.model.Note
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlin.math.absoluteValue
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Person-context reminders: the moment someone you have an open item with gets in touch (a message
 * or a call), surface that item — "ask about the contract" delivered exactly when Sarah calls,
 * instead of at some arbitrary clock time. Reuses the counterparty already captured on notes and the
 * conservative [PersonMatch] name matching, so an unrelated sender never triggers a reminder.
 */
@Singleton
class PersonContextNotifier @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dao: TaskMindDao,
) {
    private val manager: NotificationManager? = context.getSystemService(NotificationManager::class.java)

    /**
     * If [person] (an incoming message sender or caller name) has open items tied to them, post a
     * single reminder surfacing them. No-op for a blank/too-short name or when nothing matches. The
     * notification id is stable per person, so repeated contact refreshes it rather than stacking.
     */
    suspend fun notifyForContact(person: String?) {
        val who = person?.trim().orEmpty()
        if (PersonMatch.tokens(who).isEmpty()) return
        val matches = dao.getActivePersonNotes().filter {
            PersonMatch.matches(who, it.counterparty ?: "")
        }
        if (matches.isEmpty()) return
        post(who, matches)
    }

    private fun post(person: String, items: List<Note>) {
        ensureChannel()
        val title = if (items.size == 1) "About ${person.trim()}" else "${items.size} open with ${person.trim()}"
        val lines = items.take(MAX_LINES).joinToString("\n") { "• ${it.title}" }
        val body = if (items.size > MAX_LINES) "$lines\n…" else lines
        val tapIntent = PendingIntent.getActivity(
            context, TAP_RC_BASE + stableId(person),
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(items.first().title)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setSubText("They just got in touch")
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(tapIntent)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
        manager?.notify(NOTIFICATION_ID_BASE + stableId(person), builder.build())
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager?.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "When someone's in touch", NotificationManager.IMPORTANCE_DEFAULT)
            )
        }
    }

    /** A stable small id per person, so the same person's reminder refreshes instead of stacking. */
    private fun stableId(person: String): Int = person.trim().lowercase().hashCode().absoluteValue % 100_000

    companion object {
        const val CHANNEL_ID = "taskmind_person_context"
        private const val NOTIFICATION_ID_BASE = 600_000
        private const val TAP_RC_BASE = 9_600_000
        private const val MAX_LINES = 5
    }
}
