package com.rajasudhan.taskmind.ui.inbox

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rajasudhan.taskmind.data.local.TaskMindDao
import com.rajasudhan.taskmind.data.model.Suggestion
import com.rajasudhan.taskmind.data.source.RecentDataScanner
import com.rajasudhan.taskmind.data.source.RejectionLearner
import com.rajasudhan.taskmind.data.source.SuggestionApprover
import com.rajasudhan.taskmind.data.source.SuggestionNotifier
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
    private val pipeline: UnderstandingPipeline,
    private val notifier: SuggestionNotifier
) : ViewModel() {

    // Ticks every 30s so snoozed items auto-resurface shortly after their time passes.
    private val nowTicker = flow {
        while (true) { emit(System.currentTimeMillis()); delay(30_000) }
    }

    // null = still loading the first result (UI shows a skeleton); an empty list = genuinely empty
    // (UI shows the "all caught up" state). Both states come from this one DB subscription, so they
    // can never disagree for a frame.
    val pendingSuggestions: StateFlow<List<Suggestion>?> =
        combine(dao.getPendingSuggestions(), nowTicker) { list, now ->
            list.filter { it.snoozedUntil == null || it.snoozedUntil!! <= now }
        }.stateIn(viewModelScope, SharingStarted.Lazily, null)

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing

    // The most recent reversible action (approve/reject), for the "Undo" snackbar.
    private var lastUndo: (suspend () -> Unit)? = null

    fun approveSuggestion(suggestion: Suggestion, durationMinutes: Int? = null, addCalendar: Boolean = true) {
        viewModelScope.launch {
            val noteId = approver.approve(suggestion, durationMinutes, addCalendar)
            // Undo re-pends the suggestion and drops the note, but deliberately does not roll back the
            // rejection-learning decrement from approve() — same as reject's undo, which doesn't roll
            // back its increment. The penalty is a soft, self-correcting nudge, so the tiny asymmetry
            // isn't worth threading the prior count through the undo.
            lastUndo = {
                dao.updateSuggestion(suggestion.copy(status = "pending", snoozedUntil = null))
                dao.deleteNoteById(noteId.toInt())
            }
        }
    }

    fun approveAll() {
        viewModelScope.launch {
            pendingSuggestions.value.orEmpty().forEach { approver.approve(it) }
            lastUndo = null // bulk action isn't individually undoable
        }
    }

    fun rejectAll() {
        viewModelScope.launch {
            pendingSuggestions.value.orEmpty().forEach {
                dao.updateSuggestion(it.copy(status = "rejected"))
                rejectionLearner.recordRejection(it)
            }
            lastUndo = null
        }
    }

    /** Bulk-dismiss every "likely noise" suggestion (confidence below [noiseThreshold]); undoable. */
    fun sweepNoise(noiseThreshold: Double = 0.5) {
        viewModelScope.launch {
            val noise = pendingSuggestions.value.orEmpty().filter { it.confidence < noiseThreshold }
            if (noise.isEmpty()) return@launch
            noise.forEach {
                dao.updateSuggestion(it.copy(status = "rejected"))
                rejectionLearner.recordRejection(it)
            }
            lastUndo = { noise.forEach { dao.updateSuggestion(it.copy(status = "pending", snoozedUntil = null)) } }
        }
    }

    fun rejectSuggestion(suggestion: Suggestion) {
        viewModelScope.launch {
            dao.updateSuggestion(suggestion.copy(status = "rejected"))
            rejectionLearner.recordRejection(suggestion)
            lastUndo = { dao.updateSuggestion(suggestion.copy(status = "pending", snoozedUntil = null)) }
        }
    }

    /**
     * Bounce-Back: hide [suggestion] from the Inbox until [until] (epoch millis), and arm an alarm
     * that re-posts the ORIGINAL captured message as a notification at that time — so the content you
     * wanted to deal with comes back to you, not just silently to the Inbox. Refreshing the prompt now
     * also drops the snoozed item from the shade immediately. Undo clears the snooze; the alarm may
     * still fire but no-ops, since the bounce only posts while the item is still pending AND snoozed.
     */
    fun snooze(suggestion: Suggestion, until: Long) {
        viewModelScope.launch {
            dao.updateSuggestion(suggestion.copy(snoozedUntil = until))
            notifier.scheduleResurface(suggestion.id, until)
            notifier.notifyPending()
            lastUndo = {
                dao.updateSuggestion(suggestion.copy(snoozedUntil = null))
                // The snooze just dropped this item from the shade — bring it straight back, or the
                // notification would stay stale until the (now-irrelevant) resurface alarm fires.
                notifier.notifyPending()
            }
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
                pipeline.processText("Manual entry", text, seedSchedule = true)
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
                pipeline.processText("Voice note", transcript, seedSchedule = true)
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
