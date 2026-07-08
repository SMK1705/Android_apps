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
import com.google.android.gms.wearable.CapabilityClient
import com.google.android.gms.wearable.Node
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

/**
 * Sends the spoken text to a paired phone that ACTUALLY has TaskMind installed — discovered via the
 * capture [capability][WearContract.CAPABILITY_PHONE_CAPTURE], not "any connected node". The old code
 * sent to every connected node and reported success on mere transport delivery, so a capture to a node
 * without the app was silently lost while the watch showed "✓ Added to your Inbox" (and two app-bearing
 * nodes duplicated it). [pickCaptureTarget] selects a SINGLE node so a capture lands exactly once.
 */
private fun sendCapture(
    context: Context,
    text: String,
    onSent: () -> Unit,
    onNoPhone: () -> Unit,
    onError: () -> Unit,
) {
    val bytes = text.toByteArray(Charsets.UTF_8)
    Wearable.getCapabilityClient(context)
        .getCapability(WearContract.CAPABILITY_PHONE_CAPTURE, CapabilityClient.FILTER_REACHABLE)
        .addOnSuccessListener { capabilityInfo ->
            val target = pickCaptureTarget(capabilityInfo.nodes)
            if (target == null) {
                onNoPhone() // no reachable phone with TaskMind installed — don't claim it was added
                return@addOnSuccessListener
            }
            Wearable.getMessageClient(context)
                .sendMessage(target.id, WearContract.PATH_CAPTURE, bytes)
                .addOnSuccessListener { onSent() }
                .addOnFailureListener { onError() }
        }
        .addOnFailureListener { onError() }
}

/**
 * Picks the phone node to deliver a capture to: a directly-connected (nearby) one if any, else any
 * reachable node advertising the capture capability, or null when none do. Choosing a SINGLE node (not
 * every connected node) is what keeps one spoken capture from landing in two Inboxes.
 */
internal fun pickCaptureTarget(nodes: Collection<Node>): Node? =
    nodes.firstOrNull { it.isNearby } ?: nodes.firstOrNull()
