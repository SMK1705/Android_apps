package com.rajasudhan.taskmind.data.source

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The absolute-recording-folder → MediaStore RELATIVE_PATH fragment mapping (#132). Pure, so plain JVM. */
class RecordingPathsTest {

    @Test
    fun relativePattern_stripsTheVolumeRoot_keepingTheMeaningfulTail() {
        assertEquals("Recordings/Call", RecordingPaths.relativePattern("/storage/emulated/0/Recordings/Call/"))
        assertEquals(
            "Recordings/Voice Recorder",
            RecordingPaths.relativePattern("/storage/emulated/0/Recordings/Voice Recorder/")
        )
    }

    @Test
    fun relativePattern_handlesAnSdCardVolume() {
        assertEquals("Recordings/Call", RecordingPaths.relativePattern("/storage/ABCD-1234/Recordings/Call"))
    }

    @Test
    fun relativePattern_keepsASingleMeaningfulSegment() {
        assertEquals("Call recordings", RecordingPaths.relativePattern("/storage/emulated/0/Call recordings/"))
    }

    @Test
    fun relativePattern_returnsNull_forBlankOrVolumeRootOnly() {
        assertNull(RecordingPaths.relativePattern(""))
        assertNull(RecordingPaths.relativePattern("   "))
        assertNull(RecordingPaths.relativePattern("/storage/emulated/0/")) // just the volume root
    }

    // ---- scanPatterns: user folders UNIONED with the cross-OEM common set (#132 cross-device) ----

    @Test
    fun scanPatterns_unionsAConfiguredFolderWithTheCommonOEMSet() {
        // A custom (non-Samsung) call folder is scanned alongside the built-in common recorder folders,
        // so a user on another device is covered WITHOUT losing their explicit setting.
        val patterns = RecordingPaths.scanPatterns(
            callPath = "/storage/emulated/0/MyRecorder/Calls/",
            voicePath = "/storage/emulated/0/Recordings/Voice Recorder/",
        )
        assertTrue("keeps the user's custom call folder", patterns.contains("MyRecorder/Calls"))
        assertTrue("adds every common recorder folder", patterns.containsAll(RecordingPaths.COMMON_RECORDING_PATTERNS))
    }

    @Test
    fun scanPatterns_deDuplicates_whenAConfiguredFolderMatchesACommonOne() {
        // The Samsung defaults are already in the common set, so the union must not double them up.
        val patterns = RecordingPaths.scanPatterns(
            callPath = "/storage/emulated/0/Recordings/Call/",
            voicePath = "/storage/emulated/0/Recordings/Voice Recorder/",
        )
        assertEquals(patterns.distinct(), patterns)
        assertEquals(1, patterns.count { it == "Recordings/Call" })
    }

    @Test
    fun scanPatterns_isNeverEmpty_evenWithBlankConfiguredPaths() {
        // Blank/unset paths (nothing to map) still yield the common set — never an empty (match-nothing)
        // or unbounded ("%%") clause.
        assertEquals(RecordingPaths.COMMON_RECORDING_PATTERNS, RecordingPaths.scanPatterns(callPath = "", voicePath = "   "))
    }
}
