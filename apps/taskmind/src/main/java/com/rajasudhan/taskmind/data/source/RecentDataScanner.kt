package com.rajasudhan.taskmind.data.source

import android.content.ContentUris
import android.content.Context
import android.database.Cursor
import android.provider.CallLog
import android.provider.MediaStore
import android.provider.Telephony
import com.rajasudhan.taskmind.data.source.email.GmailAuth
import com.rajasudhan.taskmind.data.source.email.GmailCollector
import com.rajasudhan.taskmind.data.source.ocr.OcrEngine
import com.rajasudhan.taskmind.data.source.transcription.VoskTranscriber
import com.rajasudhan.taskmind.data.source.understanding.UnderstandingPipeline
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Scans recent SMS and call logs (since a given time) and feeds them through the
 * understanding pipeline. Shared by the manual Inbox refresh and the periodic WorkManager scan.
 * Each source is only scanned if its toggle is enabled.
 */
@Singleton
class RecentDataScanner @Inject constructor(
    @ApplicationContext private val context: Context,
    private val sourceManager: SourceManager,
    private val settingsManager: SettingsManager,
    private val pipeline: UnderstandingPipeline,
    private val gmailAuth: GmailAuth,
    private val gmailCollector: GmailCollector,
    private val appUsageCollector: AppUsageCollector,
    private val voskTranscriber: VoskTranscriber,
    private val ocrEngine: OcrEngine,
    private val personContextNotifier: PersonContextNotifier
) {
    private companion object {
        const val TAG = "RecentDataScanner"
        const val MAX_LOOKBACK_MS = 24 * 60 * 60 * 1000L      // cap a since-last-scan window at 24h
        const val FIRST_RUN_LOOKBACK_MS = 15 * 60 * 1000L     // first ever scan: just the last 15 min
        const val MAX_SMS_RECOVERY_PER_SCAN = 200             // bound the id-recovery so a long gap can't flood
    }

    /**
     * Scans everything that arrived since the last successful scan, so nothing in the gap between
     * refreshes is missed (the old fixed 10-minute window silently dropped anything older). Shared by
     * the manual Inbox refresh and the periodic worker, which both advance the same watermark.
     *
     * The window is capped at [MAX_LOOKBACK_MS] so a long-dormant app doesn't re-scan months of
     * history; the very first run only looks back [FIRST_RUN_LOOKBACK_MS] to avoid an initial flood.
     */
    suspend fun scanIncremental() {
        val now = System.currentTimeMillis()
        val last = settingsManager.lastScanAt
        val since = when {
            last <= 0L -> now - FIRST_RUN_LOOKBACK_MS
            else -> maxOf(last, now - MAX_LOOKBACK_MS)
        }
        scanSince(since)
        // Advance the watermark only after the scan; each source swallows its own errors, so this
        // is reached even if one source failed (we accept re-scanning rather than dropping data).
        settingsManager.lastScanAt = now
    }

    suspend fun scanSince(sinceMillis: Long) {
        if (sourceManager.isSmsEnabled.first()) runCatching { scanSms(sinceMillis) }
        if (sourceManager.isCallLogEnabled.first()) runCatching { scanCalls(sinceMillis) }
        if (sourceManager.isEmailEnabled.first()) runCatching { scanEmail(sinceMillis) }
        if (sourceManager.isAppUsageEnabled.first()) runCatching { appUsageCollector.generateDailyDigestIfDue() }
        if (sourceManager.isAudioEnabled.first()) runCatching { scanAudio(sinceMillis) }
        if (sourceManager.isImagesEnabled.first()) runCatching { scanImages(sinceMillis) }
    }

    /** Live entry points used by the foreground-service media observers (last few minutes only). */
    suspend fun scanAudioRecent() {
        if (sourceManager.isAudioEnabled.first()) runCatching { scanAudio(System.currentTimeMillis() - 5 * 60 * 1000) }
    }

    suspend fun scanImagesRecent() {
        if (sourceManager.isImagesEnabled.first()) runCatching { scanImages(System.currentTimeMillis() - 5 * 60 * 1000) }
    }

    private suspend fun scanSms(since: Long) {
        val processed = sourceManager.processedSmsIds.first()
        // Primary: the recent date window (real-time-ish; also what the manual refresh uses).
        val captured = context.contentResolver.query(
            Telephony.Sms.Inbox.CONTENT_URI,
            arrayOf(Telephony.Sms._ID, Telephony.Sms.ADDRESS, Telephony.Sms.BODY),
            "${Telephony.Sms.DATE} >= ?",
            arrayOf(since.toString()),
            "${Telephony.Sms.DATE} DESC"
        )?.use { consumeSmsRows(it, processed).first } ?: emptyList()
        if (captured.isNotEmpty()) sourceManager.addProcessedSmsIds(captured)
        // Gap recovery: the 24h date clamp silently drops a message that arrived while the app was dead
        // longer than that; recover it by id instead.
        recoverMissedSmsById()
    }

    /**
     * Recovers SMS the [scanSms] date window can't reach — messages that arrived while the app was dead
     * for longer than [MAX_LOOKBACK_MS], so they're older than `since` at scan time. Walks rows above a
     * persisted contiguous `_ID` watermark, oldest-first and capped at [MAX_SMS_RECOVERY_PER_SCAN] per
     * scan (so a long dormancy can't flood the LLM), and advances the watermark past everything it saw.
     * Already-captured messages (date window or the live observer) are in the ledger, so they're skipped
     * here, never re-run.
     */
    private suspend fun recoverMissedSmsById() {
        val watermark = settingsManager.lastProcessedSmsId
        if (watermark <= 0L) {
            // First run: seed to the current newest id — never mine pre-existing history by id (the
            // date window already covered the recent slice). Nothing to recover on a fresh install.
            currentMaxSmsId()?.let { settingsManager.lastProcessedSmsId = it }
            return
        }
        val processed = sourceManager.processedSmsIds.first()
        val (captured, maxSeen) = context.contentResolver.query(
            Telephony.Sms.Inbox.CONTENT_URI,
            arrayOf(Telephony.Sms._ID, Telephony.Sms.ADDRESS, Telephony.Sms.BODY),
            "${Telephony.Sms._ID} > ?",
            arrayOf(watermark.toString()),
            "${Telephony.Sms._ID} ASC LIMIT $MAX_SMS_RECOVERY_PER_SCAN"
        )?.use { consumeSmsRows(it, processed) } ?: (emptyList<String>() to 0L)
        if (captured.isNotEmpty()) sourceManager.addProcessedSmsIds(captured)
        if (maxSeen > watermark) settingsManager.lastProcessedSmsId = maxSeen
    }

    /** The newest SMS `_ID` in the inbox, or null if empty/unreadable. */
    private fun currentMaxSmsId(): Long? =
        context.contentResolver.query(
            Telephony.Sms.Inbox.CONTENT_URI,
            arrayOf(Telephony.Sms._ID),
            null, null,
            "${Telephony.Sms._ID} DESC LIMIT 1"
        )?.use { if (it.moveToFirst()) it.getLong(0) else null }

    /**
     * Feeds each SMS row through the pipeline, skipping already-processed ids and null/blank bodies (an
     * MMS stub / mid-write row — never let it abort the batch), isolating each row's failure. Returns
     * the ids newly captured and the highest `_ID` examined (for the recovery watermark).
     */
    private suspend fun consumeSmsRows(cursor: Cursor, processed: Set<String>): Pair<List<String>, Long> {
        val idCol = cursor.getColumnIndexOrThrow(Telephony.Sms._ID)
        val addressCol = cursor.getColumnIndexOrThrow(Telephony.Sms.ADDRESS)
        val bodyCol = cursor.getColumnIndexOrThrow(Telephony.Sms.BODY)
        val captured = mutableListOf<String>()
        var maxId = 0L
        while (cursor.moveToNext()) {
            val idLong = cursor.getLong(idCol)
            maxId = maxOf(maxId, idLong)
            val id = idLong.toString()
            if (id in processed) continue
            val body = cursor.getString(bodyCol)
            if (body.isNullOrBlank()) continue // don't record it — a body may materialise later
            val address = cursor.getString(addressCol) ?: "unknown"
            val ok = runCatching { pipeline.processText("SMS from $address", body) }
                .onFailure { android.util.Log.w(TAG, "SMS row $id failed", it) }
                .isSuccess
            if (ok) captured += id
        }
        return captured to maxId
    }

    private suspend fun scanCalls(since: Long) {
        context.contentResolver.query(
            CallLog.Calls.CONTENT_URI,
            arrayOf(CallLog.Calls.NUMBER, CallLog.Calls.TYPE, CallLog.Calls.DURATION, CallLog.Calls.CACHED_NAME),
            "${CallLog.Calls.DATE} >= ?",
            arrayOf(since.toString()),
            "${CallLog.Calls.DATE} DESC"
        )?.use { cursor ->
            while (cursor.moveToNext()) {
                val number = cursor.getString(0)
                val type = cursor.getInt(1)
                val duration = cursor.getString(2)
                val cachedName = cursor.getString(3)
                // Person-context: a call from a known contact surfaces any open item tied to them.
                if (!cachedName.isNullOrBlank()) personContextNotifier.notifyForContact(cachedName)
                if (type == CallLog.Calls.MISSED_TYPE) {
                    // A missed call is a concrete "call back" task, but the LLM tends to drop it as
                    // non-actionable — so build the suggestion straight from the log, which already
                    // has the number (and often the contact name). addCallback normalizes the number
                    // and skips private/unknown callers we couldn't dial back anyway.
                    pipeline.addCallback(cachedName, number)
                    continue
                }
                val typeStr = when (type) {
                    CallLog.Calls.INCOMING_TYPE -> "Incoming"
                    CallLog.Calls.OUTGOING_TYPE -> "Outgoing"
                    else -> "Unknown"
                }
                pipeline.processText("Call Log", "$typeStr call with $number lasting $duration seconds.")
            }
        }
    }

    /**
     * Transcribes recent call/voice recordings (on-device via Vosk) and runs the transcript through
     * the pipeline. Uses MediaStore to find non-music audio in the Recordings/Call folders modified
     * since [since]; already-transcribed ids are skipped. Skips silently if no Vosk model is present.
     */
    private suspend fun scanAudio(since: Long) {
        if (!voskTranscriber.isModelPresent()) {
            android.util.Log.w("RecentDataScanner", "Audio enabled but no Vosk model present; skipping")
            return
        }
        val processed = sourceManager.processedAudioIds.first()
        val collection = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        val projection = arrayOf(MediaStore.Audio.Media._ID, MediaStore.Audio.Media.DISPLAY_NAME)
        // Honour the user-configured Call / Voice recording folders (Settings → Sources) instead of a
        // hardcoded filter; fall back to the common folder names if both are blank so we never build an
        // empty clause (which would match nothing) or an unbounded one.
        val patterns = listOf(
            sourceManager.callRecordingPath.first(),
            sourceManager.voiceRecordingPath.first(),
        ).mapNotNull { RecordingPaths.relativePattern(it) }.distinct()
            .ifEmpty { listOf("Recordings", "Call") }
        val pathClause = patterns.joinToString(" OR ") { "${MediaStore.Audio.Media.RELATIVE_PATH} LIKE ?" }
        val selection = "${MediaStore.Audio.Media.DATE_MODIFIED} >= ? AND " +
            "${MediaStore.Audio.Media.IS_MUSIC} = 0 AND ($pathClause)"
        val args = (listOf((since / 1000).toString()) + patterns.map { "%$it%" }).toTypedArray()
        context.contentResolver.query(
            collection, projection, selection, args,
            "${MediaStore.Audio.Media.DATE_MODIFIED} DESC"
        )?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME)
            while (cursor.moveToNext()) {
                val id = cursor.getLong(idCol)
                if (id.toString() in processed) continue
                val name = cursor.getString(nameCol) ?: "recording"
                val uri = ContentUris.withAppendedId(collection, id)
                val transcript = runCatching { voskTranscriber.transcribe(uri) }.getOrNull()
                sourceManager.addProcessedAudioId(id.toString())
                android.util.Log.i("RecentDataScanner", "Transcribed $name -> ${transcript?.length ?: 0} chars")
                if (!transcript.isNullOrBlank()) {
                    pipeline.processText("Recording: $name", transcript)
                }
            }
        }
    }

    /**
     * OCRs recent screenshots on-device (Tesseract) and runs the text through the pipeline. Finds
     * images in the Screenshots folder modified since [since]; already-read ids are skipped. Skips
     * silently if no OCR model is present.
     */
    private suspend fun scanImages(since: Long) {
        if (!ocrEngine.isModelPresent()) {
            android.util.Log.w("RecentDataScanner", "Images enabled but no OCR model present; skipping")
            return
        }
        val processed = sourceManager.processedImageIds.first()
        val collection = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        val projection = arrayOf(MediaStore.Images.Media._ID, MediaStore.Images.Media.DISPLAY_NAME)
        val selection = "${MediaStore.Images.Media.DATE_MODIFIED} >= ? AND " +
            "${MediaStore.Images.Media.RELATIVE_PATH} LIKE '%Screenshots%'"
        val args = arrayOf((since / 1000).toString())
        context.contentResolver.query(
            collection, projection, selection, args,
            "${MediaStore.Images.Media.DATE_MODIFIED} DESC"
        )?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
            while (cursor.moveToNext()) {
                val id = cursor.getLong(idCol)
                if (id.toString() in processed) continue
                val name = cursor.getString(nameCol) ?: "screenshot"
                val uri = ContentUris.withAppendedId(collection, id)
                val text = runCatching { ocrEngine.recognize(uri) }.getOrNull()
                sourceManager.addProcessedImageId(id.toString())
                android.util.Log.i("RecentDataScanner", "OCR'd $name -> ${text?.length ?: 0} chars")
                if (!text.isNullOrBlank()) {
                    pipeline.processText("Screenshot: $name", text)
                }
            }
        }
    }

    /**
     * Fetches recent unread Primary-category emails and runs them through the pipeline. Skips the
     * scan silently if Gmail isn't currently authorized (the user reconnects in Sources). Already-
     * processed message ids are skipped so a still-unread email isn't re-run on every scan.
     */
    private suspend fun scanEmail(since: Long) {
        val accounts = settingsManager.gmailAccounts
        if (accounts.isEmpty()) {
            android.util.Log.w("RecentDataScanner", "Email enabled but no Gmail account connected; skipping")
            return
        }
        for (account in accounts) {
            val token = gmailAuth.silentAccessToken(account)
            if (token == null) {
                android.util.Log.w("RecentDataScanner", "Gmail account $account not authorized; skipping it")
                continue
            }
            val skip = sourceManager.processedEmailIds(account).first()
            val emails = gmailCollector.fetchUnreadPrimary(token, since, skip)
            android.util.Log.i("RecentDataScanner", "Gmail($account) fetched ${emails.size} new email(s) since $since")
            for (email in emails) {
                // Tag the source with the mailbox so the inbox shows which account it came from.
                pipeline.processText("Email ($account) from ${email.sender}", "${email.subject}\n\n${email.body}")
                sourceManager.addProcessedEmailId(account, email.id)
            }
        }
    }
}
