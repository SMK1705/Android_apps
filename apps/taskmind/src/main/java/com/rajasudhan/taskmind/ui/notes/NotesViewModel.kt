package com.rajasudhan.taskmind.ui.notes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rajasudhan.taskmind.data.local.TaskMindDao
import com.rajasudhan.taskmind.data.model.Note
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime
import javax.inject.Inject

@HiltViewModel
class NotesViewModel @Inject constructor(
    private val dao: TaskMindDao
) : ViewModel() {

    /**
     * All approved items, ordered "important first":
     *  1. by type priority — reminders, then todos, then notes
     *  2. within a type, by due date/time ascending (soonest first; undated last)
     */
    val notes: StateFlow<List<Note>> = dao.getAllNotes()
        .map { list -> list.sortedWith(compareBy({ typeRank(it.type) }, { dueSortKey(it) })) }
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    fun deleteNote(note: Note) {
        viewModelScope.launch { dao.deleteNote(note) }
    }

    private fun typeRank(type: String): Int = when (type) {
        "reminder" -> 0
        "todo" -> 1
        else -> 2 // "note"
    }

    private fun dueSortKey(note: Note): Long {
        val date = note.dueDate ?: return Long.MAX_VALUE
        return try {
            val day = LocalDate.parse(date).toEpochDay()
            val minutes = note.dueTime?.let { LocalTime.parse(it).toSecondOfDay() / 60 } ?: 0
            day * 1440 + minutes
        } catch (e: Exception) {
            Long.MAX_VALUE
        }
    }
}
