package com.rajasudhan.taskmind.ui.ask

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.rajasudhan.taskmind.data.model.Note
import com.rajasudhan.taskmind.ui.bold.BoldPageHeader
import com.rajasudhan.taskmind.ui.theme.BoldOnAccent
import com.rajasudhan.taskmind.ui.theme.BoldTheme
import com.rajasudhan.taskmind.ui.theme.BoldType
import com.rajasudhan.taskmind.ui.theme.ShapeCard
import com.rajasudhan.taskmind.ui.theme.ShapeField

private val EXAMPLES = listOf(
    "What's due this weekend?",
    "Anything overdue?",
    "Show my work tasks",
    "What have I finished this week?",
)

@Composable
fun AskScreen(
    isDark: Boolean = true,
    onToggleTheme: () -> Unit = {},
    onNoteClick: (Int) -> Unit = {},
    viewModel: AskViewModel = hiltViewModel(),
) {
    val c = BoldTheme.colors
    val messages by viewModel.messages.collectAsState()
    val thinking by viewModel.thinking.collectAsState()
    val onDeviceEngine by viewModel.onDeviceEngine.collectAsState()
    var input by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    // Re-read the effective engine on resume so a change made in Settings is reflected in the honest
    // privacy copy (#197) when the user returns to this tab.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) viewModel.refreshEngine()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Keep the latest turn in view as the conversation and the thinking indicator grow.
    LaunchedEffect(messages.size, thinking) {
        val count = messages.size + if (thinking) 1 else 0
        if (count > 0) listState.animateScrollToItem(count - 1)
    }

    Column(Modifier.fillMaxSize().background(c.screen).imePadding()) {
        Column(Modifier.padding(start = 22.dp, end = 22.dp, top = 14.dp)) {
            BoldPageHeader(
                title = "Ask",
                subtitle = "Search and recall across everything you've saved",
                isDark = isDark,
                onToggleTheme = onToggleTheme
            )
            Spacer(Modifier.height(6.dp))
        }

        Box(Modifier.weight(1f).fillMaxWidth()) {
            if (messages.isEmpty() && !thinking) {
                AskEmptyState(onDevice = onDeviceEngine, onExample = { viewModel.ask(it) })
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(messages) { msg -> AskBubble(msg, onNoteClick) }
                    if (thinking) item { ThinkingBubble() }
                }
            }
        }

        AskInput(
            value = input,
            onChange = { input = it },
            enabled = !thinking,
            onSend = { if (input.isNotBlank()) { viewModel.ask(input); input = "" } }
        )
    }
}

@Composable
private fun AskBubble(msg: AskMessage, onNoteClick: (Int) -> Unit) {
    val c = BoldTheme.colors
    if (msg.fromUser) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            Box(
                Modifier.widthIn(max = 300.dp).clip(RoundedCornerShape(14.dp)).background(c.accent)
                    .padding(horizontal = 14.dp, vertical = 10.dp)
            ) {
                Text(msg.text, style = BoldType.body.copy(fontSize = 14.5.sp, lineHeight = 20.sp), color = BoldOnAccent)
            }
        }
    } else {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
                Box(
                    Modifier.widthIn(max = 320.dp).clip(RoundedCornerShape(14.dp)).background(c.surface)
                        .border(1.dp, c.line, RoundedCornerShape(14.dp)).padding(horizontal = 14.dp, vertical = 10.dp)
                ) {
                    Text(msg.text, style = BoldType.body.copy(fontSize = 14.5.sp, lineHeight = 20.sp), color = c.ink)
                }
            }
            msg.result?.notes?.forEach { note ->
                AskResultCard(note) { onNoteClick(note.id) }
            }
        }
    }
}

