package com.rajasudhan.taskmind.ui.settings

import android.os.Build
import com.rajasudhan.taskmind.data.source.canScheduleExactAlarmsCompat

import android.Manifest
import android.app.AlarmManager
import android.app.AppOpsManager
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Process
import android.provider.CalendarContract
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rajasudhan.taskmind.TaskMindApp
import com.rajasudhan.taskmind.data.local.DatabaseRecovery
import com.rajasudhan.taskmind.data.local.TaskMindDao
import com.rajasudhan.taskmind.data.model.Note
import com.rajasudhan.taskmind.data.source.BackupManager
import com.rajasudhan.taskmind.data.source.SnapshotManager
import com.rajasudhan.taskmind.data.source.EgressLogger
import com.rajasudhan.taskmind.data.source.ModelDownloader
import com.rajasudhan.taskmind.data.source.SettingsManager
import com.rajasudhan.taskmind.data.source.dataStore
import com.rajasudhan.taskmind.data.source.ocr.OcrEngine
import com.rajasudhan.taskmind.data.source.transcription.VoskTranscriber
import com.rajasudhan.taskmind.data.source.transcription.WhisperTranscriber
import com.rajasudhan.taskmind.data.source.understanding.OnDeviceEngineOption
import com.rajasudhan.taskmind.data.source.understanding.OnDeviceLlmProvider
import com.rajasudhan.taskmind.data.source.understanding.UnderstandingPipeline
import com.rajasudhan.taskmind.ui.theme.ThemeMode
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

