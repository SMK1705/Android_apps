package com.rajasudhan.taskmind.ui.settings

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CalendarContract
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rajasudhan.taskmind.data.local.TaskMindDao
import com.rajasudhan.taskmind.data.source.EgressLogger
import com.rajasudhan.taskmind.data.source.SettingsManager
import com.rajasudhan.taskmind.data.source.dataStore
import com.rajasudhan.taskmind.data.source.understanding.OnDeviceLlmProvider
import com.rajasudhan.taskmind.data.source.understanding.UnderstandingPipeline
import kotlinx.coroutines.flow.first
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
    @ApplicationContext private val context: Context
) : ViewModel() {

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
}
