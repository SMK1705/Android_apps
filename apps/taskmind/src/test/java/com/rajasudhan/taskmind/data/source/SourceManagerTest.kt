package com.rajasudhan.taskmind.data.source

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * The per-source "enabled at" stamp (#243): turning a capture source ON records the moment so the scanner
 * captures forward-only instead of backfilling history. One sequential test to avoid the DataStore
 * singleton leaking state between methods.
 */
@RunWith(RobolectricTestRunner::class)
class SourceManagerTest {

    @Test
    fun enablingASource_stampsEnabledAt_forwardOnly() = runTest {
        val sm = SourceManager(ApplicationProvider.getApplicationContext<Context>())

        // Never enabled -> no stamp.
        assertEquals(0L, sm.smsEnabledAt.first())

        // OFF -> ON stamps the turn-on moment.
        sm.setSourceEnabled(SourceManager.KEY_SMS_ENABLED, true)
        val stamp = sm.smsEnabledAt.first()
        assertTrue("enabling should stamp a non-zero time", stamp > 0L)

        // Re-saving an already-ON toggle must NOT move the stamp (else every settings write resets it).
        sm.setSourceEnabled(SourceManager.KEY_SMS_ENABLED, true)
        assertEquals("re-enable must not re-stamp", stamp, sm.smsEnabledAt.first())

        // Disabling leaves the stamp untouched (we only ever move it forward, on a fresh enable).
        sm.setSourceEnabled(SourceManager.KEY_SMS_ENABLED, false)
        assertEquals("disable must not clear the stamp", stamp, sm.smsEnabledAt.first())

        // A fresh OFF -> ON re-stamps to now (>= the old mark).
        sm.setSourceEnabled(SourceManager.KEY_SMS_ENABLED, true)
        assertTrue("re-enable after disable re-stamps", sm.smsEnabledAt.first() >= stamp)

        // The stamp is per-source: enabling SMS never touched images' stamp.
        assertEquals(0L, sm.imagesEnabledAt.first())
    }
}
