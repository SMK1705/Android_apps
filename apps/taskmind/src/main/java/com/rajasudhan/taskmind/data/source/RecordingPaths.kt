package com.rajasudhan.taskmind.data.source

/**
 * Maps a user-configured recording folder to a MediaStore `RELATIVE_PATH LIKE` fragment (#132).
 *
 * The settings hold ABSOLUTE paths (e.g. `/storage/emulated/0/Recordings/Call/`), but MediaStore's
 * RELATIVE_PATH is VOLUME-relative (`Recordings/Call/`). So we drop the volume-root segments and keep
 * the meaningful tail — which also makes an SD-card path (`/storage/ABCD-1234/Recordings/…`) match the
 * same way. Pure, so the folder→pattern mapping is unit-testable without a ContentResolver.
 */
object RecordingPaths {

    // Segments that are just the storage volume, not part of the meaningful folder structure.
    private val VOLUME_SEGMENTS = setOf("storage", "emulated", "self", "primary", "sdcard", "0")

    /**
     * The `LIKE` fragment for [absolutePath] — its last [maxSegments] meaningful segments joined by `/`
     * (e.g. `Recordings/Call`), or null if nothing meaningful remains (a blank setting is skipped rather
     * than matched as `%%`, which would sweep in every recording).
     */
    fun relativePattern(absolutePath: String, maxSegments: Int = 2): String? {
        val segments = absolutePath.trim().trim('/').split('/').filter { it.isNotBlank() }
        val meaningful = segments.filterNot { it.lowercase() in VOLUME_SEGMENTS }
        // All-volume (e.g. "/storage/emulated/0/") or blank ⇒ no real folder to match on.
        if (meaningful.isEmpty()) return null
        return meaningful.takeLast(maxSegments).joinToString("/")
    }

    /**
     * Folder fragments that call/voice recorders write to across common OEMs and popular third-party
     * apps, scanned IN ADDITION to the user's configured Call/Voice folders so recording capture works
     * out-of-the-box on more than just Samsung (whose layout the defaults were shaped for). Each is a
     * `RELATIVE_PATH LIKE '%fragment%'` term, kept specific to recording folders — never a generic
     * "Music"/"Ringtones" — and always paired with the scan's `IS_MUSIC = 0` guard, so general audio is
     * not swept in. Not exhaustive across every OEM/region; the folder stays user-configurable in Settings.
     */
    val COMMON_RECORDING_PATTERNS = listOf(
        "Recordings/Call",            // Samsung One UI 5.1+ call recordings
        "Recordings/Voice Recorder",  // Samsung Voice Recorder
        "Call Recordings",            // common across popular third-party call recorders…
        "CallRecordings",
        "CallRecorder",
        "Call recording",
        "PhoneRecord",                // some OEM / older builds
        "MIUI/sound_recorder",        // Xiaomi call + voice recordings
    )

    /**
     * The de-duplicated set of `RELATIVE_PATH LIKE` fragments to scan for recordings: the user's
     * configured Call and Voice folders (mapped via [relativePattern]) unioned with
     * [COMMON_RECORDING_PATTERNS]. Never empty, so the scan always has a bounded folder clause rather
     * than an empty one (matches nothing) or an unbounded `%%` (sweeps in every recording).
     */
    fun scanPatterns(callPath: String, voicePath: String): List<String> =
        (listOfNotNull(relativePattern(callPath), relativePattern(voicePath)) + COMMON_RECORDING_PATTERNS)
            .distinct()
}
