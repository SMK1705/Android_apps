package com.rajasudhan.taskmind.testutil

import com.rajasudhan.taskmind.data.local.TaskMindDao
import com.rajasudhan.taskmind.data.model.Note
import com.rajasudhan.taskmind.data.model.NoteEmbedding
import com.rajasudhan.taskmind.data.model.RejectedPattern
import com.rajasudhan.taskmind.data.model.Suggestion
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

/**
 * In-memory, reactive [TaskMindDao] for ViewModel/logic tests — no Room or Robolectric. Query flows
 * re-emit on every mutation (backed by [MutableStateFlow]), matching Room's invalidation behaviour
 * closely enough for state tests. Auto-assigns ids on insert when the entity's id is 0.
 */
class FakeTaskMindDao : TaskMindDao {

    private val notes = MutableStateFlow<List<Note>>(emptyList())
    private val suggestions = MutableStateFlow<List<Suggestion>>(emptyList())
    private val patterns = mutableMapOf<Pair<String, String>, RejectedPattern>()
    private val embeddings = mutableMapOf<Int, NoteEmbedding>()
    private var noteSeq = 0
    private var sugSeq = 0

    override suspend fun upsertEmbedding(embedding: NoteEmbedding) { embeddings[embedding.noteId] = embedding }
    override suspend fun getAllEmbeddings(): List<NoteEmbedding> = embeddings.values.toList()
    override suspend fun embeddedNoteIds(): List<Int> = embeddings.keys.toList()

    // ---- suggestions ----
    override fun getPendingSuggestions(): Flow<List<Suggestion>> =
        suggestions.map { list -> list.filter { it.status == "pending" }.sortedByDescending { it.confidence } }

    override suspend fun getSuggestionById(id: Int): Suggestion? = suggestions.value.find { it.id == id }

    override suspend fun insertSuggestion(suggestion: Suggestion) {
        val withId = if (suggestion.id == 0) suggestion.copy(id = ++sugSeq) else suggestion
        suggestions.update { it + withId }
    }

    override suspend fun updateSuggestion(suggestion: Suggestion) {
        suggestions.update { list -> list.map { if (it.id == suggestion.id) suggestion else it } }
    }

    // ---- notes ----
    override fun getAllNotes(): Flow<List<Note>> =
        notes.map { it.sortedByDescending { n -> n.createdDate } }

    override fun getActiveNotes(): Flow<List<Note>> =
        notes.map { list -> list.filter { !it.completed && !it.archived }.sortedByDescending { it.createdDate } }

    override fun getCompletedNotes(): Flow<List<Note>> =
        notes.map { list -> list.filter { it.completed }.sortedByDescending { it.completedDate ?: 0L } }

    override fun getArchivedNotes(): Flow<List<Note>> =
        notes.map { list -> list.filter { it.archived }.sortedByDescending { it.createdDate } }

    override suspend fun updateNoteArchived(id: Int, archived: Boolean) {
        notes.update { list -> list.map { if (it.id == id) it.copy(archived = archived) else it } }
    }

    override fun searchNotes(q: String): Flow<List<Note>> {
        val term = q.trim('%')
        return notes.map { list ->
            list.filter {
                it.title.contains(term, true) || it.summary.contains(term, true) || it.body.contains(term, true)
            }.sortedByDescending { it.createdDate }
        }
    }

    override suspend fun setNoteCompleted(id: Int, completed: Boolean, date: Long?) {
        notes.update { list -> list.map { if (it.id == id) it.copy(completed = completed, completedDate = date, nagFiring = if (completed) false else it.nagFiring) else it } }
    }

    override suspend fun updateNoteChecklist(id: Int, checklist: String?) {
        notes.update { list -> list.map { if (it.id == id) it.copy(checklist = checklist) else it } }
    }

    override suspend fun updateNoteDueDate(id: Int, dueDate: String) {
        notes.update { list -> list.map { if (it.id == id) it.copy(dueDate = dueDate) else it } }
    }

    override suspend fun updateNoteRecurrence(id: Int, recurrence: String?) {
        notes.update { list -> list.map { if (it.id == id) it.copy(recurrence = recurrence) else it } }
    }

