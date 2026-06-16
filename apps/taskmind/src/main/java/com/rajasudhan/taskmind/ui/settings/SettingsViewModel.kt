package com.rajasudhan.taskmind.ui.settings

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
import com.rajasudhan.taskmind.data.local.TaskMindDao
import com.rajasudhan.taskmind.data.model.Note
import com.rajasudhan.taskmind.data.source.EgressLogger
import com.rajasudhan.taskmind.data.source.SettingsManager
import com.rajasudhan.taskmind.data.source.dataStore
import com.rajasudhan.taskmind.data.source.ocr.OcrEngine
import com.rajasudhan.taskmind.data.source.transcription.VoskTranscriber
import com.rajasudhan.taskmind.data.source.understanding.OnDeviceLlmProvider
import com.rajasudhan.taskmind.data.source.understanding.UnderstandingPipeline
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
    private val ocrEngine: OcrEngine,
    private val moshi: Moshi,
    @ApplicationContext private val context: Context
) : ViewModel() {

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

    private val _sttApiKey = MutableStateFlow(settingsManager.sttApiKey)
    val sttApiKey: StateFlow<String> = _sttApiKey

    private val _useOnDeviceLlm = MutableStateFlow(settingsManager.useOnDeviceLlm)
    val useOnDeviceLlm: StateFlow<Boolean> = _useOnDeviceLlm

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

    fun updateSttApiKey(key: String) {
        settingsManager.sttApiKey = key
        _sttApiKey.value = key
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

    /** Runs arbitrary text through the real pipeline (on-device model) — for testing without SMS. */
    fun runTestExtraction(text: String) {
        if (text.isBlank() || _testRunning.value) return
        viewModelScope.launch {
            _testRunning.value = true
            _testStatus.value = "Running on-device extraction…"
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

    /** Probes whether the on-device Gemma model is present and loads on this device. */
    fun checkOnDeviceModel() {
        viewModelScope.launch {
            _onDeviceStatus.value = "Checking on-device model…"
            val err = onDeviceLlm.tryLoad()
            _onDeviceStatus.value = when {
                err == null ->
                    "✓ On-device Gemma model loaded — runs offline, no API cost."
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
            settingsManager.clearSettings()
            context.dataStore.edit { it.clear() }

            // Reflect the reset in the UI immediately.
            _llmApiKey.value = settingsManager.llmApiKey
            _sttApiKey.value = settingsManager.sttApiKey
            _useOnDeviceLlm.value = settingsManager.useOnDeviceLlm
            _eventDurationMinutes.value = settingsManager.eventDurationMinutes
            _calendarId.value = settingsManager.calendarId
            _modelPath.value = settingsManager.onDeviceModelPath
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

    // ---- Permissions summary ----

    private val _permissions = MutableStateFlow<List<PermissionStatus>>(emptyList())
    val permissions: StateFlow<List<PermissionStatus>> = _permissions

    fun loadPermissionStatuses() {
        fun granted(p: String) =
            ContextCompat.checkSelfPermission(context, p) == PackageManager.PERMISSION_GRANTED
        val exactAlarms = context.getSystemService(AlarmManager::class.java)?.canScheduleExactAlarms() == true
        val notifAccess = NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)
        val usageAccess = runCatching {
            context.getSystemService(AppOpsManager::class.java)?.unsafeCheckOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName
            ) == AppOpsManager.MODE_ALLOWED
        }.getOrDefault(false)

        _permissions.value = listOf(
            PermissionStatus("SMS", granted(Manifest.permission.READ_SMS)),
            PermissionStatus("Call log", granted(Manifest.permission.READ_CALL_LOG)),
            PermissionStatus("Calendar", granted(Manifest.permission.READ_CALENDAR)),
            PermissionStatus("Audio files", granted(Manifest.permission.READ_MEDIA_AUDIO)),
            PermissionStatus("Post notifications", granted(Manifest.permission.POST_NOTIFICATIONS)),
            PermissionStatus("Notification access", notifAccess),
            PermissionStatus("Usage access", usageAccess),
            PermissionStatus("Exact alarms", exactAlarms),
            PermissionStatus("Gmail connected", settingsManager.gmailAccounts.isNotEmpty())
        )
    }
}

/** One row in the permissions summary panel. */
data class PermissionStatus(val label: String, val granted: Boolean)