/** A calendar the user can target for new events. */
data class CalendarOption(val id: Long, val name: String)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsManager: SettingsManager,
    private val dao: TaskMindDao,
    private val onDeviceLlm: OnDeviceLlmProvider,
    private val understandingPipeline: UnderstandingPipeline,
    private val egressLogger: EgressLogger,
    private val voskTranscriber: VoskTranscriber,
    private val whisperTranscriber: WhisperTranscriber,
    private val ocrEngine: OcrEngine,
    private val modelDownloader: ModelDownloader,
    private val backupManager: BackupManager,
    private val snapshotManager: SnapshotManager,
    private val moshi: Moshi,
    private val dailyBriefScheduler: com.rajasudhan.taskmind.data.source.DailyBriefScheduler,
    private val weeklyWinsScheduler: com.rajasudhan.taskmind.data.source.WeeklyWinsScheduler,
    @ApplicationContext private val context: Context
) : ViewModel() {

    // ---- Appearance (theme) ----
    val themeMode: StateFlow<ThemeMode> = settingsManager.themeModeFlow
    fun updateThemeMode(mode: ThemeMode) { settingsManager.themeMode = mode }

    // ---- Security (app lock) ----
    val appLockEnabled: StateFlow<Boolean> = settingsManager.appLockEnabledFlow
    fun updateAppLockEnabled(enabled: Boolean) { settingsManager.appLockEnabled = enabled }

    // ---- Transcription (on-device Vosk) ----
    private val _transcriptionStatus = MutableStateFlow<String?>(null)
    val transcriptionStatus: StateFlow<String?> = _transcriptionStatus
    val transcriptionModelPath: String get() = voskTranscriber.modelDirPath()

    fun checkTranscriptionModel() {
        viewModelScope.launch {
            _transcriptionStatus.value = "Checking transcription model…"
            val err = voskTranscriber.tryLoad()
            _transcriptionStatus.value = if (err == null) {
                "✓ Vosk model loaded — transcription runs offline."
            } else {
                "Model not ready: ${err.message ?: err::class.java.simpleName}"
            }
        }
    }

    /** Download + install the Vosk model from inside the app, then verify it loads. */
    fun downloadTranscriptionModel() {
        viewModelScope.launch {
            _transcriptionStatus.value = "Downloading model… 0%"
            val err = modelDownloader.download(
                "https://alphacephei.com/vosk/models/vosk-model-small-en-in-0.4.zip",
                File(context.filesDir, "vosk-model.zip")
            ) { pct -> _transcriptionStatus.value = "Downloading model… $pct%" }
            if (err != null) {
                _transcriptionStatus.value = "Download failed: ${err.message ?: err::class.java.simpleName}"
                return@launch
            }
            // Drop any previously-unpacked model so the freshly downloaded zip is the one that loads.
            File(context.filesDir, "vosk-model").deleteRecursively()
            _transcriptionStatus.value = "Installing…"
            checkTranscriptionModel()
        }
    }

    // ---- OCR (on-device Tesseract) ----
    private val _ocrStatus = MutableStateFlow<String?>(null)
    val ocrStatus: StateFlow<String?> = _ocrStatus
    val ocrModelPath: String get() = ocrEngine.tessDataPath()

    fun checkOcrModel() {
        viewModelScope.launch {
            _ocrStatus.value = "Checking OCR model…"
            val err = ocrEngine.tryLoad()
            _ocrStatus.value = if (err == null) {
                "✓ Tesseract model loaded — screenshot OCR runs offline."
            } else {
                "Model not ready: ${err.message ?: err::class.java.simpleName}"
            }
        }
    }

    /** Download + install the Tesseract English model from inside the app, then verify it loads. */
    fun downloadOcrModel() {
        viewModelScope.launch {
            _ocrStatus.value = "Downloading model… 0%"
            val err = modelDownloader.download(
                "https://github.com/tesseract-ocr/tessdata_fast/raw/main/eng.traineddata",
                File(File(context.filesDir, "tessdata"), "eng.traineddata")
            ) { pct -> _ocrStatus.value = "Downloading model… $pct%" }
            if (err != null) {
                _ocrStatus.value = "Download failed: ${err.message ?: err::class.java.simpleName}"
                return@launch
            }
            checkOcrModel()
        }
    }

    val egressEvents = egressLogger.events

    fun clearEgressLog() = egressLogger.clear()

    private val _onDeviceStatus = MutableStateFlow<String?>(null)
    val onDeviceStatus: StateFlow<String?> = _onDeviceStatus

    private val _testStatus = MutableStateFlow<String?>(null)
    val testStatus: StateFlow<String?> = _testStatus
    private val _testRunning = MutableStateFlow(false)
    val testRunning: StateFlow<Boolean> = _testRunning

    private val _modelPath = MutableStateFlow(settingsManager.onDeviceModelPath)
    val modelPath: StateFlow<String> = _modelPath

    /** The location used when no custom path is set — shown as a hint / adb-push target. */
    val defaultModelPath: String get() = onDeviceLlm.defaultModelFile().absolutePath

    private val _llmApiKey = MutableStateFlow(settingsManager.llmApiKey)
    val llmApiKey: StateFlow<String> = _llmApiKey

    // Whisper second pass (#126): the on/off toggle plus whether its ggml model is present. The native
    // engine is deferred (#207), so the toggle can be on while the pass is still a no-op.
    private val _whisperSecondPass = MutableStateFlow(settingsManager.whisperSecondPassEnabled)
    val whisperSecondPass: StateFlow<Boolean> = _whisperSecondPass

    private val _whisperModelPresent = MutableStateFlow(whisperTranscriber.isModelPresent())
    val whisperModelPresent: StateFlow<Boolean> = _whisperModelPresent

    /** Where to adb-push the quantized ggml Whisper model. */
    val whisperModelPath: String get() = whisperTranscriber.modelPath()

    private val _useOnDeviceLlm = MutableStateFlow(settingsManager.useOnDeviceLlm)
    val useOnDeviceLlm: StateFlow<Boolean> = _useOnDeviceLlm

    // Which on-device engine runs extraction (#214): "mediapipe" (Gemma via MediaPipe) or "nano" (system
    // Gemini Nano via ML Kit / AICore). LiteRT-LM is a scaffold (#222) so it's not offered here yet.
    private val _onDeviceEngine = MutableStateFlow(settingsManager.onDeviceEngine)
    val onDeviceEngine: StateFlow<String> = _onDeviceEngine

    private val _eventDurationMinutes = MutableStateFlow(settingsManager.eventDurationMinutes)
    val eventDurationMinutes: StateFlow<Int> = _eventDurationMinutes

    private val _calendarId = MutableStateFlow(settingsManager.calendarId)
    val calendarId: StateFlow<Long> = _calendarId

    private val _calendars = MutableStateFlow<List<CalendarOption>>(emptyList())
    val calendars: StateFlow<List<CalendarOption>> = _calendars

    fun updateLlmApiKey(key: String) {
        settingsManager.llmApiKey = key
        _llmApiKey.value = key
    }

    fun setWhisperSecondPass(enabled: Boolean) {
        settingsManager.whisperSecondPassEnabled = enabled
        _whisperSecondPass.value = enabled
    }

    /** Re-check whether the Whisper model is present (after a download or adb push). */
    fun refreshWhisperModel() { _whisperModelPresent.value = whisperTranscriber.isModelPresent() }

    // ---- Whisper model download (#241): one-tap, public ggml — no adb push needed ----
    private val _whisperStatus = MutableStateFlow<String?>(null)
    val whisperStatus: StateFlow<String?> = _whisperStatus

    /** Download + install the multilingual base ggml model, then re-check presence. */
    fun downloadWhisperModel() {
        viewModelScope.launch {
            _whisperStatus.value = "Downloading model… 0%"
            val err = modelDownloader.download(
                "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-base-q5_1.bin",
                File(whisperTranscriber.modelPath()),
            ) { pct -> _whisperStatus.value = "Downloading model… $pct%" }
            refreshWhisperModel()
            _whisperStatus.value = when {
                err != null -> "Download failed: ${err.message ?: err::class.java.simpleName}"
                _whisperModelPresent.value -> "✓ Whisper model installed (~57 MB)."
                else -> "Install failed."
            }
        }
    }

    /** Delete the Whisper model to reclaim space. */
    fun removeWhisperModel() {
        val f = File(whisperTranscriber.modelPath())
        val freedMb = if (f.exists()) f.length() / (1024 * 1024) else 0L
        f.delete()
        refreshWhisperModel()
        _whisperStatus.value = if (freedMb > 0) "Removed — freed ~$freedMb MB." else "No model to remove."
    }

    // ---- On-device LLM model download (#241): license-gated Gemma, so open the license page + one HF token ----
    /** A downloadable on-device LLM model: a Hugging Face license page + the gated file URL. */
    data class LlmModelOption(
        val label: String,
        val sizeLabel: String,
        val url: String,
        val licenseUrl: String,
        val fileName: String,
    )

    val llmModelOptions = listOf(
        LlmModelOption(
            label = "Gemma 3 · 1B · int4",
            sizeLabel = "~550 MB",
            url = "https://huggingface.co/litert-community/Gemma3-1B-IT/resolve/main/" +
                "Gemma3-1B-IT_multi-prefill-seq_q4_ekv2048.task",
            licenseUrl = "https://huggingface.co/litert-community/Gemma3-1B-IT",
            fileName = "model.task",
        ),
    )

    private val _llmModelPresent = MutableStateFlow(onDeviceLlm.isModelPresent())
    val llmModelPresent: StateFlow<Boolean> = _llmModelPresent

    private val _llmStatus = MutableStateFlow<String?>(null)
    val llmStatus: StateFlow<String?> = _llmStatus

    // Session-only — a secret we don't persist. Needed only to fetch a license-gated model.
    private val _hfToken = MutableStateFlow("")
    val hfToken: StateFlow<String> = _hfToken
    fun updateHfToken(t: String) { _hfToken.value = t }

    /** Download the chosen Gemma model into the on-device LLM's model path (Bearer-auth for the gated host). */
    fun downloadLlmModel(option: LlmModelOption) {
        viewModelScope.launch {
            _llmStatus.value = "Downloading ${option.label}… 0%"
            val dest = File(context.filesDir, option.fileName)
            val err = modelDownloader.download(option.url, dest, _hfToken.value.trim().ifBlank { null }) { pct ->
                _llmStatus.value = "Downloading ${option.label}… $pct%"
            }
            _llmModelPresent.value = onDeviceLlm.isModelPresent()
            _llmStatus.value = when {
                err != null -> "Download failed: ${err.message ?: err::class.java.simpleName}. " +
                    "If it's a 401/403, accept the license on the opened page and paste a Hugging Face token."
                _llmModelPresent.value -> "✓ Installed ${option.label}. Tap “Check on-device model” to load it."
                else -> "Install failed."
            }
        }
    }

    /** Delete the on-device LLM model(s) to reclaim space (they're large). */
    fun removeLlmModel() {
        var freedMb = 0L
        listOf("model.task", "model.litertlm").forEach {
            val f = File(context.filesDir, it)
            if (f.exists()) { freedMb += f.length() / (1024 * 1024); f.delete() }
        }
        _llmModelPresent.value = onDeviceLlm.isModelPresent()
        _llmStatus.value = if (freedMb > 0) "Removed — freed ~$freedMb MB." else "No model to remove."
    }

    fun updateUseOnDeviceLlm(useOnDevice: Boolean) {
        settingsManager.useOnDeviceLlm = useOnDevice
        _useOnDeviceLlm.value = useOnDevice
    }

    fun updateModelPath(path: String) {
        settingsManager.onDeviceModelPath = path
        _modelPath.value = path
        _onDeviceStatus.value = null // force a fresh check after a path change
    }

    fun updateOnDeviceEngine(id: String) {
        settingsManager.onDeviceEngine = id
        _onDeviceEngine.value = id
        _onDeviceStatus.value = null // force a fresh check after an engine change
    }

    /** Runs arbitrary text through the real routed pipeline (on-device or cloud) — for testing without SMS. */
    fun runTestExtraction(text: String) {
        if (text.isBlank() || _testRunning.value) return
        viewModelScope.launch {
            _testRunning.value = true
            _testStatus.value = "Running extraction…"
            val before = dao.getPendingSuggestions().first().size
            understandingPipeline.processText("Manual test", text)
            val created = dao.getPendingSuggestions().first().size - before
            _testStatus.value = if (created > 0) {
                "✓ Created $created suggestion(s) — check the Inbox."
            } else {
                "No action item found (non-actionable, low confidence, or duplicate)."
            }
            _testRunning.value = false
        }
    }

    /** Probes whether the SELECTED on-device engine (Gemma/MediaPipe or Gemini Nano) is usable here. */
    fun checkOnDeviceModel() {
        viewModelScope.launch {
            val isNano = onDeviceLlm.selectedEngineOption() == OnDeviceEngineOption.NANO
            val engineName = if (isNano) "Gemini Nano" else "Gemma (MediaPipe)"
            _onDeviceStatus.value = "Checking $engineName…"
            // The selected engine's OWN status (no fallback), so the label is truthful about what was picked.
            val err = onDeviceLlm.checkSelectedEngine()
            _onDeviceStatus.value = when {
                err == null && isNano ->
                    "✓ Gemini Nano is ready — runs on-device via AICore, zero download, no API cost."
                err == null ->
                    "✓ On-device Gemma model loaded — runs offline, no API cost."
                isNano ->
                    "Gemini Nano isn't usable here: ${err.message ?: err::class.java.simpleName}"
                !onDeviceLlm.isModelPresent() ->
                    "No model found. Push a Gemma .task file to:\n${onDeviceLlm.modelFile().absolutePath}"
                else ->
                    "Model present but failed to load:\n${err.message ?: err::class.java.simpleName}"
            }
        }
    }

    fun updateEventDurationMinutes(minutes: Int) {
        settingsManager.eventDurationMinutes = minutes
        _eventDurationMinutes.value = minutes
    }

    fun updateCalendarId(id: Long) {
        settingsManager.calendarId = id
        _calendarId.value = id
    }

    /**
     * Wipes all stored data: approved notes, pending suggestions, source toggles, and settings
     * (keys/provider/calendar prefs). The DB encryption key is kept so the emptied DB stays usable.
     */
    fun deleteAllData(onDone: () -> Unit = {}) {
        viewModelScope.launch {
            dao.deleteAllNotes()
            dao.deleteAllSuggestions()
            dao.deleteAllRejectedPatterns() // also forget the on-device rejection learning
            settingsManager.clearSettings()
            context.dataStore.edit { it.clear() }

            // Reflect the reset in the UI immediately.
            _llmApiKey.value = settingsManager.llmApiKey
            _whisperSecondPass.value = settingsManager.whisperSecondPassEnabled
            _useOnDeviceLlm.value = settingsManager.useOnDeviceLlm
            _eventDurationMinutes.value = settingsManager.eventDurationMinutes
            _calendarId.value = settingsManager.calendarId
            _modelPath.value = settingsManager.onDeviceModelPath
            _onDeviceEngine.value = settingsManager.onDeviceEngine // back to the "mediapipe" default
            onDone()
        }
    }

    /** Loads the writable calendars to populate the picker. Safe to call repeatedly. */
    fun loadCalendars() {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR)
            != PackageManager.PERMISSION_GRANTED
        ) {
            _calendars.value = emptyList()
            return
        }
        val projection = arrayOf(
            CalendarContract.Calendars._ID,
            CalendarContract.Calendars.CALENDAR_DISPLAY_NAME,
            CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL
        )
        val result = mutableListOf<CalendarOption>()
        context.contentResolver.query(
            CalendarContract.Calendars.CONTENT_URI,
            projection,
            null,
            null,
            "${CalendarContract.Calendars.IS_PRIMARY} DESC"
        )?.use { cursor ->
            while (cursor.moveToNext()) {
                if (cursor.getInt(2) >= CalendarContract.Calendars.CAL_ACCESS_CONTRIBUTOR) {
                    result.add(CalendarOption(cursor.getLong(0), cursor.getString(1) ?: "Calendar"))
                }
            }
        }
        _calendars.value = result
    }

    // ---- Data management ----

    private val _retentionDays = MutableStateFlow(settingsManager.retentionDays)
    val retentionDays: StateFlow<Int> = _retentionDays

    fun updateRetentionDays(days: Int) {
        settingsManager.retentionDays = days
        _retentionDays.value = days
    }

    private val _exportStatus = MutableStateFlow<String?>(null)
    val exportStatus: StateFlow<String?> = _exportStatus

    /** Serializes all notes as JSON and writes them to [uri] (from ACTION_CREATE_DOCUMENT). */
    fun exportNotesToUri(uri: Uri) {
        viewModelScope.launch {
            val count = withContext(Dispatchers.IO) {
                runCatching {
                    val notes = dao.getNotesList()
                    val type = Types.newParameterizedType(List::class.java, Note::class.java)
                    val json = moshi.adapter<List<Note>>(type).indent("  ").toJson(notes)
                    context.contentResolver.openOutputStream(uri)?.use { it.write(json.toByteArray()) }
                    notes.size
                }.getOrNull()
            }
            _exportStatus.value = if (count != null) "✓ Exported $count note(s)." else "Export failed."
        }
    }

    /**
     * Exports all notes as plain-text Markdown to [uri] — an open-format "exit ramp" (#121) that stays
     * readable in any editor, even without TaskMind. Rendering is delegated to the pure [NotesMarkdown].
     */
    fun exportNotesAsMarkdownToUri(uri: Uri) {
        viewModelScope.launch {
            val count = withContext(Dispatchers.IO) {
                runCatching {
                    val notes = dao.getNotesList()
                    val markdown = com.rajasudhan.taskmind.data.source.NotesMarkdown.render(notes)
                    context.contentResolver.openOutputStream(uri)?.use { it.write(markdown.toByteArray()) }
                    notes.size
                }.getOrNull()
            }
            _exportStatus.value = if (count != null) "✓ Exported $count note(s) as Markdown." else "Export failed."
        }
    }

    // ---- Local auto-snapshot safety net (#161) ----

    /** A one-line description of the newest automatic snapshot, or null if none has been written yet. */
    private val _snapshotInfo = MutableStateFlow<String?>(null)
    val snapshotInfo: StateFlow<String?> = _snapshotInfo

    /** True if the DB was ever reset (quarantined) — surfaces a "restore from snapshot?" nudge. */
    private val _databaseWasReset = MutableStateFlow(false)
    val databaseWasReset: StateFlow<Boolean> = _databaseWasReset

    private val _snapshotStatus = MutableStateFlow<String?>(null)
    val snapshotStatus: StateFlow<String?> = _snapshotStatus

    private fun dbFile(): File = context.getDatabasePath("taskmind_db")

    /** Loads the latest-snapshot summary and reset state. Called from the screen on open (LaunchedEffect). */
    fun refreshSnapshotInfo() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                _databaseWasReset.value = DatabaseRecovery.hasQuarantine(dbFile())
                _snapshotInfo.value = snapshotManager.latest()?.let { file ->
                    val stamp = java.text.SimpleDateFormat("MMM d, HH:mm", java.util.Locale.getDefault())
                        .format(java.util.Date(file.lastModified()))
                    "Last automatic snapshot: $stamp"
                }
            }
        }
    }

    /**
     * Restores notes from the latest local snapshot (inserted as new rows, reminders/geofences re-armed
     * — see [SnapshotManager]). Unlike the encrypted restore this doesn't swap the DB file, so no restart
     * is needed; the notes flow just re-emits. Only clears the quarantine marker on a real success — a
     * failure (corrupt/unreadable snapshot) is reported distinctly and leaves the reset nudge up.
     */
    fun restoreFromSnapshot() {
        viewModelScope.launch {
            val outcome = runCatching { snapshotManager.restoreLatest() }
            when {
                outcome.isFailure -> _snapshotStatus.value = "Restore failed — the snapshot couldn't be read."
                outcome.getOrNull() == null -> _snapshotStatus.value = "No local snapshot to restore."
                else -> {
                    withContext(Dispatchers.IO) { DatabaseRecovery.clearQuarantine(dbFile()) }
                    _databaseWasReset.value = false
                    _snapshotStatus.value = "✓ Restored ${outcome.getOrNull()} note(s) from the last snapshot."
                }
            }
        }
    }

    // ---- Scan frequency (reschedules the periodic WorkManager job) ----

    private val _scanFrequencyMinutes = MutableStateFlow(settingsManager.scanFrequencyMinutes)
    val scanFrequencyMinutes: StateFlow<Int> = _scanFrequencyMinutes

    fun updateScanFrequency(minutes: Int) {
        settingsManager.scanFrequencyMinutes = minutes
        _scanFrequencyMinutes.value = minutes
        TaskMindApp.scheduleScan(context, minutes.toLong(), replace = true)
    }

    // ── Daily brief (morning) ──
    private val _dailyBriefEnabled = MutableStateFlow(settingsManager.dailyBriefEnabled)
    val dailyBriefEnabled: StateFlow<Boolean> = _dailyBriefEnabled

    private val _dailyBriefHour = MutableStateFlow(settingsManager.dailyBriefHour)
    val dailyBriefHour: StateFlow<Int> = _dailyBriefHour

    fun updateDailyBriefEnabled(enabled: Boolean) {
        settingsManager.dailyBriefEnabled = enabled
        _dailyBriefEnabled.value = enabled
        applyDailyBriefSchedule()
    }

    fun updateDailyBriefHour(hour: Int) {
        settingsManager.dailyBriefHour = hour
        _dailyBriefHour.value = hour
        if (_dailyBriefEnabled.value) applyDailyBriefSchedule()
    }

    private fun applyDailyBriefSchedule() {
        dailyBriefScheduler.reschedule(
            _dailyBriefEnabled.value, _dailyBriefHour.value, settingsManager.dailyBriefMinute
        )
    }

    // ── Weekly wins (Sunday recap) ──
    private val _weeklyWinsEnabled = MutableStateFlow(settingsManager.weeklyWinsEnabled)
    val weeklyWinsEnabled: StateFlow<Boolean> = _weeklyWinsEnabled

    private val _weeklyWinsHour = MutableStateFlow(settingsManager.weeklyWinsHour)
    val weeklyWinsHour: StateFlow<Int> = _weeklyWinsHour

    fun updateWeeklyWinsEnabled(enabled: Boolean) {
        settingsManager.weeklyWinsEnabled = enabled
        _weeklyWinsEnabled.value = enabled
        applyWeeklyWinsSchedule()
    }

    fun updateWeeklyWinsHour(hour: Int) {
        settingsManager.weeklyWinsHour = hour
        _weeklyWinsHour.value = hour
        if (_weeklyWinsEnabled.value) applyWeeklyWinsSchedule()
    }

    private fun applyWeeklyWinsSchedule() {
        weeklyWinsScheduler.reschedule(_weeklyWinsEnabled.value, _weeklyWinsHour.value)
    }

    // ---- Encrypted backup / restore ----

    private val _backupStatus = MutableStateFlow<String?>(null)
    val backupStatus: StateFlow<String?> = _backupStatus

    /** Set after a successful restore; the UI shows a blocking "restart now" prompt. */
    private val _restartRequired = MutableStateFlow(false)
    val restartRequired: StateFlow<Boolean> = _restartRequired

    /** Encrypts a full DB backup (DB + key) under [passphrase] and writes it to [uri]. */
    fun backupToUri(uri: Uri, passphrase: String) {
        if (passphrase.length < MIN_PASSPHRASE_LENGTH) {
            _backupStatus.value = "Use a passphrase of at least $MIN_PASSPHRASE_LENGTH characters."
            return
        }
        viewModelScope.launch {
            _backupStatus.value = "Encrypting backup…"
            val result = withContext(Dispatchers.IO) { backupManager.backup(uri, passphrase.toCharArray()) }
            _backupStatus.value = when (result) {
                is BackupManager.Result.Success -> result.message
                is BackupManager.Result.Failure -> result.message
            }
        }
    }

    /** Decrypts and restores a backup from [uri]; on success the app must restart ([restartApp]). */
    fun restoreFromUri(uri: Uri, passphrase: String) {
        if (passphrase.isEmpty()) {
            _backupStatus.value = "Enter the backup's passphrase."
            return
        }
        viewModelScope.launch {
            _backupStatus.value = "Decrypting & restoring…"
            val result = withContext(Dispatchers.IO) { backupManager.restore(uri, passphrase.toCharArray()) }
            when (result) {
                is BackupManager.Result.Success -> {
                    _backupStatus.value = result.message
                    _restartRequired.value = true
                }
                is BackupManager.Result.Failure -> _backupStatus.value = result.message
            }
        }
    }

    fun restartApp() = backupManager.scheduleRestartAndExit()

    // ---- Permissions summary ----

    private val _permissions = MutableStateFlow<List<PermissionStatus>>(emptyList())
    val permissions: StateFlow<List<PermissionStatus>> = _permissions

    fun loadPermissionStatuses() {
        fun granted(p: String) =
            ContextCompat.checkSelfPermission(context, p) == PackageManager.PERMISSION_GRANTED
        val exactAlarms = context.getSystemService(AlarmManager::class.java)?.canScheduleExactAlarmsCompat() == true
        val notifAccess = NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)
        val usageAccess = runCatching {
            val appOps = context.getSystemService(AppOpsManager::class.java)
            val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                appOps?.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
            } else {
                @Suppress("DEPRECATION")
                appOps?.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
            }
            mode == AppOpsManager.MODE_ALLOWED
        }.getOrDefault(false)

        _permissions.value = listOf(
            PermissionStatus("SMS", granted(Manifest.permission.READ_SMS)),
            PermissionStatus("Call log", granted(Manifest.permission.READ_CALL_LOG)),
            PermissionStatus("Calendar", granted(Manifest.permission.READ_CALENDAR)),
            PermissionStatus("Contacts", granted(Manifest.permission.READ_CONTACTS)),
            PermissionStatus("Audio files", granted(Manifest.permission.READ_MEDIA_AUDIO)),
            PermissionStatus("Photos & screenshots", granted(Manifest.permission.READ_MEDIA_IMAGES)),
            PermissionStatus("Microphone", granted(Manifest.permission.RECORD_AUDIO)),
            PermissionStatus("Location", granted(Manifest.permission.ACCESS_FINE_LOCATION)),
            PermissionStatus("Background location", granted(Manifest.permission.ACCESS_BACKGROUND_LOCATION)),
            PermissionStatus("Post notifications", granted(Manifest.permission.POST_NOTIFICATIONS)),
            PermissionStatus("Notification access", notifAccess),
            PermissionStatus("Usage access", usageAccess),
            PermissionStatus("Exact alarms", exactAlarms),
            PermissionStatus("Gmail connected", settingsManager.gmailAccounts.isNotEmpty())
        )
    }

    companion object {
        private const val MIN_PASSPHRASE_LENGTH = 6
    }
}

/** One row in the permissions summary panel. */
data class PermissionStatus(val label: String, val granted: Boolean)
