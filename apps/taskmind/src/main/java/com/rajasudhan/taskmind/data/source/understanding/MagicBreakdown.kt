package com.rajasudhan.taskmind.data.source.understanding

/**
 * "Break it down": turns one vague task into a short checklist of concrete sub-steps via the
 * on-device model. The prompt + the tolerant parser live here (pure, no Android deps) so the
 * fiddly part — coaxing usable steps out of a small model's messy output — is unit-testable.
 */
object MagicBreakdown {

    const val INSTRUCTION =
        "You break a single task into a short checklist of concrete sub-steps. " +
            "Return ONLY a JSON array of 3 to 6 short strings, each a doable action of at most about " +
            "8 words. No numbering, no commentary, no markdown fences. " +
            "Example: [\"Gather documents\", \"Fill in the form\", \"Submit online\"]."

    private const val MAX_STEPS = 6
    private const val MAX_LEN = 80

    /**
     * Parses the model's reply into clean step strings, tolerating a JSON array, a numbered/bulleted
     * list, or plain lines. Empty when nothing usable came back (the caller then leaves the note as
     * it was). Steps are trimmed, de-bulleted, de-duplicated (case-insensitively), length-capped, and
     * limited to [MAX_STEPS].
     */
    fun parseSteps(raw: String): List<String> {
        // Shared, hardened fence-stripping (handles a trailing newline after the closing ```).
        val cleaned = ExtractionHeuristics.stripJsonFences(raw)

        // Prefer the quoted items inside a JSON-ish array when one is present. Detect on a lone '['
        // (not '[' AND ']') so a truncated array that never closed — `["Gather docs", "Fill the fo`
        // from a MAX_TOKENS cut-off — still yields its complete quoted items instead of falling
        // through to the comma path with bracket junk. substringBeforeLast(']') is a no-op when the
        // ']' is absent, leaving the tail intact for the quoted-token scan.
        val fromArray = if (cleaned.contains('[')) {
            QUOTED.findAll(cleaned.substringAfter('[').substringBeforeLast(']'))
                .map { it.groupValues[1] }.toList()
        } else emptyList()

        val candidates = fromArray.ifEmpty {
            // …otherwise treat it as lines / a comma list.
            val lines = cleaned.lines().map { it.trim() }.filter { it.isNotBlank() }
            if (lines.size >= 2) {
                lines
            } else {
                // A single-line comma list. If it's a bracketed array (`[milk, eggs, bread]`, or a
                // truncated `[milk, eggs`), drop the outer array brackets ONCE here so they don't
                // cling to the first/last item. Never strip brackets per-item — that would eat a
                // legitimate one inside a step like "Water plants [balcony]".
                val inner = if (cleaned.startsWith("[")) cleaned.removePrefix("[").removeSuffix("]") else cleaned
                inner.split(",")
            }
        }

        val seen = LinkedHashSet<String>()
        for (c in candidates) {
            val step = c.trim()
                .removeSurrounding("\"")
                // Undo JSON string escapes so a step like "Say \"hi\"" reads cleanly.
                .replace("\\\"", "\"").replace("\\\\", "\\").replace("\\n", " ")
                .trim()
                .trimStart('-', '•', '*', '·', '#')
                .trim()
                .let { STRIP_LEADING_NUMBER.replace(it, "") }
                .removeSuffix(",")
                .trim()
                .take(MAX_LEN)
                .trim()
            if (step.isNotBlank() && seen.none { it.equals(step, ignoreCase = true) }) {
                seen.add(step)
            }
            if (seen.size >= MAX_STEPS) break
        }
        return seen.toList()
    }

    // A JSON string body, allowing backslash escapes (so an item with an escaped quote isn't split).
    private val QUOTED = Regex("\"((?:[^\"\\\\]|\\\\.)+)\"")
    private val STRIP_LEADING_NUMBER = Regex("""^\s*\d+[.)]\s*""")
}
