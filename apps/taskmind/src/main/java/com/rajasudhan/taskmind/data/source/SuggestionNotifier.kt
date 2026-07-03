package com.rajasudhan.taskmind.data.source

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import com.rajasudhan.taskmind.MainActivity
import com.rajasudhan.taskmind.R
import com.rajasudhan.taskmind.data.local.TaskMindDao
import com.rajasudhan.taskmind.data.model.Suggestion
import com.rajasudhan.taskmind.ui.capture.QuickAddWidget
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Posts/updates a single review notification. It features the top pending suggestion with **Approve**
 * / **Reject** actions (handled by [NotificationActionReceiver]) so an item can be triaged without
 * opening the app, and tapping the body opens the Inbox.
 */
@Singleton
class SuggestionNotifier @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dao: TaskMindDao
) {
    companion object {
        const val CHANNEL_ID = "taskmind_suggestions"
        const val BOUNCE_BACK_CHANNEL_ID = "taskmind_bounce_back"
        const val NOTIFICATION_ID = 42
        // Its own notification-id lane so a bounced-back message coexists with the review prompt (42).
        const val BOUNCE_NOTIFICATION_ID_BASE = 500_000
        // High request-code bases so Call/Directions/resurface PendingIntents never collide with
        // the Approve/Reject broadcasts (which use id*2 + 0/1).
        private const val CALL_RC_BASE = 1_000_000
        private const val DIRECTIONS_RC_BASE = 2_000_000
        private const val RESURFACE_RC_BASE = 6_000_000
        private const val BOUNCE_TAP_RC_BASE = 9_500_000
    }

    private val manager: NotificationManager? =
        context.getSystemService(NotificationManager::class.java)

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID, "New Suggestions", NotificationManager.IMPORTANCE_DEFAULT
            )
            manager?.createNotificationChannel(channel)
        }
    }

    /** Shows/updates the review prompt for the top pending suggestion; cancels it when none remain. */
    suspend fun notifyPending() {
        val now = System.currentTimeMillis()
        val pending = dao.getPendingSuggestions().first()
            .filter { it.snoozedUntil == null || it.snoozedUntil!! <= now }
        if (pending.isEmpty()) {
            cancel()
            return
        }
        ensureChannel()
        val top = pending.first()
        val count = pending.size

        val tapIntent = PendingIntent.getActivity(
            context, 0, Intent(context, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE
        )
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle(if (count == 1) "1 suggestion to review" else "$count suggestions to review")
            .setContentText(top.extractedTitle)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(tapIntent)
            .setAutoCancel(true)

        // A contextual first action — Call when the item has a dialable number, else Directions when it
        // names a place — so it can be acted on straight from the shade, mirroring the in-app cards. The
        // shade shows ~3 actions, so we add at most one contextual action alongside Approve + Reject.
        val number = runCatching {
            resolveCallNumber(context, top.extractedTitle, top.summary, top.rawSnippet, top.source)
        }.getOrNull()
        val place = top.location?.trim()?.takeUnless { it.isBlank() }
        when {
            number != null -> builder.addAction(
                android.R.drawable.ic_menu_call, "Call",
                activityAction(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$number")), top.id + CALL_RC_BASE)
            )
            place != null -> builder.addAction(
                android.R.drawable.ic_menu_directions, "Directions",
                activityAction(directionsIntent(place), top.id + DIRECTIONS_RC_BASE)
            )
        }
        builder
            .addAction(android.R.drawable.ic_menu_send, "Approve", action(NotificationActionReceiver.ACTION_APPROVE, top.id))
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Reject", action(NotificationActionReceiver.ACTION_REJECT, top.id))
        manager?.notify(NOTIFICATION_ID, builder.build())
        QuickAddWidget.refresh(context)
    }

    /** A PendingIntent that launches [intent] (dialer / Maps) straight from the notification action. */
    private fun activityAction(intent: Intent, rc: Int): PendingIntent =
        PendingIntent.getActivity(
            context, rc, intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

    /** Maps directions to a named place (mirrors [com.rajasudhan.taskmind.ui.common.openDirections]). */
    private fun directionsIntent(place: String): Intent = Intent(
        Intent.ACTION_VIEW,
        Uri.parse("https://www.google.com/maps/dir/?api=1&destination=${Uri.encode(place)}")
    )

    private fun action(action: String, id: Int): PendingIntent {
        val intent = Intent(context, NotificationActionReceiver::class.java).apply {
            this.action = action
            putExtra(NotificationActionReceiver.EXTRA_ID, id)
        }
        // Distinct request code per (action, id) so Approve and Reject don't share a PendingIntent.
        val rc = id * 2 + if (action == NotificationActionReceiver.ACTION_APPROVE) 0 else 1
        return PendingIntent.getBroadcast(
            context, rc, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    fun cancel() {
        manager?.cancel(NOTIFICATION_ID)
        QuickAddWidget.refresh(context)
    }

    private fun ensureBounceBackChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager?.createNotificationChannel(
                NotificationChannel(BOUNCE_BACK_CHANNEL_ID, "Message reminders", NotificationManager.IMPORTANCE_DEFAULT)
            )
        }
    }

    /**
     * Bounce-Back: at a snoozed suggestion's chosen time, re-posts its ORIGINAL captured message as
     * its own notification — so "remind me about this later" brings back the actual content you
     * wanted to deal with, not a generic "N to review". Tapping opens the Inbox. On its own channel so
     * it can be tuned or silenced apart from the review prompt.
     */
    fun notifyBounceBack(suggestion: Suggestion) {
        ensureBounceBackChannel()
        val body = suggestion.rawSnippet.trim().ifBlank { suggestion.extractedTitle }
        val tapIntent = PendingIntent.getActivity(
            context, BOUNCE_TAP_RC_BASE + suggestion.id, Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val builder = NotificationCompat.Builder(context, BOUNCE_BACK_CHANNEL_ID)
            .setContentTitle(bounceTitle(suggestion.source))
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setSubText("Reminder")
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(tapIntent)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
        manager?.notify(BOUNCE_NOTIFICATION_ID_BASE + suggestion.id, builder.build())
        QuickAddWidget.refresh(context)
    }

    /** The sender from a source label ("Notification from Alex" → "Alex"), else the source itself. */
    private fun bounceTitle(source: String): String =
        source.substringAfter(" from ", source).trim().ifBlank { "Reminder" }

    /**
     * Arms an alarm for a snoozed suggestion's return time, so the review notification re-posts
     * (and the widget refreshes) the moment the snooze expires — without this a snoozed item only
     * resurfaced silently, whenever the app was next opened. Inexact-but-doze-safe is enough here:
     * "back around tomorrow morning" doesn't need to-the-minute delivery, and it keeps snoozes off
     * the exact-alarm budget reserved for real reminders. Firing is harmless if the suggestion was
     * meanwhile handled or its snooze undone — notifyPending() just re-renders the current state.
     */
    fun scheduleResurface(suggestionId: Int, at: Long) {
        val intent = Intent(context, NotificationActionReceiver::class.java).apply {
            action = NotificationActionReceiver.ACTION_RESURFACE
            putExtra(NotificationActionReceiver.EXTRA_ID, suggestionId)
        }
        val pi = PendingIntent.getBroadcast(
            context, RESURFACE_RC_BASE + suggestionId, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        context.getSystemService(AlarmManager::class.java)
            ?.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
    }
}
