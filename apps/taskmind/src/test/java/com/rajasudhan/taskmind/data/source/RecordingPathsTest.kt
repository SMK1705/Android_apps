package com.rajasudhan.taskmind.data.source

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
}
