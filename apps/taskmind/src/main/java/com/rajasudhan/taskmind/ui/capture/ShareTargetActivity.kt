package com.rajasudhan.taskmind.ui.capture

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import com.rajasudhan.taskmind.data.source.CaptureWorker

/**
 * Invisible share-sheet target: accepts shared text (ACTION_SEND text/plain) and feeds it to the
 * capture pipeline, then finishes. No existing data is shown, so it deliberately bypasses the
 * biometric lock (capture only) — the lock still guards everything that *reads* your data.
 */
class ShareTargetActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val subject = intent.getStringExtra(Intent.EXTRA_SUBJECT).orEmpty()
        val body = intent.getStringExtra(Intent.EXTRA_TEXT).orEmpty()
        val text = listOf(subject, body).filter { it.isNotBlank() }.joinToString("\n\n")
        if (text.isNotBlank()) {
            CaptureWorker.enqueue(applicationContext, "Shared", text)
            Toast.makeText(this, "Added to TaskMind for review.", Toast.LENGTH_SHORT).show()
        }
        finish()
    }
}
