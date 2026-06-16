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
        const val NOTIFICATION_ID = 42
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
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle(if (count == 1) "1 suggestion to review" else "$count suggestions to review")
            .setContentText(top.extractedTitle)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(tapIntent)
            .setAutoCancel(true)
            .addAction(android.R.drawable.ic_menu_send, "Approve", action(NotificationActionReceiver.ACTION_APPROVE, top.id))
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Reject", action(NotificationActionReceiver.ACTION_REJECT, top.id))
            .build()
        manager?.notify(NOTIFICATION_ID, notification)
    }

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
    }
}
