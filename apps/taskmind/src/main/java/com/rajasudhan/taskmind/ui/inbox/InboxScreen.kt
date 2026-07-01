package com.rajasudhan.taskmind.ui.inbox

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.Directions
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import java.time.ZoneId
import kotlin.math.roundToInt

/** A snooze target: a friendly [label], the concrete [whenText] it resolves to, and the [until] epoch. */
private data class SnoozeChoice(val label: String, val whenText: String, val until: Long)

private fun snoozeOptions(): List<SnoozeChoice> {
    val zone = ZoneId.systemDefault()
    val now = LocalDateTime.now()
    fun millis(dt: LocalDateTime) = dt.atZone(zone).toInstant().toEpochMilli()
    val fmt = java.time.format.DateTimeFormatter.ofPattern("EEE h:mm a")
    fun choice(label: String, dt: LocalDateTime) = SnoozeChoice(label, dt.format(fmt), millis(dt))
    val eveningDay = if (now.hour < 18) now.toLocalDate() else now.toLocalDate().plusDays(1)
    val tomorrow = now.toLocalDate().plusDays(1)
    return listOf(
        choice("In 30 minutes", now.plusMinutes(30)),
        choice("In 1 hour", now.plusHours(1)),
        choice("This evening", eveningDay.atTime(18, 0)),
        choice("Tomorrow morning", tomorrow.atTime(9, 0)),
        choice("Tomorrow evening", tomorrow.atTime(18, 0)),
        choice("Next week", now.toLocalDate().plusDays(7).atTime(9, 0)),
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
    isDark: Boolean = true,
    onToggleTheme: () -> Unit = {},
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
    // The suggestion whose snooze sheet is open (null = closed).
    var snoozeFor by remember { mutableStateOf<Suggestion?>(null) }

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
            else -> {
                val isEmpty = current.isEmpty()
                val noiseCount = current.count { it.confidence < 0.5 }
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(paddingValues),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 96.dp),
                    verticalArrangement = Arrangement.spacedBy(11.dp)
                ) {
                    item {
                        InboxHeader(
                            count = current.size,
                            isDark = isDark,
                            onToggleTheme = onToggleTheme,
                            overflowExpanded = overflowMenu,
                            onOverflowToggle = { overflowMenu = it },
                            isRefreshing = isRefreshing,
                            onRefresh = { viewModel.refreshRecentData() },
                            onAdd = { showManualDialog = true },
                            onApproveAll = { showApproveAllDialog = true },
                            onRejectAll = { viewModel.rejectAll() }
                        )
                    }
                    if (isRefreshing) {
                        item { ScanningPipelineCard() }
                    }
                    if (isEmpty) {
                        item {
                            InboxEmptyBlock(
                                isRefreshing = isRefreshing,
                                onRefresh = { viewModel.refreshRecentData() },
                                onAdd = { showManualDialog = true }
                            )
                        }
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
                                onSnoozeClick = { snoozeFor = suggestion }
                            )
                        }
                    }
                    if (!isEmpty) {
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

        snoozeFor?.let { s ->
            BoldSnoozeSheet(
                onDismiss = { snoozeFor = null },
                onPick = { until -> doSnooze(s, until); snoozeFor = null }
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
    isDark: Boolean,
    onToggleTheme: () -> Unit,
    overflowExpanded: Boolean,
    onOverflowToggle: (Boolean) -> Unit,
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    onAdd: () -> Unit,
    onApproveAll: () -> Unit,
    onRejectAll: () -> Unit,
) {
    val c = BoldTheme.colors
    Row(
        // +6dp over the list's 16dp content padding → the spec's 22dp header inset.
        Modifier.fillMaxWidth().padding(horizontal = 6.dp).padding(top = 8.dp, bottom = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top
    ) {
        Column(Modifier.weight(1f).padding(end = 8.dp)) {
            Text("Inbox", style = BoldType.screenTitle, color = c.ink)
            Spacer(Modifier.height(6.dp))
            Text(
                if (count == 0) "All caught up · on-device" else "$count pending · on-device",
                style = BoldType.srcLabel.copy(fontSize = 11.5.sp, letterSpacing = 0.3.sp),
                color = c.ink2
            )
        }
        // 48dp targets with 38dp visuals; negative spacing keeps the chips ~6dp apart as designed
        // while every touch target stays at the 48dp accessibility minimum.
        Row(horizontalArrangement = Arrangement.spacedBy((-4).dp), verticalAlignment = Alignment.CenterVertically) {
            HeaderIconButton(
                onClick = onToggleTheme,
                label = if (isDark) "Switch to light theme" else "Switch to dark theme"
            ) {
                Icon(
                    if (isDark) Icons.Outlined.LightMode else Icons.Outlined.DarkMode,
                    contentDescription = null, tint = c.ink, modifier = Modifier.size(18.dp)
                )
            }
            HeaderIconButton(
                onClick = onRefresh,
                label = if (isRefreshing) "Scanning in progress" else "Scan now"
            ) {
                if (isRefreshing) CircularProgressIndicator(Modifier.size(17.dp), strokeWidth = 2.dp, color = c.accent)
                else Icon(Icons.Default.Refresh, contentDescription = null, tint = c.ink, modifier = Modifier.size(18.dp))
            }
            Box {
                HeaderIconButton(
                    onClick = { onOverflowToggle(true) },
                    label = if (overflowExpanded) "More actions, menu open" else "More actions"
                ) {
                    Icon(Icons.Default.MoreVert, contentDescription = null, tint = c.ink, modifier = Modifier.size(18.dp))
                }
                DropdownMenu(expanded = overflowExpanded, onDismissRequest = { onOverflowToggle(false) }) {
                    DropdownMenuItem(text = { Text("Add item") }, onClick = { onOverflowToggle(false); onAdd() })
                    DropdownMenuItem(text = { Text("Keep all") }, onClick = { onOverflowToggle(false); onApproveAll() })
                    DropdownMenuItem(text = { Text("Dismiss all") }, onClick = { onOverflowToggle(false); onRejectAll() })
                }
            }
        }
    }
}

/** 38dp visual chip inside a 48dp touch target — the design's rounded square header buttons. */
@Composable
private fun HeaderIconButton(onClick: () -> Unit, label: String, content: @Composable () -> Unit) {
    val c = BoldTheme.colors
    Box(
        Modifier
            .size(48.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .semantics { contentDescription = label; role = Role.Button },
        contentAlignment = Alignment.Center
    ) {
        Box(
            Modifier.size(38.dp).clip(RoundedCornerShape(12.dp)).background(c.surface)
                .border(1.dp, c.line, RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center
        ) { content() }
    }
}

/**
 * The "ANALYSING ON-DEVICE" pipeline card shown while a scan is running. The three stages advance on
 * a short timer purely for feedback — the underlying scan is opaque, so this mirrors the design's
 * read → understand → draft sequence rather than reporting true progress.
 */
@Composable
private fun ScanningPipelineCard() {
    val c = BoldTheme.colors
    var stage by remember { mutableStateOf(0) }
    LaunchedEffect(Unit) {
        delay(750); stage = 1
        delay(800); stage = 2
    }
    val transition = rememberInfiniteTransition(label = "scan")
    // The accent dot breathes; a 2dp line sweeps top → bottom across the card (the design's tmScan).
    val pulse by transition.animateFloat(
        initialValue = 0.4f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "pulse"
    )
    val sweep by transition.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1700, easing = LinearEasing)), label = "sweep"
    )
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 6.dp).clip(ShapeCard).background(c.surface)
            .border(1.dp, c.line, ShapeCard)
            .drawWithContent {
                drawContent()
                val y = sweep * size.height
                drawRect(
                    brush = Brush.horizontalGradient(
                        listOf(Color.Transparent, c.accent.copy(alpha = 0.85f), Color.Transparent)
                    ),
                    topLeft = Offset(0f, y),
                    size = Size(size.width, 2.dp.toPx())
                )
            }
    ) {
        Row(
            Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 13.dp, bottom = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(9.dp)
        ) {
            Box(Modifier.size(7.dp).clip(CircleShape).background(c.accent.copy(alpha = pulse)))
            Text("ANALYSING ON-DEVICE", style = BoldType.srcLabel.copy(fontSize = 11.sp, letterSpacing = 0.5.sp), color = c.accent)
            Spacer(Modifier.weight(1f))
            Text("Gemma · local", style = BoldType.detailMeta, color = c.ink2)
        }
        HorizontalDivider(color = c.ink.copy(alpha = 0.06f))
        Column(
            Modifier.padding(start = 16.dp, end = 16.dp, top = 13.dp, bottom = 15.dp),
            verticalArrangement = Arrangement.spacedBy(13.dp)
        ) {
            PipelineStep("Reading your sources", stageIndex = 0, currentStage = stage)
            PipelineStep("Understanding meaning & intent", stageIndex = 1, currentStage = stage)
            PipelineStep("Drafting suggestions to review", stageIndex = 2, currentStage = stage)
        }
    }
}

