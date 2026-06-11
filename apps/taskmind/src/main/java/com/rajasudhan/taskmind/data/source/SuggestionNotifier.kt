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
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** Posts/updates a single "N suggestions to review" notification that opens the app. */
@Singleton
class SuggestionNotifier @Inject constructor(
    @ApplicationContext private val context: Context
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

    /** Shows/updates the review prompt; cancels it when there's nothing pending. */
    fun notifyPending(count: Int) {
        if (count <= 0) {
            cancel()
            return
        }
        ensureChannel()
        val pendingIntent = PendingIntent.getActivity(
            context, 0, Intent(context, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE
        )
        val text = if (count == 1) "1 new suggestion to review" else "$count suggestions to review"
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle("TaskMind")
            .setContentText(text)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()
        manager?.notify(NOTIFICATION_ID, notification)
    }

    fun cancel() {
        manager?.cancel(NOTIFICATION_ID)
    }
}
