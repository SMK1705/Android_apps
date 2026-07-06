package com.rajasudhan.taskmind.ui.notes

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.provider.AlarmClock
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Directions
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.outlined.Alarm
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.Circle
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.rememberCameraPositionState
import com.google.maps.android.compose.rememberMarkerState
import com.rajasudhan.taskmind.data.model.Note
import com.rajasudhan.taskmind.data.source.RecurrenceUtil
import com.rajasudhan.taskmind.data.source.resolveCallNumber
import com.rajasudhan.taskmind.ui.bold.BoldBottomSheet
import com.rajasudhan.taskmind.ui.bold.BoldFilterChip
import com.rajasudhan.taskmind.ui.bold.BoldKindChip
import com.rajasudhan.taskmind.ui.bold.boldKindFor
import com.rajasudhan.taskmind.ui.bold.color
import com.rajasudhan.taskmind.ui.common.dialNumber
import com.rajasudhan.taskmind.ui.common.openDirections
import com.rajasudhan.taskmind.ui.theme.BoldOnAccent
import com.rajasudhan.taskmind.ui.theme.BoldTheme
import com.rajasudhan.taskmind.ui.theme.BoldType
import com.rajasudhan.taskmind.ui.theme.ShapeCard
import sh.calvin.reorderable.ReorderableColumn

/**
 * Full view of a single approved item — to the Bold handoff: kind pill + serif title, the body and
 * metadata in editorial cards, with a Call / Complete action bar pinned to the bottom. Reached by
 * tapping a row in [NotesScreen]; [onBack] is invoked after a delete to pop the screen.
 */
