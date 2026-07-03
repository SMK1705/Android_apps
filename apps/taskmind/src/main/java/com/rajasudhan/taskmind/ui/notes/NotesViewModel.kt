package com.rajasudhan.taskmind.ui.notes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rajasudhan.taskmind.data.local.TaskMindDao
import com.rajasudhan.taskmind.data.model.Note
import com.rajasudhan.taskmind.data.source.AlarmScheduler
import com.rajasudhan.taskmind.data.source.embedding.SemanticIndex
import com.rajasudhan.taskmind.ui.common.isOverdue
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
    private val dao: TaskMindDao,
    private val alarmScheduler: AlarmScheduler,
    private val semanticIndex: SemanticIndex
) : ViewModel() {

    init {
        // Catch up any notes saved before semantic indexing existed, so search/dedup cover them too.
        viewModelScope.launch { semanticIndex.backfill() }
    }

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query

    private val _showCompleted = MutableStateFlow(false)
    val showCompleted: StateFlow<Boolean> = _showCompleted

    // null = all kinds; otherwise "todo" | "reminder" | "note". Mutually exclusive with the Done view.
    private val _kindFilter = MutableStateFlow<String?>(null)
    val kindFilter: StateFlow<String?> = _kindFilter

    /** Per-kind counts of the *active* notes, for the filter-chip badges. Key "all" = total. */
    val kindCounts: StateFlow<Map<String, Int>> =
        dao.getActiveNotes()
            .map { list ->
                mapOf(
                    "all" to list.size,
                    "todo" to list.count { it.type == "todo" },
                    "reminder" to list.count { it.type == "reminder" },
                    "note" to list.count { it.type == "note" },
                    "waiting_on" to list.count { it.type == "waiting_on" },
                    "overdue" to list.count { isOverdue(it.dueDate, it.dueTime) }
                )
            }
            .stateIn(viewModelScope, SharingStarted.Lazily, emptyMap())

    /** Total completed notes — feeds the "Done · N" segment in the redesigned header. */
    val completedCount: StateFlow<Int> =
        dao.getCompletedNotes()
            .map { it.size }
            .stateIn(viewModelScope, SharingStarted.Lazily, 0)

    /**
     * The list to display, driven by the search box and the Active/Completed segment:
     *  - searching: matches within the current segment;
     *  - Active: open items, "important first" (reminders → todos → notes, soonest due first);
     *  - Completed: done items, most-recently-completed first.
     */
    // null = the active query hasn't delivered its first result yet (UI shows a skeleton); an empty
    // list = loaded but nothing matches (UI shows the empty state). Both come from this one flow, so
    // the loading and empty states are always derived from the *displayed* query — never a stale one.
    val notes: StateFlow<List<Note>?> =
        combine(_query, _showCompleted, _kindFilter) { q, c, k -> Triple(q, c, k) }
            .flatMapLatest { (q, completed, kind) ->
                fun keep(n: Note) = when (kind) {
                    null -> true
                    "overdue" -> isOverdue(n.dueDate, n.dueTime)
                    else -> n.type == kind
                }
                when {
                    q.isNotBlank() -> {
                        // Semantic + lexical search: keep every substring match, plus notes the query
                        // is semantically close to (meaning, not just keywords), ranked by relevance.
                        val base = if (completed) dao.getCompletedNotes() else dao.getActiveNotes()
                        base.map { list -> rankBySearch(q, list.filter { keep(it) }) }
                    }
                    completed -> dao.getCompletedNotes().map { list -> list.filter { keep(it) } }
                    else -> dao.getActiveNotes().map { list -> prioritise(list.filter { keep(it) }) }
                }
            }
            .stateIn(viewModelScope, SharingStarted.Lazily, null)

    fun setQuery(q: String) { _query.value = q }
    fun setShowCompleted(c: Boolean) { _showCompleted.value = c; if (c) _kindFilter.value = null }

    /** Filter the active list by kind (null = all); leaves the Done view. */
    fun setKindFilter(kind: String?) { _kindFilter.value = kind; _showCompleted.value = false }

    /** Toggle a single inline checklist item and persist the new encoded block. */
    fun toggleChecklistItem(note: Note, index: Int) {
        val items = note.checklist?.let { Checklist.decode(it) } ?: return
        if (index !in items.indices) return
        viewModelScope.launch { dao.updateNoteChecklist(note.id, Checklist.toggleEncoded(items, index)) }
    }

    fun setCompleted(note: Note, completed: Boolean) {
        viewModelScope.launch {
            dao.setNoteCompleted(note.id, completed, if (completed) System.currentTimeMillis() else null)
        }
    }

    /** Bump an overdue item's due date (keeping its time) and re-arm its alarm — one-tap triage. */
    fun reschedule(note: Note, newDueDate: String) {
        viewModelScope.launch {
            dao.updateNoteDueDate(note.id, newDueDate)
            alarmScheduler.schedule(note.id, note.title, newDueDate, note.dueTime, note.recurrence)
        }
    }

    fun deleteNote(note: Note) {
        viewModelScope.launch { dao.deleteNote(note) }
    }

    /**
     * Rank [notes] for search query [q]: substring (lexical) hits always show and sort to the top,
     * plus notes the query is semantically close to (via the embedding index), by relevance. Falls
     * back to plain substring matching when nothing is indexed yet.
     */
    private suspend fun rankBySearch(q: String, notes: List<Note>): List<Note> {
        val scores = semanticIndex.scores(q, SemanticIndex.SEARCH_FLOOR)
        val ql = q.trim().lowercase()
        fun lexical(n: Note) =
            n.title.lowercase().contains(ql) || n.summary.lowercase().contains(ql) || n.body.lowercase().contains(ql)
        return notes.mapNotNull { n ->
            val sem = scores[n.id] ?: 0f
            val relevance = when {
                lexical(n) -> 1f + sem  // a keyword hit always shows, ranked above semantic-only matches
                sem >= SemanticIndex.SEARCH_FLOOR -> sem
                else -> return@mapNotNull null
            }
            n to relevance
        }.sortedByDescending { it.second }.map { it.first }
    }

    // Priority order (research-backed: overdue → soonest due → priority → type → recency). Bucket 0
    // overdue, 1 upcoming-dated, 2 undated; within dated buckets by due (ascending), then the priority
    // flag (high first — the lead signal for undated items), then type, then most-recently-created.
    private fun prioritise(list: List<Note>) =
        list.sortedWith(
            compareBy(
                { dueBucket(it) }, { dueSortKey(it) }, { priorityRank(it.priority) },
                { typeRank(it.type) }, { -it.createdDate }
            )
        )

    private fun dueBucket(note: Note): Int = when {
        isOverdue(note.dueDate, note.dueTime) -> 0
        note.dueDate != null -> 1
        else -> 2
    }

    private fun priorityRank(priority: String): Int = when (priority) {
        "high" -> 0
        "low" -> 2
        else -> 1 // normal
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
