package com.rajasudhan.taskmind.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp

import androidx.hilt.navigation.compose.hiltViewModel
import com.rajasudhan.taskmind.data.source.SettingsManager

private val EngineAccent = Color(0xFF7C4DFF)
private val TranscriptionAccent = Color(0xFF00897B)
private val CalendarAccent = Color(0xFF2E7D32)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val llmApiKey by viewModel.llmApiKey.collectAsState()
    val sttApiKey by viewModel.sttApiKey.collectAsState()
    val transcriptionStatus by viewModel.transcriptionStatus.collectAsState()
    val transcriptionModelPath = viewModel.transcriptionModelPath
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
    val exportStatus by viewModel.exportStatus.collectAsState()
    val permissions by viewModel.permissions.collectAsState()
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri -> uri?.let { viewModel.exportNotesToUri(it) } }

    LaunchedEffect(Unit) {
        viewModel.loadCalendars()
        viewModel.loadPermissionStatuses()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
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
                    "Voice/Call Recordings source (Sources tab), then push a Vosk model to:\n" +
                    transcriptionModelPath,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            OutlinedButton(onClick = { viewModel.checkTranscriptionModel() }) {
                Text("Check transcription model")
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
            OutlinedButton(onClick = { exportLauncher.launch("taskmind-notes.json") }) {
                Text("Export Notes (JSON)")
            }
            exportStatus?.let {
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
    }
}

@Composable
private fun SettingsSectionCard(
    accent: Color,
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.height(IntrinsicSize.Min)) {
            Box(modifier = Modifier.width(6.dp).fillMaxHeight().background(accent))
            Column(
                modifier = Modifier.weight(1f).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium,
                    color = accent,
                    fontWeight = FontWeight.Bold
                )
                content()
            }
        }
    }
}
