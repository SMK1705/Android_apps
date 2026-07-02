package com.rajasudhan.taskmind.ui.settings

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rajasudhan.taskmind.data.source.HealthCheck
import com.rajasudhan.taskmind.data.source.ReliabilityChecker
import com.rajasudhan.taskmind.data.source.ReliabilityTestReceiver
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject

/** State of the one-shot "test alarm" round-trip. */
sealed interface TestAlarmState {
    data object Idle : TestAlarmState
    data object Running : TestAlarmState
    data class Delivered(val latencyMs: Long) : TestAlarmState
    /** Fired-and-armed but nothing came back in time — the classic battery-optimization symptom. */
    data object Missed : TestAlarmState
    /** The OS won't let us schedule an exact alarm at all. */
    data object CannotSchedule : TestAlarmState
}

/** Backs [ReliabilityScreen]: runs the diagnostics and the live test-alarm round-trip. */
@HiltViewModel
class ReliabilityViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val checker: ReliabilityChecker,
) : ViewModel() {

    private val _checks = MutableStateFlow(checker.run())
    val checks: StateFlow<List<HealthCheck>> = _checks

    private val _test = MutableStateFlow<TestAlarmState>(TestAlarmState.Idle)
    val test: StateFlow<TestAlarmState> = _test

    /** Re-read every check — call on screen open and on return from a fix screen. */
    fun refresh() {
        _checks.value = checker.run()
    }

    /**
     * Schedules a real exact alarm [DELAY_MS] out and waits for [ReliabilityTestReceiver] to stamp
     * its arrival, measuring the round-trip. If it never comes back within the window the alarm was
     * dropped — almost always battery optimization — which is exactly what the doctor exists to
     * surface. Honest about the exact-alarm permission: if it's missing we say so rather than
     * silently scheduling an inexact alarm that would muddy the result.
     */
    fun runTestAlarm() {
        if (_test.value == TestAlarmState.Running) return
        val am = context.getSystemService(AlarmManager::class.java)
        if (am == null || !am.canScheduleExactAlarms()) {
            _test.value = TestAlarmState.CannotSchedule
            return
        }
        _test.value = TestAlarmState.Running
        val pi = testPendingIntent()
        viewModelScope.launch {
            ReliabilityTestReceiver.firedAt.value = null
            val startedAt = System.currentTimeMillis()
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, startedAt + DELAY_MS, pi)

            val arrivedAt = withTimeoutOrNull(TIMEOUT_MS) {
                // Suspend until the receiver stamps a time at/after we started (StateFlow.first
                // checks the current value — reset to null above — then each new emission).
                ReliabilityTestReceiver.firedAt.first { it != null && it >= startedAt }!!
            }
            _test.value = if (arrivedAt != null) {
                TestAlarmState.Delivered((arrivedAt - startedAt).coerceAtLeast(0))
            } else {
                am.cancel(pi) // give up cleanly
                TestAlarmState.Missed
            }
        }
    }

    /**
     * If the screen closes while a test is in flight, viewModelScope is cancelled inside
     * withTimeoutOrNull — so the "give up" cleanup never runs and the armed alarm would fire later
     * into nothing. Cancel it here so we never leave a stray alarm behind.
     */
    override fun onCleared() {
        if (_test.value == TestAlarmState.Running) {
            context.getSystemService(AlarmManager::class.java)?.cancel(testPendingIntent())
        }
    }

    private fun testPendingIntent(): PendingIntent = PendingIntent.getBroadcast(
        context, ReliabilityTestReceiver.REQUEST_CODE,
        Intent(context, ReliabilityTestReceiver::class.java),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    private companion object {
        const val DELAY_MS = 3_000L
        const val TIMEOUT_MS = 20_000L
    }
}
