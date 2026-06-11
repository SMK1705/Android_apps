package com.rajasudhan.taskmind.data.source

import android.content.Context
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.Telephony
import com.rajasudhan.taskmind.data.source.understanding.UnderstandingPipeline
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SmsObserver @Inject constructor(
    @ApplicationContext private val context: Context,
    private val understandingPipeline: UnderstandingPipeline
) : ContentObserver(Handler(Looper.getMainLooper())) {

    private val scope = CoroutineScope(Dispatchers.IO)
    private var lastSmsId: Long = -1

    init {
        // Initialize last seen SMS ID
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
        
        scope.launch {
            val newSmsId = getLastSmsId()
            if (newSmsId > lastSmsId && newSmsId != -1L) {
                lastSmsId = newSmsId
                processLatestSms()
            }
        }
    }

    private fun getLastSmsId(): Long {
        var id = -1L
        try {
            val cursor = context.contentResolver.query(
                Telephony.Sms.Inbox.CONTENT_URI,
                arrayOf(Telephony.Sms._ID),
                null,
                null,
                Telephony.Sms.DEFAULT_SORT_ORDER + " LIMIT 1"
            )
            cursor?.use {
                if (it.moveToFirst()) {
                    id = it.getLong(0)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return id
    }

    private suspend fun processLatestSms() {
        try {
            val cursor = context.contentResolver.query(
                Telephony.Sms.Inbox.CONTENT_URI,
                arrayOf(Telephony.Sms.ADDRESS, Telephony.Sms.BODY),
                null,
                null,
                Telephony.Sms.DEFAULT_SORT_ORDER + " LIMIT 1"
            )
            cursor?.use {
                if (it.moveToFirst()) {
                    val address = it.getString(0)
                    val body = it.getString(1)
                    
                    understandingPipeline.processText("SMS from $address", body)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
