package com.rajasudhan.taskmind.ui.sources

import android.Manifest
import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.google.accompanist.permissions.*

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
    val audioPermissionState = rememberPermissionState(Manifest.permission.READ_MEDIA_AUDIO)
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

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text(
                text = "Turn sources on or off. Note: Some require special Android permissions which will be requested when toggled on.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 8.dp)
            )
        }

        item {
            SourceToggle(
                title = "Notifications",
                subtitle = "Read incoming notification text",
                isChecked = notificationsEnabled,
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
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                }
            }
        }

        item {
            SourceToggle(
                title = "SMS Messages",
                subtitle = "Read incoming and recent text messages",
                isChecked = smsEnabled,
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
            SourceToggle(
                title = "Calendar",
                subtitle = "Read to prevent duplicates; write to add events on approve",
                isChecked = calendarEnabled,
                onCheckedChange = {
                    if (it && !calendarPermissionState.allPermissionsGranted) {
                        calendarPermissionState.launchMultiplePermissionRequest()
                    } else {
                        viewModel.toggleCalendar(it)
                    }
                }
            )
        }

        item {
            SourceToggle(
                title = "App Usage",
                subtitle = "Track which apps you use and when",
                isChecked = appUsageEnabled,
                onCheckedChange = {
                    if (it) {
                        context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
                    }
                    viewModel.toggleAppUsage(it)
                }
            )
        }

        item {
            SourceToggle(
                title = "Email (Gmail)",
                subtitle = if (gmailAccounts.isEmpty()) "Read unread Primary emails (read-only)"
                    else "${gmailAccounts.size} account${if (gmailAccounts.size == 1) "" else "s"} connected",
                isChecked = emailEnabled,
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
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            Text("Media Folders", style = MaterialTheme.typography.titleLarge)
        }

        item {
            SourceToggle(
                title = "Voice/Call Recordings",
                subtitle = "Watch folders for new audio to transcribe",
                isChecked = audioEnabled,
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
    }
}

@Composable
fun SourceToggle(
    title: String,
    subtitle: String,
    isChecked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Row(
            modifier = Modifier.padding(16.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f).padding(end = 16.dp)) {
                Text(text = title, style = MaterialTheme.typography.titleMedium)
                Text(text = subtitle, style = MaterialTheme.typography.bodySmall)
            }
            Switch(
                checked = isChecked,
                onCheckedChange = onCheckedChange
            )
        }
    }
}
