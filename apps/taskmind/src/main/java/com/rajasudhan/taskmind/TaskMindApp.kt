package com.rajasudhan.taskmind

import android.app.Application
import android.content.Context
import androidx.appfunctions.service.AppFunctionConfiguration
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.rajasudhan.taskmind.data.source.AutoSnapshotScheduler
import com.rajasudhan.taskmind.data.source.DailyBriefScheduler
import com.rajasudhan.taskmind.data.source.DataCollectionWorker
import com.rajasudhan.taskmind.data.source.RecurrenceDetectorScheduler
import com.rajasudhan.taskmind.data.source.SettingsManager
import com.rajasudhan.taskmind.data.source.TaskMindForegroundService
import com.rajasudhan.taskmind.data.source.WeeklyWinsScheduler
import com.rajasudhan.taskmind.data.source.appfunctions.AgentFunctions
import com.rajasudhan.taskmind.data.source.wear.WearSyncScheduler
import com.rajasudhan.taskmind.ui.capture.CaptureShortcuts
import dagger.hilt.android.HiltAndroidApp
import java.util.concurrent.TimeUnit
import javax.inject.Inject

@HiltAndroidApp
class TaskMindApp : Application(), Configuration.Provider, AppFunctionConfiguration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    // The Hilt-provided AppFunctions binding (#209). Handed to the framework below so the system agent
    // (Gemini) can invoke createTask / getItemsDueToday / snoozeItem against the real, injected data layer.
    @Inject
    lateinit var agentFunctions: AgentFunctions

    @Inject
    lateinit var settingsManager: SettingsManager

    @Inject
    lateinit var dailyBriefScheduler: DailyBriefScheduler

    @Inject
    lateinit var weeklyWinsScheduler: WeeklyWinsScheduler

    @Inject
    lateinit var autoSnapshotScheduler: AutoSnapshotScheduler

    @Inject
    lateinit var recurrenceDetectorScheduler: RecurrenceDetectorScheduler

    @Inject
    lateinit var wearSyncScheduler: WearSyncScheduler

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    // Lets the AppFunctions framework instantiate the Hilt-managed [AgentFunctions] (which has injected
    // dependencies) rather than needing a no-arg constructor (#209).
    override val appFunctionConfiguration: AppFunctionConfiguration
        get() = AppFunctionConfiguration.Builder()
            .addEnclosingClassFactory(AgentFunctions::class.java) { agentFunctions }
            .build()

    override fun onCreate() {
        super.onCreate()
        // Create the notification channel eagerly so a reminder alarm that fires after a reboot (before
        // the foreground service has ever run this process) still has a channel to post to.
        TaskMindForegroundService.ensureNotificationChannel(this)
        // Publish the launcher long-press capture shortcuts (Type / Speak / Inbox) so they exist from
        // first launch; SuggestionNotifier re-stamps the Inbox count as the pending set changes.
        CaptureShortcuts.refresh(this, 0)
        // Keep the existing schedule on launch; only an explicit settings change replaces it.
        scheduleScan(this, settingsManager.scanFrequencyMinutes.toLong(), replace = false)
        // Re-arm the brief/recap from the saved preference, KEEPing any already-scheduled job (replace =
        // false) so a run WorkManager has deferred under Doze past its target time isn't destroyed. This
        // still arms them after a reinstall (no existing job to keep) and preserves them across reboots;
        // an explicit settings change re-enqueues fresh (replace defaults true) to apply a new time.
        dailyBriefScheduler.reschedule(
            settingsManager.dailyBriefEnabled, settingsManager.dailyBriefHour, settingsManager.dailyBriefMinute,
            replace = false
        )
        weeklyWinsScheduler.reschedule(settingsManager.weeklyWinsEnabled, settingsManager.weeklyWinsHour, replace = false)
        // Always-on data safety net: a daily plain-JSON snapshot of notes to app-private storage, so a
        // Keystore reset that renders the encrypted DB unopenable can never mean a total loss (#161).
        autoSnapshotScheduler.schedule()
        // Mine captured history a few times a week for a habit that keeps repeating, and offer to make it
        // recurring (#124). KEEPs any existing schedule so a deferred run isn't reset on launch.
        recurrenceDetectorScheduler.schedule()
        // Keep the paired watch's next-due tile fresh (#216). KEEPs any existing schedule on launch.
        wearSyncScheduler.schedule()
        // …and re-publish the moment the due set changes (#245), so the tile isn't stale for up to 30 min
        // after an approve/complete/snooze/reschedule/delete.
        wearSyncScheduler.observeDueChanges()
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
