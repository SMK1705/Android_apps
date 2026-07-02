package com.rajasudhan.taskmind.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rajasudhan.taskmind.data.local.TaskMindDao
import com.rajasudhan.taskmind.data.model.RejectedPattern
import com.rajasudhan.taskmind.data.source.RejectionLearner
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/** One learned fact, ready for the dashboard: the sender + whether it's actively down-ranked yet. */
data class LearnedSender(
    val kind: String,
    val value: String,
    val count: Int,
) {
    /** True once the rejection count crosses the learner's threshold and suggestions get penalised. */
    val downRanked: Boolean get() = count >= RejectionLearner.REJECT_THRESHOLD
}

/**
 * Backs the "What TaskMind knows about me" screen: surfaces the on-device rejection-learning memory
 * (senders the user keeps dismissing) so it's visible and reversible — instead of an invisible penalty
 * with no recourse short of wiping all data. Everything here is local; "forget" simply deletes the row.
 */
@HiltViewModel
class KnowsViewModel @Inject constructor(
    private val dao: TaskMindDao,
) : ViewModel() {

    private val _senders = MutableStateFlow<List<LearnedSender>>(emptyList())
    val senders: StateFlow<List<LearnedSender>> = _senders

    private val _loaded = MutableStateFlow(false)
    val loaded: StateFlow<Boolean> = _loaded

    init { refresh() }

    fun refresh() {
        viewModelScope.launch {
            _senders.value = dao.allRejectedPatterns()
                .map { LearnedSender(it.kind, it.value, it.count) }
                // Most-penalised first, so the senders TaskMind trusts least are on top.
                .sortedByDescending { it.count }
            _loaded.value = true
        }
    }

    /** Forget one learned sender — deletes the row so future suggestions from it are no longer down-ranked. */
    fun forget(sender: LearnedSender) {
        viewModelScope.launch {
            dao.deleteRejectedPattern(sender.kind, sender.value)
            refresh()
        }
    }

    /** Forget everything TaskMind has learned to down-rank. */
    fun forgetAll() {
        viewModelScope.launch {
            dao.deleteAllRejectedPatterns()
            refresh()
        }
    }
}
