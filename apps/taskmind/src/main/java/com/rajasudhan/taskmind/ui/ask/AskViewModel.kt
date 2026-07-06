package com.rajasudhan.taskmind.ui.ask

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rajasudhan.taskmind.data.source.understanding.AskEngine
import com.rajasudhan.taskmind.data.source.understanding.AskResult
import com.rajasudhan.taskmind.data.source.understanding.AskResultKind
import com.rajasudhan.taskmind.data.source.understanding.RoutingLlmProvider
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/** One turn in the Ask conversation: the user's line, or the assistant's answer + result cards. */
data class AskMessage(val fromUser: Boolean, val text: String, val result: AskResult? = null)

@HiltViewModel
class AskViewModel @Inject constructor(
    private val engine: AskEngine,
    private val routing: RoutingLlmProvider,
) : ViewModel() {

    private val _messages = MutableStateFlow<List<AskMessage>>(emptyList())
    val messages: StateFlow<List<AskMessage>> = _messages

    private val _thinking = MutableStateFlow(false)
    val thinking: StateFlow<Boolean> = _thinking

    // Whether classification actually stays on the phone, so the empty state can promise it honestly
    // (#197). The utterance is sent to Gemini when cloud is selected (or on-device falls back to it).
    private val _onDeviceEngine = MutableStateFlow(routing.isOnDeviceEffective())
    val onDeviceEngine: StateFlow<Boolean> = _onDeviceEngine

    fun refreshEngine() { _onDeviceEngine.value = routing.isOnDeviceEffective() }

    fun ask(utterance: String) {
        val text = utterance.trim()
        if (text.isBlank() || _thinking.value) return
        _messages.value = _messages.value + AskMessage(fromUser = true, text = text)
        _thinking.value = true
        viewModelScope.launch {
            val result = try {
                engine.ask(text)
            } catch (e: Exception) {
                AskResult("Something went wrong — try rephrasing.", kind = AskResultKind.EMPTY)
            }
            _messages.value = _messages.value + AskMessage(fromUser = false, text = result.answer, result = result)
            _thinking.value = false
        }
    }
}
