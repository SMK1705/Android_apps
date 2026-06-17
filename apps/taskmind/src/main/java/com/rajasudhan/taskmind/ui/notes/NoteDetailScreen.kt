package com.rajasudhan.taskmind.ui.notes

import android.Manifest
import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Directions
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
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
import com.rajasudhan.taskmind.data.source.PhoneUtil
import com.rajasudhan.taskmind.data.source.RecurrenceUtil
import com.rajasudhan.taskmind.ui.common.CategoryBadge
import com.rajasudhan.taskmind.ui.common.accent
import com.rajasudhan.taskmind.ui.common.categoryFor

/**
 * Full view of a single approved item: heading, summary, the complete body, and metadata.
 * Reached by tapping a row in [NotesScreen]. [onBack] is invoked after a delete to pop the screen.
 */
@OptIn(ExperimentalPermissionsApi::class, ExperimentalMaterial3Api::class)
@Composable
fun NoteDetailScreen(
    onBack: () -> Unit,
    viewModel: NoteDetailViewModel = hiltViewModel()
) {
    val note by viewModel.note.collectAsState()
    val n = note
    var deleting by remember { mutableStateOf(false) }

    // ---- Location reminder plumbing (hooks must run before any early return) ----
    val context = LocalContext.current
    val fineLocation = rememberPermissionState(Manifest.permission.ACCESS_FINE_LOCATION)
    val backgroundLocation = rememberPermissionState(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
    var showLocationDialog by remember { mutableStateOf(false) }
    var locationLabel by remember { mutableStateOf("") }
    var pendingLabel by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(fineLocation.status.isGranted, pendingLabel) {
        val label = pendingLabel
        if (label != null && fineLocation.status.isGranted) {
            if (!backgroundLocation.status.isGranted) backgroundLocation.launchPermissionRequest()
            captureCurrentLocation(context) { lat, lng -> viewModel.setLocationReminder(lat, lng, label) }
            pendingLabel = null
        }
    }

    if (n == null) {
        // A delete makes the note flow emit null; render nothing until we pop, rather than
        // flashing a "not found" error in the brief window before navigation completes.
        if (!deleting) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Note not found.", style = MaterialTheme.typography.bodyLarge)
            }
        }
        return
    }

    val category = categoryFor(n.type, n.dueDate, n.dueTime)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CategoryBadge(category)
            if (n.dueDate != null) {
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "${n.dueDate} ${n.dueTime ?: ""}".trim(),
                    style = MaterialTheme.typography.labelLarge,
                    color = category.accent(),
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        Spacer(Modifier.height(12.dp))
        Text(
            text = n.title,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = "from ${n.source}",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        // Call shortcut for "call X / call me back" items: prefer a number named in the message,
        // else fall back to the sender's number. Opens the dialer with it pre-filled.
        val rawSnippet = remember(n.body) { n.body.substringAfter("\n\n", n.body) }
        val callNumber = remember(n.id, n.body, n.title, n.summary, n.source) {
            if (PhoneUtil.isCallIntent(n.title, n.summary, rawSnippet)) {
                // Prefer a number stated in the item itself (a voice note spells digits out as
                // words, so the real number survives only in the model's title/summary); fall back
                // to the message body, then the sender's number for a "call me back".
                PhoneUtil.extractFirst(n.title)
                    ?: PhoneUtil.extractFirst(n.summary)
                    ?: PhoneUtil.extractFirst(rawSnippet)
                    ?: PhoneUtil.extractFirst(n.source)
            } else null
        }
        if (callNumber != null) {
            Spacer(Modifier.height(12.dp))
            FilledTonalButton(
                onClick = { context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$callNumber"))) }
            ) {
                Icon(Icons.Default.Call, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Call $callNumber", maxLines = 1)
            }
        }

        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = n.completed, onCheckedChange = { viewModel.setCompleted(it) })
            Text(
                text = if (n.completed) "Completed" else "Mark complete",
                style = MaterialTheme.typography.bodyMedium
            )
        }

        if (n.summary.isNotBlank()) {
            Spacer(Modifier.height(16.dp))
            Text(
                text = n.summary,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
        }

        // Checklist: shown for to-dos whose content is list-like; ticks persist to the note.
        val checklistItems = if (!n.checklist.isNullOrBlank()) Checklist.decode(n.checklist!!)
            else if (n.type == "todo") Checklist.derive(n.summary) else emptyList()
        if (checklistItems.isNotEmpty()) {
            Spacer(Modifier.height(16.dp))
            Text("Checklist", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            checklistItems.forEachIndexed { i, item ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = item.checked,
                        onCheckedChange = { viewModel.updateChecklist(Checklist.toggleEncoded(checklistItems, i)) }
                    )
                    Text(
                        text = item.text,
                        style = MaterialTheme.typography.bodyMedium,
                        textDecoration = if (item.checked) TextDecoration.LineThrough else null
                    )
                }
            }
        }

        // ---- Reminder scheduling: repeat + location ----
        if (n.type == "reminder" || n.type == "todo") {
            Spacer(Modifier.height(16.dp))
            HorizontalDivider()
            Spacer(Modifier.height(12.dp))
            Text("Reminder", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

            if (n.type == "reminder") {
                Spacer(Modifier.height(8.dp))
                var repeatExpanded by remember { mutableStateOf(false) }
                val currentRepeat = (n.recurrence ?: "None").replaceFirstChar { it.uppercase() }
                ExposedDropdownMenuBox(expanded = repeatExpanded, onExpandedChange = { repeatExpanded = it }) {
                    OutlinedTextField(
                        value = currentRepeat,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Repeat") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = repeatExpanded) },
                        modifier = Modifier.menuAnchor().fillMaxWidth()
                    )
                    ExposedDropdownMenu(expanded = repeatExpanded, onDismissRequest = { repeatExpanded = false }) {
                        RecurrenceUtil.OPTIONS.forEach { option ->
                            DropdownMenuItem(
                                text = { Text(option) },
                                onClick = { repeatExpanded = false; viewModel.updateRecurrence(option) }
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(8.dp))
            if (n.locationLabel != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Place, contentDescription = null, tint = category.accent())
                    Spacer(Modifier.width(4.dp))
                    Text(n.locationLabel!!, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    TextButton(onClick = { viewModel.clearLocationReminder() }) { Text("Remove") }
                }
                // Mini map of the place (when it geocoded) + a shortcut to navigate there.
                val lat = n.locationLat
                val lng = n.locationLng
                if (lat != null && lng != null) {
                    Spacer(Modifier.height(8.dp))
                    LocationMapCard(
                        lat = lat,
                        lng = lng,
                        radiusMeters = n.locationRadius ?: 150.0,
                        accent = category.accent()
                    )
                }
                // Directions resolve from the coordinates, or the place name itself, so they work
                // (and point at the named venue) even when the embedded map couldn't render it.
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = { openDirections(context, n.locationLabel, lat, lng) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Directions, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Get directions")
                }
            } else {
                OutlinedButton(onClick = { locationLabel = ""; showLocationDialog = true }) {
                    Icon(Icons.Default.Place, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Remind me at a place")
                }
            }
        }

        Spacer(Modifier.height(16.dp))
        HorizontalDivider()
        Spacer(Modifier.height(16.dp))

        Text(
            text = "Details",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = linkifyNoteBody(n.body, MaterialTheme.colorScheme.primary),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface
        )

        Spacer(Modifier.height(24.dp))
        OutlinedButton(onClick = { deleting = true; viewModel.deleteNote(onBack) }) {
            Icon(Icons.Default.DeleteOutline, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Delete")
        }
    }

    if (showLocationDialog) {
        AlertDialog(
            onDismissRequest = { showLocationDialog = false },
            title = { Text("Remind me at a place") },
            text = {
                Column {
                    Text("Saves your current location; you'll be reminded when you return here.")
                    Spacer(Modifier.height(12.dp))
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
                    val label = locationLabel.trim().ifBlank { "Saved location" }
                    showLocationDialog = false
                    pendingLabel = label // captured by the LaunchedEffect once location permission is granted
                    if (!fineLocation.status.isGranted) fineLocation.launchPermissionRequest()
                }) { Text("Use current location") }
            },
            dismissButton = {
                TextButton(onClick = { showLocationDialog = false }) { Text("Cancel") }
            }
        )
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
        modifier = Modifier
            .fillMaxWidth()
            .height(180.dp)
            .clip(RoundedCornerShape(12.dp)),
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
 * Open directions to the place in Google Maps (origin = current location). Prefers the geocoded
 * coordinates; otherwise sends the place name as a destination query so Maps resolves the venue.
 */
private fun openDirections(context: Context, placeName: String?, lat: Double?, lng: Double?) {
    val destination = when {
        lat != null && lng != null -> "$lat,$lng"
        !placeName.isNullOrBlank() -> Uri.encode(placeName)
        else -> return
    }
    val uri = Uri.parse("https://www.google.com/maps/dir/?api=1&destination=$destination")
    val maps = Intent(Intent.ACTION_VIEW, uri).setPackage("com.google.android.apps.maps")
    try {
        context.startActivity(maps)
    } catch (e: ActivityNotFoundException) {
        // Google Maps app not installed — let any handler (e.g. a browser) take the URL.
        context.startActivity(Intent(Intent.ACTION_VIEW, uri))
    }
}