@OptIn(ExperimentalPermissionsApi::class, ExperimentalMaterial3Api::class)
@Composable
fun NoteDetailScreen(
    onBack: () -> Unit,
    viewModel: NoteDetailViewModel = hiltViewModel()
) {
    val c = BoldTheme.colors
    val note by viewModel.note.collectAsState()
    val n = note
    var deleting by remember { mutableStateOf(false) }

    val breakingDown by viewModel.breakingDown.collectAsState()

    // ---- Location reminder plumbing (hooks must run before any early return) ----
    val context = LocalContext.current
    val fineLocation = rememberPermissionState(Manifest.permission.ACCESS_FINE_LOCATION)
    val backgroundLocation = rememberPermissionState(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
    var showLocationDialog by remember { mutableStateOf(false) }
    var showReminderSheet by remember { mutableStateOf(false) }
    var locationLabel by remember { mutableStateOf("") }
    var placeQuery by remember { mutableStateOf("") }
    var pendingLabel by remember { mutableStateOf<String?>(null) }
    // When set, geocode this typed place instead of capturing the current location — both still gate on
    // location permission below, because the geofence can't register without it either way.
    var pendingPlace by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(fineLocation.status.isGranted, pendingLabel) {
        val label = pendingLabel
        if (label != null && fineLocation.status.isGranted) {
            if (!backgroundLocation.status.isGranted) backgroundLocation.launchPermissionRequest()
            val place = pendingPlace
            if (place != null) {
                viewModel.setLocationReminderByPlace(place, label) { resolved ->
                    if (!resolved) Toast.makeText(
                        context, "Couldn't find that place — try adding a city.", Toast.LENGTH_SHORT
                    ).show()
                }
            } else {
                captureCurrentLocation(context) { lat, lng -> viewModel.setLocationReminder(lat, lng, label) }
            }
            pendingLabel = null
            pendingPlace = null
        }
    }

    if (n == null) {
        // A delete makes the note flow emit null; render nothing until we pop, rather than
        // flashing a "not found" error in the brief window before navigation completes.
        if (!deleting) {
            Box(Modifier.fillMaxSize().background(c.screen), contentAlignment = Alignment.Center) {
                Text("Note not found.", style = BoldType.body, color = c.ink2)
            }
        }
        return
    }

    val kind = boldKindFor(n.type, n.dueDate != null)
    val kindColor = kind.color()
    val completable = n.type == "todo" || n.type == "reminder" || n.type == "waiting_on"

    // Call shortcut for "call X / call me back" items: prefer a number named in the message, else
    // resolve the named contact's number. Resolution can touch contacts, so it runs off-thread.
    val rawSnippet = remember(n.body) { n.body.substringAfter("\n\n", n.body) }
    val callNumber by produceState<String?>(null, n.id, n.body, n.title, n.summary, n.source) {
        value = resolveCallNumber(context, n.title, n.summary, rawSnippet, n.source)
    }
    val hasActionBar = completable || callNumber != null

    Box(Modifier.fillMaxSize().background(c.screen)) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                .padding(start = 18.dp, end = 18.dp, top = 12.dp, bottom = if (hasActionBar) 96.dp else 24.dp)
        ) {
            // ── Kind + source ──
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                BoldKindChip(kind)
                Text("from ${n.source}", style = BoldType.noteSrcMeta.copy(fontSize = 11.sp), color = c.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }

            Spacer(Modifier.height(14.dp))
            InlineEditableText(
                value = n.title,
                onSave = { viewModel.updateTitle(it) },
                textStyle = BoldType.emptyTitle.copy(fontSize = 31.sp, lineHeight = 34.sp, letterSpacing = (-0.3).sp),
                color = c.ink,
                showEditHint = true
            )

            if (n.dueDate != null) {
                Spacer(Modifier.height(10.dp))
                Text(
                    "${n.dueDate} ${n.dueTime ?: ""}".trim(),
                    style = BoldType.detailMeta.copy(fontSize = 11.sp, letterSpacing = 0.3.sp),
                    color = c.reminder
                )
            }

            if (n.summary.isNotBlank()) {
                Spacer(Modifier.height(14.dp))
                InlineEditableText(
                    value = n.summary,
                    onSave = { viewModel.updateSummary(it) },
                    textStyle = BoldType.body.copy(fontSize = 15.sp, lineHeight = 23.sp),
                    color = c.muted
                )
            }

            // ── Checklist ──
            val checklistItems = if (!n.checklist.isNullOrBlank()) Checklist.decode(n.checklist!!)
                else if (n.type == "todo") Checklist.derive(n.summary) else emptyList()
            if (checklistItems.isNotEmpty()) {
                Spacer(Modifier.height(22.dp))
                SectionLabel("Checklist")
                Spacer(Modifier.height(10.dp))
                var items by remember(n.checklist, n.summary) { mutableStateOf(checklistItems) }
                ReorderableColumn(
                    list = items,
                    onSettle = { from, to ->
                        items = items.toMutableList().apply { add(to, removeAt(from)) }
                        viewModel.updateChecklist(Checklist.encode(items))
                    }
                ) { index, item, _ ->
                    key(item.text) {
                        val itemShape = RoundedCornerShape(12.dp)
                        Row(
                            Modifier.fillMaxWidth().padding(bottom = 8.dp).clip(itemShape).background(c.surface)
                                .border(1.dp, c.line, itemShape)
                                .clickable {
                                    items = items.toMutableList().also { it[index] = it[index].copy(checked = !it[index].checked) }
                                    viewModel.updateChecklist(Checklist.encode(items))
                                }
                                .semantics { role = Role.Checkbox; selected = item.checked }
                                .padding(horizontal = 14.dp, vertical = 11.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            BoldCheckSquare(item.checked, size = 20.dp)
                            Spacer(Modifier.width(11.dp))
                            Text(
                                item.text,
                                style = BoldType.body.copy(fontSize = 14.sp),
                                color = if (item.checked) c.ink3 else c.ink,
                                textDecoration = if (item.checked) TextDecoration.LineThrough else null,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(modifier = Modifier.draggableHandle().size(28.dp), onClick = {}) {
                                Icon(Icons.Default.DragHandle, contentDescription = "Reorder", tint = c.ink3, modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }
            }

            // ── Magic Breakdown: split a vague task into steps (on-device), shown until there's a list ──
            if (completable && checklistItems.isEmpty()) {
                Spacer(Modifier.height(22.dp))
                SectionLabel("Checklist")
                Spacer(Modifier.height(10.dp))
                BoldActionButton(
                    if (breakingDown) "Breaking it down…" else "Break into steps",
                    Icons.Outlined.AutoAwesome,
                    filled = false
                ) {
                    if (!breakingDown) viewModel.breakDown { msg ->
                        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                    }
                }
            }

            // ── Reminder: repeat + location ──
            if (completable) {
                Spacer(Modifier.height(22.dp))
                SectionLabel("Reminder")
                Spacer(Modifier.height(10.dp))
                // A tappable summary that opens the reminder sheet (Once / Repeat / Location).
                ReminderScheduleCard(note = n, onClick = { showReminderSheet = true })

                // Escalation controls — only meaningful once a timed reminder exists.
                if (n.dueTime != null) {
                    Spacer(Modifier.height(10.dp))
                    DetailCard {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("Nag until done", style = BoldType.body.copy(fontSize = 14.sp), color = c.ink)
                                Text(
                                    "After it fires, re-rings every few minutes until completed",
                                    style = BoldType.noteSrcMeta,
                                    color = c.ink3
                                )
                            }
                            Switch(checked = n.nag, onCheckedChange = { viewModel.setNag(it) })
                        }
                        // Alarm-grade handoff for the truly critical: sets a real Clock-app alarm at
                        // the due time — full-screen, ringtone, unmissable by construction. Only for a
                        // reminder due TODAY: AlarmClock.ACTION_SET_ALARM has no date extra, so it would
                        // otherwise ring at the next occurrence of that wall-clock time — the wrong day.
                        if (n.dueDate == java.time.LocalDate.now().toString()) {
                            Spacer(Modifier.height(12.dp))
                            BoldActionButton("Ring as system alarm", Icons.Outlined.Alarm, filled = false) {
                                val time = RecurrenceUtil.parseTime(n.dueTime!!)
                                if (time != null) {
                                    val ok = runCatching {
                                        context.startActivity(
                                            Intent(AlarmClock.ACTION_SET_ALARM).apply {
                                                putExtra(AlarmClock.EXTRA_HOUR, time.hour)
                                                putExtra(AlarmClock.EXTRA_MINUTES, time.minute)
                                                putExtra(AlarmClock.EXTRA_MESSAGE, n.title)
                                                putExtra(AlarmClock.EXTRA_SKIP_UI, true)
                                            }
                                        )
                                    }.isSuccess
                                    Toast.makeText(
                                        context,
                                        if (ok) "Alarm set in your Clock app for today at ${n.dueTime}" else "No clock app found",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            }
                        }
                    }
                }

                Spacer(Modifier.height(10.dp))
                if (n.locationLabel != null) {
                    val lat = n.locationLat
                    val lng = n.locationLng
                    DetailCard {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                Modifier.size(38.dp).clip(RoundedCornerShape(11.dp)).background(kindColor.copy(alpha = 0.16f)),
                                contentAlignment = Alignment.Center
                            ) { Icon(Icons.Default.Place, contentDescription = null, tint = kindColor, modifier = Modifier.size(19.dp)) }
                            Spacer(Modifier.width(13.dp))
                            Text(n.locationLabel!!, style = BoldType.body.copy(fontSize = 14.sp), color = c.ink, modifier = Modifier.weight(1f))
                            Text(
                                "REMOVE",
                                style = BoldType.detailMeta.copy(fontSize = 10.sp, letterSpacing = 0.5.sp),
                                color = c.ink3,
                                modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable { viewModel.clearLocationReminder() }.padding(horizontal = 8.dp, vertical = 6.dp)
                            )
                        }
                        if (lat != null && lng != null) {
                            Spacer(Modifier.height(12.dp))
                            LocationMapCard(lat = lat, lng = lng, radiusMeters = n.locationRadius ?: 150.0, accent = c.accent)
                        }
                        Spacer(Modifier.height(12.dp))
                        BoldActionButton("Get directions", Icons.Default.Directions, filled = false) {
                            openDirections(context, n.locationLabel, lat, lng)
                        }
                    }
                } else {
                    BoldActionButton("Remind me at a place", Icons.Default.Place, filled = false) {
                        locationLabel = ""; placeQuery = ""; showLocationDialog = true
                    }
                }
            }

            // ── Details (full body, linkified) ──
            Spacer(Modifier.height(22.dp))
            SectionLabel("Details")
            Spacer(Modifier.height(10.dp))
            DetailCard {
                Text(
                    linkifyNoteBody(n.body, c.accent),
                    style = BoldType.body.copy(fontSize = 14.sp, lineHeight = 21.sp),
                    color = c.ink2
                )
            }

            // ── Priority (low / normal / high) ──
            Spacer(Modifier.height(22.dp))
            SectionLabel("Priority")
            Spacer(Modifier.height(10.dp))
            DetailCard {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("Low", "Normal", "High").forEach { level ->
                        val value = level.lowercase()
                        BoldFilterChip(level, n.priority == value, { viewModel.updatePriority(value) })
                    }
                }
            }

            Spacer(Modifier.height(20.dp))
            BoldActionButton("Delete", Icons.Default.DeleteOutline, filled = false, danger = true) {
                deleting = true; viewModel.deleteNote(onBack)
            }
        }

        // ── Bottom action bar: Call + Complete ──
        if (hasActionBar) {
            Row(
                Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                    .background(Brush.verticalGradient(colorStops = arrayOf(0f to Color.Transparent, 0.35f to c.screen, 1f to c.screen)))
                    .padding(start = 18.dp, end = 18.dp, top = 14.dp, bottom = 18.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                callNumber?.let { number ->
                    BoldBarButton("CALL", Icons.Default.Call, modifier = Modifier.weight(1f), filled = false) { dialNumber(context, number) }
                }
                if (completable) {
                    BoldBarButton(
                        if (n.completed) "COMPLETED" else "MARK COMPLETE",
                        Icons.Default.Check,
                        modifier = Modifier.weight(2f),
                        filled = true
                    ) { viewModel.setCompleted(!n.completed) }
                }
            }
        }
    }

    if (showLocationDialog) {
        AlertDialog(
            onDismissRequest = { showLocationDialog = false; placeQuery = "" },
            title = { Text("Remind me at a place") },
            text = {
                Column {
                    Text("Type a place, or leave it blank to use your current location. You'll be reminded when you arrive.")
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = placeQuery,
                        onValueChange = { placeQuery = it },
                        label = { Text("Place (e.g. Panda Express, Dunwoody)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = locationLabel,
                        onValueChange = { locationLabel = it },
                        label = { Text("Label (e.g. Office)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val place = placeQuery.trim()
                    val label = locationLabel.trim().ifBlank { place.ifBlank { "Saved location" } }
                    showLocationDialog = false
                    placeQuery = ""
                    // Both paths gate on location permission (the geofence needs it); the LaunchedEffect
                    // then geocodes the typed place, or falls back to the current location when blank.
                    pendingPlace = place.ifBlank { null }
                    pendingLabel = label
                    if (!fineLocation.status.isGranted) fineLocation.launchPermissionRequest()
                }) { Text("Set reminder") }
            },
            dismissButton = {
                TextButton(onClick = { showLocationDialog = false; placeQuery = "" }) { Text("Cancel") }
            }
        )
    }

    if (showReminderSheet) {
        ReminderSheet(
            note = n,
            onDismiss = { showReminderSheet = false },
            onSchedule = { date, time -> viewModel.updateSchedule(date, time); showReminderSheet = false },
            onSetRecurrence = { viewModel.updateRecurrence(it) },
            onUseLocation = { showReminderSheet = false; locationLabel = ""; placeQuery = ""; showLocationDialog = true }
        )
    }
}

/** Mono uppercase section label (matches the rest of the redesign). */
@Composable
private fun SectionLabel(text: String) {
    Text(text.uppercase(), style = BoldType.sectionMono, color = BoldTheme.colors.ink3)
}

/** A tappable summary of the current schedule (due + repeat) that opens the reminder sheet. */
@Composable
private fun ReminderScheduleCard(note: Note, onClick: () -> Unit) {
    val c = BoldTheme.colors
    val due = listOfNotNull(note.dueDate, note.dueTime).joinToString(" · ").ifBlank { "Not scheduled" }
    val repeat = note.recurrence?.replaceFirstChar { it.uppercase() }
    Row(
        Modifier.fillMaxWidth().clip(ShapeCard).background(c.surface).border(1.dp, c.line, ShapeCard)
            .clickable(onClick = onClick).semantics { role = Role.Button }
            .padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(13.dp)
    ) {
        Box(
            Modifier.size(38.dp).clip(RoundedCornerShape(11.dp)).background(c.reminderSoft),
            contentAlignment = Alignment.Center
        ) { Icon(Icons.Outlined.Schedule, contentDescription = null, tint = c.reminder, modifier = Modifier.size(19.dp)) }
        Column(Modifier.weight(1f)) {
            Text(due, style = BoldType.body.copy(fontSize = 14.sp), color = c.ink)
            Text(
                if (repeat != null) "Repeats $repeat · tap to change" else "Tap to set date, repeat or place",
                style = BoldType.body.copy(fontSize = 12.sp), color = c.muted
            )
        }
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = c.ink3, modifier = Modifier.size(18.dp))
    }
}

/**
 * The handoff's "Set a reminder" sheet: a mode toggle (Once / Repeat / Location). Once picks a date +
 * time; Repeat sets the recurrence; Location hands off to the current-place capture.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReminderSheet(
    note: Note,
    onDismiss: () -> Unit,
    onSchedule: (String?, String?) -> Unit,
    onSetRecurrence: (String) -> Unit,
    onUseLocation: () -> Unit,
) {
    val c = BoldTheme.colors
    var mode by remember { mutableStateOf(if (note.locationLabel != null) 2 else 0) }
    val timeState = rememberTimePickerState(
        initialHour = note.dueTime?.substringBefore(":")?.trim()?.toIntOrNull() ?: 9,
        initialMinute = note.dueTime?.substringAfter(":")?.trim()?.toIntOrNull() ?: 0,
        is24Hour = false
    )
    var pickedDate by remember { mutableStateOf(note.dueDate) }
    var showDatePicker by remember { mutableStateOf(false) }

    BoldBottomSheet(
        title = "Set a reminder",
        onDismiss = onDismiss,
        subtitle = "Scheduled with an exact alarm — fires even in Doze."
    ) {
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(13.dp)).background(c.bg2)
                .border(1.dp, c.line, RoundedCornerShape(13.dp)).padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            listOf("ONCE", "REPEAT", "LOCATION").forEachIndexed { i, label ->
                Box(
                    Modifier.weight(1f).height(36.dp)
                        .shadow(if (mode == i) 3.dp else 0.dp, RoundedCornerShape(10.dp), clip = false)
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (mode == i) c.surface else Color.Transparent).clickable { mode = i }
                        .semantics { role = Role.Button; selected = mode == i },
                    contentAlignment = Alignment.Center
                ) { Text(label, style = BoldType.detailMeta.copy(fontSize = 11.sp, letterSpacing = 0.4.sp), color = if (mode == i) c.ink else c.muted) }
            }
        }
        Spacer(Modifier.height(18.dp))

        when (mode) {
            0 -> {
                Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(c.bg2)
                        .border(1.dp, c.line, RoundedCornerShape(14.dp)).clickable { showDatePicker = true }.padding(14.dp)
                ) {
                    Text("DATE", style = BoldType.detailMeta.copy(fontSize = 10.sp, letterSpacing = 0.5.sp), color = c.ink3)
                    Spacer(Modifier.height(6.dp))
                    Text(pickedDate ?: "Pick a date", style = BoldType.body.copy(fontSize = 16.sp, fontWeight = FontWeight.Medium), color = if (pickedDate != null) c.ink else c.muted)
                }
                Spacer(Modifier.height(12.dp))
                Text("TIME", style = BoldType.detailMeta.copy(fontSize = 10.sp, letterSpacing = 0.5.sp), color = c.ink3)
                Spacer(Modifier.height(6.dp))
                TimeInput(state = timeState)
            }
            1 -> {
                Text("FREQUENCY", style = BoldType.detailMeta.copy(fontSize = 10.sp, letterSpacing = 0.5.sp), color = c.ink3)
                Spacer(Modifier.height(10.dp))
                val current = (note.recurrence ?: "None").replaceFirstChar { it.uppercase() }
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    RecurrenceUtil.OPTIONS.forEach { option ->
                        BoldFilterChip(option, current.equals(option, ignoreCase = true), { onSetRecurrence(option) })
                    }
                }
            }
            else -> {
                Text(
                    if (note.locationLabel != null) "Reminding you at ${note.locationLabel}."
                        else "Save your current place and get reminded when you return.",
                    style = BoldType.body.copy(fontSize = 13.sp), color = c.muted
                )
                Spacer(Modifier.height(14.dp))
                BoldActionButton(if (note.locationLabel != null) "Change place" else "Use current location", Icons.Default.Place, filled = false) { onUseLocation() }
            }
        }

        if (mode != 2) {
            Spacer(Modifier.height(22.dp))
            Row(
                Modifier.fillMaxWidth().height(52.dp).clip(RoundedCornerShape(15.dp)).background(c.accent)
                    .clickable {
                        if (mode == 0) {
                            val time = String.format(java.util.Locale.US, "%02d:%02d", timeState.hour, timeState.minute)
                            onSchedule(pickedDate, time)
                        } else onDismiss()
                    }.semantics { role = Role.Button },
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center
            ) { Text(if (mode == 0) "SCHEDULE REMINDER" else "DONE", style = BoldType.monoBtn, color = BoldOnAccent) }
        }
    }

    if (showDatePicker) {
        val initMillis = pickedDate?.let {
            runCatching { java.time.LocalDate.parse(it).atStartOfDay(java.time.ZoneOffset.UTC).toInstant().toEpochMilli() }.getOrNull()
        }
        val dateState = rememberDatePickerState(initialSelectedDateMillis = initMillis)
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    dateState.selectedDateMillis?.let {
                        pickedDate = java.time.Instant.ofEpochMilli(it).atZone(java.time.ZoneOffset.UTC).toLocalDate().toString()
                    }
                    showDatePicker = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text("Cancel") } }
        ) { DatePicker(state = dateState) }
    }
}

/** A flat editorial card wrapping a section's content. */
@Composable
private fun DetailCard(content: @Composable ColumnScope.() -> Unit) {
    val c = BoldTheme.colors
    Column(
        Modifier.fillMaxWidth().clip(ShapeCard).background(c.surface).border(1.dp, c.line, ShapeCard)
            .padding(16.dp),
        content = content
    )
}

/** Read-only checkbox visual (the row owns the click) — accent fill + tick when checked. */
@Composable
private fun BoldCheckSquare(checked: Boolean, size: androidx.compose.ui.unit.Dp) {
    val c = BoldTheme.colors
    val base = Modifier.size(size).clip(RoundedCornerShape(7.dp))
    Box(
        if (checked) base.background(c.accent) else base.border(1.5.dp, c.line2, RoundedCornerShape(7.dp)),
        contentAlignment = Alignment.Center
    ) {
        if (checked) Icon(Icons.Default.Check, contentDescription = null, tint = BoldOnAccent, modifier = Modifier.size(size * 0.62f))
    }
}

/** Full-width Bold button used inside the scroll content. */
@Composable
private fun BoldActionButton(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    filled: Boolean,
    danger: Boolean = false,
    onClick: () -> Unit,
) {
    val c = BoldTheme.colors
    val fg = when { danger -> c.skip; filled -> BoldOnAccent; else -> c.ink }
    val base = Modifier.fillMaxWidth().height(50.dp).clip(RoundedCornerShape(14.dp)).clickable(onClick = onClick)
        .semantics { role = Role.Button }
    Row(
        if (filled) base.background(c.accent) else base.border(1.dp, if (danger) c.skip else c.line2, RoundedCornerShape(14.dp)),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Icon(icon, contentDescription = null, tint = fg, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(label, style = BoldType.button.copy(fontSize = 13.sp), color = fg)
    }
}

/** A button for the bottom action bar (52dp, weighted). */
@Composable
private fun BoldBarButton(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    modifier: Modifier,
    filled: Boolean,
    onClick: () -> Unit,
) {
    val c = BoldTheme.colors
    val fg = if (filled) BoldOnAccent else c.ink
    val base = modifier.height(52.dp).clip(RoundedCornerShape(15.dp)).clickable(onClick = onClick)
        .semantics { role = Role.Button }
    Row(
        if (filled) base.background(c.accent) else base.background(c.surface).border(1.dp, c.line2, RoundedCornerShape(15.dp)),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Icon(icon, contentDescription = null, tint = fg, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(label, style = BoldType.monoBtn, color = fg, maxLines = 1)
    }
}

@SuppressLint("MissingPermission")
private fun captureCurrentLocation(context: Context, onResult: (Double, Double) -> Unit) {
    val client = LocationServices.getFusedLocationProviderClient(context)
    client.getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, null)
        .addOnSuccessListener { location ->
            if (location != null) onResult(location.latitude, location.longitude)
            else Toast.makeText(context, "Couldn't get your location — try again outside.", Toast.LENGTH_SHORT).show()
        }
        .addOnFailureListener {
            Toast.makeText(context, "Couldn't get your location.", Toast.LENGTH_SHORT).show()
        }
}

/** A non-interactive Google Maps preview of the saved place, with its geofence circle drawn on. */
@Composable
private fun LocationMapCard(lat: Double, lng: Double, radiusMeters: Double, accent: Color) {
    val target = LatLng(lat, lng)
    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(target, 15f)
    }
    GoogleMap(
        modifier = Modifier.fillMaxWidth().height(160.dp).clip(RoundedCornerShape(16.dp)),
        cameraPositionState = cameraPositionState,
        // A static preview: gestures off so it never fights the page's vertical scroll.
        uiSettings = MapUiSettings(
            zoomControlsEnabled = false,
            scrollGesturesEnabled = false,
            zoomGesturesEnabled = false,
            tiltGesturesEnabled = false,
            rotationGesturesEnabled = false,
            mapToolbarEnabled = false,
            compassEnabled = false
        )
    ) {
        Marker(state = rememberMarkerState(position = target), title = "Reminder location")
        Circle(
            center = target,
            radius = radiusMeters,
            strokeColor = accent,
            strokeWidth = 4f,
            fillColor = accent.copy(alpha = 0.18f)
        )
    }
}

/**
 * A text block you tap to edit in place: shows [value] (optionally with an edit hint), and on tap
 * swaps to a borderless editor; the IME "Done" action saves via [onSave].
 */
@Composable
private fun InlineEditableText(
    value: String,
    onSave: (String) -> Unit,
    textStyle: TextStyle,
    color: Color,
    modifier: Modifier = Modifier,
    showEditHint: Boolean = false
) {
    val c = BoldTheme.colors
    var editing by remember(value) { mutableStateOf(false) }
    if (editing) {
        var draft by remember { mutableStateOf(value) }
        var hasFocused by remember { mutableStateOf(false) }
        val focusRequester = remember { FocusRequester() }
        BasicTextField(
            value = draft,
            onValueChange = { draft = it },
            textStyle = textStyle.copy(color = color),
            cursorBrush = SolidColor(c.accent),
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Sentences,
                imeAction = ImeAction.Done
            ),
            keyboardActions = KeyboardActions(onDone = { editing = false; onSave(draft) }),
            modifier = modifier
                .fillMaxWidth()
                .focusRequester(focusRequester)
                // Save when focus leaves (tapped away / keyboard dismissed), as well as on "Done".
                .onFocusChanged { state ->
                    if (state.isFocused) hasFocused = true
                    else if (hasFocused) { editing = false; onSave(draft) }
                }
        )
        LaunchedEffect(Unit) { focusRequester.requestFocus() }
    } else {
        Row(
            modifier = modifier.fillMaxWidth().clickable { editing = true }.semantics { role = Role.Button },
            verticalAlignment = Alignment.Top
        ) {
            Text(text = value, style = textStyle, color = color, modifier = Modifier.weight(1f))
            if (showEditHint) {
                Spacer(Modifier.width(6.dp))
                Icon(Icons.Default.Edit, contentDescription = "Edit", tint = c.ink3, modifier = Modifier.size(18.dp).padding(top = 4.dp))
            }
        }
    }
}
