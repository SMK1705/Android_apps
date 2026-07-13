package com.rajasudhan.taskmind.data.source

import android.app.AppOpsManager
import android.os.Build

/**
 * [AppOpsManager.unsafeCheckOpNoThrow] is API 29+. Below it the identically-behaving (now deprecated)
 * [AppOpsManager.checkOpNoThrow] is the equivalent. Centralising the version guard here keeps the
 * call site clean and lint-clean below `minSdk 29` — calling `unsafeCheckOpNoThrow` on API 26–28
 * would `NoSuchMethodError`.
 */
fun AppOpsManager.checkOpNoThrowCompat(op: String, uid: Int, packageName: String): Int =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        unsafeCheckOpNoThrow(op, uid, packageName)
    } else {
        @Suppress("DEPRECATION")
        checkOpNoThrow(op, uid, packageName)
    }