@Composable
private fun PipelineStep(label: String, stageIndex: Int, currentStage: Int) {
    val c = BoldTheme.colors
    val done = currentStage > stageIndex
    val active = currentStage == stageIndex
    Row(
        Modifier.fillMaxWidth().alpha(if (done || active) 1f else 0.4f),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(Modifier.size(20.dp), contentAlignment = Alignment.Center) {
            when {
                done -> Icon(Icons.Default.Check, contentDescription = null, tint = c.accent, modifier = Modifier.size(17.dp))
                active -> CircularProgressIndicator(Modifier.size(15.dp), strokeWidth = 2.dp, color = c.accent)
                else -> Box(Modifier.size(7.dp).clip(CircleShape).background(c.line2))
            }
        }
        Text(label, style = BoldType.body.copy(fontSize = 13.5.sp), color = c.ink)
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
private fun InboxEmptyBlock(isRefreshing: Boolean, onRefresh: () -> Unit, onAdd: () -> Unit) {
    val c = BoldTheme.colors
    Column(
        // 14dp + the list's 16dp content padding → the spec's 30dp empty-state inset.
        Modifier.fillMaxWidth().padding(horizontal = 14.dp).padding(top = 40.dp, bottom = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            Modifier.size(76.dp).clip(CircleShape).background(c.surface).border(1.dp, c.line, CircleShape),
            contentAlignment = Alignment.Center
        ) { Icon(Icons.Default.Check, contentDescription = null, tint = c.accent, modifier = Modifier.size(30.dp)) }
        Spacer(Modifier.height(20.dp))
        Text("Inbox zero", style = BoldType.emptyTitle.copy(fontSize = 28.sp), color = c.ink)
        Spacer(Modifier.height(6.dp))
        Text(
            "You've reviewed everything. TaskMind keeps watching your sources in the background.",
            style = BoldType.body.copy(fontSize = 14.sp),
            color = c.ink2,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
        Spacer(Modifier.height(22.dp))
        Row(
            Modifier.clip(RoundedCornerShape(14.dp)).background(c.accentGlow)
                .border(1.dp, c.accent, RoundedCornerShape(14.dp)).clickable { onRefresh() }
                // Visible "SCAN NOW"/"SCANNING…" text is the accessible name; just mark the role.
                .semantics { role = Role.Button }
                .padding(horizontal = 22.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(Icons.Default.Refresh, contentDescription = null, tint = c.accent, modifier = Modifier.size(16.dp))
            Text(
                if (isRefreshing) "SCANNING…" else "SCAN NOW",
                style = BoldType.confBadge.copy(fontWeight = FontWeight.Bold, fontSize = 12.sp, letterSpacing = 0.6.sp),
                color = c.accent
            )
        }
        Spacer(Modifier.height(12.dp))
        Text(
            "Add an item",
            style = BoldType.detailMeta,
            color = c.ink3,
            modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable { onAdd() }
                .semantics { role = Role.Button }.padding(horizontal = 10.dp, vertical = 6.dp)
        )
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

/** The handoff's "Snooze until" bottom sheet — friendly options, each with the time it resolves to. */
@Composable
private fun BoldSnoozeSheet(onDismiss: () -> Unit, onPick: (Long) -> Unit) {
    val c = BoldTheme.colors
    BoldBottomSheet(
        title = "Snooze until",
        onDismiss = onDismiss,
        subtitle = "It comes back to your Inbox then — nothing is saved yet."
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            snoozeOptions().forEach { opt ->
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(c.bg2)
                        .border(1.dp, c.line, RoundedCornerShape(14.dp)).clickable { onPick(opt.until) }
                        .semantics { role = Role.Button }
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(13.dp)
                ) {
                    Box(
                        Modifier.size(34.dp).clip(RoundedCornerShape(10.dp)).background(c.reminderSoft),
                        contentAlignment = Alignment.Center
                    ) { Icon(Icons.Outlined.Schedule, contentDescription = null, tint = c.reminder, modifier = Modifier.size(17.dp)) }
                    Text(
                        opt.label,
                        style = BoldType.body.copy(fontSize = 15.sp, fontWeight = FontWeight.Medium),
                        color = c.ink,
                        modifier = Modifier.weight(1f)
                    )
                    Text(opt.whenText, style = BoldType.detailMeta.copy(fontSize = 11.5.sp), color = c.muted)
                }
            }
        }
    }
}

@Composable
private fun BoldSuggestionCard(
    suggestion: Suggestion,
    onApprove: (Suggestion) -> Unit,
    onReject: (Suggestion) -> Unit,
    onEdit: (Suggestion) -> Unit,
    onSnoozeClick: () -> Unit
) {
    val c = BoldTheme.colors
    var expanded by remember { mutableStateOf(false) }
    var isEditing by remember { mutableStateOf(false) }
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
                // Meta row: kind pill + source (matches the design handoff's suggestion card).
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    BoldKindChip(kind)
                    BoldSourcePill(suggestion.source, modifier = Modifier.weight(1f, fill = false))
                    BoldKindDot(kind)
                }
                Spacer(Modifier.height(11.dp))
                Text(
                    suggestion.extractedTitle,
                    style = BoldType.sugTitle,
                    color = c.ink,
                    modifier = Modifier.fillMaxWidth().clickable { expanded = !expanded }
                )
                if (preview.isNotBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        preview,
                        style = BoldType.body.copy(fontSize = 13.5.sp, lineHeight = 19.sp),
                        color = c.ink2,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(Modifier.height(14.dp))
                // Confidence read-out + inline reject / snooze / approve.
                val confFrac = suggestion.confidence.coerceIn(0.0, 1.0).toFloat()
                val confColor = if (suggestion.confidence >= 0.6) c.accent else c.amber
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                        Text("CONF", style = BoldType.detailMeta.copy(letterSpacing = 0.5.sp), color = c.ink3)
                        Box(Modifier.width(46.dp).height(4.dp).clip(RoundedCornerShape(2.dp)).background(c.line2)) {
                            Box(Modifier.fillMaxHeight().fillMaxWidth(confFrac).clip(RoundedCornerShape(2.dp)).background(confColor))
                        }
                        Text("${(confFrac * 100).roundToInt()}%", style = BoldType.confBadge.copy(fontWeight = FontWeight.Bold), color = confColor)
                    }
                    Spacer(Modifier.weight(1f))
                    Box(
                        Modifier.size(34.dp).clip(RoundedCornerShape(10.dp))
                            .border(1.dp, c.line2, RoundedCornerShape(10.dp)).clickable { onReject(suggestion) },
                        contentAlignment = Alignment.Center
                    ) { Icon(Icons.Default.Close, contentDescription = "Reject", tint = c.ink2, modifier = Modifier.size(15.dp)) }
                    Spacer(Modifier.width(8.dp))
                    Box(
                        Modifier.size(34.dp).clip(RoundedCornerShape(10.dp))
                            .border(1.dp, c.line2, RoundedCornerShape(10.dp)).clickable { onSnoozeClick() },
                        contentAlignment = Alignment.Center
                    ) { Icon(Icons.Outlined.Schedule, contentDescription = "Snooze", tint = c.ink2, modifier = Modifier.size(15.dp)) }
                    Spacer(Modifier.width(8.dp))
                    Row(
                        Modifier.height(34.dp).clip(RoundedCornerShape(10.dp)).background(c.accent)
                            .clickable { onApprove(suggestion) }.padding(horizontal = 15.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Icon(Icons.Default.Check, contentDescription = null, tint = BoldOnAccent, modifier = Modifier.size(14.dp))
                        Text("OK", style = BoldType.confBadge.copy(fontWeight = FontWeight.Bold, fontSize = 12.sp), color = BoldOnAccent)
                    }
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
                        // Reassign the kind.
                        Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                            KindPickerChip("Task", suggestion.type == "todo") { onEdit(suggestion.copy(type = "todo")) }
                            KindPickerChip("Reminder", suggestion.type == "reminder") { onEdit(suggestion.copy(type = "reminder")) }
                            KindPickerChip("Note", suggestion.type == "note") { onEdit(suggestion.copy(type = "note")) }
                        }
                        Spacer(Modifier.height(10.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
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
