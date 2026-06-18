package com.rajasudhan.taskmind.ui.sources

import android.Manifest
import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.google.accompanist.permissions.*

/** Health of an observation stream, shown as a colored badge on each source. */
enum class ObserverStatus { ACTIVE, ATTENTION, OFF }

private fun statusFor(enabled: Boolean, ready: Boolean): ObserverStatus = when {
    !enabled -> ObserverStatus.OFF
    ready -> ObserverStatus.ACTIVE
    else -> ObserverStatus.ATTENTION
}

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun SourcesScreen(
    viewModel: SourcesViewModel = hiltViewModel()
) {
    val context = LocalContext.current

    val notificationsEnabled by viewModel.isNotificationsEnabled.collectAsState()
    val smsEnabled by viewModel.isSmsEnabled.collectAsState()
    val callLogEnabled by viewModel.isCallLogEnabled.collectAsState()
    val audioEnabled by viewModel.isAudioEnabled.collectAsState()
    val imagesEnabled by viewModel.isImagesEnabled.collectAsState()
    val calendarEnabled by viewModel.isCalendarEnabled.collectAsState()
    val appUsageEnabled by viewModel.isAppUsageEnabled.collectAsState()
    val emailEnabled by viewModel.isEmailEnabled.collectAsState()
    val gmailAccounts by viewModel.gmailAccounts.collectAsState()
    val gmailStatus by viewModel.gmailStatus.collectAsState()

    val callPath by viewModel.callRecordingPath.collectAsState()
    val voicePath by viewModel.voiceRecordingPath.collectAsState()

    val allowlist by viewModel.notificationAllowlist.collectAsState()
    val installedApps by viewModel.installedApps.collectAsState()
    var appSearch by remember { mutableStateOf("") }

    LaunchedEffect(notificationsEnabled) {
        if (notificationsEnabled) viewModel.loadInstalledApps()
    }
    val filteredApps = remember(appSearch, installedApps) {
        if (appSearch.isBlank()) installedApps
        else installedApps.filter {
            it.label.contains(appSearch, true) || it.packageName.contains(appSearch, true)
        }
    }

    // Permissions State
    val smsPermissionState = rememberPermissionState(Manifest.permission.READ_SMS)
    val callLogPermissionState = rememberPermissionState(Manifest.permission.READ_CALL_LOG)
    val contactsPermissionState = rememberPermissionState(Manifest.permission.READ_CONTACTS)
    val audioPermissionState = rememberPermissionState(Manifest.permission.READ_MEDIA_AUDIO)
    val imagesPermissionState = rememberPermissionState(Manifest.permission.READ_MEDIA_IMAGES)
    val calendarPermissionState = rememberMultiplePermissionsState(
        listOf(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR)
    )

    // Gmail OAuth consent: the ViewModel emits an IntentSender, we launch it and report the result.
    val gmailLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result -> viewModel.onConsentResult(result.data) }
    LaunchedEffect(Unit) {
        viewModel.gmailConsent.collect { sender ->
            gmailLauncher.launch(IntentSenderRequest.Builder(sender).build())
        }
    }

    // Google account chooser: pick which account to connect, then the ViewModel authorizes it.
    val accountChooserLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result -> viewModel.onAccountChosen(result.data) }
    LaunchedEffect(Unit) {
        viewModel.gmailAccountChooser.collect { intent ->
            accountChooserLauncher.launch(intent)
        }
    }

    // Honest, derivable telemetry only (no fabricated "scanned this week" counts).
    val activeCount = listOf(
        notificationsEnabled, smsEnabled, callLogEnabled, appUsageEnabled,
        emailEnabled, calendarEnabled, audioEnabled, imagesEnabled
    ).count { it }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            IngestionSummary(activeCount = activeCount, totalCount = 8, gmailAccounts = gmailAccounts.size)
        }

        item { SectionHeader("Passive observers") }

        item {
            SourceToggle(
                title = "Notifications",
                subtitle = "Read incoming notification text",
                isChecked = notificationsEnabled,
                status = statusFor(notificationsEnabled, ready = true),
                onCheckedChange = {
                    if (it) {
                        context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                    }
                    viewModel.toggleNotifications(it)
                }
            )
        }

        // Per-app picker: only show when notifications are enabled. Height-capped (its own scroll)
        // so a long app list doesn't push the other sources (SMS, Email, …) off the screen.
        if (notificationsEnabled) {
            item {
                Column {
                    Text(
                        "Monitored apps — leave all unchecked to watch every app, or check specific " +
                            "apps (e.g. Messages, WhatsApp, Gmail) to cut out noise.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = appSearch,
                        onValueChange = { appSearch = it },
                        label = { Text("Search apps") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                    )
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth().heightIn(max = 240.dp).padding(top = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        items(filteredApps, key = { it.packageName }) { app ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                                    Text(app.label, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(
                                        app.packageName,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                Checkbox(
                                    checked = app.packageName in allowlist,
                                    onCheckedChange = { viewModel.setAppMonitored(app.packageName, it) }
                                )
                            }
                        }
                    }
                }
            }
        }

        item {
            SourceToggle(
                title = "SMS Messages",
                subtitle = "Read incoming and recent text messages",
                isChecked = smsEnabled,
                status = statusFor(smsEnabled, ready = smsPermissionState.status.isGranted),
                onCheckedChange = {
                    if (it && !smsPermissionState.status.isGranted) {
                        smsPermissionState.launchPermissionRequest()
                    } else {
                        viewModel.toggleSms(it)
                    }
                }
            )
        }

        item {
            SourceToggle(
                title = "Call Logs",
                subtitle = "Read recent call history context",
                isChecked = callLogEnabled,
                status = statusFor(callLogEnabled, ready = callLogPermissionState.status.isGranted),
                onCheckedChange = {
                    if (it && !callLogPermissionState.status.isGranted) {
                        callLogPermissionState.launchPermissionRequest()
                    } else {
                        viewModel.toggleCallLog(it)
                    }
                }
            )
        }

        item {
            // Not an ingestion source — an enrichment so the Call button can dial. When a message
            // names someone with no number (a WhatsApp "call me", a missed call from a saved
            // contact), we look the name up in Contacts. Reflects/requests READ_CONTACTS only.
            val contactsGranted = contactsPermissionState.status.isGranted
            SourceToggle(
                title = "Contacts",
                subtitle = "Match a name in a message to a number so \"Call\" can dial",
                isChecked = contactsGranted,
                status = statusFor(contactsGranted, ready = contactsGranted),
                onCheckedChange = {
                    if (it && !contactsGranted) contactsPermissionState.launchPermissionRequest()
                }
            )
        }

        item {
            SourceToggle(
                title = "App Usage",
                subtitle = "Track which apps you use and when",
                isChecked = appUsageEnabled,
                status = statusFor(appUsageEnabled, ready = true),
                onCheckedChange = {
                    if (it) {
                        context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
                    }
                    viewModel.toggleAppUsage(it)
                }
            )
        }

        item { SectionHeader("Connected accounts") }

        item {
            SourceToggle(
                title = "Email (Gmail)",
                subtitle = if (gmailAccounts.isEmpty()) "Read unread Primary emails (read-only)"
                    else "${gmailAccounts.size} account${if (gmailAccounts.size == 1) "" else "s"} connected",
                isChecked = emailEnabled,
                status = statusFor(emailEnabled, ready = gmailAccounts.isNotEmpty()),
                onCheckedChange = { viewModel.onEmailToggle(it) }
            )
            gmailStatus?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 4.dp, top = 4.dp)
                )
            }
            if (emailEnabled) {
                // One row per connected mailbox, each with its own Disconnect.
                gmailAccounts.forEach { account ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(start = 4.dp, top = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            account,
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(onClick = { viewModel.disconnectGmailAccount(account) }) {
                            Text("Disconnect")
                        }
                    }
                }
                TextButton(onClick = { viewModel.addGmailAccount() }) {
                    Text(if (gmailAccounts.isEmpty()) "Connect Gmail" else "Add another account")
                }
            }
        }

        item {
            SourceToggle(
                title = "Calendar",
                subtitle = "Read to prevent duplicates; write to add events on approve",
                isChecked = calendarEnabled,
                status = statusFor(calendarEnabled, ready = calendarPermissionState.allPermissionsGranted),
                onCheckedChange = {
                    if (it && !calendarPermissionState.allPermissionsGranted) {
                        calendarPermissionState.launchMultiplePermissionRequest()
                    } else {
                        viewModel.toggleCalendar(it)
                    }
                }
            )
        }

        item { SectionHeader("Reactive sensors") }

        item {
            SourceToggle(
                title = "Voice/Call Recordings",
                subtitle = "Watch folders for new audio to transcribe",
                isChecked = audioEnabled,
                status = statusFor(audioEnabled, ready = audioPermissionState.status.isGranted),
                onCheckedChange = {
                    if (it && !audioPermissionState.status.isGranted) {
                        audioPermissionState.launchPermissionRequest()
                    } else {
                        viewModel.toggleAudio(it)
                    }
                }
            )
            if (audioEnabled) {
                OutlinedTextField(
                    value = callPath,
                    onValueChange = { viewModel.updateCallRecordingPath(it) },
                    label = { Text("Call Recording Path") },
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                )
                OutlinedTextField(
                    value = voicePath,
                    onValueChange = { viewModel.updateVoiceRecordingPath(it) },
                    label = { Text("Voice Memo Path") },
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                )
            }
        }

        item {
            SourceToggle(
                title = "Screenshots (OCR)",
                subtitle = "Read text from new screenshots on-device. Needs a Tesseract model (Settings).",
                isChecked = imagesEnabled,
                status = statusFor(imagesEnabled, ready = imagesPermissionState.status.isGranted),
                onCheckedChange = {
                    if (it && !imagesPermissionState.status.isGranted) {
                        imagesPermissionState.launchPermissionRequest()
                    } else {
                        viewModel.toggleImages(it)
                    }
                }
            )
        }
    }
}

