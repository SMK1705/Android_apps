package com.rajasudhan.taskmind

import android.app.Application
import android.content.Context
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.rajasudhan.taskmind.data.source.DailyBriefScheduler
import com.rajasudhan.taskmind.data.source.DataCollectionWorker
import com.rajasudhan.taskmind.data.source.SettingsManager
import com.rajasudhan.taskmind.data.source.TaskMindForegroundService
import dagger.hilt.android.HiltAndroidApp
import java.util.concurrent.TimeUnit
import javax.inject.Inject

@HiltAndroidApp
class TaskMindApp : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    @Inject
    lateinit var settingsManager: SettingsManager

    @Inject
    lateinit var dailyBriefScheduler: DailyBriefScheduler

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()
        // Create the notification channel eagerly so a reminder alarm that fires after a reboot (before
        // the foreground service has ever run this process) still has a channel to post to.
        TaskMindForegroundService.ensureNotificationChannel(this)
        // Keep the existing schedule on launch; only an explicit settings change replaces it.
        scheduleScan(this, settingsManager.scanFrequencyMinutes.toLong(), replace = false)
        // Re-arm the daily brief from the saved preference (the WorkManager job is cleared on reinstall
        // and its exact firing drifts across reboots; enqueueUniquePeriodicWork with UPDATE is idempotent).
        dailyBriefScheduler.reschedule(
            settingsManager.dailyBriefEnabled, settingsManager.dailyBriefHour, settingsManager.dailyBriefMinute
        )
    }

    companion object {
        const val PERIODIC_WORK_NAME = "taskmind_periodic_scan"

        /**
         * (Re)schedules the battery-friendly periodic scan of recent data. Pass [replace] = true to
         * apply a changed interval immediately (e.g. from Settings); false keeps any existing schedule.
         */
        fun scheduleScan(context: Context, minutes: Long, replace: Boolean) {
            val request = PeriodicWorkRequestBuilder<DataCollectionWorker>(minutes, TimeUnit.MINUTES)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiresBatteryNotLow(true)
                        .build()
                )
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                PERIODIC_WORK_NAME,
                if (replace) ExistingPeriodicWorkPolicy.UPDATE else ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }
    }
}
