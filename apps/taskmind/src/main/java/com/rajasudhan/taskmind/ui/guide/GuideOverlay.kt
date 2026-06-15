package com.rajasudhan.taskmind.ui.guide

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.launch

private data class GuidePage(val emoji: String, val title: String, val body: String)

private val guidePages = listOf(
    GuidePage(
        "👋", "Welcome to TaskMind",
        "Your private, on-device assistant. It spots action items in your messages, calls, email and " +
            "recordings — and saves nothing until you approve it."
    ),
    GuidePage(
        "🎚️", "Choose your sources",
        "Open the Sources tab and turn on what TaskMind should watch — SMS, Notifications, Gmail and " +
            "more. Grant each permission when asked."
    ),
    GuidePage(
        "📥", "Review your Inbox",
        "Suggestions arrive as cards with a short summary. Tap a card to expand the full text, then " +
            "Approve ✓, Edit ✎ or Reject ✗ — or use Approve all to clear them fast."
    ),
    GuidePage(
        "🎤", "Add by voice",
        "Tap the mic button in the Inbox to speak a quick note. It's transcribed on-device and added " +
            "as a suggestion for you to approve."
    ),
    GuidePage(
        "🗂️", "Find it in Notes",
        "Everything you approve lands in the Notes tab, color-coded by type. Tap any note to open its " +
            "full detail — body, source and due date."
    ),
    GuidePage(
        "🔒", "Private by design",
        "It all runs on your phone, locked behind biometrics. Open Settings → Data Egress any time to " +
            "confirm nothing has left your device."
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
            modifier = Modifier.fillMaxWidth(0.92f).fillMaxHeight(0.78f),
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
                        Text(p.emoji, style = MaterialTheme.typography.displayMedium)
                        Spacer(Modifier.height(20.dp))
                        Text(
                            p.title,
                            style = MaterialTheme.typography.headlineSmall,
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
