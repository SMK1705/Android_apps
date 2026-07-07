package com.rajasudhan.taskmind.ui.capture

import android.content.Intent
import android.os.Bundle
import android.speech.RecognizerIntent
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.rajasudhan.taskmind.data.source.CaptureWorker
import com.rajasudhan.taskmind.ui.theme.TaskMindTheme

/**
 * A lightweight, lock-free capture launched by the Quick Settings tile, the home widget, and the
 * launcher long-press shortcuts. Shows only an input surface (no existing data), so it doesn't require
 * unlocking the app. With [EXTRA_MODE] = [MODE_SPEAK] it jumps straight to the system dictation UI —
 * the launcher "Speak" shortcut — falling back to the type dialog if no recognizer is available.
 */
class QuickCaptureActivity : ComponentActivity() {

    // Registered unconditionally at construction (required before the activity is STARTED). Used only by
    // the Speak shortcut: the spoken text comes back here and goes straight into the capture pipeline.
    private val speechLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            val spoken = result.data
                ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                ?.firstOrNull()
                ?.trim()
                .orEmpty()
            if (spoken.isNotBlank()) {
                submit(spoken)
                finish()
            } else {
                // Cancelled or nothing recognised — don't dump the user out empty-handed; drop to the
                // type field so they can still capture (this activity's default entry point anyway).
                showTypeDialog()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Speak mode: launch dictation once (not on config-change recreation, where the result is still
        // pending). Fall back to typing if the device has no speech recognizer.
        if (intent?.getStringExtra(EXTRA_MODE) == MODE_SPEAK && savedInstanceState == null) {
            if (launchSpeech()) return
        }
        showTypeDialog()
    }

    private fun launchSpeech(): Boolean {
        val speech = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak a note, task or reminder")
        return runCatching { speechLauncher.launch(speech) }.isSuccess
    }

    private fun submit(text: String) {
        CaptureWorker.enqueue(applicationContext, "Quick add", text)
        Toast.makeText(this, "Added to TaskMind for review.", Toast.LENGTH_SHORT).show()
    }

    private fun showTypeDialog() {
        setContent {
            TaskMindTheme {
                var text by remember { mutableStateOf("") }
                AlertDialog(
                    onDismissRequest = { finish() },
                    title = { Text("Add to TaskMind") },
                    text = {
                        OutlinedTextField(
                            value = text,
                            onValueChange = { text = it },
                            label = { Text("Type a note, task or reminder") },
                            modifier = Modifier.fillMaxWidth()
                        )
                    },
                    confirmButton = {
                        TextButton(enabled = text.isNotBlank(), onClick = {
                            submit(text.trim())
                            finish()
                        }) { Text("Add") }
                    },
                    dismissButton = { TextButton(onClick = { finish() }) { Text("Cancel") } }
                )
            }
        }
    }

    companion object {
        /** String extra selecting the capture mode; absent = type, [MODE_SPEAK] = dictation. */
        const val EXTRA_MODE = "com.rajasudhan.taskmind.extra.MODE"
        const val MODE_SPEAK = "speak"
    }
}
