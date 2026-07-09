package com.rajasudhan.taskmind.ui.sources

import android.Manifest
import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.google.accompanist.permissions.*
import com.rajasudhan.taskmind.ui.bold.BoldCard
import com.rajasudhan.taskmind.ui.bold.BoldCollapsingHeader
import com.rajasudhan.taskmind.ui.bold.BoldSwitch
import com.rajasudhan.taskmind.ui.theme.BoldTheme
import com.rajasudhan.taskmind.ui.theme.BoldType
import com.rajasudhan.taskmind.ui.theme.ShapeCard
import com.rajasudhan.taskmind.ui.theme.ShapePanel

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun SourcesScreen(
    isDark: Boolean = true,
    onToggleTheme: () -> Unit = {},
    onOpenGuide: () -> Unit = {},
    onLock: (() -> Unit)? = null,
    viewModel: SourcesViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val c = BoldTheme.colors
    val listState = rememberLazyListState()

    val notificationsEnabled by viewModel.isNotificationsEnabled.collectAsState()
    val smsEnabled by viewModel.isSmsEnabled.collectAsState()
    val callLogEnabled by viewModel.isCallLogEnabled.collectAsState()
    val audioEnabled by viewModel.isAudioEnabled.collectAsState()
    val imagesEnabled by viewModel.isImagesEnabled.collectAsState()
    val calendarEnabled by viewModel.isCalendarEnabled.collectAsState()
    val appUsageEnabled by viewModel.isAppUsageEnabled.collectAsState()
    val emailEnabled by viewModel.isEmailEnabled.collectAsState()
    val contactsEnabled by viewModel.isContactsEnabled.collectAsState()
    val gmailAccounts by viewModel.gmailAccounts.collectAsState()
    val gmailStatus by viewModel.gmailStatus.collectAsState()

    val callPath by viewModel.callRecordingPath.collectAsState()
    val voicePath by viewModel.voiceRecordingPath.collectAsState()

    val allowlist by viewModel.notificationAllowlist.collectAsState()
    val installedApps by viewModel.installedApps.collectAsState()
    var appSearch by remember { mutableStateOf("") }

    val voskPresent by viewModel.voskModelPresent.collectAsState()
    val ocrPresent by viewModel.ocrModelPresent.collectAsState()

    LaunchedEffect(notificationsEnabled) {
        if (notificationsEnabled) viewModel.loadInstalledApps()
    }
    // Re-check on entry so a model downloaded in Settings clears the warning on return.
    LaunchedEffect(Unit) { viewModel.refreshModelStatus() }
    val filteredApps = remember(appSearch, installedApps) {
        if (appSearch.isBlank()) installedApps
        else installedApps.filter {
            it.label.contains(appSearch, true) || it.packageName.contains(appSearch, true)
        }
    }

    val smsPermissionState = rememberPermissionState(Manifest.permission.READ_SMS)
    val callLogPermissionState = rememberPermissionState(Manifest.permission.READ_CALL_LOG)
    val contactsPermissionState = rememberPermissionState(Manifest.permission.READ_CONTACTS)
    val audioPermissionState = rememberPermissionState(Manifest.permission.READ_MEDIA_AUDIO)
    val imagesPermissionState = rememberPermissionState(Manifest.permission.READ_MEDIA_IMAGES)
    val calendarPermissionState = rememberMultiplePermissionsState(
        listOf(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR)
    )

    val gmailLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result -> viewModel.onConsentResult(result.data) }
    LaunchedEffect(Unit) {
        viewModel.gmailConsent.collect { intent ->
            gmailLauncher.launch(intent)
        }
    }

    val accountChooserLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result -> viewModel.onAccountChosen(result.data) }
    LaunchedEffect(Unit) {
        viewModel.gmailAccountChooser.collect { intent ->
            accountChooserLauncher.launch(intent)
        }
    }

    BoldCollapsingHeader(
        title = "Sources",
        subtitle = "What TaskMind is allowed to read",
        isDark = isDark,
        onToggleTheme = onToggleTheme,
        onOpenGuide = onOpenGuide,
        onLock = onLock,
        listState = listState,
        hasScrollableContent = true,
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(11.dp)
        ) {
            item { SectionLabel("Passive observers") }

            item {
                BoldSourceRow(
                    name = "Notifications",
                    desc = "Read incoming notification text",
                    meta = "Notification access",
                    checked = notificationsEnabled,
                    onCheckedChange = {
                        if (it) context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                        viewModel.toggleNotifications(it)
                    }
                )
            }

            if (notificationsEnabled) {
                item {
                    BoldCard(Modifier.fillMaxWidth(), shape = ShapePanel) {
                        Column(Modifier.padding(16.dp)) {
                            Text("MONITOR THESE APPS", style = BoldType.sectionMono, color = c.ink3, modifier = Modifier.padding(bottom = 9.dp))
                            Text(
                                "Leave all unchecked to watch every app, or pick specific apps (e.g. Messages, " +
                                    "WhatsApp, Gmail) to cut noise.",
                                style = BoldType.sourceMeta, color = c.ink2
                            )
                            OutlinedTextField(
                                value = appSearch,
                                onValueChange = { appSearch = it },
                                label = { Text("Search apps") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth().padding(top = 10.dp)
                            )
                            LazyColumn(
                                modifier = Modifier.fillMaxWidth().heightIn(max = 240.dp).padding(top = 8.dp),
                                verticalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                items(filteredApps, key = { it.packageName }) { app ->
                                    val monitored = app.packageName in allowlist
                                    Row(
                                        // One toggleable element so TalkBack announces the app name with the checkbox state.
                                        Modifier.fillMaxWidth().toggleable(
                                            value = monitored,
                                            role = Role.Checkbox,
                                            onValueChange = { viewModel.setAppMonitored(app.packageName, it) }
                                        ),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(Modifier.weight(1f).padding(end = 8.dp)) {
                                            Text(app.label, style = MaterialTheme.typography.bodyLarge, color = c.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                            Text(app.packageName, style = BoldType.noteSrcMeta, color = c.ink3, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        }
                                        Checkbox(checked = monitored, onCheckedChange = null)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            item {
                BoldSourceRow(
                    name = "SMS Messages",
                    desc = "Read incoming & recent texts",
                    meta = "READ_SMS",
                    checked = smsEnabled && smsPermissionState.status.isGranted,
                    onCheckedChange = {
                        viewModel.toggleSms(it)
                        if (it && !smsPermissionState.status.isGranted) smsPermissionState.launchPermissionRequest()
                    }
                )
            }

            item {
                BoldSourceRow(
                    name = "Call Logs",
                    desc = "Missed calls → \"Call back\"",
                    meta = "READ_CALL_LOG",
                    checked = callLogEnabled && callLogPermissionState.status.isGranted,
                    onCheckedChange = {
                        viewModel.toggleCallLog(it)
                        if (it && !callLogPermissionState.status.isGranted) callLogPermissionState.launchPermissionRequest()
                    }
                )
            }

            item {
                val contactsGranted = contactsPermissionState.status.isGranted
                BoldSourceRow(
                    name = "Contacts",
                    desc = "Match a name in a message to a number so \"Call\" can dial",
                    meta = "READ_CONTACTS",
                    // On only when the user allows it AND the permission is granted, so it can be turned off.
                    checked = contactsEnabled && contactsGranted,
                    onCheckedChange = {
                        viewModel.toggleContacts(it)
                        if (it && !contactsGranted) contactsPermissionState.launchPermissionRequest()
                    }
                )
            }

            item {
                BoldSourceRow(
                    name = "App Usage",
                    desc = "Daily screen-time digest",
                    meta = "Usage access",
                    checked = appUsageEnabled,
                    onCheckedChange = {
                        if (it) context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
                        viewModel.toggleAppUsage(it)
                    }
                )
            }

            item { SectionLabel("Connected accounts") }

            item {
                BoldSourceRow(
                    name = "Email (Gmail)",
                    desc = "Primary inbox, read-only",
                    meta = if (gmailAccounts.isEmpty()) "Not connected"
                        else "${gmailAccounts.size} account${if (gmailAccounts.size == 1) "" else "s"} connected",
                    checked = emailEnabled,
                    onCheckedChange = { viewModel.onEmailToggle(it) }
                )
                gmailStatus?.let {
                    Text(it, style = BoldType.body, color = c.ink2, modifier = Modifier.padding(start = 6.dp, top = 6.dp))
                }
                if (emailEnabled) {
                    gmailAccounts.forEach { account ->
                        Row(
                            Modifier.fillMaxWidth().padding(start = 6.dp, top = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(account, style = MaterialTheme.typography.bodyMedium, color = c.ink, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                            TextButton(onClick = { viewModel.disconnectGmailAccount(account) }) { Text("Disconnect") }
                        }
                    }
                    TextButton(onClick = { viewModel.addGmailAccount() }, modifier = Modifier.padding(start = 6.dp)) {
                        Text(if (gmailAccounts.isEmpty()) "Connect Gmail" else "Add another account")
                    }
                }
            }

            item {
                BoldSourceRow(
                    name = "Calendar",
                    desc = "Dedupe + add events on approve",
                    meta = "Calendar access",
                    checked = calendarEnabled && calendarPermissionState.allPermissionsGranted,
                    onCheckedChange = {
                        viewModel.toggleCalendar(it)
                        if (it && !calendarPermissionState.allPermissionsGranted) calendarPermissionState.launchMultiplePermissionRequest()
                    }
                )
            }

            item { SectionLabel("Reactive sensors") }

            item {
                BoldSourceRow(
                    name = "Voice / Call recordings",
                    desc = "Watch folders for new audio to transcribe",
                    meta = if (voskPresent) "On-device (Vosk)" else "Model not downloaded — get it in Settings",
                    checked = audioEnabled && audioPermissionState.status.isGranted,
                    warn = audioEnabled && audioPermissionState.status.isGranted && !voskPresent,
                    onCheckedChange = {
                        viewModel.toggleAudio(it)
                        if (it && !audioPermissionState.status.isGranted) audioPermissionState.launchPermissionRequest()
                    }
                )
                if (audioEnabled) {
                    OutlinedTextField(
                        value = callPath,
                        onValueChange = { viewModel.updateCallRecordingPath(it) },
                        label = { Text("Call recording path") },
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                    )
                    OutlinedTextField(
                        value = voicePath,
                        onValueChange = { viewModel.updateVoiceRecordingPath(it) },
                        label = { Text("Voice memo path") },
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                    )
                }
            }

            item {
                BoldSourceRow(
                    name = "Screenshots (OCR)",
                    desc = "Read text from new screenshots, on-device",
                    meta = if (ocrPresent) "On-device (Tesseract)" else "Model not downloaded — get it in Settings",
                    checked = imagesEnabled && imagesPermissionState.status.isGranted,
                    warn = imagesEnabled && imagesPermissionState.status.isGranted && !ocrPresent,
                    onCheckedChange = {
                        viewModel.toggleImages(it)
                        if (it && !imagesPermissionState.status.isGranted) imagesPermissionState.launchPermissionRequest()
                    }
                )
            }

            item {
                Text(
                    "Each source asks for its permission when enabled. Understanding runs on-device by default — change the engine in Privacy.",
                    style = BoldType.body.copy(fontSize = 12.sp, lineHeight = 18.sp),
                    color = c.ink3,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp)
                )
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text.uppercase(),
        style = BoldType.sectionMono,
        color = BoldTheme.colors.ink3,
        modifier = Modifier.padding(start = 4.dp, top = 6.dp)
    )
}

/**
 * A source card to the handoff spec: no icon — the name itself carries the on/off state (ink when on,
 * faint when off), with a behaviour line, a mono permission/status line, and the pill [BoldSwitch].
 */
@Composable
private fun BoldSourceRow(
    name: String,
    desc: String,
    meta: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    warn: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val c = BoldTheme.colors
    BoldCard(modifier.fillMaxWidth(), shape = ShapeCard) {
        Row(
            Modifier.padding(start = 16.dp, end = 16.dp, top = 15.dp, bottom = 15.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(13.dp)
        ) {
            Column(Modifier.weight(1f)) {
                // On = ink; off = ink2 (the spec's faint fails contrast, so keep the off name legible).
                Text(name, style = BoldType.sourceName.copy(fontSize = 15.5.sp), color = if (checked) c.ink else c.ink2)
                Spacer(Modifier.height(3.dp))
                Text(desc, style = BoldType.sourceMeta.copy(fontSize = 12.5.sp), color = c.ink2)
                Spacer(Modifier.height(6.dp))
                Text(
                    meta,
                    style = BoldType.detailMeta.copy(fontSize = 10.sp, letterSpacing = 0.3.sp),
                    color = if (warn) c.reminder else c.ink3
                )
            }
            BoldSwitch(checked = checked, onCheckedChange = onCheckedChange)
        }
    }
}
