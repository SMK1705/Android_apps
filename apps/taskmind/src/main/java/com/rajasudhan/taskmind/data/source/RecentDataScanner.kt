package com.rajasudhan.taskmind.data.source

import android.content.Context
import android.provider.CallLog
import android.provider.Telephony
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
    private val pipeline: UnderstandingPipeline
) {
    suspend fun scanSince(sinceMillis: Long) {
        if (sourceManager.isSmsEnabled.first()) runCatching { scanSms(sinceMillis) }
        if (sourceManager.isCallLogEnabled.first()) runCatching { scanCalls(sinceMillis) }
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
}
