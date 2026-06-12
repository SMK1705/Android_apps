package com.rajasudhan.taskmind.data.source

import android.content.ContentUris
import android.content.Context
import android.provider.CallLog
import android.provider.MediaStore
import android.provider.Telephony
import com.rajasudhan.taskmind.data.source.email.GmailAuth
import com.rajasudhan.taskmind.data.source.email.GmailCollector
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
    private val pipeline: UnderstandingPipeline,
    private val gmailAuth: GmailAuth,
    private val gmailCollector: GmailCollector,
    private val appUsageCollector: AppUsageCollector,
    private val voskTranscriber: VoskTranscriber
) {
    suspend fun scanSince(sinceMillis: Long) {
        if (sourceManager.isSmsEnabled.first()) runCatching { scanSms(sinceMillis) }
        if (sourceManager.isCallLogEnabled.first()) runCatching { scanCalls(sinceMillis) }
        if (sourceManager.isEmailEnabled.first()) runCatching { scanEmail(sinceMillis) }
        if (sourceManager.isAppUsageEnabled.first()) runCatching { appUsageCollector.generateDailyDigestIfDue() }
        if (sourceManager.isAudioEnabled.first()) runCatching { scanAudio(sinceMillis) }
    }

    private suspend fun scanSms(since: Long) {
        context.contentResolver.query(
            Telephony.Sms.Inbox.CONTENT_URI,
            arrayOf(Telephony.Sms.ADDRESS, Telephony.Sms.BODY),
            "${Telephony.Sms.DATE} >= ?",
            arrayOf(since.toString()),
            "${Telephony.Sms.DATE} DESC"
        )?.use { cursor ->
            while (cursor.moveToNext()) {
                val address = cursor.getString(0)
                val body = cursor.getString(1)
                pipeline.processText("SMS from $address", body)
            }
        }
    }

    private suspend fun scanCalls(since: Long) {
        context.contentResolver.query(
            CallLog.Calls.CONTENT_URI,
            arrayOf(CallLog.Calls.NUMBER, CallLog.Calls.TYPE, CallLog.Calls.DURATION),
            "${CallLog.Calls.DATE} >= ?",
            arrayOf(since.toString()),
            "${CallLog.Calls.DATE} DESC"
        )?.use { cursor ->
            while (cursor.moveToNext()) {
                val number = cursor.getString(0)
                val typeStr = when (cursor.getInt(1)) {
                    CallLog.Calls.INCOMING_TYPE -> "Incoming"
                    CallLog.Calls.OUTGOING_TYPE -> "Outgoing"
                    CallLog.Calls.MISSED_TYPE -> "Missed"
                    else -> "Unknown"
                }
                val duration = cursor.getString(2)
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
        val selection = "${MediaStore.Audio.Media.DATE_MODIFIED} >= ? AND " +
            "${MediaStore.Audio.Media.IS_MUSIC} = 0 AND " +
            "(${MediaStore.Audio.Media.RELATIVE_PATH} LIKE '%Recordings%' OR " +
            "${MediaStore.Audio.Media.RELATIVE_PATH} LIKE '%Call%')"
        val args = arrayOf((since / 1000).toString())
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
     * Fetches recent unread Primary-category emails and runs them through the pipeline. Skips the
     * scan silently if Gmail isn't currently authorized (the user reconnects in Sources). Already-
     * processed message ids are skipped so a still-unread email isn't re-run on every scan.
     */
    private suspend fun scanEmail(since: Long) {
        val token = gmailAuth.silentAccessToken()
        if (token == null) {
            android.util.Log.w("RecentDataScanner", "Gmail enabled but not authorized; skipping email scan")
            return
        }
        val skip = sourceManager.processedEmailIds.first()
        val emails = gmailCollector.fetchUnreadPrimary(token, since, skip)
        android.util.Log.i("RecentDataScanner", "Gmail fetched ${emails.size} new email(s) since $since")
        for (email in emails) {
            pipeline.processText("Email from ${email.sender}", "${email.subject}\n\n${email.body}")
            sourceManager.addProcessedEmailId(email.id)
        }
    }
}
