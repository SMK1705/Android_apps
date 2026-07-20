package com.rajasudhan.taskmind.data.source.understanding

/**
 * The multi-turn memory behind Ask (#318). #312 carried exactly one prior intent, so a three-step
 * refinement lost the earliest slots ("anything overdue?" → "just the Work ones" → "which are done?"
 * dropped `overdue` by the third turn). This keeps a bounded window of resolved query intents and
 * FOLDS them — most-recent non-null wins per slot — so an earlier slot survives even if a later turn's
 * model output happens to omit it. [context] is what gets handed to the classifier as the running state.
 *
 * It resets cleanly on a topic change so stale slots never skew a fresh question: a non-query turn
 * (search/create → null intent) clears it, and so does a genuine subject switch (the resolved `type`
 * or `tag` changing to a different value). Pure and deterministic — the LLM still does refine-vs-fresh
 * per turn; this only makes the accumulation robust and keeps the prompt small (bounded depth).
 */
class AskConversation(private val maxDepth: Int = DEFAULT_DEPTH) {
    private val history = ArrayDeque<AskIntent>()

    /** The accumulated running intent handed to the classifier, or null when there's nothing to inherit. */
    fun context(): AskIntent? = if (history.isEmpty()) null else history.reduce(::fold)

    /**
     * Fold a freshly-resolved intent into the window. A null intent (a keyword search or a create — no
     * structured slots) is a fresh topic and clears the window, matching #312's "don't inherit" rule.
     */
    fun record(resolved: AskIntent?) {
        if (resolved == null || resolved.action == "create") {
            history.clear()
            return
        }
        val prior = context()
        if (prior != null && subjectChanged(prior, resolved)) history.clear() // topic switch, not a refinement
        history.addLast(resolved)
        while (history.size > maxDepth) history.removeFirst() // keep the prompt small
    }

    /** Restore a persisted window (#317) so multi-turn survives process death. */
    fun restore(intents: List<AskIntent>) {
        history.clear()
        intents.takeLast(maxDepth).forEach(history::addLast)
    }

    fun snapshot(): List<AskIntent> = history.toList()

    fun clear() = history.clear()

    /** Later turn wins per slot; anything it leaves null falls back to the accumulated value. */
    private fun fold(acc: AskIntent, next: AskIntent) = AskIntent(
        action = next.action,
        type = next.type ?: acc.type,
        tag = next.tag ?: acc.tag,
        window = next.window ?: acc.window,
        status = next.status ?: acc.status,
        keyword = next.keyword ?: acc.keyword,
        text = next.text ?: acc.text,
    )

    /** The subject changed if the new turn names a DIFFERENT type or tag — that's a new topic, not a refine
     *  (narrowing a window or adding a status keeps the same subject and should accumulate). */
    private fun subjectChanged(prior: AskIntent, next: AskIntent): Boolean =
        (next.type != null && prior.type != null && next.type != prior.type) ||
            (next.tag != null && prior.tag != null && next.tag != prior.tag)

    companion object {
        const val DEFAULT_DEPTH = 4
    }
}
