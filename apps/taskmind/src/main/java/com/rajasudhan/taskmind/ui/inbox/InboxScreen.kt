package com.rajasudhan.taskmind.ui.inbox

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Call
import androidx.compose.material.icons.outlined.CleaningServices
import androidx.compose.material.icons.outlined.Directions
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import com.rajasudhan.taskmind.data.model.Suggestion
import com.rajasudhan.taskmind.data.source.resolveCallNumber
import com.rajasudhan.taskmind.data.source.transcription.AudioRecorder
import com.rajasudhan.taskmind.ui.bold.*
import com.rajasudhan.taskmind.ui.common.SkeletonList
import com.rajasudhan.taskmind.ui.common.dialNumber
import com.rajasudhan.taskmind.ui.common.openDirections
import com.rajasudhan.taskmind.ui.theme.BoldOnAccent
import com.rajasudhan.taskmind.ui.theme.BoldTheme
import com.rajasudhan.taskmind.ui.theme.BoldType
import com.rajasudhan.taskmind.ui.theme.ShapeCard
import com.rajasudhan.taskmind.ui.theme.ShapeField
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import java.time.ZoneId

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

/** Friendly mono detail for a row footer, e.g. "Today · 6:00 PM" or "no date". */
private fun rowDetail(s: Suggestion): String {
    val date = s.dueDate
    if (date.isNullOrBlank()) return "no date set"
    return listOfNotNull(date, s.dueTime).joinToString(" · ")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InboxScreen(
    viewModel: InboxViewModel = hiltViewModel()
) {
    val c = BoldTheme.colors
    // null = first load not finished yet (skeleton); empty list = caught up (empty state).
    val suggestions by viewModel.pendingSuggestions.collectAsState()
    val isRefreshing by viewModel.isRefreshing.collectAsState()
    var showApproveAllDialog by remember { mutableStateOf(false) }
    var overflowMenu by remember { mutableStateOf(false) }
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
        if (s.dueDate != null && s.dueTime == null) timePickerFor = s
        else { viewModel.approveSuggestion(s); showUndo("Kept") }
    }

    fun doReject(s: Suggestion) {
        viewModel.rejectSuggestion(s)
        showUndo("Dismissed")
    }

    fun doSnooze(s: Suggestion, until: Long) {
        viewModel.snooze(s, until)
        showUndo("Snoozed")
    }

    Scaffold(
        containerColor = c.screen,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { onMicClick() },
                containerColor = c.accent,
                contentColor = BoldOnAccent
            ) {
                if (isProcessingVoice) {
                    CircularProgressIndicator(modifier = Modifier.size(22.dp), color = BoldOnAccent, strokeWidth = 2.dp)
                } else {
                    Icon(Icons.Default.Mic, contentDescription = "Add by voice")
                }
            }
        }
    ) { paddingValues ->
        val current = suggestions
        when {
            current == null -> SkeletonList(modifier = Modifier.padding(paddingValues))
            current.isEmpty() -> InboxEmpty(
                modifier = Modifier.padding(paddingValues),
                isRefreshing = isRefreshing,
                onRefresh = { viewModel.refreshRecentData() },
                onAdd = { showManualDialog = true }
            )
            else -> {
                val noiseCount = current.count { it.confidence < 0.5 }
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(paddingValues),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 96.dp),
                    verticalArrangement = Arrangement.spacedBy(11.dp)
                ) {
                    item {
                        InboxHeader(
                            count = current.size,
                            overflowExpanded = overflowMenu,
                            onOverflowToggle = { overflowMenu = it },
                            isRefreshing = isRefreshing,
                            onRefresh = { viewModel.refreshRecentData() },
                            onAdd = { showManualDialog = true },
                            onApproveAll = { showApproveAllDialog = true },
                            onRejectAll = { viewModel.rejectAll() }
                        )
                    }
                    if (noiseCount > 0) {
                        item { SweepBanner(noiseCount = noiseCount, onSweep = {
                            viewModel.sweepNoise()
                            showUndo("$noiseCount noise item${if (noiseCount == 1) "" else "s"} swept")
                        }) }
                    }
                    items(current, key = { it.id }) { suggestion ->
                        val haptic = LocalHapticFeedback.current
                        val dismissState = rememberSwipeToDismissBoxState(
                            confirmValueChange = { value ->
                                when (value) {
                                    SwipeToDismissBoxValue.StartToEnd -> {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress); doApprove(suggestion)
                                    }
                                    SwipeToDismissBoxValue.EndToStart -> {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress); doReject(suggestion)
                                    }
                                    SwipeToDismissBoxValue.Settled -> {}
                                }
                                false
                            }
                        )
                        SwipeToDismissBox(
                            state = dismissState,
                            modifier = Modifier.animateItem(),
                            backgroundContent = { SwipeReveal(dismissState.dismissDirection) }
                        ) {
                            BoldSuggestionCard(
                                suggestion = suggestion,
                                onApprove = { doApprove(it) },
                                onReject = { doReject(it) },
                                onEdit = { viewModel.updateSuggestion(it) },
                                onSnooze = { until -> doSnooze(suggestion, until) }
                            )
                        }
                    }
                    item {
                        Text(
                            "swipe → keep · ← dismiss · tap to edit",
                            style = BoldType.hint,
                            color = c.ink3,
                            modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
            }
        }

        if (showApproveAllDialog) {
            AlertDialog(
                onDismissRequest = { showApproveAllDialog = false },
                title = { Text("Keep all ${suggestions.orEmpty().size}?") },
                text = { Text("This saves every pending suggestion and schedules any reminders/calendar events.") },
                confirmButton = {
                    TextButton(onClick = { viewModel.approveAll(); showApproveAllDialog = false }) { Text("Keep all") }
                },
                dismissButton = { TextButton(onClick = { showApproveAllDialog = false }) { Text("Cancel") } }
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
                dismissButton = { TextButton(onClick = { showManualDialog = false; manualText = "" }) { Text("Cancel") } }
            )
        }

        timePickerFor?.let { suggestion ->
            ApproveTimePickerDialog(
                onSetTime = { hour, minute ->
                    val time = String.format(java.util.Locale.US, "%02d:%02d", hour, minute)
                    viewModel.approveSuggestion(suggestion.copy(dueTime = time, type = "reminder"))
                    showUndo("Kept")
                    timePickerFor = null
                },
                onKeepAllDay = {
                    viewModel.approveSuggestion(suggestion)
                    showUndo("Kept")
                    timePickerFor = null
                },
                onDismiss = { timePickerFor = null }
            )
        }

        if (isRecording) {
            AlertDialog(
                onDismissRequest = { recorder.cancel(); isRecording = false },
                title = { Text("Listening…") },
                text = { Text("Speak your note, then tap Stop. It's transcribed on-device and added to your inbox to review.") },
                confirmButton = {
                    Button(onClick = { finishRecording() }) {
                        Icon(Icons.Default.Stop, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Stop")
                    }
                },
                dismissButton = { TextButton(onClick = { recorder.cancel(); isRecording = false }) { Text("Cancel") } }
            )
        }
    }
}