/** Top-of-screen "cockpit" summary — only facts we can actually derive. */
@Composable
private fun IngestionSummary(activeCount: Int, totalCount: Int, gmailAccounts: Int) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                "$activeCount of $totalCount sources active",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            Text(
                if (gmailAccounts > 0) "$gmailAccounts Gmail account${if (gmailAccounts == 1) "" else "s"} connected · understanding runs on-device"
                else "Understanding runs on-device by default",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
            )
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 4.dp, top = 4.dp)
    )
}

@Composable
private fun StatusPill(status: ObserverStatus) {
    val (color, label) = when (status) {
        ObserverStatus.ACTIVE -> Color(0xFF2E9E4F) to "Active"
        ObserverStatus.ATTENTION -> Color(0xFFE0A100) to "Needs setup"
        ObserverStatus.OFF -> Color(0xFF9E9E9E) to "Off"
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(4.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = color, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun SourceToggle(
    title: String,
    subtitle: String,
    isChecked: Boolean,
    status: ObserverStatus,
    onCheckedChange: (Boolean) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
    ) {
        Row(
            modifier = Modifier.padding(16.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f).padding(end = 16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(text = title, style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.width(8.dp))
                    StatusPill(status)
                }
                Text(text = subtitle, style = MaterialTheme.typography.bodySmall)
            }
            Switch(
                checked = isChecked,
                onCheckedChange = onCheckedChange
            )
        }
    }
}
