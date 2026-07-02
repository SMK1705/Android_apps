package com.rajasudhan.taskmind.data.source

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Target of the Reliability Doctor's "test alarm" — a real exact alarm the doctor schedules a few
 * seconds out to prove, end to end, that AlarmManager can actually wake the app on time. When it
 * fires it stamps [firedAt]; the doctor watches that to measure the round-trip (and, if it never
 * arrives, to reveal that something — usually battery optimization — is dropping alarms).
 */
class ReliabilityTestReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        firedAt.value = System.currentTimeMillis()
    }

    companion object {
        /** Epoch-millis of the most recent test-alarm delivery, or null if none yet. */
        val firedAt = MutableStateFlow<Long?>(null)

        /** Request-code namespace for the test alarm, clear of every other PendingIntent. */
        const val REQUEST_CODE = 9_000_000
    }
}