@Composable
private fun InboxHeader(
    count: Int,
    overflowExpanded: Boolean,
    onOverflowToggle: (Boolean) -> Unit,
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    onAdd: () -> Unit,
    onApproveAll: () -> Unit,
    onRejectAll: () -> Unit,
) {
    val c = BoldTheme.colors
    Column(Modifier.fillMaxWidth().padding(top = 6.dp, bottom = 4.dp)) {
        BoldScreenHeader(
            eyebrow = "Today · On-device",
            title = "Inbox",
            trailing = {
                Row(verticalAlignment = Alignment.Bottom) {
                    Box {
                        IconButton(onClick = { onOverflowToggle(true) }, modifier = Modifier.size(28.dp)) {
                            if (isRefreshing) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = c.ink3)
                            else Icon(Icons.Default.MoreVert, contentDescription = "More actions", tint = c.ink3)
                        }
                        DropdownMenu(expanded = overflowExpanded, onDismissRequest = { onOverflowToggle(false) }) {
                            DropdownMenuItem(text = { Text("Add item") }, onClick = { onOverflowToggle(false); onAdd() })
                            DropdownMenuItem(text = { Text("Refresh") }, onClick = { onOverflowToggle(false); onRefresh() })
                            DropdownMenuItem(text = { Text("Keep all") }, onClick = { onOverflowToggle(false); onApproveAll() })
                            DropdownMenuItem(text = { Text("Dismiss all") }, onClick = { onOverflowToggle(false); onRejectAll() })
                        }
                    }
                    Spacer(Modifier.width(8.dp))
                    Column(horizontalAlignment = Alignment.End) {
                        Text("$count", style = BoldType.deckCount, color = c.ink)
                        Text("TO REVIEW", style = BoldType.deckCountLabel, color = c.ink3)
                    }
                }
            }
        )
    }
}

