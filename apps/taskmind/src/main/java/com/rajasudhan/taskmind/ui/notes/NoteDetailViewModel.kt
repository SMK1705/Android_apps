package com.rajasudhan.taskmind.ui.notes

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rajasudhan.taskmind.data.local.TaskMindDao
import com.rajasudhan.taskmind.data.model.Note
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
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val noteId: Int = savedStateHandle.get<Int>("noteId") ?: -1

    val note: StateFlow<Note?> = dao.getNoteById(noteId)
        .stateIn(viewModelScope, SharingStarted.Lazily, null)

    /** Deletes the note; [onDeleted] runs after so the screen can navigate back. */
    fun deleteNote(onDeleted: () -> Unit) {
        val current = note.value ?: return
        viewModelScope.launch {
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

    /** Persists a toggled checklist (encoded by [Checklist.encode]). */
    fun updateChecklist(encoded: String) {
        val current = note.value ?: return
        viewModelScope.launch { dao.updateNoteChecklist(current.id, encoded) }
    }
}
