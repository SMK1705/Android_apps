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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Directions
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import com.rajasudhan.taskmind.data.model.Suggestion
import com.rajasudhan.taskmind.data.source.resolveCallNumber
import com.rajasudhan.taskmind.data.source.transcription.AudioRecorder
import com.rajasudhan.taskmind.ui.common.*
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import java.time.ZoneId

private val ApproveGreen = Color(0xFF2E7D32)
private val RejectRed = Color(0xFFD32F2F)

private val inboxFilters = listOf("All", "Reminders", "To-dos", "Notes")

/** "In 1 hour / This evening / Tomorrow" snooze targets as (label, epochMillis). */
private fun snoozeOptions(): List<Pair<String, Long>> {
    val zone = ZoneId.systemDefault()
    val now = LocalDateTime.now()
    fun millis(dt: LocalDateTime) = dt.atZone(zone).toInstant().toEpochMilli()
    val eveningDay = if (now.hour < 18) now.toLocalDate() else now.toLocalDate().plusDays(1)
    return listOf(
        "In 1 hour" to System.currentTimeMillis() + 60 * 60 * 1000,
        "This evening" to millis(eveningDay.atTime(18, 0)),
        "Tomorrow" to millis(now.toLocalDate().plusDays(1).atTime(9, 0))
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InboxScreen(
    viewModel: InboxViewModel = hiltViewModel()
) {
    // null = first load not finished yet (skeleton); empty list = caught up (empty state).
    val suggestions by viewModel.pendingSuggestions.collectAsState()
    val isRefreshing by viewModel.isRefreshing.collectAsState()
    var showApproveAllDialog by remember { mutableStateOf(false) }
    var overflowMenu by remember { mutableStateOf(false) }
    var selectedFilter by remember { mutableStateOf("All") }
    var showManualDialog by remember { mutableStateOf(false) }
    var manualText by remember { mutableStateOf("") }
    // When approving a dated item that has no time, ask for one before it lands on the calendar.
    var timePickerFor by remember { mutableStateOf<Suggestion?>(null) }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    // ---- Voice capture ----
    val recorder = remember { AudioRecorder(context) }
    var isRecording by remember { mutableStateOf(false) }
    var isProcessingVoice by remember { mutableStateOf(false) }

    // Release the mic (and discard any partial file) if we leave the screen mid-recording.
    DisposableEffect(Unit) { onDispose { recorder.cancel() } }

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
        ) startRecording() else micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
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

    // ---- Review actions (with Undo) ----
    fun showUndo(message: String) {
        scope.launch {
            val r = snackbarHostState.showSnackbar(message, actionLabel = "Undo", duration = SnackbarDuration.Short)
            if (r == SnackbarResult.ActionPerformed) viewModel.undoLast()
        }
    }

    fun doApprove(s: Suggestion) {
        // A dated item with no time: ask before silently filing it as all-day.
        if (s.dueDate != null && s.dueTime == null) timePickerFor = s
        else { viewModel.approveSuggestion(s); showUndo("Approved") }
    }

    fun doReject(s: Suggestion) {
        viewModel.rejectSuggestion(s)
        showUndo("Rejected")
    }

    fun doSnooze(s: Suggestion, until: Long) {
        viewModel.snooze(s, until)
        showUndo("Snoozed")
    }

    val shown = remember(suggestions, selectedFilter) {
        val list = suggestions.orEmpty()
        when (selectedFilter) {
            "Reminders" -> list.filter { it.type == "reminder" }
            "To-dos" -> list.filter { it.type == "todo" }
            "Notes" -> list.filter { it.type == "note" }
            else -> list
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
        val current = suggestions
        when {
            current == null -> SkeletonList(modifier = Modifier.padding(paddingValues))
            current.isEmpty() -> EmptyState(
                modifier = Modifier.padding(paddingValues),
                icon = Icons.Default.Check,
                title = "All caught up",
                subtitle = "New suggestions show up here as they're captured. Pull in recent items or add one yourself.",
                actions = {
                    OutlinedButton(onClick = { viewModel.refreshRecentData() }, enabled = !isRefreshing) {
                        if (isRefreshing) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Default.Refresh, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Refresh")
                        }
                    }
                    OutlinedButton(onClick = { showManualDialog = true }) {
                        Icon(Icons.Default.Add, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Add item")
                    }
                }
            )
            else -> {
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
                            "${current.size} pending",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(onClick = { viewModel.refreshRecentData() }, enabled = !isRefreshing) {
                            if (isRefreshing) {
                                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                            } else {
                                Icon(Icons.Default.Refresh, contentDescription = "Refresh Recent Data")
                            }
                        }
                        IconButton(onClick = { showManualDialog = true }) {
                            Icon(Icons.Default.Add, contentDescription = "Add item manually")
                        }
                        Box {
                            IconButton(onClick = { overflowMenu = true }) {
                                Icon(Icons.Default.MoreVert, contentDescription = "More actions")
                            }
                            DropdownMenu(expanded = overflowMenu, onDismissRequest = { overflowMenu = false }) {
                                DropdownMenuItem(
                                    text = { Text("Approve all") },
                                    onClick = { overflowMenu = false; showApproveAllDialog = true }
                                )
                                DropdownMenuItem(
                                    text = { Text("Reject all") },
                                    onClick = { overflowMenu = false; viewModel.rejectAll() }
                                )
                            }
                        }
                    }
                }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        inboxFilters.forEach { f ->
                            FilterChip(
                                selected = selectedFilter == f,
                                onClick = { selectedFilter = f },
                                label = { Text(f) }
                            )
                        }
                    }
                }
                items(shown, key = { it.id }) { suggestion ->
                    val haptic = LocalHapticFeedback.current
                    val dismissState = rememberSwipeToDismissBoxState(
                        confirmValueChange = { value ->
                            when (value) {
                                SwipeToDismissBoxValue.StartToEnd -> {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    doApprove(suggestion)
                                }
                                SwipeToDismissBoxValue.EndToStart -> {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    doReject(suggestion)
                                }
                                SwipeToDismissBoxValue.Settled -> {}
                            }
                            // Always return false: the data change (approve/reject) removes the card,
                            // and an approve that needs a time keeps the card until the dialog resolves.
                            false
                        }
                    )
                    SwipeToDismissBox(
                        state = dismissState,
                        modifier = Modifier.animateItem(),
                        backgroundContent = {
                            val dir = dismissState.dismissDirection
                            val bg = when (dir) {
                                SwipeToDismissBoxValue.StartToEnd -> ApproveGreen
                                SwipeToDismissBoxValue.EndToStart -> RejectRed
                                else -> Color.Transparent
                            }
                            val icon = when (dir) {
                                SwipeToDismissBoxValue.StartToEnd -> Icons.Default.Check
                                SwipeToDismissBoxValue.EndToStart -> Icons.Default.Close
                                else -> null
                            }
                            val align =
                                if (dir == SwipeToDismissBoxValue.StartToEnd) Alignment.CenterStart else Alignment.CenterEnd
                            Box(
                                Modifier.fillMaxSize().clip(RoundedCornerShape(12.dp)).background(bg)
                                    .padding(horizontal = 24.dp),
                                contentAlignment = align
                            ) {
                                if (icon != null) Icon(icon, contentDescription = null, tint = Color.White)
                            }
                        }
                    ) {
                        SuggestionCard(
                            suggestion = suggestion,
                            onApprove = { doApprove(it) },
                            onReject = { doReject(it) },
                            onEdit = { viewModel.updateSuggestion(it) },
                            onSnooze = { until -> doSnooze(suggestion, until) }
                        )
                    }
                }
            }
            }
        }

        if (showApproveAllDialog) {
            AlertDialog(
                onDismissRequest = { showApproveAllDialog = false },
                title = { Text("Approve all ${suggestions.orEmpty().size}?") },
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

        if (showManualDialog) {
            AlertDialog(
                onDismissRequest = { showManualDialog = false },
                title = { Text("Add an item") },
                text = {
                    OutlinedTextField(
                        value = manualText,
                        onValueChange = { manualText = it },
                        label = { Text("Type a note, task or reminder") },
                        modifier = Modifier.fillMaxWidth()
                    )
                },
                confirmButton = {
                    TextButton(
                        enabled = manualText.isNotBlank(),
                        onClick = {
                            val t = manualText.trim()
                            manualText = ""
                            showManualDialog = false
                            viewModel.addManualEntry(t) { msg -> scope.launch { snackbarHostState.showSnackbar(msg) } }
                        }
                    ) { Text("Add") }
                },
                dismissButton = {
                    TextButton(onClick = { showManualDialog = false; manualText = "" }) { Text("Cancel") }
                }
            )
        }

        timePickerFor?.let { suggestion ->
            ApproveTimePickerDialog(
                onSetTime = { hour, minute ->
                    // Locale.US so digits stay ASCII — the stored value must parse as HH:MM.
                    val time = String.format(java.util.Locale.US, "%02d:%02d", hour, minute)
                    viewModel.approveSuggestion(suggestion.copy(dueTime = time, type = "reminder"))
                    showUndo("Approved")
                    timePickerFor = null
                },
                onKeepAllDay = {
                    viewModel.approveSuggestion(suggestion)
                    showUndo("Approved")
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
    onEdit: (Suggestion) -> Unit,
    onSnooze: (Long) -> Unit
) {
    var isEditing by remember { mutableStateOf(false) }
    var expanded by remember { mutableStateOf(false) }
    var snoozeMenu by remember { mutableStateOf(false) }
    var editedTitle by remember { mutableStateOf(suggestion.extractedTitle) }
    var editedDueDate by remember { mutableStateOf(suggestion.dueDate ?: "") }
    var editedDueTime by remember { mutableStateOf(suggestion.dueTime ?: "") }

    val category = categoryFor(suggestion.type, suggestion.dueDate, suggestion.dueTime)
    val darkFieldStyle = TextStyle(color = onCard())
    // Show the model's one-line summary when present; otherwise fall back to the raw message preview.
    val preview = suggestion.summary.ifBlank { suggestion.rawSnippet }
    val chevronRotation by animateFloatAsState(if (expanded) 180f else 0f, label = "chevron")

    // Quick actions: a number to call (for "call …" items) and a place to navigate to.
    val context = LocalContext.current
    val callNumber by produceState<String?>(null, suggestion) {
        value = resolveCallNumber(
            context, suggestion.extractedTitle, suggestion.summary, suggestion.rawSnippet, suggestion.source
        )
    }
    val place = suggestion.location?.trim()?.ifBlank { null }

    TmCard(
        modifier = Modifier.fillMaxWidth(),
        accent = category.accent(),
        verticalAlignment = Alignment.Top
    ) {
            Column(modifier = Modifier.weight(1f).padding(16.dp)) {
                if (isEditing) {
                    Text(
                        text = "From ${suggestion.source}",
                        style = MaterialTheme.typography.labelSmall,
                        color = onCardMuted()
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
                            Text("Cancel", color = onCardMuted())
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
                    val visual = sourceVisual(suggestion.source)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            visual.icon,
                            contentDescription = visual.label,
                            tint = visual.tint,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        CategoryBadge(category)
                        Spacer(Modifier.width(6.dp))
                        ConfidencePill(suggestion.confidence)
                        if (suggestion.dueDate != null) {
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "${suggestion.dueDate} ${suggestion.dueTime ?: ""}".trim(),
                                style = MaterialTheme.typography.labelMedium,
                                color = category.accent(),
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
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
                                color = onCard()
                            )
                            if (preview.isNotBlank()) {
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    text = preview,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = onCard(),
                                    maxLines = if (expanded) Int.MAX_VALUE else 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = "From ${suggestion.source}",
                                style = MaterialTheme.typography.labelSmall,
                                color = onCardMuted()
                            )
                        }
                        Icon(
                            Icons.Default.ExpandMore,
                            contentDescription = if (expanded) "Collapse" else "Expand",
                            tint = onCardMuted(),
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
                            HorizontalDivider(color = onCardMuted().copy(alpha = 0.25f))
                            Spacer(Modifier.height(8.dp))
                            Text(
                                text = "\"${suggestion.rawSnippet}\"",
                                style = MaterialTheme.typography.bodySmall,
                                fontStyle = FontStyle.Italic,
                                color = onCard()
                            )
                        }
                    }

                    // Quick actions before approving: call the number / get directions to the place.
                    val number = callNumber
                    if (number != null || place != null) {
                        Row(
                            modifier = Modifier.padding(top = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            if (number != null) {
                                TextButton(
                                    onClick = { dialNumber(context, number) },
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Icon(Icons.Default.Call, contentDescription = null, tint = category.accent(), modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Call", color = category.accent())
                                }
                            }
                            if (place != null) {
                                TextButton(
                                    onClick = { openDirections(context, place, null, null) },
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Icon(Icons.Default.Directions, contentDescription = null, tint = category.accent(), modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Directions", color = category.accent())
                                }
                            }
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
                            Box {
                                IconButton(onClick = { snoozeMenu = true }) {
                                    Icon(Icons.Default.Schedule, contentDescription = "Snooze", tint = onCardMuted())
                                }
                                DropdownMenu(expanded = snoozeMenu, onDismissRequest = { snoozeMenu = false }) {
                                    snoozeOptions().forEach { (label, until) ->
                                        DropdownMenuItem(
                                            text = { Text(label) },
                                            onClick = { snoozeMenu = false; onSnooze(until) }
                                        )
                                    }
                                }
                            }
                            IconButton(onClick = { isEditing = true }) {
                                Icon(Icons.Default.Edit, contentDescription = "Edit", tint = onCardMuted())
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
