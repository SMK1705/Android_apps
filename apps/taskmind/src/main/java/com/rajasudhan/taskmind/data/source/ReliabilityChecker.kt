package com.rajasudhan.taskmind.data.source

import android.app.AlarmManager
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** How healthy a single reliability check is. */
enum class HealthStatus { OK, WARN, FAIL }

/**
 * One diagnosable aspect of whether TaskMind can actually deliver — with a status and, when it's not
 * OK, a deep link into the exact system screen that fixes it. [fix] is a bare Intent (data, no
 * Android side effects) that the UI launches.
 */
data class HealthCheck(
    val id: String,
    val title: String,
    val status: HealthStatus,
    val detail: String,
    val fixLabel: String? = null,
    val fix: Intent? = null,
)

/**
 * The Reliability Doctor's diagnostics. A privacy-first, always-on app lives or dies by its
 * background access — a revoked notification listener, an OEM battery-killer, a downgraded channel,
 * or missing exact-alarm permission all silently break the core "catches them before you forget"
 * promise. This reads the current system state (no mutations) and reports what's wrong and where to
 * fix it. Kept out of the ViewModel so the branching logic is unit-testable under Robolectric.
 */
@Singleton
class ReliabilityChecker @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    /** Runs every check against the live system state, worst-status-first for the UI. */
    fun run(): List<HealthCheck> = listOf(
        notificationAccess(),
        notificationsEnabled(),
        reminderChannel(),
        exactAlarms(),
        batteryOptimization(),
        backgroundWatcher(),
    ).sortedBy { it.status.ordinal * -1 } // FAIL(2) and WARN(1) float above OK(0)

    private val pkg get() = context.packageName

    /** The notification listener is the lifeblood — without it, every app's notifications go unseen. */
    private fun notificationAccess(): HealthCheck {
        val enabled = NotificationManagerCompat.getEnabledListenerPackages(context).contains(pkg)
        return HealthCheck(
            id = "listener",
            title = "Notification access",
            status = if (enabled) HealthStatus.OK else HealthStatus.FAIL,
            detail = if (enabled) "TaskMind can read your notifications to catch tasks."
            else "Off — TaskMind can't see notifications, so most sources are dark.",
            fixLabel = if (enabled) null else "Grant access",
            fix = if (enabled) null else Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS),
        )
    }

    /** If notifications are blocked at the app level, no reminder or review can ever reach you. */
    private fun notificationsEnabled(): HealthCheck {
        val on = NotificationManagerCompat.from(context).areNotificationsEnabled()
        return HealthCheck(
            id = "notifications",
            title = "Notifications allowed",
            status = if (on) HealthStatus.OK else HealthStatus.FAIL,
            detail = if (on) "Reminders and reviews can reach you." else "Blocked — nothing can notify you.",
            fixLabel = if (on) null else "Allow",
            fix = if (on) null else Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, pkg),
        )
    }

    /** The user (or an OEM) can silence the Reminders channel; then alarms arrive without a sound. */
    private fun reminderChannel(): HealthCheck {
        val manager = context.getSystemService(NotificationManager::class.java)
        val channel = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager?.getNotificationChannel(TaskMindForegroundService.REMINDER_CHANNEL_ID)
        } else null
        // Not created yet (fresh install, service hasn't run) → nothing wrong; it's made HIGH on first use.
        val importance = channel?.importance ?: NotificationManager.IMPORTANCE_HIGH
        val loud = importance >= NotificationManager.IMPORTANCE_DEFAULT
        return HealthCheck(
            id = "reminder_channel",
            title = "Reminders make a sound",
            status = if (loud) HealthStatus.OK else HealthStatus.WARN,
            detail = if (loud) "The Reminders channel is set to alert." else "The Reminders channel was quieted — reminders won't heads-up.",
            fixLabel = if (loud) null else "Adjust",
            fix = if (loud) null else Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, pkg)
                .putExtra(Settings.EXTRA_CHANNEL_ID, TaskMindForegroundService.REMINDER_CHANNEL_ID),
        )
    }

    /** Without exact-alarm permission, reminders fire late (or batched) instead of on the minute. */
    private fun exactAlarms(): HealthCheck {
        val am = context.getSystemService(AlarmManager::class.java)
        val ok = am?.canScheduleExactAlarmsCompat() ?: false
        return HealthCheck(
            id = "exact_alarms",
            title = "Exact alarms",
            status = if (ok) HealthStatus.OK else HealthStatus.WARN,
            detail = if (ok) "Reminders fire at the exact minute." else "Off — reminders may fire late or be batched.",
            fixLabel = if (ok) null else "Allow",
            fix = if (ok) null else Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:$pkg")),
        )
    }

    /**
     * The big one on Indian-market devices (Xiaomi, OnePlus, vivo, Samsung): aggressive battery
     * optimization freezes the background watcher and drops alarms. Exempting the app is the single
     * biggest reliability win. Opens the battery-optimization list (Play-policy-safe) rather than the
     * direct-grant prompt.
     */
    private fun batteryOptimization(): HealthCheck {
        val pm = context.getSystemService(PowerManager::class.java)
        val exempt = pm?.isIgnoringBatteryOptimizations(pkg) ?: false
        return HealthCheck(
            id = "battery",
            title = "Battery optimization",
            status = if (exempt) HealthStatus.OK else HealthStatus.WARN,
            detail = if (exempt) "TaskMind is exempt — the watcher keeps running."
            else "Optimized — the OS may freeze TaskMind and drop reminders. Set it to “Unrestricted”.",
            fixLabel = if (exempt) null else "Fix",
            fix = if (exempt) null else Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS),
        )
    }

    /** Is the always-on foreground watcher actually alive right now? */
    private fun backgroundWatcher(): HealthCheck {
        val running = TaskMindForegroundService.isRunning
        return HealthCheck(
            id = "service",
            title = "Background watcher",
            status = if (running) HealthStatus.OK else HealthStatus.WARN,
            detail = if (running) "Running — watching your sources in real time."
            else "Not running right now — reopening TaskMind restarts it.",
        )
    }
}
