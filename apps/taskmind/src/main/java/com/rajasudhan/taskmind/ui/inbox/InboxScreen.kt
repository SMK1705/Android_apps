package com.rajasudhan.taskmind.ui.inbox

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import com.rajasudhan.taskmind.data.model.Suggestion
import com.rajasudhan.taskmind.data.source.transcription.AudioRecorder
import com.rajasudhan.taskmind.ui.common.*
import kotlinx.coroutines.launch

private val ApproveGreen = Color(0xFF2E7D32)
private val RejectRed = Color(0xFFD32F2F)

@Composable
fun InboxScreen(
    viewModel: InboxViewModel = hiltViewModel()
) {
    val suggestions by viewModel.pendingSuggestions.collectAsState()
    val isRefreshing by viewModel.isRefreshing.collectAsState()
    var showApproveAllDialog by remember { mutableStateOf(false) }
    // When approving a dated item that has no time, ask for one before it lands on the calendar.
    var timePickerFor by remember { mutableStateOf<Suggestion?>(null) }

    // ---- Voice capture ----
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val recorder = remember { AudioRecorder(context) }
    var isRecording by remember { mutableStateOf(false) }
    var isProcessingVoice by remember { mutableStateOf(false) }

    // Release the mic (and discard any partial file) if we leave the screen mid-recording.
    DisposableEffect(Unit) {
        onDispose { recorder.cancel() }
    }

    fun startRecording() {
        if (recorder.start()) isRecording = true
        else scope.launch { snackbarHostState.showSnackbar("Couldn't start recording.") }
    }

    val micPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) startRecording()
        else scope.launch { snackbarHostState.showSnackbar("Microphone permission is needed for voice input.") }
    }

    fun onMicClick() {
        if (isProcessingVoice) return
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)
            == PackageManager.PERMISSION_GRANTED
        ) {
            startRecording()
        } else {
            micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    fun finishRecording() {
        val file = recorder.stop()
        isRecording = false
        if (file == null) {
            scope.launch { snackbarHostState.showSnackbar("Didn't catch that — please try again.") }
            return
        }
        isProcessingVoice = true
        viewModel.addVoiceNote(file) { message ->
            isProcessingVoice = false
            scope.launch { snackbarHostState.showSnackbar(message) }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            FloatingActionButton(onClick = { onMicClick() }) {
                if (isProcessingVoice) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        strokeWidth = 2.dp
                    )
                } else {
                    Icon(Icons.Default.Mic, contentDescription = "Add by voice")
                }
            }
        }
    ) { paddingValues ->
        if (suggestions.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().padding(paddingValues), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(text = "No pending suggestions. All caught up!", style = MaterialTheme.typography.bodyLarge)
                    Spacer(Modifier.height(12.dp))
                    OutlinedButton(onClick = { viewModel.refreshRecentData() }, enabled = !isRefreshing) {
                        if (isRefreshing) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Default.Refresh, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Refresh")
                        }
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(paddingValues),
                // Extra bottom inset so the last card's actions always clear the floating mic button.
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "${suggestions.size} pending",
                            style = MaterialTheme.typography.labelLarge,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(onClick = { viewModel.refreshRecentData() }, enabled = !isRefreshing) {
                            if (isRefreshing) {
                                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                            } else {
                                Icon(Icons.Default.Refresh, contentDescription = "Refresh Recent Data")
                            }
                        }
                        TextButton(onClick = { viewModel.rejectAll() }) {
                            Text("Reject all", color = RejectRed)
                        }
                        Button(onClick = { showApproveAllDialog = true }) {
                            Text("Approve all")
                        }
                    }
                }
                items(suggestions, key = { it.id }) { suggestion ->
                    SuggestionCard(
                        suggestion = suggestion,
                        onApprove = { s ->
                            // A dated item with no time: ask before silently filing it as all-day.
                            if (s.dueDate != null && s.dueTime == null) timePickerFor = s
                            else viewModel.approveSuggestion(s)
                        },
                        onReject = { viewModel.rejectSuggestion(it) },
                        onEdit = { viewModel.updateSuggestion(it) }
                    )
                }
            }
        }

        if (showApproveAllDialog) {
            AlertDialog(
                onDismissRequest = { showApproveAllDialog = false },
                title = { Text("Approve all ${suggestions.size}?") },
                text = { Text("This saves every pending suggestion and schedules any reminders/calendar events.") },
                confirmButton = {
                    TextButton(onClick = {
                        viewModel.approveAll()
                        showApproveAllDialog = false
                    }) { Text("Approve all") }
                },
                dismissButton = {
                    TextButton(onClick = { showApproveAllDialog = false }) { Text("Cancel") }
                }
            )
        }

        timePickerFor?.let { suggestion ->
            ApproveTimePickerDialog(
                onSetTime = { hour, minute ->
                    // Locale.US so digits stay ASCII — the stored value must parse as HH:MM.
                    val time = String.format(java.util.Locale.US, "%02d:%02d", hour, minute)
                    // Filling in a time promotes it to a timed reminder (alarm + timed calendar event).
                    viewModel.approveSuggestion(suggestion.copy(dueTime = time, type = "reminder"))
                    timePickerFor = null
                },
                onKeepAllDay = {
                    viewModel.approveSuggestion(suggestion)
                    timePickerFor = null
                },
                onDismiss = { timePickerFor = null }
            )
        }

        if (isRecording) {
            AlertDialog(
                onDismissRequest = { recorder.cancel(); isRecording = false },
                title = { Text("Listening…") },
                text = {
                    Text("Speak your note, then tap Stop. It's transcribed on-device and added to your inbox to review.")
                },
                confirmButton = {
                    Button(onClick = { finishRecording() }) {
                        Icon(Icons.Default.Stop, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Stop")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { recorder.cancel(); isRecording = false }) { Text("Cancel") }
                }
            )
        }
    }
}

