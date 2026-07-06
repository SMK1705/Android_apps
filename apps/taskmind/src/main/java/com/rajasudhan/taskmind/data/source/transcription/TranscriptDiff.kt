package com.rajasudhan.taskmind.data.source.transcription

import com.rajasudhan.taskmind.data.source.understanding.NearDuplicate

/**
 * Decides whether a second-pass transcript (#126, Whisper) differs from the first pass (Vosk) enough to
 * be worth acting on. A pass that only reshuffles whitespace/case or nudges a word isn't worth swapping
 * in and re-running the LLM; a materially different one — Whisper fixing an accent or Hindi/Tamil/English
 * code-switch that Vosk mangled — is. Pure (content-token overlap), so it's unit-tested.
 */
object TranscriptDiff {

    /** Shared content-word ratio at/above which the two transcripts are "the same" — no re-extract. */
    const val MATERIAL_OVERLAP = 0.7

    /**
     * True when [second] is present AND meaningfully different from [first]: the first pass had nothing
     * while the second does, or the two share less than [MATERIAL_OVERLAP] of their content words.
     */
    fun isMaterialChange(first: String?, second: String?): Boolean {
        if (second.isNullOrBlank()) return false // nothing better to swap in
        if (first.isNullOrBlank()) return true   // second pass rescued a recording the first pass dropped
        // Identical text never counts — guard it before the overlap check, which would otherwise score an
        // all-filler-word transcript ("at the in on") as 0 overlap and wrongly flag it as changed.
        if (first.trim().equals(second.trim(), ignoreCase = true)) return false
        return NearDuplicate.tokenOverlap(first, second) < MATERIAL_OVERLAP
    }
}