@Composable
private fun SweepBanner(noiseCount: Int, onSweep: () -> Unit) {
    val c = BoldTheme.colors
    val label = buildAnnotatedString {
        withStyle(SpanStyle(color = c.ink, fontWeight = FontWeight.Bold)) { append("$noiseCount likely noise") }
        withStyle(SpanStyle(color = c.ink2)) { append(" detected") }
    }
    BoldCard(modifier = Modifier.fillMaxWidth(), shape = ShapeField, onClick = onSweep) {
        Row(
            Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                Modifier.size(26.dp).clip(RoundedCornerShape(8.dp)).background(c.amberSoft),
                contentAlignment = Alignment.Center
            ) { Icon(Icons.Outlined.CleaningServices, contentDescription = null, tint = c.amber, modifier = Modifier.size(15.dp)) }
            Text(label, style = BoldType.body, modifier = Modifier.weight(1f))
            Text("SWEEP →", style = BoldType.confBadge.copy(fontWeight = FontWeight.Bold), color = c.accent)
        }
    }
}

@Composable
private fun SwipeReveal(dir: SwipeToDismissBoxValue) {
    val c = BoldTheme.colors
    val keep = dir == SwipeToDismissBoxValue.StartToEnd
    val skip = dir == SwipeToDismissBoxValue.EndToStart
    val bg = when {
        keep -> c.keepBg
        skip -> c.skipBg
        else -> Color.Transparent
    }
    Box(
        Modifier.fillMaxSize().clip(ShapeCard).background(bg).padding(horizontal = 20.dp),
        contentAlignment = if (keep) Alignment.CenterStart else Alignment.CenterEnd
    ) {
        if (keep) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.Check, contentDescription = null, tint = c.accent, modifier = Modifier.size(18.dp))
                Text("Keep", style = BoldType.button, color = c.accent)
            }
        } else if (skip) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Dismiss", style = BoldType.button, color = c.skip)
                Icon(Icons.Default.Close, contentDescription = null, tint = c.skip, modifier = Modifier.size(18.dp))
            }
        }
    }
}

