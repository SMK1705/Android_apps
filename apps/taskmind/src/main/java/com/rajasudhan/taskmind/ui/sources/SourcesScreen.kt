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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.google.accompanist.permissions.*
import com.rajasudhan.taskmind.ui.bold.BoldCard
import com.rajasudhan.taskmind.ui.bold.BoldEyebrow
import com.rajasudhan.taskmind.ui.theme.BoldOnAccent
import com.rajasudhan.taskmind.ui.theme.BoldTheme
import com.rajasudhan.taskmind.ui.theme.BoldType
import com.rajasudhan.taskmind.ui.theme.ShapeHero
import com.rajasudhan.taskmind.ui.theme.ShapePanel
import com.rajasudhan.taskmind.ui.theme.ShapeWell

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun SourcesScreen(
    viewModel: SourcesViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val c = BoldTheme.colors

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

    val smsPermissionState = rememberPermissionState(Manifest.permission.READ_SMS)
    val callLogPermissionState = rememberPermissionState(Manifest.permission.READ_CALL_LOG)
    val contactsPermissionState = rememberPermissionState(Manifest.permission.READ_CONTACTS)
    val audioPermissionState = rememberPermissionState(Manifest.permission.READ_MEDIA_AUDIO)
    val imagesPermissionState = rememberPermissionState(Manifest.permission.READ_MEDIA_IMAGES)
    val calendarPermissionState = rememberMultiplePermissionsState(
        listOf(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR)
    )

    val gmailLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result -> viewModel.onConsentResult(result.data) }
    LaunchedEffect(Unit) {
        viewModel.gmailConsent.collect { sender ->
            gmailLauncher.launch(IntentSenderRequest.Builder(sender).build())
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

    val activeCount = listOf(
        notificationsEnabled, smsEnabled, callLogEnabled, appUsageEnabled,
        emailEnabled, calendarEnabled, audioEnabled, imagesEnabled
    ).count { it }

    LazyColumn(
        modifier = Modifier.fillMaxSize().background(c.screen),
        contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 6.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(11.dp)
    ) {
        item {
            Column {
                BoldEyebrow("What it may read")
                Text("Sources", style = BoldType.screenTitle, color = c.ink, modifier = Modifier.semantics { heading() })
                Spacer(Modifier.height(14.dp))
                SourcesHero(activeCount = activeCount, totalCount = 8)
            }
        }

        item { SectionLabel("Passive observers") }

        item {
            BoldSourceRow(
                icon = Icons.Default.Notifications,
                name = "Notifications",
                meta = "Read incoming notification text",
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
                        Text(
                            "Leave all unchecked to watch every app, or pick specific apps (e.g. Messages, " +
                                "WhatsApp, Gmail) to cut noise.",
                            style = BoldType.body, color = c.ink2
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
                                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                    Column(Modifier.weight(1f).padding(end = 8.dp)) {
                                        Text(app.label, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        Text(app.packageName, style = BoldType.noteSrcMeta, color = c.ink3, maxLines = 1, overflow = TextOverflow.Ellipsis)
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
        }

        item {
            BoldSourceRow(
                icon = Icons.Default.Sms,
                name = "SMS Messages",
                meta = "Read incoming and recent text messages",
                checked = smsEnabled,
                onCheckedChange = {
                    if (it && !smsPermissionState.status.isGranted) smsPermissionState.launchPermissionRequest()
                    else viewModel.toggleSms(it)
                }
            )
        }

        item {
            BoldSourceRow(
                icon = Icons.Default.Call,
                name = "Call Logs",
                meta = "Read recent call history context",
                checked = callLogEnabled,
                onCheckedChange = {
                    if (it && !callLogPermissionState.status.isGranted) callLogPermissionState.launchPermissionRequest()
                    else viewModel.toggleCallLog(it)
                }
            )
        }

        item {
            val contactsGranted = contactsPermissionState.status.isGranted
            BoldSourceRow(
                icon = Icons.Default.Person,
                name = "Contacts",
                meta = "Match a name in a message to a number so \"Call\" can dial",
                checked = contactsGranted,
                onCheckedChange = { if (it && !contactsGranted) contactsPermissionState.launchPermissionRequest() }
            )
        }

        item {
            BoldSourceRow(
                icon = Icons.Default.BarChart,
                name = "App Usage",
                meta = "Track which apps you use and when",
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
                icon = Icons.Default.Email,
                name = "Email (Gmail)",
                meta = if (gmailAccounts.isEmpty()) "Read unread Primary emails (read-only)"
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
                        Text(account, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
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
                icon = Icons.Default.Event,
                name = "Calendar",
                meta = "Read to prevent duplicates; write to add events on approve",
                checked = calendarEnabled,
                onCheckedChange = {
                    if (it && !calendarPermissionState.allPermissionsGranted) calendarPermissionState.launchMultiplePermissionRequest()
                    else viewModel.toggleCalendar(it)
                }
            )
        }

        item { SectionLabel("Reactive sensors") }

        item {
            BoldSourceRow(
                icon = Icons.Default.Mic,
                name = "Voice / Call recordings",
                meta = "Watch folders for new audio to transcribe",
                checked = audioEnabled,
                onCheckedChange = {
                    if (it && !audioPermissionState.status.isGranted) audioPermissionState.launchPermissionRequest()
                    else viewModel.toggleAudio(it)
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
                icon = Icons.Default.Image,
                name = "Screenshots (OCR)",
                meta = "Read text from new screenshots on-device. Needs a Tesseract model (Settings).",
                checked = imagesEnabled,
                onCheckedChange = {
                    if (it && !imagesPermissionState.status.isGranted) imagesPermissionState.launchPermissionRequest()
                    else viewModel.toggleImages(it)
                }
            )
        }
    }
}

@Composable
private fun SourcesHero(activeCount: Int, totalCount: Int) {
    Box(Modifier.fillMaxWidth().clip(ShapeHero).background(BoldTheme.colors.accent).padding(18.dp)) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(42.dp).clip(RoundedCornerShape(13.dp)).background(Color.Black.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) { Icon(Icons.Default.Memory, contentDescription = null, tint = BoldOnAccent, modifier = Modifier.size(22.dp)) }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("On-device intelligence", style = BoldType.heroTitle, color = BoldOnAccent)
                    Text("$activeCount of $totalCount sources active", style = BoldType.detailMeta, color = BoldOnAccent.copy(alpha = 0.7f))
                }
                Row(
                    Modifier.clip(CircleShape).background(Color.Black.copy(alpha = 0.12f)).padding(horizontal = 9.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Box(Modifier.size(6.dp).clip(CircleShape).background(BoldOnAccent))
                    Text("ACTIVE", style = BoldType.deckCountLabel, color = BoldOnAccent)
                }
            }
            Spacer(Modifier.height(12.dp))
            Text("Read and understood on this phone. Nothing is uploaded.", style = BoldType.body, color = BoldOnAccent.copy(alpha = 0.85f))
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

@Composable
private fun BoldSourceRow(
    icon: ImageVector,
    name: String,
    meta: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = BoldTheme.colors
    BoldCard(modifier.fillMaxWidth(), shape = ShapePanel) {
        Row(
            Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(13.dp)
        ) {
            Box(
                Modifier.size(40.dp).clip(ShapeWell).background(c.surface2),
                contentAlignment = Alignment.Center
            ) { Icon(icon, contentDescription = null, tint = if (checked) c.accent else c.ink3, modifier = Modifier.size(19.dp)) }
            Column(Modifier.weight(1f)) {
                Text(name, style = BoldType.sourceName, color = c.ink)
                Text(meta, style = BoldType.sourceMeta, color = c.ink2)
            }
            Switch(checked = checked, onCheckedChange = onCheckedChange)
        }
    }
}
