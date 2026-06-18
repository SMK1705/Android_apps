package com.rajasudhan.taskmind.ui.notes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rajasudhan.taskmind.data.local.TaskMindDao
import com.rajasudhan.taskmind.data.model.Note
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class NotesViewModel @Inject constructor(
    private val dao: TaskMindDao
) : ViewModel() {

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query

    private val _showCompleted = MutableStateFlow(false)
    val showCompleted: StateFlow<Boolean> = _showCompleted

    /**
     * The list to display, driven by the search box and the Active/Completed segment:
     *  - searching: matches within the current segment;
     *  - Active: open items, "important first" (reminders → todos → notes, soonest due first);
     *  - Completed: done items, most-recently-completed first.
     */
    val notes: StateFlow<List<Note>> =
        combine(_query, _showCompleted) { q, c -> q to c }
            .flatMapLatest { (q, completed) ->
                when {
                    q.isNotBlank() ->
                        dao.searchNotes("%$q%").map { list -> prioritise(list.filter { it.completed == completed }) }
                    completed -> dao.getCompletedNotes()
                    else -> dao.getActiveNotes().map { prioritise(it) }
                }
            }
            .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    // True until the notes DB delivers its first result, so the list shows a skeleton on first load
    // instead of momentarily flashing the empty state.
    val isLoading: StateFlow<Boolean> = dao.getActiveNotes()
        .map { false }
        .stateIn(viewModelScope, SharingStarted.Lazily, true)

    fun setQuery(q: String) { _query.value = q }
    fun setShowCompleted(c: Boolean) { _showCompleted.value = c }

    fun setCompleted(note: Note, completed: Boolean) {
        viewModelScope.launch {
            dao.setNoteCompleted(note.id, completed, if (completed) System.currentTimeMillis() else null)
        }
    }

    fun deleteNote(note: Note) {
        viewModelScope.launch { dao.deleteNote(note) }
    }

    private fun prioritise(list: List<Note>) =
        list.sortedWith(compareBy({ typeRank(it.type) }, { dueSortKey(it) }))

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
