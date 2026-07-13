package com.rajasudhan.taskmind.data.source

import android.app.AlarmManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowAlarmManager

/**
 * [canScheduleExactAlarmsCompat] is the version guard behind every exact-alarm decision (5 call
 * sites). The exact-alarm permission only exists from API 31 ([AlarmManager.canScheduleExactAlarms]);
 * below it, exact alarms are always allowed, so the helper must return `true` regardless of what the
 * platform would report. These run the SAME code at several API levels, exercising both the pre-31
 * fallback branch (which would `NoSuchMethodError` if the guard were wrong) and the >=31 delegation.
 */
@RunWith(RobolectricTestRunner::class)
class AlarmCompatTest {

    private val am: AlarmManager
        get() = ApplicationProvider.getApplicationContext<Context>()
            .getSystemService(Context.ALARM_SERVICE) as AlarmManager

    @Test
    @Config(sdk = [26])
    fun api26_alwaysAllowed_evenWhenPlatformWouldDeny() {
        ShadowAlarmManager.setCanScheduleExactAlarms(false) // ignored below API 31
        assertTrue("pre-31 must treat exact alarms as always allowed", am.canScheduleExactAlarmsCompat())
    }

    @Test
    @Config(sdk = [30])
    fun api30_stillAlwaysAllowed() {
        ShadowAlarmManager.setCanScheduleExactAlarms(false)
        assertTrue(am.canScheduleExactAlarmsCompat())
    }

    @Test
    @Config(sdk = [31])
    fun api31_delegates_denied() {
        ShadowAlarmManager.setCanScheduleExactAlarms(false)
        assertFalse("from API 31 it must reflect the platform grant", am.canScheduleExactAlarmsCompat())
    }

    @Test
    @Config(sdk = [31])
    fun api31_delegates_granted() {
        ShadowAlarmManager.setCanScheduleExactAlarms(true)
        assertTrue(am.canScheduleExactAlarmsCompat())
    }

    @Test
    @Config(sdk = [34])
    fun api34_delegates_denied() {
        ShadowAlarmManager.setCanScheduleExactAlarms(false)
        assertFalse(am.canScheduleExactAlarmsCompat())
    }
}
