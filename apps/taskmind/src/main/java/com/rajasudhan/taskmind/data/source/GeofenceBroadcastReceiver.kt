package com.rajasudhan.taskmind.data.source

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingEvent
import com.rajasudhan.taskmind.MainActivity
import com.rajasudhan.taskmind.R
import com.rajasudhan.taskmind.data.local.TaskMindDao
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Fires a reminder notification when the user enters a note's saved location. */
@AndroidEntryPoint
class GeofenceBroadcastReceiver : BroadcastReceiver() {

    @Inject lateinit var dao: TaskMindDao

    override fun onReceive(context: Context, intent: Intent) {
        val event = GeofencingEvent.fromIntent(intent) ?: return
        if (event.hasError() || event.geofenceTransition != Geofence.GEOFENCE_TRANSITION_ENTER) return
        val ids = event.triggeringGeofences?.mapNotNull { it.requestId.toIntOrNull() } ?: return
        if (ids.isEmpty()) return

        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                notifyEntered(context, ids)
            } finally {
                pending.finish()
            }
        }
    }

    /**
     * Posts an arrival notification for each entered geofence's note. Extracted from [onReceive] so
     * it's unit-testable without the Play-services GeofencingEvent / goAsync plumbing. Completed
     * notes are skipped — arriving somewhere shouldn't resurface a task already finished.
     */
    internal suspend fun notifyEntered(context: Context, ids: List<Int>) {
        for (id in ids) {
            val note = dao.getNoteByIdNow(id) ?: continue
            if (note.completed) continue
            notify(context, id, note.locationLabel ?: "a saved place", note.title)
        }
    }

    /**
     * Arrival notification on the HIGH-importance reminders channel (a place alert that arrives
     * silently defeats its purpose), tap deep-linking to the note, with a Done action mirroring the
     * fired-reminder notification.
     */
    private fun notify(context: Context, id: Int, place: String, title: String) {
        val openIntent = Intent(context, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            putExtra(MainActivity.EXTRA_OPEN_NOTE_ID, id)
        }
        val tapIntent = PendingIntent.getActivity(
            context, ReminderActionReceiver.OPEN_RC_BASE + id, openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val doneIntent = Intent(context, ReminderActionReceiver::class.java).apply {
            action = ReminderActionReceiver.ACTION_DONE
            putExtra(ReminderActionReceiver.EXTRA_NOTE_ID, id)
            putExtra(ReminderActionReceiver.EXTRA_TITLE, title)
        }
        val notification = NotificationCompat.Builder(context, TaskMindForegroundService.REMINDER_CHANNEL_ID)
            .setContentTitle("You're near $place")
            .setContentText(title)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(tapIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .addAction(
                android.R.drawable.checkbox_on_background, "Done",
                PendingIntent.getBroadcast(
                    context, ReminderActionReceiver.DONE_RC_BASE + id, doneIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
            )
            .build()
        (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
            .notify(NOTIFICATION_ID_BASE + id, notification)
    }

    companion object {
        // Arrival notifications: stable id per note, clear of fired reminders (200_000 + id).
        const val NOTIFICATION_ID_BASE = 100_000
    }
}
