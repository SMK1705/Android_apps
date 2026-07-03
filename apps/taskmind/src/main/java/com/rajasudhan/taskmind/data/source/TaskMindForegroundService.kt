package com.rajasudhan.taskmind.data.source

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.database.ContentObserver
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.MediaStore
import androidx.core.app.NotificationCompat
import com.rajasudhan.taskmind.MainActivity
import com.rajasudhan.taskmind.R
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class TaskMindForegroundService : Service() {

    @Inject
    lateinit var sourceManager: SourceManager

    @Inject
    lateinit var smsObserver: SmsObserver

    @Inject
    lateinit var scanner: RecentDataScanner

    private val serviceScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var smsObserverStarted = false

    // Live MediaStore watchers: new recordings transcribe / new screenshots OCR instantly, instead
    // of waiting for the 30-min periodic scan. Each scan skips already-processed ids.
    private val audioObserver = mediaObserver { serviceScope.launch { scanner.scanAudioRecent() } }
    private val imageObserver = mediaObserver { serviceScope.launch { scanner.scanImagesRecent() } }
    private var audioWatching = false
    private var imageWatching = false

    private fun mediaObserver(onChange: () -> Unit) =
        object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean, uri: Uri?) { onChange() }
        }

    companion object {
        const val NOTIFICATION_CHANNEL_ID = "taskmind_service_channel"
        const val REMINDER_CHANNEL_ID = "taskmind_reminders"
        const val DAILY_BRIEF_CHANNEL_ID = "taskmind_daily_brief"
        const val WEEKLY_WINS_CHANNEL_ID = "taskmind_weekly_wins"
        const val WAITING_CONFIRM_CHANNEL_ID = "taskmind_waiting_confirm"
        const val NOTIFICATION_ID = 1

        /**
         * True while the live-watcher service is up. Read by the Reliability Doctor to tell the user
         * whether the background watcher is actually running. `@Volatile` because it's flipped on the
         * main thread (onCreate/onDestroy) and read from a ViewModel coroutine.
         */
        @Volatile
        var isRunning: Boolean = false
            private set

        /**
         * Idempotently creates the app's notification channels. Also called from
         * [com.rajasudhan.taskmind.TaskMindApp] on startup, so an alarm that fires after a reboot —
         * before the user reopens the app, so this service has never run — still has a channel to
         * post to. Without it, Android O+ silently drops a notification posted to a non-existent
         * channel.
         *
         * Reminders get their own HIGH-importance channel: a channel's importance is fixed at first
         * creation and overrides the notification builder's priority, so posting reminders to the
         * service's LOW channel (as the app once did) made every alarm and geofence alert arrive
         * silently — no sound, no heads-up.
         */
        fun ensureNotificationChannel(context: Context) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val manager = context.getSystemService(NotificationManager::class.java) ?: return
                manager.createNotificationChannel(
                    NotificationChannel(
                        NOTIFICATION_CHANNEL_ID,
                        "Background service",
                        NotificationManager.IMPORTANCE_LOW
                    )
                )
                manager.createNotificationChannel(
                    NotificationChannel(
                        REMINDER_CHANNEL_ID,
                        "Reminders",
                        NotificationManager.IMPORTANCE_HIGH
                    )
                )
                manager.createNotificationChannel(
                    NotificationChannel(
                        DAILY_BRIEF_CHANNEL_ID,
                        "Daily brief",
                        NotificationManager.IMPORTANCE_DEFAULT
                    )
                )
                manager.createNotificationChannel(
                    NotificationChannel(
                        WEEKLY_WINS_CHANNEL_ID,
                        "Weekly wins",
                        NotificationManager.IMPORTANCE_DEFAULT
                    )
                )
                // Quiet on purpose (IMPORTANCE_LOW: no sound, no heads-up): the "did they deliver?"
                // check rides in right after the counterparty's own message, so a second buzz would
                // just be noise. It waits calmly in the shade until the user gets to it.
                manager.createNotificationChannel(
                    NotificationChannel(
                        WAITING_CONFIRM_CHANNEL_ID,
                        "Waiting-on check-ins",
                        NotificationManager.IMPORTANCE_LOW
                    )
                )
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        isRunning = true
        ensureNotificationChannel(this)
        observeSmsSource()
        observeMediaSource(
            enabledFlow = { sourceManager.isAudioEnabled },
            uri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            observer = audioObserver,
            isWatching = { audioWatching },
            setWatching = { audioWatching = it }
        )
        observeMediaSource(
            enabledFlow = { sourceManager.isImagesEnabled },
            uri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            observer = imageObserver,
            isWatching = { imageWatching },
            setWatching = { imageWatching = it }
        )
    }

    /** Registers/unregisters [observer] for [uri] live, following the source toggle. */
    private fun observeMediaSource(
        enabledFlow: () -> kotlinx.coroutines.flow.Flow<Boolean>,
        uri: Uri,
        observer: ContentObserver,
        isWatching: () -> Boolean,
        setWatching: (Boolean) -> Unit
    ) {
        serviceScope.launch {
            enabledFlow().collectLatest { enabled ->
                if (enabled && !isWatching()) {
                    contentResolver.registerContentObserver(uri, true, observer)
                    setWatching(true)
                } else if (!enabled && isWatching()) {
                    contentResolver.unregisterContentObserver(observer)
                    setWatching(false)
                }
            }
        }
    }

    /** Reacts to the SMS toggle live, so enabling/disabling it takes effect immediately. */
    private fun observeSmsSource() {
        serviceScope.launch {
            sourceManager.isSmsEnabled.collectLatest { enabled ->
                if (enabled && !smsObserverStarted) {
                    smsObserver.start()
                    smsObserverStarted = true
                } else if (!enabled && smsObserverStarted) {
                    smsObserver.stop()
                    smsObserverStarted = false
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, notificationIntent, PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle("TaskMind is active")
            .setContentText("Monitoring enabled data sources")
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(pendingIntent)
            .build()

        startForeground(NOTIFICATION_ID, notification)
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
        if (smsObserverStarted) smsObserver.stop()
        if (audioWatching) contentResolver.unregisterContentObserver(audioObserver)
        if (imageWatching) contentResolver.unregisterContentObserver(imageObserver)
        serviceScope.cancel()
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }
}
