package com.rajasudhan.taskmind.ui.inbox

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rajasudhan.taskmind.data.local.TaskMindDao
import com.rajasudhan.taskmind.data.model.Suggestion
import com.rajasudhan.taskmind.data.source.RecentDataScanner
import com.rajasudhan.taskmind.data.source.RejectionLearner
import com.rajasudhan.taskmind.data.source.SuggestionApprover
import com.rajasudhan.taskmind.data.source.transcription.VoskTranscriber
import com.rajasudhan.taskmind.data.source.understanding.UnderstandingPipeline
import java.io.File
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class InboxViewModel @Inject constructor(
    private val dao: TaskMindDao,
    private val scanner: RecentDataScanner,
    private val approver: SuggestionApprover,
    private val rejectionLearner: RejectionLearner,
    private val voskTranscriber: VoskTranscriber,
    private val pipeline: UnderstandingPipeline
) : ViewModel() {

    // Ticks every 30s so snoozed items auto-resurface shortly after their time passes.
    private val nowTicker = flow {
        while (true) { emit(System.currentTimeMillis()); delay(30_000) }
    }

    val pendingSuggestions = combine(dao.getPendingSuggestions(), nowTicker) { list, now ->
        list.filter { it.snoozedUntil == null || it.snoozedUntil!! <= now }
    }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    // True until the DB delivers its first result, so the Inbox can show a skeleton on first load
    // rather than flashing the "all caught up" empty state.
    val isLoading: StateFlow<Boolean> = dao.getPendingSuggestions()
        .map { false }
        .stateIn(viewModelScope, SharingStarted.Lazily, true)

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing

    // The most recent reversible action (approve/reject), for the "Undo" snackbar.
    private var lastUndo: (suspend () -> Unit)? = null

    fun approveSuggestion(suggestion: Suggestion) {
        viewModelScope.launch {
            val noteId = approver.approve(suggestion)
            lastUndo = {
                dao.updateSuggestion(suggestion.copy(status = "pending", snoozedUntil = null))
                dao.deleteNoteById(noteId.toInt())
            }
        }
    }

    fun approveAll() {
        viewModelScope.launch {
            pendingSuggestions.value.forEach { approver.approve(it) }
            lastUndo = null // bulk action isn't individually undoable
        }
    }

    fun rejectAll() {
        viewModelScope.launch {
            pendingSuggestions.value.forEach {
                dao.updateSuggestion(it.copy(status = "rejected"))
                rejectionLearner.recordRejection(it)
            }
            lastUndo = null
        }
    }

    fun rejectSuggestion(suggestion: Suggestion) {
        viewModelScope.launch {
            dao.updateSuggestion(suggestion.copy(status = "rejected"))
            rejectionLearner.recordRejection(suggestion)
            lastUndo = { dao.updateSuggestion(suggestion.copy(status = "pending", snoozedUntil = null)) }
        }
    }

    /** Hide [suggestion] from the Inbox until [until] (epoch millis). It returns on its own. */
    fun snooze(suggestion: Suggestion, until: Long) {
        viewModelScope.launch {
            dao.updateSuggestion(suggestion.copy(snoozedUntil = until))
            lastUndo = { dao.updateSuggestion(suggestion.copy(snoozedUntil = null)) }
        }
    }

    /** Reverses the most recent approve/reject/snooze. No-op if nothing is undoable. */
    fun undoLast() {
        val action = lastUndo ?: return
        lastUndo = null
        viewModelScope.launch { action() }
    }

    /** Type-to-add: feed a typed line through the same pipeline as every other source. */
    fun addManualEntry(text: String, onResult: (String) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            var message = "Couldn't add that — please try again."
            try {
                pipeline.processText("Manual entry", text)
                message = "Added to your inbox for review."
            } catch (e: Exception) {
                message = "Couldn't add that — please try again."
            } finally {
                onResult(message)
            }
        }
    }

    fun updateSuggestion(suggestion: Suggestion) {
        viewModelScope.launch {
            dao.updateSuggestion(suggestion)
        }
    }

    fun refreshRecentData() {
        viewModelScope.launch {
            _isRefreshing.value = true
            // Scan everything since the last scan (manual or periodic) so nothing in the gap is
            // missed — a fixed window used to drop anything older than it.
            scanner.scanIncremental()
            _isRefreshing.value = false
        }
    }

    /**
     * Transcribes a recorded voice note on-device (Vosk) and runs it through the understanding
     * pipeline, so it lands in the Inbox as a pending suggestion to approve. The temp [file] is
     * always deleted. [onResult] reports a short user-facing message for a snackbar.
     */
    fun addVoiceNote(file: File, onResult: (String) -> Unit) {
        // Off the main thread: model lookup, transcription, and the temp-file delete all touch disk.
        viewModelScope.launch(Dispatchers.IO) {
            // Always report a result (and delete the temp file) so the UI never gets stuck "processing",
            // even if transcription or the LLM call throws.
            var message = "Didn't catch that — please try again."
            try {
                if (!voskTranscriber.isModelPresent()) {
                    message = "Add an offline voice model in Settings to use voice input."
                    return@launch
                }
                val transcript = runCatching { voskTranscriber.transcribe(Uri.fromFile(file)) }.getOrNull()
                if (transcript.isNullOrBlank()) return@launch
                pipeline.processText("Voice note", transcript)
                message = "Added to your inbox for review."
            } catch (e: Exception) {
                message = "Couldn't process the recording."
            } finally {
                runCatching { file.delete() }
                onResult(message)
            }
        }
    }
}