/**
 * Asked when approving a dated item that has no time. "Set time" turns it into a timed reminder;
 * "Keep as all-day" approves it unchanged (the all-day calendar entry).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ApproveTimePickerDialog(
    onSetTime: (Int, Int) -> Unit,
    onKeepAllDay: () -> Unit,
    onDismiss: () -> Unit
) {
    val state = rememberTimePickerState(initialHour = 9, initialMinute = 0, is24Hour = false)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Set a time?") },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("No time was detected. Pick a time to add a timed reminder, or keep it as an all-day item.")
                Spacer(Modifier.height(16.dp))
                // Compact text-entry variant keeps the dialog from overflowing on small screens.
                TimeInput(state = state)
            }
        },
        confirmButton = {
            TextButton(onClick = { onSetTime(state.hour, state.minute) }) { Text("Set time") }
        },
        dismissButton = {
            TextButton(onClick = onKeepAllDay) { Text("Keep as all-day") }
        }
    )
}

@Composable
fun SuggestionCard(
    suggestion: Suggestion,
    onApprove: (Suggestion) -> Unit,
    onReject: (Suggestion) -> Unit,
    onEdit: (Suggestion) -> Unit
) {
    var isEditing by remember { mutableStateOf(false) }
    var expanded by remember { mutableStateOf(false) }
    var editedTitle by remember { mutableStateOf(suggestion.extractedTitle) }
    var editedDueDate by remember { mutableStateOf(suggestion.dueDate ?: "") }
    var editedDueTime by remember { mutableStateOf(suggestion.dueTime ?: "") }

    val category = categoryFor(suggestion.type, suggestion.dueDate, suggestion.dueTime)
    val darkFieldStyle = TextStyle(color = OnLightCard)
    // Show the model's one-line summary when present; otherwise fall back to the raw message preview.
    val preview = suggestion.summary.ifBlank { suggestion.rawSnippet }
    val chevronRotation by animateFloatAsState(if (expanded) 180f else 0f, label = "chevron")

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = category.container),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(modifier = Modifier.height(IntrinsicSize.Min)) {
            // Colored accent bar on the left edge.
            Box(modifier = Modifier.width(6.dp).fillMaxHeight().background(category.accent))

            Column(modifier = Modifier.weight(1f).padding(16.dp)) {
                if (isEditing) {
                    Text(
                        text = "From ${suggestion.source}",
                        style = MaterialTheme.typography.labelSmall,
                        color = OnLightCardMuted
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = editedTitle,
                        onValueChange = { editedTitle = it },
                        label = { Text("Title") },
                        textStyle = darkFieldStyle,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = editedDueDate,
                            onValueChange = { editedDueDate = it },
                            label = { Text("Date (YYYY-MM-DD)") },
                            textStyle = darkFieldStyle,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = editedDueTime,
                            onValueChange = { editedDueTime = it },
                            label = { Text("Time (HH:MM)") },
                            textStyle = darkFieldStyle,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(onClick = { isEditing = false }) {
                            Text("Cancel", color = OnLightCardMuted)
                        }
                        Button(onClick = {
                            val updated = suggestion.copy(
                                extractedTitle = editedTitle,
                                dueDate = editedDueDate.ifBlank { null },
                                dueTime = editedDueTime.ifBlank { null }
                            )
                            onEdit(updated)
                            isEditing = false
                        }) {
                            Text("Save")
                        }
                    }
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CategoryBadge(category)
                        if (suggestion.dueDate != null) {
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "${suggestion.dueDate} ${suggestion.dueTime ?: ""}".trim(),
                                style = MaterialTheme.typography.labelMedium,
                                color = category.accent,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                    Spacer(Modifier.height(8.dp))

                    // Heading + one-line summary + source. Tap anywhere here to expand the full message.
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable { expanded = !expanded },
                        verticalAlignment = Alignment.Top
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = suggestion.extractedTitle,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = OnLightCard
                            )
                            if (preview.isNotBlank()) {
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    text = preview,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = OnLightCard,
                                    maxLines = if (expanded) Int.MAX_VALUE else 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = "From ${suggestion.source}",
                                style = MaterialTheme.typography.labelSmall,
                                color = OnLightCardMuted
                            )
                        }
                        Icon(
                            Icons.Default.ExpandMore,
                            contentDescription = if (expanded) "Collapse" else "Expand",
                            tint = OnLightCardMuted,
                            modifier = Modifier.rotate(chevronRotation)
                        )
                    }

                    // Full original message, revealed with a smooth expand/collapse animation.
                    AnimatedVisibility(
                        visible = expanded,
                        enter = expandVertically() + fadeIn(),
                        exit = shrinkVertically() + fadeOut()
                    ) {
                        Column {
                            Spacer(Modifier.height(8.dp))
                            HorizontalDivider(color = OnLightCardMuted.copy(alpha = 0.25f))
                            Spacer(Modifier.height(8.dp))
                            Text(
                                text = "\"${suggestion.rawSnippet}\"",
                                style = MaterialTheme.typography.bodySmall,
                                fontStyle = FontStyle.Italic,
                                color = OnLightCard
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = { onReject(suggestion) }) {
                            Icon(Icons.Default.Close, contentDescription = "Reject", tint = RejectRed)
                        }
                        Row {
                            IconButton(onClick = { isEditing = true }) {
                                Icon(Icons.Default.Edit, contentDescription = "Edit", tint = OnLightCardMuted)
                            }
                            IconButton(onClick = { onApprove(suggestion) }) {
                                Icon(Icons.Default.Check, contentDescription = "Approve", tint = ApproveGreen)
                            }
                        }
                    }
                }
            }
        }
    }
}
