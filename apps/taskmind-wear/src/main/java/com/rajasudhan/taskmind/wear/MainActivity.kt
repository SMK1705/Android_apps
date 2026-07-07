package com.rajasudhan.taskmind.wear

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognizerIntent
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.CircularProgressIndicator
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Scaffold
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.TimeText
import com.google.android.gms.wearable.Wearable

/**
 * The wrist voice-capture app (#216): raise wrist, tap, speak, done. The system speech recognizer turns
 * speech into text on the watch; the RAW text is sent to the paired phone over the Data Layer
 * ([WearContract.PATH_CAPTURE]), where the phone runs it through the existing extraction pipeline. No
 * watch-side model, so extraction stays on the phone and the on-device privacy claim holds.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { WearApp() }
    }
}

private enum class Status { IDLE, SENDING, SENT, NO_PHONE, FAILED }

@Composable
fun WearApp() {
    val context = LocalContext.current
    var status by remember { mutableStateOf(Status.IDLE) }

    val speech = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val text = result.data
            ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            ?.firstOrNull()
            ?.trim()
            .orEmpty()
        if (result.resultCode == Activity.RESULT_OK && text.isNotBlank()) {
            status = Status.SENDING
            sendCapture(
                context, text,
                onSent = { status = Status.SENT },
                onNoPhone = { status = Status.NO_PHONE },
                onError = { status = Status.FAILED },
            )
        } else {
            status = Status.IDLE
        }
    }

    MaterialTheme {
        Scaffold(timeText = { TimeText() }) {
            Column(
                modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                if (status == Status.SENDING) {
                    CircularProgressIndicator()
                } else {
                    Chip(
                        onClick = {
                            status = Status.IDLE
                            speech.launch(
                                Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                                    .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                                    .putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak a task"),
                            )
                        },
                        label = { Text("Speak a task") },
                        colors = ChipDefaults.primaryChipColors(),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    text = when (status) {
                        Status.IDLE -> "Add to TaskMind"
                        Status.SENDING -> "Sending…"
                        Status.SENT -> "✓ Added to your Inbox"
                        Status.NO_PHONE -> "Phone not reachable"
                        Status.FAILED -> "Couldn't send — try again"
                    },
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.caption1,
                )
            }
        }
    }
}

/** Fire-and-forget send of the spoken text to every connected phone node over the Data Layer. */
private fun sendCapture(
    context: Context,
    text: String,
    onSent: () -> Unit,
    onNoPhone: () -> Unit,
    onError: () -> Unit,
) {
    val bytes = text.toByteArray(Charsets.UTF_8)
    Wearable.getNodeClient(context).connectedNodes
        .addOnSuccessListener { nodes ->
            if (nodes.isEmpty()) {
                onNoPhone()
                return@addOnSuccessListener
            }
            val client = Wearable.getMessageClient(context)
            var remaining = nodes.size
            var anyFailed = false
            nodes.forEach { node ->
                client.sendMessage(node.id, WearContract.PATH_CAPTURE, bytes)
                    .addOnCompleteListener { task ->
                        if (!task.isSuccessful) anyFailed = true
                        if (--remaining == 0) if (anyFailed) onError() else onSent()
                    }
            }
        }
        .addOnFailureListener { onError() }
}
