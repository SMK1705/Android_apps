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
        const val NOTIFICATION_ID = 1
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
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
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(pendingIntent)
            .build()

        startForeground(NOTIFICATION_ID, notification)
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        if (smsObserverStarted) smsObserver.stop()
        if (audioWatching) contentResolver.unregisterContentObserver(audioObserver)
        if (imageWatching) contentResolver.unregisterContentObserver(imageObserver)
        serviceScope.cancel()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                "TaskMind Service Channel",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(serviceChannel)
        }
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }
}