@Composable
private fun InboxEmpty(modifier: Modifier, isRefreshing: Boolean, onRefresh: () -> Unit, onAdd: () -> Unit) {
    val c = BoldTheme.colors
    Column(
        modifier.fillMaxSize().padding(horizontal = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            Modifier.size(80.dp).clip(RoundedCornerShape(24.dp)).background(c.accent),
            contentAlignment = Alignment.Center
        ) { Icon(Icons.Outlined.AutoAwesome, contentDescription = null, tint = BoldOnAccent, modifier = Modifier.size(34.dp)) }
        Spacer(Modifier.height(22.dp))
        Text("All clear.", style = BoldType.emptyTitle, color = c.ink)
        Spacer(Modifier.height(8.dp))
        Text(
            "You've triaged everything. New action items surface here the moment TaskMind reads them on your phone.",
            style = BoldType.body,
            color = c.ink2,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
        Spacer(Modifier.height(22.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            BoldPillButton(text = if (isRefreshing) "Refreshing…" else "Refresh", onClick = onRefresh, icon = Icons.Default.Refresh, filled = false)
            BoldPillButton(text = "Add item", onClick = onAdd, icon = Icons.Default.Add, filled = true)
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
                TimeInput(state = state)
            }
        },
        confirmButton = { TextButton(onClick = { onSetTime(state.hour, state.minute) }) { Text("Set time") } },
        dismissButton = { TextButton(onClick = onKeepAllDay) { Text("Keep as all-day") } }
    )
}

@Composable
private fun BoldSuggestionCard(
    suggestion: Suggestion,
    onApprove: (Suggestion) -> Unit,
    onReject: (Suggestion) -> Unit,
    onEdit: (Suggestion) -> Unit,
    onSnooze: (Long) -> Unit
) {
    val c = BoldTheme.colors
    var expanded by remember { mutableStateOf(false) }
    var isEditing by remember { mutableStateOf(false) }
    var snoozeMenu by remember { mutableStateOf(false) }
    var editedTitle by remember { mutableStateOf(suggestion.extractedTitle) }
    var editedDueDate by remember { mutableStateOf(suggestion.dueDate ?: "") }
    var editedDueTime by remember { mutableStateOf(suggestion.dueTime ?: "") }

    val kind = boldKindFor(suggestion.type, suggestion.dueDate != null)
    val preview = suggestion.summary.ifBlank { suggestion.rawSnippet }

    val context = LocalContext.current
    val callNumber by produceState<String?>(null, suggestion) {
        value = resolveCallNumber(context, suggestion.extractedTitle, suggestion.summary, suggestion.rawSnippet, suggestion.source)
    }
    val place = suggestion.location?.trim()?.ifBlank { null }

    BoldCard(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 15.dp, bottom = 15.dp)) {
            if (isEditing) {
                Text("From ${suggestion.source}", style = BoldType.noteSrcMeta, color = c.ink3)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = editedTitle, onValueChange = { editedTitle = it },
                    label = { Text("Title") }, modifier = Modifier.fillMaxWidth()
                )
                Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = editedDueDate, onValueChange = { editedDueDate = it },
                        label = { Text("Date (YYYY-MM-DD)") }, modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = editedDueTime, onValueChange = { editedDueTime = it },
                        label = { Text("Time (HH:MM)") }, modifier = Modifier.weight(1f)
                    )
                }
                Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = { isEditing = false }) { Text("Cancel") }
                    Button(onClick = {
                        onEdit(suggestion.copy(
                            extractedTitle = editedTitle,
                            dueDate = editedDueDate.ifBlank { null },
                            dueTime = editedDueTime.ifBlank { null }
                        ))
                        isEditing = false
                    }) { Text("Save") }
                }
            } else {
                // Meta row: source + confidence on the left, kind dot pinned right.
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        BoldSourcePill(suggestion.source, modifier = Modifier.weight(1f, fill = false))
                        BoldConfidenceChip(suggestion.confidence)
                    }
                    BoldKindDot(kind)
                }
                Spacer(Modifier.height(11.dp))
                Text(
                    suggestion.extractedTitle,
                    style = BoldType.cardTitle,
                    color = c.ink,
                    modifier = Modifier.fillMaxWidth().clickable { expanded = !expanded }
                )
                Spacer(Modifier.height(11.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    BoldKindChip(kind)
                    Text(rowDetail(suggestion), style = BoldType.detailMeta, color = c.ink3, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }

                AnimatedVisibility(
                    visible = expanded,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut()
                ) {
                    Column {
                        Spacer(Modifier.height(14.dp))
                        HorizontalDivider(color = c.line)
                        Spacer(Modifier.height(12.dp))
                        if (preview.isNotBlank()) {
                            Text("\"$preview\"", style = BoldType.body, fontStyle = FontStyle.Italic, color = c.ink2)
                            Spacer(Modifier.height(12.dp))
                        }
                        // Reassign the kind.
                        Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                            KindPickerChip("Task", suggestion.type == "todo") { onEdit(suggestion.copy(type = "todo")) }
                            KindPickerChip("Reminder", suggestion.type == "reminder") { onEdit(suggestion.copy(type = "reminder")) }
                            KindPickerChip("Note", suggestion.type == "note") { onEdit(suggestion.copy(type = "note")) }
                        }
                        Spacer(Modifier.height(12.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                            DismissButton(Modifier.weight(1f)) { onReject(suggestion) }
                            KeepButton(Modifier.weight(1f)) { onApprove(suggestion) }
                        }
                        Spacer(Modifier.height(4.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box {
                                CardTextAction("Snooze", Icons.Outlined.Schedule) { snoozeMenu = true }
                                DropdownMenu(expanded = snoozeMenu, onDismissRequest = { snoozeMenu = false }) {
                                    snoozeOptions().forEach { (label, until) ->
                                        DropdownMenuItem(text = { Text(label) }, onClick = { snoozeMenu = false; onSnooze(until) })
                                    }
                                }
                            }
                            callNumber?.let { num -> CardTextAction("Call", Icons.Outlined.Call) { dialNumber(context, num) } }
                            place?.let { p -> CardTextAction("Directions", Icons.Outlined.Directions) { openDirections(context, p, null, null) } }
                            CardTextAction("Edit", Icons.Outlined.Edit) { isEditing = true }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun KindPickerChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val c = BoldTheme.colors
    Box(
        Modifier
            .clip(RoundedCornerShape(9.dp))
            .background(if (selected) c.accent else c.surface2)
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Text(label, style = BoldType.filterChip.copy(fontSize = 11.5.sp), color = if (selected) BoldOnAccent else c.ink2)
    }
}

@Composable
private fun DismissButton(modifier: Modifier, onClick: () -> Unit) {
    val c = BoldTheme.colors
    Box(
        modifier
            .clip(RoundedCornerShape(12.dp))
            .border(1.dp, c.skipLine, RoundedCornerShape(12.dp))
            .clickable { onClick() }
            .padding(vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) { Text("Dismiss", style = BoldType.button, color = c.skip) }
}

@Composable
private fun KeepButton(modifier: Modifier, onClick: () -> Unit) {
    val c = BoldTheme.colors
    Row(
        modifier
            .clip(RoundedCornerShape(12.dp))
            .background(c.accent)
            .clickable { onClick() }
            .padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Default.Check, contentDescription = null, tint = BoldOnAccent, modifier = Modifier.size(17.dp))
        Spacer(Modifier.width(7.dp))
        Text("Keep", style = BoldType.button, color = BoldOnAccent)
    }
}

@Composable
private fun CardTextAction(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    val c = BoldTheme.colors
    Row(
        Modifier.clip(RoundedCornerShape(8.dp)).clickable { onClick() }.padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Icon(icon, contentDescription = null, tint = c.ink2, modifier = Modifier.size(16.dp))
        Text(label, style = BoldType.detailMeta, color = c.ink2)
    }
}