    override suspend fun updateNoteRecurrenceAnchor(id: Int, anchor: Int?) {
        notes.update { list -> list.map { if (it.id == id) it.copy(recurrenceAnchorDay = anchor) else it } }
    }

    override suspend fun updateNoteRepeatFromCompletion(id: Int, fromCompletion: Boolean) {
        notes.update { list -> list.map { if (it.id == id) it.copy(repeatFromCompletion = fromCompletion) else it } }
    }

    override suspend fun updateNotePriority(id: Int, priority: String) {
        notes.update { list -> list.map { if (it.id == id) it.copy(priority = priority) else it } }
    }

    override suspend fun updateNoteNag(id: Int, nag: Boolean) {
        notes.update { list -> list.map { if (it.id == id) it.copy(nag = nag, nagFiring = if (nag) it.nagFiring else false) else it } }
    }

    override suspend fun setNagFiring(id: Int, firing: Boolean) {
        notes.update { list -> list.map { if (it.id == id) it.copy(nagFiring = firing) else it } }
    }

    override suspend fun setPendingConfirm(id: Int, since: Long?) {
        notes.update { list -> list.map { if (it.id == id) it.copy(pendingConfirmSince = since) else it } }
    }

    override suspend fun updateNoteLocation(id: Int, lat: Double?, lng: Double?, radius: Double?, label: String?) {
        notes.update { list ->
            list.map {
                if (it.id == id) it.copy(locationLat = lat, locationLng = lng, locationRadius = radius, locationLabel = label)
                else it
            }
        }
    }

    override suspend fun getNoteByIdNow(id: Int): Note? = notes.value.find { it.id == id }

    override fun getNoteById(id: Int): Flow<Note?> = notes.map { list -> list.find { it.id == id } }

    override suspend fun insertNote(note: Note): Long {
        val id = ++noteSeq
        notes.update { it + note.copy(id = id) }
        return id.toLong()
    }

    override suspend fun insertNotes(notes: List<Note>): List<Long> = notes.map { insertNote(it) }

    override suspend fun updateNote(note: Note) {
        notes.update { list -> list.map { if (it.id == note.id) note else it } }
    }

    override suspend fun deleteNote(note: Note) {
        notes.update { list -> list.filter { it.id != note.id } }
    }

    override suspend fun deleteNoteById(id: Int) {
        notes.update { list -> list.filter { it.id != id } }
    }

    override suspend fun deleteAllNotes() { notes.value = emptyList() }
    override suspend fun deleteAllSuggestions() { suggestions.value = emptyList() }

    override suspend fun getNotesList(): List<Note> = notes.value.sortedByDescending { it.createdDate }

    override suspend fun getReminderNotes(): List<Note> =
        notes.value.filter { !it.completed && !it.archived && it.type == "reminder" && it.dueDate != null && it.dueTime != null }

    override suspend fun getActiveWaitingOn(): List<Note> =
        notes.value.filter { !it.completed && !it.archived && it.type == "waiting_on" && it.counterparty != null }

    override suspend fun getWaitingOnReminders(): List<Note> =
        notes.value.filter { !it.completed && !it.archived && it.type == "waiting_on" && it.dueDate != null && it.dueTime != null }

    override suspend fun getActivePersonNotes(): List<Note> =
        notes.value.filter { !it.completed && !it.archived && it.counterparty != null && it.type != "waiting_on" }

    override suspend fun deleteNotesOlderThan(cutoff: Long) {
        notes.update { list -> list.filter { it.createdDate >= cutoff } }
    }

    override suspend fun deletePurgeableSuggestions() {
        suggestions.update { list -> list.filter { it.status == "pending" } }
    }

    // ---- rejected patterns ----
    override suspend fun rejectedPatternFor(kind: String, value: String): RejectedPattern? = patterns[kind to value]

    override suspend fun upsertRejectedPattern(pattern: RejectedPattern) {
        patterns[pattern.kind to pattern.value] = pattern
    }

    override suspend fun allRejectedPatterns(): List<RejectedPattern> = patterns.values.toList()

    override suspend fun deleteRejectedPattern(kind: String, value: String) { patterns.remove(kind to value) }

    override suspend fun deleteAllRejectedPatterns() { patterns.clear() }
}
