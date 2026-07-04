package com.rajasudhan.taskmind.data.source

import android.content.Context
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.Telephony
import androidx.annotation.VisibleForTesting
import com.rajasudhan.taskmind.data.source.understanding.UnderstandingPipeline
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Real-time SMS capture: a [ContentObserver] on the inbox that feeds newly-arrived messages into the
 * understanding pipeline. The periodic [RecentDataScanner] is the catch-up safety net for anything
 * that arrives while the app process is dead; this observer handles messages that land while it runs.
 *
 * Everything keys off the SMS provider `_ID` (monotonic), never the message date: [getLastSmsId]
 * seeds the watermark from the highest existing id, and each [onChange] processes *every* row with
 * `_ID > watermark` — not just the newest — so a burst delivered in a single coalesced callback is
 * captured in full rather than all-but-one dropped. onChange deliveries are serialised by [mutex] so
 * two concurrent callbacks can't both advance past the same rows (which would double-run the LLM).
 */
@Singleton
class SmsObserver @Inject constructor(
    @ApplicationContext private val context: Context,
    private val understandingPipeline: UnderstandingPipeline,
    private val sourceManager: SourceManager
) : ContentObserver(Handler(Looper.getMainLooper())) {

    private val scope = CoroutineScope(Dispatchers.IO)
    // Serialises the read-process-advance of [lastSmsId] across concurrent onChange deliveries. The
    // ContentObserver framework routinely re-delivers a change (and fans out over subtree URIs), so
    // without this two coroutines could both read the same watermark and process a message twice.
    private val mutex = Mutex()

    // Highest SMS _ID already handled. @Volatile for visibility across the IO coroutines; all
    // read-modify-write of it happens under [mutex].
    @Volatile
    @VisibleForTesting
    internal var lastSmsId: Long = -1

    init {
        // Seed from the current newest id so we only ever process messages that arrive *after* start;
        // the process-death gap is the periodic scanner's job, not ours.
        lastSmsId = getLastSmsId()
    }

    fun start() {
        context.contentResolver.registerContentObserver(
            Telephony.Sms.CONTENT_URI,
            true,
            this
        )
    }

    fun stop() {
        context.contentResolver.unregisterContentObserver(this)
    }

    override fun onChange(selfChange: Boolean, uri: Uri?) {
        super.onChange(selfChange, uri)
        scope.launch { mutex.withLock { processNewSms() } }
    }

    /** Highest `_ID` currently in the inbox, or -1 if empty/unreadable. Ordered by id (not date). */
    private fun getLastSmsId(): Long {
        var id = -1L
        try {
            context.contentResolver.query(
                Telephony.Sms.Inbox.CONTENT_URI,
                arrayOf(Telephony.Sms._ID),
                null,
                null,
                "${Telephony.Sms._ID} DESC LIMIT 1"
            )?.use { if (it.moveToFirst()) id = it.getLong(0) }
        } catch (e: Exception) {
            android.util.Log.w(TAG, "getLastSmsId failed", e)
        }
        return id
    }

    /**
     * Processes every inbox row with `_ID` above the watermark, oldest-first so the watermark advances
     * monotonically. Null/blank bodies (MMS stubs, WAP-push) are skipped per-row without aborting the
     * batch; a per-row failure is isolated so one bad message can't drop the rest. Runs under [mutex].
     */
    @VisibleForTesting
    internal suspend fun processNewSms() {
        val since = lastSmsId
        val processedIds = mutableListOf<String>()
        try {
            context.contentResolver.query(
                Telephony.Sms.Inbox.CONTENT_URI,
                arrayOf(Telephony.Sms._ID, Telephony.Sms.ADDRESS, Telephony.Sms.BODY),
                "${Telephony.Sms._ID} > ?",
                arrayOf(since.toString()),
                "${Telephony.Sms._ID} ASC"
            )?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(Telephony.Sms._ID)
                val addressCol = cursor.getColumnIndexOrThrow(Telephony.Sms.ADDRESS)
                val bodyCol = cursor.getColumnIndexOrThrow(Telephony.Sms.BODY)
                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idCol)
                    // Advance past every row we look at (even skipped ones) so we don't re-examine it.
                    lastSmsId = maxOf(lastSmsId, id)
                    val body = cursor.getString(bodyCol)
                    if (body.isNullOrBlank()) continue
                    val address = cursor.getString(addressCol) ?: "unknown"
                    val ok = runCatching { understandingPipeline.processText("SMS from $address", body) }
                        .onFailure { android.util.Log.w(TAG, "SMS row $id failed", it) }
                        .isSuccess
                    if (ok) processedIds += id.toString()
                }
            }
        } catch (e: Exception) {
            android.util.Log.w(TAG, "processNewSms failed", e)
        }
        // Record what we captured so the periodic scanner skips it instead of re-running the LLM.
        if (processedIds.isNotEmpty()) sourceManager.addProcessedSmsIds(processedIds)
    }

    private companion object {
        const val TAG = "SmsObserver"
    }
}
