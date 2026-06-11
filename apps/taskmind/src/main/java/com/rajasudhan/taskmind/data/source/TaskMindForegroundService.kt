package com.rajasudhan.taskmind.data.source

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
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

    private val serviceScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var smsObserverStarted = false

    companion object {
        const val NOTIFICATION_CHANNEL_ID = "taskmind_service_channel"
        const val NOTIFICATION_ID = 1
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        observeSmsSource()
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
