package com.rajasudhan.taskmind.data.source

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Tests the processed-notification-key ledger (issue #170) that the reconnect/boot catch-up sweep
 * uses to avoid re-running the LLM on a notification the live listener already handled.
 */
@RunWith(RobolectricTestRunner::class)
class NotificationLedgerTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Before
    fun clearLedger() = runTest {
        // DataStore is a process singleton; start each test clean.
        context.dataStore.edit { it.remove(SourceManager.KEY_PROCESSED_NOTIFICATION_KEYS) }
    }

    @Test
    fun recordsAndReadsBackKeys() = runTest {
        val sm = SourceManager(context)
        sm.addProcessedNotificationKeys(listOf("0|com.whatsapp|1|null", "0|org.telegram|2|null"))

        val kept = sm.processedNotificationKeys.first()
        assertTrue(kept.contains("0|com.whatsapp|1|null"))
        assertTrue(kept.contains("0|org.telegram|2|null"))
    }

    @Test
    fun emptyBatch_isANoOp() = runTest {
        val sm = SourceManager(context)
        sm.addProcessedNotificationKeys(emptyList())
        assertTrue(sm.processedNotificationKeys.first().isEmpty())
    }

    @Test
    fun cap_boundsGrowth() = runTest {
        val sm = SourceManager(context)
        val keys = (1..(SourceManager.MAX_PROCESSED_NOTIFICATION_KEYS + 50)).map { "key-$it" }
        sm.addProcessedNotificationKeys(keys)

        val kept = sm.processedNotificationKeys.first()
        assertEquals(SourceManager.MAX_PROCESSED_NOTIFICATION_KEYS, kept.size)
    }

    @Test
    fun addingKeysAccumulates_acrossCalls() = runTest {
        val sm = SourceManager(context)
        sm.addProcessedNotificationKeys(listOf("a"))
        sm.addProcessedNotificationKeys(listOf("b"))

        val kept = sm.processedNotificationKeys.first()
        assertTrue(kept.containsAll(listOf("a", "b")))
        assertFalse(kept.contains("c"))
    }

    // ---- catch-up sweep age gate ----

    @Test
    fun sweepWindow_recoversRecent_butNotStaleShadeItems() {
        val now = 1_700_000_000_000L
        val hour = 60 * 60 * 1000L
        // A recent notification (a battery-kill/reboot gap) is recovered.
        assertTrue(TaskMindNotificationListener.isWithinSweepWindow(now - 1, now))
        assertTrue(TaskMindNotificationListener.isWithinSweepWindow(now - 5 * hour, now))
        // A days-old message still sitting in the shade is NOT mined into a fresh, mis-dated task.
        assertFalse(TaskMindNotificationListener.isWithinSweepWindow(now - 7 * hour, now))
        assertFalse(TaskMindNotificationListener.isWithinSweepWindow(now - 48 * hour, now))
    }
}