/** A compact result card for the chat — a self-contained citation the user can tap to open. */
@Composable
private fun AskResultCard(note: Note, onClick: () -> Unit) {
    val c = BoldTheme.colors
    val due = listOfNotNull(note.dueDate, note.dueTime).joinToString(" · ")
    Column(
        Modifier.fillMaxWidth().clip(ShapeCard).background(c.surface).border(1.dp, c.line, ShapeCard)
            .clickable(onClick = onClick, onClickLabel = "Open note", role = Role.Button)
            .padding(horizontal = 13.dp, vertical = 11.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                note.type.replace('_', ' ').uppercase(),
                style = BoldType.detailMeta.copy(fontSize = 9.sp, letterSpacing = 0.5.sp), color = c.accent
            )
            if (due.isNotBlank()) Text(due, style = BoldType.detailMeta.copy(fontSize = 10.sp), color = c.muted)
            Spacer(Modifier.weight(1f))
            Text(
                note.source, style = BoldType.detailMeta.copy(fontSize = 9.5.sp), color = c.ink3,
                maxLines = 1, overflow = TextOverflow.Ellipsis
            )
        }
        Text(note.title, style = BoldType.sugTitle.copy(fontSize = 14.5.sp, lineHeight = 19.sp), color = c.ink)
    }
}

@Composable
private fun ThinkingBubble() {
    val c = BoldTheme.colors
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
        Row(
            Modifier.clip(RoundedCornerShape(14.dp)).background(c.surface).border(1.dp, c.line, RoundedCornerShape(14.dp))
                .padding(horizontal = 14.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)
        ) {
            CircularProgressIndicator(Modifier.size(14.dp), color = c.accent, strokeWidth = 2.dp)
            Text("Thinking…", style = BoldType.detailMeta.copy(fontSize = 12.sp), color = c.muted)
        }
    }
}

@Composable
private fun AskEmptyState(onDevice: Boolean, onExample: (String) -> Unit) {
    val c = BoldTheme.colors
    Column(
        Modifier.fillMaxSize().padding(horizontal = 26.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            Modifier.size(56.dp).clip(RoundedCornerShape(16.dp)).background(c.surface).border(1.dp, c.line, RoundedCornerShape(16.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Outlined.AutoAwesome, contentDescription = null, tint = c.accent, modifier = Modifier.size(26.dp))
        }
        Spacer(Modifier.height(16.dp))
        Text("Ask your second brain", style = BoldType.emptyTitle.copy(fontSize = 22.sp), color = c.ink)
        Spacer(Modifier.height(6.dp))
        Text(
            // Honest about where the question is interpreted (#197): the saved items are always local,
            // but the utterance is read by Gemma on-device only when that engine is effective.
            if (onDevice) "Answered from your own saved items — read on your phone, nothing leaves the device."
            else "Answered from your own saved items — your question is read by your cloud engine (Gemini).",
            style = BoldType.body.copy(fontSize = 14.sp), color = c.muted,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
        Spacer(Modifier.height(18.dp))
        EXAMPLES.forEach { ex ->
            Box(
                Modifier.fillMaxWidth().padding(vertical = 4.dp).clip(RoundedCornerShape(12.dp))
                    .background(c.surface2).clickable(onClickLabel = ex, role = Role.Button) { onExample(ex) }
                    .padding(horizontal = 14.dp, vertical = 12.dp)
            ) {
                Text("“$ex”", style = BoldType.body.copy(fontSize = 13.5.sp), color = c.ink2)
            }
        }
    }
}

@Composable
private fun AskInput(value: String, onChange: (String) -> Unit, enabled: Boolean, onSend: () -> Unit) {
    val c = BoldTheme.colors
    Row(
        Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            Modifier.weight(1f).heightIn(min = 46.dp).clip(ShapeField).background(c.surface)
                .border(1.dp, c.line, ShapeField).padding(horizontal = 14.dp, vertical = 12.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            if (value.isEmpty()) Text("Ask about your tasks and notes…", style = BoldType.searchInput.copy(fontSize = 14.5.sp), color = c.ink3)
            BasicTextField(
                value = value, onValueChange = onChange,
                textStyle = BoldType.searchInput.copy(fontSize = 14.5.sp, color = c.ink),
                cursorBrush = SolidColor(c.accent),
                modifier = Modifier.fillMaxWidth().semantics { contentDescription = "Ask TaskMind" }
            )
        }
        val active = enabled && value.isNotBlank()
        Box(
            Modifier.size(46.dp).clip(RoundedCornerShape(13.dp)).background(if (active) c.accent else c.surface2)
                .clickable(enabled = active, onClickLabel = "Send", role = Role.Button, onClick = onSend),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null, tint = if (active) BoldOnAccent else c.ink3, modifier = Modifier.size(20.dp))
        }
    }
}
