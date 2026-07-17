package com.rajasudhan.taskmind.ui.ask

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rajasudhan.taskmind.data.model.Note
import com.rajasudhan.taskmind.data.source.NoteActions
import com.rajasudhan.taskmind.data.source.embedding.SemanticIndex
import com.rajasudhan.taskmind.data.source.understanding.AskEngine
import com.rajasudhan.taskmind.data.source.understanding.AskIntent
import com.rajasudhan.taskmind.data.source.understanding.AskResult
import com.rajasudhan.taskmind.data.source.understanding.AskResultKind
import com.rajasudhan.taskmind.data.source.understanding.RoutingLlmProvider
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

/** One turn in the Ask conversation: the user's line, or the assistant's answer + result cards. */
data class AskMessage(val fromUser: Boolean, val text: String, val result: AskResult? = null)

@HiltViewModel
class AskViewModel @Inject constructor(
    private val engine: AskEngine,
    private val routing: RoutingLlmProvider,
    private val semanticIndex: SemanticIndex,
    private val noteActions: NoteActions,
) : ViewModel() {

    init {
        // Ask is often the first screen a user opens, and a note without a vector is invisible to
        // semantic recall — search silently degrades to lexical-only. Notes backfills on its side, but
        // a user who never opens Notes would otherwise query a half-empty index.
        viewModelScope.launch { semanticIndex.backfill() }
    }

    private val _messages = MutableStateFlow<List<AskMessage>>(emptyList())
    val messages: StateFlow<List<AskMessage>> = _messages

    private val _thinking = MutableStateFlow(false)
    val thinking: StateFlow<Boolean> = _thinking

    // Whether classification actually stays on the phone, so the empty state can promise it honestly
    // (#197). The utterance is sent to Gemini when cloud is selected (or on-device falls back to it).
    private val _onDeviceEngine = MutableStateFlow(routing.isOnDeviceEffective())
    val onDeviceEngine: StateFlow<Boolean> = _onDeviceEngine

    fun refreshEngine() { _onDeviceEngine.value = routing.isOnDeviceEffective() }

    /**
     * The intent behind the last answer, so a short follow-up ("what about next week?") refines that
     * query instead of being classified blind. Null after a search/create — nothing worth inheriting,
     * and stale slots would silently skew the next question.
     */
    private var lastIntent: AskIntent? = null

    fun ask(utterance: String) {
        val text = utterance.trim()
        if (text.isBlank() || _thinking.value) return
        _messages.value = _messages.value + AskMessage(fromUser = true, text = text)
        _thinking.value = true
        viewModelScope.launch {
            val result = try {
                engine.ask(text, previous = lastIntent)
            } catch (e: Exception) {
                AskResult("Something went wrong — try rephrasing.", kind = AskResultKind.EMPTY)
            }
            lastIntent = result.intent
            _messages.value = _messages.value + AskMessage(fromUser = false, text = result.answer, result = result)
            _thinking.value = false
        }
    }

    // ---- act on a result card, in-place (#319) ----
    // Ask is a place you DO things, not just look them up: complete, reschedule and add-to-calendar on the
    // card itself. Each reuses NoteActions — the exact path Notes uses — so there's no second, drifting copy
    // of the alarm/calendar/recurrence side effects. The acted card is patched in the thread so it reflects
    // the new state immediately (done, moved, on-calendar) without re-asking the model.

    /** Mark a result done. Reference notes have nothing to complete; only actionable, not-yet-done items. */
    fun completeResult(note: Note) {
        if (!isActionable(note) || note.completed) return
        viewModelScope.launch {
            noteActions.setCompleted(note, true)
            patchNote(note.id) { it.copy(completed = true, completedDate = System.currentTimeMillis()) }
        }
    }

    /** Push a dated result out by [days] (Tomorrow / Next week), reflecting the date the alarm landed on. */
    fun rescheduleResult(note: Note, days: Long) {
        if (note.dueDate.isNullOrBlank() || note.completed) return
        val target = LocalDate.now().plusDays(days).toString()
        viewModelScope.launch {
            val landed = noteActions.reschedule(note, target)
            patchNote(note.id) { it.copy(dueDate = landed) }
        }
    }

    /** Put a dated to-do/reminder on the calendar; the card then shows it's mirrored. */
    fun addResultToCalendar(note: Note) {
        if (!noteActions.canAddToCalendar(note)) return
        viewModelScope.launch {
            val eventId = noteActions.addToCalendar(note)
            if (eventId != null) patchNote(note.id) { it.copy(calendarEventId = eventId) }
        }
    }

    private fun isActionable(note: Note) = note.type == "todo" || note.type == "reminder" || note.type == "waiting_on"

    /** Replace a note (by id) everywhere it appears in the thread so its card re-renders with the new state. */
    private fun patchNote(id: Int, transform: (Note) -> Note) {
        _messages.value = _messages.value.map { m ->
            val r = m.result ?: return@map m
            if (r.notes.none { it.id == id }) m
            else m.copy(result = r.copy(notes = r.notes.map { if (it.id == id) transform(it) else it }))
        }
    }
}
