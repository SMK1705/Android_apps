package com.rajasudhan.taskmind.ui.notes

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rajasudhan.taskmind.data.local.TaskMindDao
import com.rajasudhan.taskmind.data.model.Note
import com.rajasudhan.taskmind.data.source.AlarmScheduler
import com.rajasudhan.taskmind.data.source.GeofenceManager
import dagger.hilt.android.lifecycle.HiltViewModel
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
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val noteId: Int = savedStateHandle.get<Int>("noteId") ?: -1

    val note: StateFlow<Note?> = dao.getNoteById(noteId)
        .stateIn(viewModelScope, SharingStarted.Lazily, null)

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

    /** Inline-edit the title; reschedules a timed reminder's alarm so its notification stays in sync. */
    fun updateTitle(newTitle: String) {
        val current = note.value ?: return
        val title = newTitle.trim()
        if (title.isBlank() || title == current.title) return
        viewModelScope.launch {
            dao.updateNote(current.copy(title = title))
            if (current.type == "reminder" && current.dueTime != null) {
                alarmScheduler.schedule(current.id, title, current.dueDate, current.dueTime, current.recurrence)
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

    /** Set/clear how a reminder repeats; reschedules the alarm so the next fire reflects it. */
    fun updateRecurrence(option: String) {
        val current = note.value ?: return
        val value = option.takeIf { it != "None" && it.isNotBlank() }
        viewModelScope.launch {
            dao.updateNoteRecurrence(current.id, value)
            alarmScheduler.schedule(current.id, current.title, current.dueDate, current.dueTime, value)
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
