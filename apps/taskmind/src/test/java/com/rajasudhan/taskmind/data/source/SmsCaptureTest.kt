package com.rajasudhan.taskmind.data.source

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.provider.Telephony
import androidx.datastore.preferences.core.edit
import androidx.test.core.app.ApplicationProvider
import com.rajasudhan.taskmind.data.source.understanding.UnderstandingPipeline
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner

/**
 * Regression tests for the SMS capture path (issue #167). The headline bug: a single null-BODY row
 * (MMS stub / WAP-push) used to throw an NPE that aborted the whole scan loop and, because the
 * watermark still advanced, permanently dropped every older SMS in the same window.
 */
@RunWith(RobolectricTestRunner::class)
class SmsCaptureTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val pipeline = mockk<UnderstandingPipeline>(relaxed = true)

    @Before
    fun setUp() {
        Robolectric.setupContentProvider(FakeSmsProvider::class.java, "sms")
        FakeSmsProvider.rows = emptyList()
    }

    /** Each element is a row: [_id: Long, address: String?, body: String?]. */
    private fun rows(vararg r: Array<Any?>) { FakeSmsProvider.rows = r.toList() }

    /** A SourceManager mock with only the SMS source enabled and a given processed-id set. */
    private fun sourceManager(processed: Set<String> = emptySet()) = mockk<SourceManager>(relaxed = true).also { sm ->
        every { sm.isSmsEnabled } returns flowOf(true)
        every { sm.isCallLogEnabled } returns flowOf(false)
        every { sm.isEmailEnabled } returns flowOf(false)
        every { sm.isAppUsageEnabled } returns flowOf(false)
        every { sm.isAudioEnabled } returns flowOf(false)
        every { sm.isImagesEnabled } returns flowOf(false)
        every { sm.processedSmsIds } returns flowOf(processed)
    }

    private fun scanner(sm: SourceManager) = RecentDataScanner(
        context, sm,
        settingsManager = mockk(relaxed = true),
        pipeline = pipeline,
        gmailAuth = mockk(relaxed = true),
        gmailCollector = mockk(relaxed = true),
        appUsageCollector = mockk(relaxed = true),
        voskTranscriber = mockk(relaxed = true),
        ocrEngine = mockk(relaxed = true),
        personContextNotifier = mockk(relaxed = true),
    )

    // ---------------- RecentDataScanner.scanSms ----------------

    @Test
    fun scanSms_nullBodyRow_doesNotAbortTheBatch_olderMessagesStillCaptured() = runTest {
        // Window (DATE DESC): text A (id 100), a null-BODY row (id 101), text B (id 102). The null row
        // sits BETWEEN the two texts, so if it aborted the loop the older text would be lost.
        rows(
            arrayOf<Any?>(100L, "+15550001", "Pay rent Friday"),
            arrayOf<Any?>(101L, "+15550002", null),
            arrayOf<Any?>(102L, "+15550003", "Dentist Mon 3pm"),
        )
        val sm = sourceManager()

        scanner(sm).scanSince(0L)

        // Both real texts captured; the null row neither aborts the batch nor reaches the pipeline.
        coVerify(exactly = 1) { pipeline.processText("SMS from +15550001", "Pay rent Friday") }
        coVerify(exactly = 1) { pipeline.processText("SMS from +15550003", "Dentist Mon 3pm") }
        coVerify(exactly = 2) { pipeline.processText(any(), any()) }
        // Only the successfully-fed rows are recorded; the contentless null row is left un-recorded so
        // a body materialising later can still be picked up.
        coVerify(exactly = 1) { sm.addProcessedSmsIds(match { it.toSet() == setOf("100", "102") }) }
    }

    @Test
    fun scanSms_alreadyProcessedIds_areSkipped() = runTest {
        rows(
            arrayOf<Any?>(200L, "+15550001", "Buy milk"),
            arrayOf<Any?>(201L, "+15550002", "Call plumber"),
        )
        val sm = sourceManager(processed = setOf("200")) // 200 already handled by the live observer

        scanner(sm).scanSince(0L)

        // Only the un-seen row runs the LLM again; the already-processed one is skipped.
        coVerify(exactly = 1) { pipeline.processText("SMS from +15550002", "Call plumber") }
        coVerify(exactly = 1) { pipeline.processText(any(), any()) }
    }

    @Test
    fun scanSms_oneRowFailing_doesNotStopTheRest_andFailedRowIsNotRecorded() = runTest {
        rows(
            arrayOf<Any?>(300L, "+15550001", "boom"),
            arrayOf<Any?>(301L, "+15550002", "survivor"),
        )
        coEvery { pipeline.processText("SMS from +15550001", "boom") } throws RuntimeException("kaboom")
        val sm = sourceManager()

        scanner(sm).scanSince(0L)

        // The failure is isolated: the later row still processes.
        coVerify(exactly = 1) { pipeline.processText("SMS from +15550002", "survivor") }
        // The failed row is NOT recorded (retried next scan); only the good one is.
        coVerify(exactly = 1) { sm.addProcessedSmsIds(match { it.toSet() == setOf("301") }) }
    }

    // ---------------- SmsObserver ----------------

    @Test
    fun observer_processesEveryNewRowNotJustTheNewest_andSkipsNullBody() = runTest {
        rows(
            arrayOf<Any?>(100L, "+15550001", "Pay rent Friday"),
            arrayOf<Any?>(101L, "+15550002", null),
            arrayOf<Any?>(102L, "+15550003", "Dentist Mon 3pm"),
        )
        val sm = mockk<SourceManager>(relaxed = true)
        val observer = SmsObserver(context, pipeline, sm)
        observer.lastSmsId = -1 // treat all three as new (a coalesced burst)

        observer.processNewSms()

        // The whole burst is captured — not just the single newest row — and the null body is skipped.
        coVerify(exactly = 1) { pipeline.processText("SMS from +15550001", "Pay rent Friday") }
        coVerify(exactly = 1) { pipeline.processText("SMS from +15550003", "Dentist Mon 3pm") }
        coVerify(exactly = 2) { pipeline.processText(any(), any()) }
        // Watermark advances past every row seen (including the skipped null one).
        assertEquals(102L, observer.lastSmsId)
        coVerify(exactly = 1) { sm.addProcessedSmsIds(match { it.toSet() == setOf("100", "102") }) }
    }

    @Test
    fun observer_reprocess_isNoOp_onceWatermarkPassed() = runTest {
        rows(arrayOf<Any?>(500L, "+15550001", "Water plants"))
        val sm = mockk<SourceManager>(relaxed = true)
        val observer = SmsObserver(context, pipeline, sm)
        observer.lastSmsId = 500L // already handled

        observer.processNewSms()

        // Nothing with _ID above the watermark → no pipeline work, no new ids recorded.
        coVerify(exactly = 0) { pipeline.processText(any(), any()) }
        coVerify(exactly = 0) { sm.addProcessedSmsIds(any()) }
    }

    // ---------------- SourceManager SMS ledger ----------------

    @Test
    fun processedSmsIds_cap_keepsTheHighestIds() = runTest {
        // DataStore is a process singleton; clear the key so this test starts clean regardless of order.
        context.dataStore.edit { it.remove(SourceManager.KEY_PROCESSED_SMS_IDS) }
        val sm = SourceManager(context)
        // Add more than the cap; ids are monotonic, so the cap must keep the most-recent (highest).
        val ids = (1..(SourceManager.MAX_PROCESSED_SMS_IDS + 100)).map { it.toString() }
        sm.addProcessedSmsIds(ids)

        val kept = sm.processedSmsIds.first()
        assertEquals(SourceManager.MAX_PROCESSED_SMS_IDS, kept.size)
        assertTrue(kept.contains((SourceManager.MAX_PROCESSED_SMS_IDS + 100).toString())) // newest kept
        assertFalse(kept.contains("1")) // oldest evicted
    }
}

/** A stub SMS provider whose query returns a fresh cursor from [rows], honouring an `_ID > ?` filter. */
class FakeSmsProvider : ContentProvider() {
    override fun onCreate(): Boolean = true

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?,
    ): Cursor {
        val cursor = MatrixCursor(arrayOf(Telephony.Sms._ID, Telephony.Sms.ADDRESS, Telephony.Sms.BODY))
        // Honour the observer's `_ID > ?` watermark filter; other selections return every row.
        val minExclusive = if (selection?.contains("${Telephony.Sms._ID} >") == true)
            selectionArgs?.firstOrNull()?.toLongOrNull() else null
        rows.filter { minExclusive == null || (it[0] as Long) > minExclusive }
            .forEach { cursor.addRow(it) }
        return cursor
    }

    override fun getType(uri: Uri): String? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = 0

    companion object {
        /** Each element is a row: [_id: Long, address: String?, body: String?]. */
        var rows: List<Array<Any?>> = emptyList()
    }
}
