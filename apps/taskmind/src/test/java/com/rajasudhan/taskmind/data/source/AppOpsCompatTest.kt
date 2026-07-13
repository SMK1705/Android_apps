package com.rajasudhan.taskmind.data.source

import android.app.AppOpsManager
import android.content.Context
import android.os.Process
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * [checkOpNoThrowCompat] backs the "Usage access" permission row. [AppOpsManager.unsafeCheckOpNoThrow]
 * only exists from API 29; below it the deprecated [AppOpsManager.checkOpNoThrow] must be used —
 * calling the newer method on API 26–28 would `NoSuchMethodError`. Each case sets a mode via the
 * shadow and asserts the helper reflects it, running the SAME code at API 26/28 (fallback branch)
 * and 29/34 (unsafe branch).
 */
@RunWith(RobolectricTestRunner::class)
class AppOpsCompatTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val appOps get() = context.getSystemService(AppOpsManager::class.java)!!
    private val op = AppOpsManager.OPSTR_GET_USAGE_STATS
    private val uid get() = Process.myUid()
    private val pkg get() = context.packageName

    private fun assertReflectsMode(mode: Int) {
        shadowOf(appOps).setMode(op, uid, pkg, mode)
        assertEquals(mode, appOps.checkOpNoThrowCompat(op, uid, pkg))
    }

    @Test
    @Config(sdk = [26])
    fun api26_fallback_reflectsAllowed() = assertReflectsMode(AppOpsManager.MODE_ALLOWED)

    @Test
    @Config(sdk = [28])
    fun api28_fallback_reflectsIgnored() = assertReflectsMode(AppOpsManager.MODE_IGNORED)

    @Test
    @Config(sdk = [29])
    fun api29_unsafeBranch_reflectsAllowed() = assertReflectsMode(AppOpsManager.MODE_ALLOWED)

    @Test
    @Config(sdk = [34])
    fun api34_unsafeBranch_reflectsErrored() = assertReflectsMode(AppOpsManager.MODE_ERRORED)
}
