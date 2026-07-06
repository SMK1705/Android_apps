package com.rajasudhan.taskmind.data.source.transcription

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Pure tests for the second-pass material-change decision (#126). */
class TranscriptDiffTest {

    @Test
    fun identicalTranscripts_areNotAMaterialChange() =
        assertFalse(TranscriptDiff.isMaterialChange("call mom at five", "call mom at five"))

    @Test
    fun aMinorWordAddition_isNotMaterial() =
        assertFalse(
            TranscriptDiff.isMaterialChange(
                "pick up groceries milk eggs bread today",
                "pick up groceries milk eggs bread today evening"
            )
        )

    @Test
    fun aDifferentTranscript_isMaterial() =
        assertTrue(
            TranscriptDiff.isMaterialChange(
                "call the dentist tomorrow morning",
                "email landlord about the broken heater"
            )
        )

    @Test
    fun identicalFillerOnlyTranscripts_areNotMaterial() =
        assertFalse(TranscriptDiff.isMaterialChange("at the in on", "at the in on"))

    @Test
    fun aBlankSecondPass_isNeverMaterial() {
        assertFalse(TranscriptDiff.isMaterialChange("something worth keeping", ""))
        assertFalse(TranscriptDiff.isMaterialChange("something worth keeping", null))
    }

    @Test
    fun secondPassRescuingAFailedFirstPass_isMaterial() {
        assertTrue(TranscriptDiff.isMaterialChange(null, "the second pass found speech"))
        assertTrue(TranscriptDiff.isMaterialChange("", "the second pass found speech"))
    }
}
