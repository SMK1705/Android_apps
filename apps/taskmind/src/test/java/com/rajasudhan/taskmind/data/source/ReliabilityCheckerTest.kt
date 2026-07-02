package com.rajasudhan.taskmind.data.source

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.PowerManager
import android.provider.Settings
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowAlarmManager

/** The Reliability Doctor's diagnostics, driven through Robolectric shadows of the system services. */
@RunWith(RobolectricTestRunner::class)
class ReliabilityCheckerTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val checker = ReliabilityChecker(context)

    private fun byId() = checker.run().associateBy { it.id }

    private fun enableListener(enabled: Boolean) {
        Settings.Secure.putString(
            context.contentResolver, "enabled_notification_listeners",
            if (enabled) "${context.packageName}/.data.source.TaskMindNotificationListener" else ""
        )
    }

    private fun setNotifications(on: Boolean) {
        shadowOf(context.getSystemService(NotificationManager::class.java)).setNotificationsEnabled(on)
    }

    private fun setReminderChannelImportance(importance: Int) {
        context.getSystemService(NotificationManager::class.java)
            .createNotificationChannel(NotificationChannel(TaskMindForegroundService.REMINDER_CHANNEL_ID, "Reminders", importance))
    }

    private fun setBatteryExempt(exempt: Boolean) {
        shadowOf(context.getSystemService(PowerManager::class.java))
            .setIgnoringBatteryOptimizations(context.packageName, exempt)
    }

    @Test
    fun everythingHealthy_reportsAllOkExceptTheDormantWatcher() {
        enableListener(true)
        setNotifications(true)
        setReminderChannelImportance(NotificationManager.IMPORTANCE_HIGH)
        ShadowAlarmManager.setCanScheduleExactAlarms(true)
        setBatteryExempt(true)

        val checks = byId()
        assertEquals(HealthStatus.OK, checks["listener"]!!.status)
        assertEquals(HealthStatus.OK, checks["notifications"]!!.status)
        assertEquals(HealthStatus.OK, checks["reminder_channel"]!!.status)
        assertEquals(HealthStatus.OK, checks["exact_alarms"]!!.status)
        assertEquals(HealthStatus.OK, checks["battery"]!!.status)
        // The foreground service isn't running under test, so the watcher check warns.
        assertEquals(HealthStatus.WARN, checks["service"]!!.status)
    }

    @Test
    fun everythingBroken_reportsFailsAndWarns_withFixIntents() {
        enableListener(false)
        setNotifications(false)
        setReminderChannelImportance(NotificationManager.IMPORTANCE_LOW)
        ShadowAlarmManager.setCanScheduleExactAlarms(false)
        setBatteryExempt(false)

        val checks = byId()
        assertEquals(HealthStatus.FAIL, checks["listener"]!!.status)
        assertEquals(HealthStatus.FAIL, checks["notifications"]!!.status)
        assertEquals(HealthStatus.WARN, checks["reminder_channel"]!!.status)
        assertEquals(HealthStatus.WARN, checks["exact_alarms"]!!.status)
        assertEquals(HealthStatus.WARN, checks["battery"]!!.status)

        // Every non-OK check offers a way to fix it.
        checker.run().filter { it.status != HealthStatus.OK && it.id != "service" }
            .forEach { assertTrue("${it.id} should have a fix", it.fix != null && it.fixLabel != null) }
    }

    @Test
    fun freshInstall_withNoReminderChannelYet_isNotFlaggedAsQuiet() {
        // Channel not created (service never ran) — treated as fine; it's made HIGH on first use.
        enableListener(true)
        setNotifications(true)
        ShadowAlarmManager.setCanScheduleExactAlarms(true)
        setBatteryExempt(true)

        assertEquals(HealthStatus.OK, byId()["reminder_channel"]!!.status)
    }

    @Test
    fun results_areSortedWorstFirst() {
        enableListener(false) // one FAIL
        setNotifications(true)
        setReminderChannelImportance(NotificationManager.IMPORTANCE_HIGH)
        ShadowAlarmManager.setCanScheduleExactAlarms(true)
        setBatteryExempt(true)

        val statuses = checker.run().map { it.status }
        // Non-increasing severity: FAIL(2) ≥ WARN(1) ≥ OK(0) as we go down the list.
        assertTrue(statuses.zipWithNext().all { (a, b) -> a.ordinal >= b.ordinal })
        assertEquals(HealthStatus.FAIL, statuses.first())
    }
}
