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
     * it's unit-testable without the Play-services GeofencingEvent / goAsync plumbing.
     */
    internal suspend fun notifyEntered(context: Context, ids: List<Int>) {
        for (id in ids) {
            val note = dao.getNoteByIdNow(id) ?: continue
            notify(context, id, note.locationLabel ?: "a saved place", note.title)
        }
    }

    private fun notify(context: Context, id: Int, place: String, title: String) {
        val tapIntent = PendingIntent.getActivity(
            context, 0, Intent(context, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(context, TaskMindForegroundService.NOTIFICATION_CHANNEL_ID)
            .setContentTitle("You're near $place")
            .setContentText(title)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(tapIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
        (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
            .notify(100_000 + id, notification)
    }
}
