package com.rajasudhan.taskmind.ui.inbox

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rajasudhan.taskmind.data.local.TaskMindDao
import com.rajasudhan.taskmind.data.model.Suggestion
import com.rajasudhan.taskmind.data.source.RecentDataScanner
import com.rajasudhan.taskmind.data.source.SuggestionApprover
import com.rajasudhan.taskmind.data.source.transcription.VoskTranscriber
import com.rajasudhan.taskmind.data.source.understanding.UnderstandingPipeline
import java.io.File
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class InboxViewModel @Inject constructor(
    private val dao: TaskMindDao,
    private val scanner: RecentDataScanner,
    private val approver: SuggestionApprover,
    private val voskTranscriber: VoskTranscriber,
    private val pipeline: UnderstandingPipeline
) : ViewModel() {

    val pendingSuggestions = dao.getPendingSuggestions()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing

    fun approveSuggestion(suggestion: Suggestion) {
        viewModelScope.launch { approver.approve(suggestion) }
    }

    fun approveAll() {
        viewModelScope.launch {
            pendingSuggestions.value.forEach { approver.approve(it) }
        }
    }

    fun rejectAll() {
        viewModelScope.launch {
            pendingSuggestions.value.forEach { dao.updateSuggestion(it.copy(status = "rejected")) }
        }
    }

    fun rejectSuggestion(suggestion: Suggestion) {
        viewModelScope.launch {
            val updated = suggestion.copy(status = "rejected")
            dao.updateSuggestion(updated)
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
            // Manual refresh scans the last 10 minutes; the periodic worker covers longer gaps.
            scanner.scanSince(System.currentTimeMillis() - 600_000)
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
