package com.rajasudhan.taskmind.data.source.understanding

import com.rajasudhan.taskmind.data.model.Note
import com.squareup.moshi.JsonClass

/**
 * The constrained intent an "Ask TaskMind" utterance is mapped to (#128).
 *
 * Deliberately a SMALL closed set of actions + slots — not open-ended RAG — so a Gemma-class model
 * stays reliable; a low-confidence or unparseable reply falls back to keyword/semantic search. Every
 * field defaults so a partial reply still parses.
 */
@JsonClass(generateAdapter = true)
data class AskIntent(
    val action: String = "query",  // "query" (find items) | "create" (capture a new item)
    val type: String? = null,      // filter to a kind: "todo" | "reminder" | "note" | "waiting_on"
    val tag: String? = null,       // filter to a taxonomy tag (Money/Health/Family/Work/Shopping/Travel/Home)
    val window: String? = null,    // "today" | "tomorrow" | "this_week" | "this_weekend" | "overdue" | "upcoming"
    val status: String? = null,    // "active" | "done" (default active)
    val keyword: String? = null,   // a content word to match on title/summary/body
    val text: String? = null,      // for action="create": the item to capture, phrased as the user would
)

/** What the engine renders back into the chat: a one-line answer plus any matching item cards. */
data class AskResult(
    val answer: String,
    val notes: List<Note> = emptyList(),
    val kind: AskResultKind = AskResultKind.RESULTS,
    /**
     * The structured intent this answer was built from, handed back so the NEXT turn can refine it
     * ("anything overdue?" -> "what about next week?"). Null for a keyword search or a create — there
     * are no slots worth inheriting, and a stale carry-over would silently skew an unrelated question.
     */
    val intent: AskIntent? = null,
    /**
     * True when [answer] was written by the cloud model FROM the note content (the opt-in answer
     * layer) rather than composed deterministically here. Surfaced in the chat so the user always
     * knows when a sentence came from a model reading their notes — the same honesty rule as the
     * engine label (#197).
     */
    val answeredFromNotes: Boolean = false,
)

enum class AskResultKind { RESULTS, CREATED, EMPTY }
