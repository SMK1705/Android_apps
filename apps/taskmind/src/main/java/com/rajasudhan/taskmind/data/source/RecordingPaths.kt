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
}
