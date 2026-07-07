package com.rajasudhan.taskmind.data.source.wear

import android.content.Context
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService
import com.rajasudhan.taskmind.data.source.CaptureWorker

/**
 * Receives wrist voice captures (#216). The watch does the speech-to-text and sends the RAW spoken text
 * over the Data Layer; here it goes through the SAME [CaptureWorker] -> UnderstandingPipeline path as every
 * other capture source, so the phone does the extraction (dates, type, dedupe) and no model ever runs on
 * the watch — the on-device privacy claim holds. Enqueuing needs only a Context, so this service stays free
 * of Hilt.
 */
class WearCaptureListenerService : WearableListenerService() {

    override fun onMessageReceived(event: MessageEvent) {
        handleMessage(this, event.path, event.data)
    }

    companion object {
        const val SOURCE_WATCH = "Watch"

        /**
         * The whole receiver behaviour, split out from [onMessageReceived] so it's unit-testable without a
         * live Service/Data Layer: for a capture-path message with non-blank speech, enqueue the trimmed
         * text as a "Watch" capture; ignore everything else.
         */
        internal fun handleMessage(context: Context, path: String, data: ByteArray) {
            val text = captureTextFor(path, data) ?: return
            CaptureWorker.enqueue(context, SOURCE_WATCH, text)
        }

        /**
         * The pure text decision: returns the trimmed capture text for a capture-path message, or null to
         * ignore the message (wrong path, or empty/blank speech).
         */
        internal fun captureTextFor(path: String, data: ByteArray): String? {
            if (path != WearContract.PATH_CAPTURE) return null
            return String(data, Charsets.UTF_8).trim().ifBlank { null }
        }
    }
}
