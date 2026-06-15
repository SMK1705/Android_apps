package com.rajasudhan.taskmind.ui.guide

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Note
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.WavingHand
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.launch

private data class GuidePage(val icon: ImageVector, val title: String, val body: String)

private val guidePages = listOf(
    GuidePage(
        Icons.Filled.WavingHand, "Welcome to TaskMind",
        "Your private, on-device assistant. It identifies action items in your messages, calls, email " +
            "and recordings — and saves nothing until you approve it."
    ),
    GuidePage(
        Icons.Filled.Tune, "Choose your sources",
        "Open the Sources tab and enable what TaskMind may watch — SMS, Notifications, Gmail and more. " +
            "Each requests its permission when it is turned on."
    ),
    GuidePage(
        Icons.Filled.Inbox, "Review your Inbox",
        "Suggestions arrive as cards with a short summary. Tap a card to expand the full text, then " +
            "Approve, Edit or Reject — or use Approve all to review them quickly."
    ),
    GuidePage(
        Icons.Filled.Mic, "Add by voice",
        "Tap the microphone in the Inbox to dictate a quick note. It is transcribed on your device and " +
            "added as a suggestion for your review."
    ),
    GuidePage(
        Icons.AutoMirrored.Filled.Note, "Find it in Notes",
        "Everything you approve is kept in the Notes tab, organised by type. Tap any note to open its " +
            "full detail — body, source and due date."
    ),
    GuidePage(
        Icons.Filled.Lock, "Private by design",
        "Everything runs on your phone, behind a biometric lock. Open Settings → Data Egress at any " +
            "time to confirm nothing has left your device. Reopen this guide from the help icon in the top bar."
    ),
)

/** First-run (and re-openable) walkthrough of the core flow. [onDismiss] both closes and marks seen. */
@Composable
fun GuideOverlay(onDismiss: () -> Unit) {
    val pagerState = rememberPagerState(pageCount = { guidePages.size })
    val scope = rememberCoroutineScope()
    val isLast = pagerState.currentPage == guidePages.lastIndex

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            modifier = Modifier.fillMaxWidth(0.92f).fillMaxHeight(0.80f),
            shape = MaterialTheme.shapes.extraLarge,
            tonalElevation = 6.dp
        ) {
            Column(modifier = Modifier.fillMaxSize().padding(20.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text("Skip") }
                }

                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.weight(1f).fillMaxWidth()
                ) { page ->
                    val p = guidePages[page]
                    Column(
                        modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.size(96.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    p.icon,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.size(44.dp)
                                )
                            }
                        }
                        Spacer(Modifier.height(24.dp))
                        Text(
                            p.title,
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.SemiBold,
                            textAlign = TextAlign.Center
                        )
                        Spacer(Modifier.height(12.dp))
                        Text(
                            p.body,
                            style = MaterialTheme.typography.bodyLarge,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Page indicator dots.
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                    horizontalArrangement = Arrangement.Center
                ) {
                    repeat(guidePages.size) { i ->
                        val selected = i == pagerState.currentPage
                        Box(
                            modifier = Modifier
                                .padding(horizontal = 4.dp)
                                .size(if (selected) 10.dp else 8.dp)
                                .clip(CircleShape)
                                .background(
                                    if (selected) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.outlineVariant
                                )
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(
                        onClick = {
                            scope.launch {
                                pagerState.animateScrollToPage((pagerState.currentPage - 1).coerceAtLeast(0))
                            }
                        },
                        enabled = pagerState.currentPage > 0
                    ) { Text("Back") }

                    Button(onClick = {
                        if (isLast) onDismiss()
                        else scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
                    }) { Text(if (isLast) "Get started" else "Next") }
                }
            }
        }
    }
}
