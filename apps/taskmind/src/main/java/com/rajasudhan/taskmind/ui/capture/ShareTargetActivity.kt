package com.rajasudhan.taskmind.ui.capture

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.core.content.IntentCompat
import com.rajasudhan.taskmind.data.source.CaptureWorker
import java.io.File

/**
 * Invisible share-sheet target: accepts shared text (ACTION_SEND text/plain) or a shared image
 * (OCR'd on-device) and feeds it to the capture pipeline, then finishes. No existing data is shown,
 * so it deliberately bypasses the biometric lock (capture only) — the lock still guards reads.
 */
class ShareTargetActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val handled = when {
            intent?.type?.startsWith("image/") == true -> handleImage()
            else -> handleText()
        }
        if (handled) Toast.makeText(this, "Added to TaskMind for review.", Toast.LENGTH_SHORT).show()
        finish()
    }

    private fun handleText(): Boolean {
        val subject = intent.getStringExtra(Intent.EXTRA_SUBJECT).orEmpty()
        val body = intent.getStringExtra(Intent.EXTRA_TEXT).orEmpty()
        val text = listOf(subject, body).filter { it.isNotBlank() }.joinToString("\n\n")
        if (text.isBlank()) return false
        CaptureWorker.enqueue(applicationContext, "Shared", text)
        return true
    }

    private fun handleImage(): Boolean {
        val uri = IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java) ?: return false
        // Copy into app storage now (we hold the read grant); the worker OCRs and deletes it.
        val dest = File(cacheDir, "share_${System.currentTimeMillis()}.img")
        val copied = runCatching {
            contentResolver.openInputStream(uri)?.use { input ->
                dest.outputStream().use { input.copyTo(it) }
            } != null
        }.getOrDefault(false)
        if (!copied) return false
        CaptureWorker.enqueueImage(applicationContext, "Shared image", dest)
        return true
    }
}
