package com.rajasudhan.taskmind.ui.notes

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rajasudhan.taskmind.data.local.TaskMindDao
import com.rajasudhan.taskmind.data.model.Note
import com.rajasudhan.taskmind.data.source.AlarmScheduler
import com.rajasudhan.taskmind.data.source.GeofenceManager
import com.rajasudhan.taskmind.data.source.PlaceGeocoder
import com.rajasudhan.taskmind.data.source.RecurrenceUtil
import com.rajasudhan.taskmind.data.source.SettingsManager
import com.rajasudhan.taskmind.data.source.understanding.LlmProvider
import com.rajasudhan.taskmind.data.source.understanding.MagicBreakdown
import com.rajasudhan.taskmind.data.source.understanding.OnDeviceLlmProvider
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Backs [NoteDetailScreen]; loads a single note by the `noteId` nav argument. */
@HiltViewModel
class NoteDetailViewModel @Inject constructor(
    private val dao: TaskMindDao,
    private val alarmScheduler: AlarmScheduler,
    private val geofenceManager: GeofenceManager,
    private val onDeviceLlm: OnDeviceLlmProvider,
    private val llm: LlmProvider,
    private val settingsManager: SettingsManager,
    private val placeGeocoder: PlaceGeocoder,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val noteId: Int = savedStateHandle.get<Int>("noteId") ?: -1

    val note: StateFlow<Note?> = dao.getNoteById(noteId)
        .stateIn(viewModelScope, SharingStarted.Lazily, null)

    /** True while a Magic Breakdown is running, so the UI can show a spinner. */
    private val _breakingDown = MutableStateFlow(false)
    val breakingDown: StateFlow<Boolean> = _breakingDown

    /** Deletes the note and tears down its alarm + geofence; [onDeleted] runs after, to pop back. */
    fun deleteNote(onDeleted: () -> Unit) {
        val current = note.value ?: return
        viewModelScope.launch {
            alarmScheduler.cancel(current.id)
            geofenceManager.remove(current.id)
            dao.deleteNote(current)
            onDeleted()
        }
    }

    fun setCompleted(completed: Boolean) {
        val current = note.value ?: return
        viewModelScope.launch {
            dao.setNoteCompleted(current.id, completed, if (completed) System.currentTimeMillis() else null)
        }
    }

    /** Persists a toggled/reordered checklist (encoded by [Checklist.encode]). */
    fun updateChecklist(encoded: String) {
        val current = note.value ?: return
        viewModelScope.launch { dao.updateNoteChecklist(current.id, encoded) }
    }

    /**
     * Magic Breakdown: ask the model to split this task into concrete steps and save them as the
     * note's checklist, via [LlmProvider.generateList]. The routed provider picks the backend —
     * on-device Gemma (free-form) when selected and present, or the cloud model, which pins its reply
     * to an array-of-strings schema so cloud output is clean steps instead of the extraction shape.
     * [onResult] reports a short snackbar message, with a mode-aware nudge when neither backend is
     * usable. Leaves any existing checklist untouched when nothing usable comes back.
     */
    fun breakDown(onResult: (String) -> Unit) {
        val current = note.value ?: return
        if (_breakingDown.value) return
        _breakingDown.value = true
        // Runs on the main dispatcher: the provider offloads inference/network to a background
        // dispatcher itself (Default on-device, IO on cloud) and the DAO write is a suspend Room
        // call, so nothing blocks the UI — while onResult (a Toast) still lands on the main thread.
        viewModelScope.launch {
            val message = try {
                val onDeviceMode = settingsManager.useOnDeviceLlm
                val modelReady = onDeviceLlm.isModelPresent()
                val cloudReady = settingsManager.llmApiKey.isNotBlank()
                when {
                    // On-device mode, model not downloaded, and no cloud key to fall back to.
                    onDeviceMode && !modelReady && !cloudReady ->
                        "Add the on-device model in Settings to break tasks down."
                    // Cloud mode without an API key configured.
                    !onDeviceMode && !cloudReady ->
                        "Add your Cloud API key in Settings to break tasks down."
                    else -> {
                        val task = listOf(current.title, current.summary).filter { it.isNotBlank() }.joinToString(" — ")
                        val steps = MagicBreakdown.parseSteps(llm.generateList(MagicBreakdown.INSTRUCTION, "Task: $task"))
                        if (steps.size < 2) "Couldn't break that into steps — try again."
                        else {
                            dao.updateNoteChecklist(current.id, Checklist.encode(steps.map { Checklist.Item(it, false) }))
                            "Broke it into ${steps.size} steps."
                        }
                    }
                }
            } catch (e: Exception) {
                "Couldn't break that down — try again."
            } finally {
                _breakingDown.value = false
            }
            onResult(message)
        }
    }

    /** Inline-edit the title; reschedules a timed reminder's alarm so its notification stays in sync. */
    fun updateTitle(newTitle: String) {
        val current = note.value ?: return
        val title = newTitle.trim()
        if (title.isBlank() || title == current.title) return
        viewModelScope.launch {
            dao.updateNote(current.copy(title = title))
            if (current.type == "reminder" && current.dueTime != null) {
                alarmScheduler.schedule(current.id, title, current.dueDate, current.dueTime, current.recurrence)
                // Re-arming cancels any in-flight nag re-fire (schedule → cancelRefire), so the chain is
                // now dead — clear the persisted flag or a reboot would resurrect it. Unconditional: the
                // loaded snapshot may predate a fire that set nagFiring, so a guard could miss it.
                dao.setNagFiring(current.id, false)
            }
        }
    }

    /** Inline-edit the one-line summary. */
    fun updateSummary(newSummary: String) {
        val current = note.value ?: return
        val summary = newSummary.trim()
        if (summary == current.summary) return
        viewModelScope.launch { dao.updateNote(current.copy(summary = summary)) }
    }

    /** Set the item's priority flag ("low" / "normal" / "high"). */
    fun updatePriority(priority: String) {
        val current = note.value ?: return
        if (priority == current.priority) return
        viewModelScope.launch { dao.updateNotePriority(current.id, priority) }
    }

    /** Toggle nag-until-done: after the reminder fires, it re-rings until completed. */
    fun setNag(nag: Boolean) {
        val current = note.value ?: return
        if (nag == current.nag) return
        viewModelScope.launch {
            dao.updateNoteNag(current.id, nag)
            // Turning it off mid-chain must silence the pending re-fire too, or one more nag still
            // lands. Cancel only the re-fire — the note's own reminder alarm stays armed.
            if (!nag) alarmScheduler.cancelRefire(current.id)
        }
    }

    /** Set/clear how a reminder repeats; reschedules the alarm so the next fire reflects it. */
    fun updateRecurrence(option: String) {
        val current = note.value ?: return
        val value = option.takeIf { it != "None" && it.isNotBlank() }
        viewModelScope.launch {
            dao.updateNoteRecurrence(current.id, value)
            // Capture the intended day-of-month for a monthly reminder (clear it otherwise), so stepping
            // keeps the day instead of drifting to the 28th after February.
            val anchor = if (value?.lowercase() == "monthly") RecurrenceUtil.dayOfMonth(current.dueDate) else null
            dao.updateNoteRecurrenceAnchor(current.id, anchor)
            // schedule() advances a recurring reminder past a stale slot; persist the armed date so the
            // stored dueDate matches when the reminder will actually next fire.
            val armed = alarmScheduler.schedule(current.id, current.title, current.dueDate, current.dueTime, value)
            if (!armed.isNullOrBlank() && armed != current.dueDate) dao.updateNoteDueDate(current.id, armed)
            // Changing the schedule cancels any in-flight nag re-fire (schedule → cancelRefire); clear the
            // now-stale flag so a reboot can't resurrect the dead chain (see updateTitle for why it's
            // unconditional rather than guarded on the loaded snapshot).
            dao.setNagFiring(current.id, false)
        }
    }

    /** Set the item's one-off date + time (the sheet's "Once" mode); (re)schedules or cancels the alarm. */
    fun updateSchedule(dueDate: String?, dueTime: String?) {
        val current = note.value ?: return
        viewModelScope.launch {
            val type = if (dueTime != null) "reminder" else current.type
            // Both branches below tear down any in-flight nag re-fire (schedule/cancel → cancelRefire), so
            // clear nagFiring in the same write — otherwise a reboot resurrects the now-dead chain.
            dao.updateNote(current.copy(dueDate = dueDate, dueTime = dueTime, type = type, nagFiring = false))
            if (dueTime != null) {
                // A monthly reminder's intended day-of-month moves with the new date — re-anchor it.
                if (current.recurrence?.lowercase() == "monthly") {
                    dao.updateNoteRecurrenceAnchor(current.id, RecurrenceUtil.dayOfMonth(dueDate))
                }
                val armed = alarmScheduler.schedule(current.id, current.title, dueDate, dueTime, current.recurrence)
                if (!armed.isNullOrBlank() && armed != dueDate) dao.updateNoteDueDate(current.id, armed)
            } else {
                alarmScheduler.cancel(current.id)
            }
        }
    }

    /** Attach a place reminder (caller must already hold fine + background location). */
    fun setLocationReminder(lat: Double, lng: Double, label: String) {
        val current = note.value ?: return
        viewModelScope.launch {
            dao.updateNoteLocation(current.id, lat, lng, RADIUS_METERS, label)
            geofenceManager.add(current.id, lat, lng, RADIUS_METERS.toFloat())
        }
    }

    /**
     * Attach a place reminder for a TYPED place name (not just the current location), resolving it to
     * coordinates with [PlaceGeocoder] — the same geocoder the approval path already uses. [onResult]
     * gets false when the place can't be resolved so the caller can prompt the user to refine it.
     */
    fun setLocationReminderByPlace(placeName: String, label: String, onResult: (Boolean) -> Unit) {
        val current = note.value ?: return
        viewModelScope.launch {
            // The geocoder reaches out to the system Geocoder (IO); treat it as fallible. runCatching keeps
            // a thrown error (not just a null result) from killing the coroutine and leaving onResult —
            // and the UI waiting on it — hanging with no feedback.
            val ok = runCatching {
                val coords = placeGeocoder.geocode(placeName) ?: return@runCatching false
                dao.updateNoteLocation(current.id, coords.first, coords.second, RADIUS_METERS, label)
                geofenceManager.add(current.id, coords.first, coords.second, RADIUS_METERS.toFloat())
                true
            }.getOrDefault(false)
            onResult(ok)
        }
    }

    fun clearLocationReminder() {
        val current = note.value ?: return
        viewModelScope.launch {
            geofenceManager.remove(current.id)
            dao.updateNoteLocation(current.id, null, null, null, null)
        }
    }

    companion object {
        const val RADIUS_METERS = 150.0
    }
}
