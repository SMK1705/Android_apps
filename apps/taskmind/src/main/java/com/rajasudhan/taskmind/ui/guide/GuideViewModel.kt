package com.rajasudhan.taskmind.ui.guide

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rajasudhan.taskmind.data.source.SourceManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Drives the in-app guide: shows it automatically on first run (until seen) and whenever the user
 * re-opens it with the "?" button. "Seen" is persisted so it doesn't reappear on every launch.
 */
@HiltViewModel
class GuideViewModel @Inject constructor(
    private val sourceManager: SourceManager
) : ViewModel() {

    private val _manualOpen = MutableStateFlow(false)

    val showGuide: StateFlow<Boolean> =
        combine(sourceManager.hasSeenGuide, _manualOpen) { seen, manual -> manual || !seen }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    /** Re-open the guide on demand (the "?" action). */
    fun open() {
        _manualOpen.value = true
    }

    /** Close the guide and remember it's been seen. */
    fun dismiss() {
        _manualOpen.value = false
        viewModelScope.launch { sourceManager.setHasSeenGuide(true) }
    }
}
