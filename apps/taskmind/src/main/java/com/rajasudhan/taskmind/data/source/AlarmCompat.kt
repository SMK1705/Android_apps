package com.rajasudhan.taskmind.data.source

import android.app.AlarmManager
import android.os.Build

/**
 * The exact-alarm scheduling permission only exists from Android 12 (API 31,
 * [AlarmManager.canScheduleExactAlarms]). On older releases exact alarms are always permitted, so
 * treat that as "can schedule". Centralising the version guard here keeps every call site clean and
 * lint-clean below `minSdk 31`.
 */
fun AlarmManager.canScheduleExactAlarmsCompat(): Boolean =
    Build.VERSION.SDK_INT < Build.VERSION_CODES.S || canScheduleExactAlarms()
