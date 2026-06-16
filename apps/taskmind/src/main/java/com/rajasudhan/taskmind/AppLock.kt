package com.rajasudhan.taskmind

/**
 * Bridges the biometric app-lock with intentional activity-result round-trips.
 *
 * [MainActivity] re-arms the lock on every `ON_STOP`, so returning to the app
 * always requires authentication. But launching a system document picker (SAF) —
 * for an encrypted backup, a restore, or a JSON export — also fires `ON_STOP`.
 * Re-locking there swaps the Settings subtree (which owns the picker's result
 * callback) out of composition; when the picker returns, the callback is gone,
 * the write never runs, and SAF leaves a silently empty 0-byte file behind.
 *
 * Call [expectResult] immediately before launching such a picker. The next
 * `ON_STOP` is then treated as a deliberate round-trip and does not re-lock, so
 * the Settings screen stays composed and receives the result.
 */
object AppLock {
    @Volatile
    private var awaitingResult = false

    /** Mark that the app is about to background itself for an activity result. */
    fun expectResult() {
        awaitingResult = true
    }

    /**
     * Consulted from the `ON_STOP` observer. Returns `true` (and clears the flag)
     * when this background is a deliberate picker round-trip that must not re-lock.
     */
    fun shouldKeepUnlockedOnStop(): Boolean {
        val expected = awaitingResult
        awaitingResult = false
        return expected
    }

    /** Reset once back in the foreground, in case a launch never reached `ON_STOP`. */
    fun reset() {
        awaitingResult = false
    }
}
