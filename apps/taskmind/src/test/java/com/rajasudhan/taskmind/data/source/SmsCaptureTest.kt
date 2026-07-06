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

    /** A SettingsManager mock whose lastProcessedSmsId watermark is a controllable, mutable var. */
    private fun settingsWithSmsWatermark(initial: Long) = mockk<SettingsManager>(relaxed = true).also { s ->
        var wm = initial
        every { s.lastProcessedSmsId } answers { wm }
        every { s.lastProcessedSmsId = any() } answers { wm = firstArg() }
    }

    private fun scanner(sm: SourceManager, settings: SettingsManager = mockk(relaxed = true)) = RecentDataScanner(
        context, sm,
        settingsManager = settings,
        pipeline = pipeline,
        gmailAuth = mockk(relaxed = true),
        gmailCollector = mockk(relaxed = true),
        appUsageCollector = mockk(relaxed = true),
        transcriber = mockk(relaxed = true),
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

    // ---------------- #168: id-watermark gap recovery ----------------

    @Test
    fun scanSms_firstRun_seedsTheWatermark_withoutMiningHistory() = runTest {
        // Fresh install (watermark 0): the id-recovery must NOT walk pre-existing history — it just
        // seeds the watermark to the current newest id so only later arrivals are ever recovered.
        rows(arrayOf<Any?>(500L, "+1", "old history", 0L)) // an OLD message (date 0), id 500
        val sm = sourceManager()
        val settings = settingsWithSmsWatermark(0L)

        // `since` huge so the date window matches nothing (the old message is well outside it).
        scanner(sm, settings).scanSince(Long.MAX_VALUE)

        coVerify(exactly = 0) { pipeline.processText(any(), any()) } // history not mined
        assertEquals(500L, settings.lastProcessedSmsId)              // watermark seeded to newest id
    }

    @Test
    fun scanSms_recoversAMessageOlderThanTheDateWindow_viaTheIdWatermark() = runTest {
        // A message (id 50, old date) that arrived while the app was dead >24h ago: the DATE window
        // misses it, but its _ID is above the watermark (40) → the id-recovery captures it exactly once.
        rows(arrayOf<Any?>(50L, "+15550001", "Court date Aug 12", 100L))
        val sm = sourceManager()
        val settings = settingsWithSmsWatermark(40L)

        scanner(sm, settings).scanSince(Long.MAX_VALUE) // date window matches nothing → only recovery

        coVerify(exactly = 1) { pipeline.processText("SMS from +15550001", "Court date Aug 12") }
        coVerify(exactly = 1) { sm.addProcessedSmsIds(match { it.toSet() == setOf("50") }) }
        assertEquals(50L, settings.lastProcessedSmsId) // advanced past the recovered message
    }

    @Test
    fun scanSms_recovery_isBoundedPerScan_andAdvancesGradually() = runTest {
        // 250 un-processed messages above the watermark (a long-dormancy gap). One scan must recover at
        // most MAX_SMS_RECOVERY_PER_SCAN of them (oldest-first) and advance the watermark to exactly
        // there — NOT jump to the newest and drop the middle — so later scans catch up the rest.
        val cap = 200 // RecentDataScanner.MAX_SMS_RECOVERY_PER_SCAN
        rows(*(1000..1249).map { arrayOf<Any?>(it.toLong(), "+1", "msg $it", 100L) }.toTypedArray())
        val sm = sourceManager()
        val settings = settingsWithSmsWatermark(999L)

        scanner(sm, settings).scanSince(Long.MAX_VALUE) // date window matches nothing → only recovery

        coVerify(exactly = cap) { pipeline.processText(any(), any()) }
        assertEquals((1000 + cap - 1).toLong(), settings.lastProcessedSmsId) // capped, not jumped to 1249
    }

    @Test
    fun scanSms_recovery_skipsAlreadyProcessedIds_butStillAdvancesTheWatermark() = runTest {
        // The message is above the watermark but already in the ledger (captured live/earlier): it must
        // NOT be re-run through the LLM, but the watermark must still advance past it.
        rows(arrayOf<Any?>(50L, "+1", "already seen", 100L))
        val sm = sourceManager(processed = setOf("50"))
        val settings = settingsWithSmsWatermark(40L)

        scanner(sm, settings).scanSince(Long.MAX_VALUE)

        coVerify(exactly = 0) { pipeline.processText(any(), any()) }
        assertEquals(50L, settings.lastProcessedSmsId)
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
        // Honour the observer/recovery `_ID > ?` watermark filter and the scan's `DATE >= ?` window;
        // an unfiltered selection returns every row.
        val idGt = if (selection?.contains("${Telephony.Sms._ID} >") == true)
            selectionArgs?.firstOrNull()?.toLongOrNull() else null
        val dateGe = if (selection?.contains("${Telephony.Sms.DATE} >=") == true)
            selectionArgs?.firstOrNull()?.toLongOrNull() else null
        var matched = rows.filter { r ->
            (idGt == null || (r[0] as Long) > idGt) && (dateGe == null || rowDate(r) >= dateGe)
        }
        // Honour ORDER BY _ID ASC/DESC and a trailing LIMIT n, so the recovery's bounded oldest-first
        // walk is exercised faithfully (a date-ordered query is left in definition order).
        when {
            sortOrder?.contains("${Telephony.Sms._ID} DESC") == true -> matched = matched.sortedByDescending { it[0] as Long }
            sortOrder?.contains("${Telephony.Sms._ID} ASC") == true -> matched = matched.sortedBy { it[0] as Long }
        }
        Regex("""LIMIT\s+(\d+)""").find(sortOrder ?: "")?.groupValues?.get(1)?.toIntOrNull()
            ?.let { matched = matched.take(it) }
        matched.forEach { cursor.addRow(arrayOf(it[0], it[1], it[2])) } // only the 3 projected columns
        return cursor
    }

    override fun getType(uri: Uri): String? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = 0

    companion object {
        /**
         * Each element is a row: [_id: Long, address: String?, body: String?, date: Long?]. The date is
         * optional and defaults to "always recent", so a row without one always falls inside a
         * `DATE >= ?` window (matching every pre-#168 test); set it to place a row outside the window.
         */
        var rows: List<Array<Any?>> = emptyList()

        private fun rowDate(r: Array<Any?>): Long = (r.getOrNull(3) as? Long) ?: Long.MAX_VALUE
    }
}
