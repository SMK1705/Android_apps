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
)

enum class AskResultKind { RESULTS, CREATED, EMPTY }
