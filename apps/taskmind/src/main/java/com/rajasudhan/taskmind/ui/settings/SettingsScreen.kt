package com.rajasudhan.taskmind.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.biometric.BiometricManager
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

import androidx.hilt.navigation.compose.hiltViewModel
import com.rajasudhan.taskmind.AppLock
import com.rajasudhan.taskmind.data.source.SettingsManager
import com.rajasudhan.taskmind.ui.bold.BoldEyebrow
import com.rajasudhan.taskmind.ui.bold.BoldFilterChip
import com.rajasudhan.taskmind.ui.theme.BoldOnAccent
import com.rajasudhan.taskmind.ui.theme.BoldTheme
import com.rajasudhan.taskmind.ui.theme.BoldType
import com.rajasudhan.taskmind.ui.theme.ShapeHeroBig
import com.rajasudhan.taskmind.ui.theme.ShapePanel
import com.rajasudhan.taskmind.ui.theme.ThemeMode

private val EngineAccent = Color(0xFF7C4DFF)
private val TranscriptionAccent = Color(0xFF00897B)
private val OcrAccent = Color(0xFF5E35B1)
private val CalendarAccent = Color(0xFF2E7D32)

/** True only when the device has a biometric or device credential enrolled to authenticate against. */
private fun deviceCanEnforceLock(context: android.content.Context): Boolean =
    BiometricManager.from(context).canAuthenticate(
        BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL
    ) == BiometricManager.BIOMETRIC_SUCCESS

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val llmApiKey by viewModel.llmApiKey.collectAsState()
    val sttApiKey by viewModel.sttApiKey.collectAsState()
    val transcriptionStatus by viewModel.transcriptionStatus.collectAsState()
    val transcriptionModelPath = viewModel.transcriptionModelPath
    val ocrStatus by viewModel.ocrStatus.collectAsState()
    val ocrModelPath = viewModel.ocrModelPath
    val useOnDeviceLlm by viewModel.useOnDeviceLlm.collectAsState()
    val eventDurationMinutes by viewModel.eventDurationMinutes.collectAsState()
    val calendarId by viewModel.calendarId.collectAsState()
    val calendars by viewModel.calendars.collectAsState()
    val onDeviceStatus by viewModel.onDeviceStatus.collectAsState()
    val modelPath by viewModel.modelPath.collectAsState()
    val testStatus by viewModel.testStatus.collectAsState()
    val testRunning by viewModel.testRunning.collectAsState()
    var testInput by remember { mutableStateOf("") }
    val egressEvents by viewModel.egressEvents.collectAsState()
    val egressTimeFormat = remember { java.text.SimpleDateFormat("MMM d, HH:mm", java.util.Locale.getDefault()) }
    val retentionDays by viewModel.retentionDays.collectAsState()
    val scanFrequencyMinutes by viewModel.scanFrequencyMinutes.collectAsState()
    val exportStatus by viewModel.exportStatus.collectAsState()
    val backupStatus by viewModel.backupStatus.collectAsState()
    val restartRequired by viewModel.restartRequired.collectAsState()
    val permissions by viewModel.permissions.collectAsState()
    val themeMode by viewModel.themeMode.collectAsState()
    val appLockEnabled by viewModel.appLockEnabled.collectAsState()
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri -> uri?.let { viewModel.exportNotesToUri(it) } }

    // ---- Encrypted backup / restore plumbing ----
    // The passphrase is collected first, then carried across the SAF round-trip to the file callback.
    var passphraseMode by remember { mutableStateOf<String?>(null) } // "backup" | "restore" | null
    var passphraseInput by remember { mutableStateOf("") }
    var pendingPassphrase by remember { mutableStateOf("") }
    val backupDate = remember { java.text.SimpleDateFormat("yyyyMMdd", java.util.Locale.getDefault()).format(java.util.Date()) }
    val backupLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri ->
        uri?.let { viewModel.backupToUri(it, pendingPassphrase) }
        pendingPassphrase = ""
    }
    val restoreLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let { viewModel.restoreFromUri(it, pendingPassphrase) }
        pendingPassphrase = ""
    }

    LaunchedEffect(Unit) {
        viewModel.loadCalendars()
        viewModel.loadPermissionStatuses()
    }

    val c = BoldTheme.colors
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(c.screen)
            .verticalScroll(rememberScrollState())
            .padding(start = 18.dp, end = 18.dp, top = 6.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Column {
            BoldEyebrow("Tune & configure")
            Text("Settings", style = BoldType.screenTitle, color = c.ink, modifier = Modifier.semantics { heading() })
        }
        // "today" = since local midnight, so the hero stat matches its label.
        val startOfToday = remember {
            java.util.Calendar.getInstance().apply {
                set(java.util.Calendar.HOUR_OF_DAY, 0); set(java.util.Calendar.MINUTE, 0)
                set(java.util.Calendar.SECOND, 0); set(java.util.Calendar.MILLISECOND, 0)
            }.timeInMillis
        }
        val todayOutbound = remember(egressEvents, startOfToday) { egressEvents.count { it.timestamp >= startOfToday } }
        PrivacyHero(outbound = todayOutbound)

        SettingsSectionCard(accent = Color(0xFF5C6BC0), title = "Appearance") {
            Text("Theme", style = MaterialTheme.typography.bodyMedium, color = c.ink)
            Text(
                "Follow the system day-night setting, or force light or dark.",
                style = MaterialTheme.typography.bodySmall,
                color = c.ink2
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ThemeMode.values().forEach { mode ->
                    val label = when (mode) {
                        ThemeMode.SYSTEM -> "System"
                        ThemeMode.LIGHT -> "Light"
                        ThemeMode.DARK -> "Dark"
                    }
                    BoldFilterChip(label, themeMode == mode, { viewModel.updateThemeMode(mode) })
                }
            }
        }

        SettingsSectionCard(accent = Color(0xFF00796B), title = "Security") {
            // Whether the device can actually enforce a lock right now (a biometric or device
            // credential is enrolled). Re-queried on ON_RESUME so leaving to set up a screen lock
            // and returning refreshes the warning below — and memoized otherwise, so we don't pay a
            // binder IPC on every recomposition.
            val context = LocalContext.current
            val lifecycleOwner = LocalLifecycleOwner.current
            var canEnforceLock by remember { mutableStateOf(deviceCanEnforceLock(context)) }
            DisposableEffect(lifecycleOwner) {
                val observer = LifecycleEventObserver { _, event ->
                    if (event == Lifecycle.Event.ON_RESUME) canEnforceLock = deviceCanEnforceLock(context)
                }
                lifecycleOwner.lifecycle.addObserver(observer)
                onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("App lock (biometric)", style = MaterialTheme.typography.bodyMedium)
                    Text(
                        "Require fingerprint / face / PIN on launch and on every return. Turn off to open " +
                            "straight to your data — your notes stay encrypted at rest either way.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(checked = appLockEnabled, onCheckedChange = { viewModel.updateAppLockEnabled(it) })
            }
            // The toggle can read ON while there's no screen lock to enforce it — the app still opens
            // straight to the data in that case. Say so plainly instead of showing a checked switch
            // that silently does nothing.
            if (appLockEnabled && !canEnforceLock) {
                Text(
                    "No screen lock is set up on this device, so the app can't lock yet. Add a " +
                        "fingerprint, face unlock, or PIN in your device's security settings.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }

        SettingsSectionCard(accent = EngineAccent, title = "Understanding Engine") {
            Text(
                "On-device is the fast, private default. Switch to Cloud only when you want higher " +
                    "accuracy — its calls are auditable in the Data Egress panel below.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row {
                RadioButton(selected = useOnDeviceLlm, onClick = { viewModel.updateUseOnDeviceLlm(true) })
                Text("On-Device (private, fast — default)", modifier = Modifier.padding(start = 8.dp, top = 12.dp))
            }
            Row {
                RadioButton(selected = !useOnDeviceLlm, onClick = { viewModel.updateUseOnDeviceLlm(false) })
                Text("Cloud (higher accuracy — data leaves device)", modifier = Modifier.padding(start = 8.dp, top = 12.dp))
            }

            if (useOnDeviceLlm) {
                OutlinedTextField(
                    value = modelPath,
                    onValueChange = { viewModel.updateModelPath(it) },
                    label = { Text("Model .task path (blank = default)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    "Push a Gemma .task model (e.g. gemma3-4b-it-int4.task). Default location:\n" +
                        viewModel.defaultModelPath,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedButton(onClick = { viewModel.checkOnDeviceModel() }) {
                    Text("Check on-device model")
                }
                onDeviceStatus?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                OutlinedTextField(
                    value = llmApiKey,
                    onValueChange = { viewModel.updateLlmApiKey(it) },
                    label = { Text("Cloud LLM API Key") },
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        SettingsSectionCard(accent = TranscriptionAccent, title = "Transcription (Audio)") {
            Text(
                "On-device speech-to-text (Vosk) for call/voice recordings — runs offline. Enable the " +
                    "Voice/Call Recordings source (Sources tab), then tap Download to fetch and install " +
                    "the model (~36 MB), or push your own to:\n" +
                    transcriptionModelPath,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { viewModel.downloadTranscriptionModel() },
                    enabled = transcriptionStatus?.startsWith("Downloading") != true
                ) { Text("Download model") }
                OutlinedButton(onClick = { viewModel.checkTranscriptionModel() }) {
                    Text("Check")
                }
            }
            transcriptionStatus?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            OutlinedTextField(
                value = sttApiKey,
                onValueChange = { viewModel.updateSttApiKey(it) },
                label = { Text("Cloud STT API Key (optional, future)") },
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth()
            )
        }

        SettingsSectionCard(accent = OcrAccent, title = "Screenshot OCR") {
            Text(
                "On-device text recognition (Tesseract) for screenshots — runs offline. Enable the " +
                    "Screenshots (OCR) source (Sources tab), then tap Download to fetch and install the " +
                    "model (~4 MB), or push eng.traineddata to:\n" +
                    ocrModelPath,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { viewModel.downloadOcrModel() },
                    enabled = ocrStatus?.startsWith("Downloading") != true
                ) { Text("Download model") }
                OutlinedButton(onClick = { viewModel.checkOcrModel() }) {
                    Text("Check")
                }
            }
            ocrStatus?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        SettingsSectionCard(accent = CalendarAccent, title = "Calendar Events") {
            Text(
                "Applied when you approve a reminder or dated to-do.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // Event duration picker
            val durations = listOf(15, 30, 45, 60, 90, 120)
            var durationExpanded by remember { mutableStateOf(false) }
            ExposedDropdownMenuBox(
                expanded = durationExpanded,
                onExpandedChange = { durationExpanded = it }
            ) {
                OutlinedTextField(
                    value = "$eventDurationMinutes min",
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Event duration") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = durationExpanded) },
                    modifier = Modifier.menuAnchor().fillMaxWidth()
                )
                ExposedDropdownMenu(
                    expanded = durationExpanded,
                    onDismissRequest = { durationExpanded = false }
                ) {
                    durations.forEach { minutes ->
                        DropdownMenuItem(
                            text = { Text("$minutes min") },
                            onClick = {
                                viewModel.updateEventDurationMinutes(minutes)
                                durationExpanded = false
                            }
                        )
                    }
                }
            }

            // Target calendar picker
            var calendarExpanded by remember { mutableStateOf(false) }
            val selectedCalendarName = when {
                calendarId == SettingsManager.CALENDAR_ID_AUTO -> "Automatic (primary)"
                else -> calendars.firstOrNull { it.id == calendarId }?.name ?: "Automatic (primary)"
            }
            ExposedDropdownMenuBox(
                expanded = calendarExpanded,
                onExpandedChange = { calendarExpanded = it }
            ) {
                OutlinedTextField(
                    value = selectedCalendarName,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Target calendar") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = calendarExpanded) },
                    modifier = Modifier.menuAnchor().fillMaxWidth()
                )
                ExposedDropdownMenu(
                    expanded = calendarExpanded,
                    onDismissRequest = { calendarExpanded = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("Automatic (primary)") },
                        onClick = {
                            viewModel.updateCalendarId(SettingsManager.CALENDAR_ID_AUTO)
                            calendarExpanded = false
                        }
                    )
                    calendars.forEach { option ->
                        DropdownMenuItem(
                            text = { Text(option.name) },
                            onClick = {
                                viewModel.updateCalendarId(option.id)
                                calendarExpanded = false
                            }
                        )
                    }
                }
            }
            if (calendars.isEmpty()) {
                Text(
                    "Enable the Calendar source (Sources tab) to choose a specific calendar.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        SettingsSectionCard(accent = Color(0xFF1976D2), title = "Data Egress (privacy)") {
            if (egressEvents.isEmpty()) {
                Text(
                    "✓ No data has left this device. Understanding runs on-device by default.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Text(
                    "Every time data left the device (metadata only — never content):",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                egressEvents.take(15).forEach { e ->
                    Text(
                        "${egressTimeFormat.format(java.util.Date(e.timestamp))} · ${e.host} — ${e.purpose}",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                TextButton(onClick = { viewModel.clearEgressLog() }) { Text("Clear log") }
            }
        }

        SettingsSectionCard(accent = Color(0xFF455A64), title = "Test Extraction (debug)") {
            Text(
                "Paste any message/email text and run it through the on-device pipeline — " +
                    "no SMS needed. Results land in the Inbox.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            OutlinedTextField(
                value = testInput,
                onValueChange = { testInput = it },
                label = { Text("Text to analyze") },
                minLines = 2,
                modifier = Modifier.fillMaxWidth()
            )
            Button(
                onClick = { viewModel.runTestExtraction(testInput) },
                enabled = !testRunning && testInput.isNotBlank()
            ) {
                Text(if (testRunning) "Running…" else "Run extraction")
            }
            testStatus?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        SettingsSectionCard(accent = Color(0xFF6D4C41), title = "Data Management") {
            Text(
                "Auto-delete old notes and export your data. Actioned suggestions are always cleaned up.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            val retentionOptions = listOf(0 to "Keep forever", 30 to "30 days", 90 to "90 days", 365 to "1 year")
            var retentionExpanded by remember { mutableStateOf(false) }
            val retentionLabel = retentionOptions.firstOrNull { it.first == retentionDays }?.second ?: "Keep forever"
            ExposedDropdownMenuBox(
                expanded = retentionExpanded,
                onExpandedChange = { retentionExpanded = it }
            ) {
                OutlinedTextField(
                    value = retentionLabel,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Keep notes for") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = retentionExpanded) },
                    modifier = Modifier.menuAnchor().fillMaxWidth()
                )
                ExposedDropdownMenu(
                    expanded = retentionExpanded,
                    onDismissRequest = { retentionExpanded = false }
                ) {
                    retentionOptions.forEach { (days, label) ->
                        DropdownMenuItem(
                            text = { Text(label) },
                            onClick = {
                                viewModel.updateRetentionDays(days)
                                retentionExpanded = false
                            }
                        )
                    }
                }
            }
            OutlinedButton(onClick = {
                AppLock.expectResult()
                exportLauncher.launch("taskmind-notes.json")
            }) {
                Text("Export Notes (JSON)")
            }
            exportStatus?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            HorizontalDivider()
            Text(
                "How often TaskMind scans recent data in the background. Less frequent saves battery.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            val scanOptions = listOf(15 to "Every 15 min", 30 to "Every 30 min", 60 to "Hourly", 180 to "Every 3 hours", 360 to "Every 6 hours")
            var scanExpanded by remember { mutableStateOf(false) }
            val scanLabel = scanOptions.firstOrNull { it.first == scanFrequencyMinutes }?.second ?: "Every 30 min"
            ExposedDropdownMenuBox(
                expanded = scanExpanded,
                onExpandedChange = { scanExpanded = it }
            ) {
                OutlinedTextField(
                    value = scanLabel,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Scan frequency") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = scanExpanded) },
                    modifier = Modifier.menuAnchor().fillMaxWidth()
                )
                ExposedDropdownMenu(
                    expanded = scanExpanded,
                    onDismissRequest = { scanExpanded = false }
                ) {
                    scanOptions.forEach { (minutes, label) ->
                        DropdownMenuItem(
                            text = { Text(label) },
                            onClick = {
                                viewModel.updateScanFrequency(minutes)
                                scanExpanded = false
                            }
                        )
                    }
                }
            }
        }

        SettingsSectionCard(accent = Color(0xFFAD1457), title = "Encrypted Backup & Restore") {
            Text(
                "Back up everything — notes, suggestions, and the database key — into a single file " +
                    "encrypted with a passphrase you choose. Nothing is readable without it. Restore " +
                    "replaces all current data and restarts the app.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            OutlinedButton(onClick = { passphraseInput = ""; passphraseMode = "backup" }) {
                Text("Back up (encrypted)")
            }
            OutlinedButton(onClick = { passphraseInput = ""; passphraseMode = "restore" }) {
                Text("Restore from backup")
            }
            backupStatus?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        SettingsSectionCard(accent = Color(0xFF00838F), title = "Permissions") {
            Text(
                "What TaskMind can access right now. Turn sources on/off in the Sources tab.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            permissions.forEach { p ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(p.label, style = MaterialTheme.typography.bodyMedium)
                    Text(
                        if (p.granted) "✓ Granted" else "✗ Not granted",
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (p.granted) Color(0xFF2E7D32) else MaterialTheme.colorScheme.error
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        var showDeleteDialog by remember { mutableStateOf(false) }
        Button(
            onClick = { showDeleteDialog = true },
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Delete All Private Data")
        }

        if (showDeleteDialog) {
            AlertDialog(
                onDismissRequest = { showDeleteDialog = false },
                title = { Text("Delete all data?") },
                text = {
                    Text(
                        "This permanently erases all approved notes, pending suggestions, " +
                            "source toggles, and saved keys/settings. This cannot be undone."
                    )
                },
                confirmButton = {
                    TextButton(onClick = {
                        viewModel.deleteAllData()
                        showDeleteDialog = false
                    }) {
                        Text("Delete", color = MaterialTheme.colorScheme.error)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDeleteDialog = false }) { Text("Cancel") }
                }
            )
        }

        // Passphrase prompt for backup/restore; on confirm it launches the file picker.
        passphraseMode?.let { mode ->
            val isBackup = mode == "backup"
            AlertDialog(
                onDismissRequest = { passphraseMode = null; passphraseInput = "" },
                title = { Text(if (isBackup) "Set a backup passphrase" else "Enter backup passphrase") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            if (isBackup)
                                "This passphrase encrypts your backup. If you lose it, the backup can't be opened — there's no recovery."
                            else
                                "Enter the passphrase used when this backup was created.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        OutlinedTextField(
                            value = passphraseInput,
                            onValueChange = { passphraseInput = it },
                            label = { Text("Passphrase") },
                            singleLine = true,
                            visualTransformation = PasswordVisualTransformation(),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                },
                confirmButton = {
                    TextButton(
                        enabled = passphraseInput.isNotBlank(),
                        onClick = {
                            pendingPassphrase = passphraseInput
                            passphraseMode = null
                            passphraseInput = ""
                            AppLock.expectResult()
                            if (isBackup) backupLauncher.launch("taskmind-backup-$backupDate.tmbk")
                            else restoreLauncher.launch(arrayOf("*/*"))
                        }
                    ) { Text("Continue") }
                },
                dismissButton = {
                    TextButton(onClick = { passphraseMode = null; passphraseInput = "" }) { Text("Cancel") }
                }
            )
        }

        // After a restore the live DB is closed; the app must restart to reopen the restored data.
        if (restartRequired) {
            AlertDialog(
                onDismissRequest = {},
                title = { Text("Restore complete") },
                text = { Text("TaskMind needs to restart to load the restored data.") },
                confirmButton = {
                    TextButton(onClick = { viewModel.restartApp() }) { Text("Restart now") }
                }
            )
        }
    }
}

/** The "0 B left your device today" privacy stat card. [outbound] = recorded egress events. */
@Composable
private fun PrivacyHero(outbound: Int) {
    val c = BoldTheme.colors
    Box(Modifier.fillMaxWidth().clip(ShapeHeroBig).background(c.surface).border(1.dp, c.line, ShapeHeroBig)) {
        Box(
            Modifier.align(Alignment.TopCenter).fillMaxWidth().height(140.dp)
                .background(Brush.verticalGradient(listOf(c.accentGlow, Color.Transparent)))
        )
        Column(
            Modifier.fillMaxWidth().padding(vertical = 26.dp, horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                Modifier.size(56.dp).clip(RoundedCornerShape(18.dp)).background(c.accent),
                contentAlignment = Alignment.Center
            ) { Icon(Icons.Outlined.Shield, contentDescription = null, tint = BoldOnAccent, modifier = Modifier.size(26.dp)) }
            Spacer(Modifier.height(16.dp))
            Text("$outbound", style = BoldType.privacyBig, color = if (outbound == 0) c.accent else c.ink)
            Spacer(Modifier.height(8.dp))
            Text(
                if (outbound == 0) "Nothing left your device today" else "left your device today",
                style = MaterialTheme.typography.bodyMedium, color = c.ink, fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(3.dp))
            Text("$outbound outbound · 0 trackers", style = BoldType.detailMeta, color = c.ink2)
        }
    }
}

@Composable
private fun SettingsSectionCard(
    accent: Color,
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    val c = BoldTheme.colors
    Column(
        Modifier.fillMaxWidth().clip(ShapePanel).background(c.surface).border(1.dp, c.line, ShapePanel).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            Box(Modifier.size(8.dp).clip(CircleShape).background(accent))
            Text(title, style = MaterialTheme.typography.titleMedium, color = c.ink, fontWeight = FontWeight.SemiBold)
        }
        content()
    }
}
