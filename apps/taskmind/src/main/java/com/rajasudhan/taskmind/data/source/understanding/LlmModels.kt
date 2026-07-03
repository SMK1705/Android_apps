package com.rajasudhan.taskmind.data.source.understanding

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class LlmResponse(
    val items: List<LlmItem>
)

@JsonClass(generateAdapter = true)
data class LlmItem(
    // Defaults make parsing tolerant of small on-device models that omit fields.
    val type: String = "note",
    val title: String = "",
    val notes: String = "",
    @Json(name = "due_date") val dueDate: String? = null,
    @Json(name = "due_time") val dueTime: String? = null,
    // A place/venue/address named in the text (e.g. "Panda Express, Dunwoody GA"), or null.
    val location: String? = null,
    // "daily" | "weekly" | "monthly" for a repeating reminder ("every Monday"), else null.
    val recurrence: String? = null,
    // "high" only on explicit urgency cues; otherwise "normal". Defaulted so on-device models that
    // omit it fall back to "normal" (never "low" — extraction only distinguishes normal vs high).
    val priority: String = "normal",
    // The other party an item involves — who you're waiting on (type "waiting_on") or who you owe a
    // commitment ("Send Alex the deck" → "Alex"). Null when no person/org is named.
    val counterparty: String? = null,
    val confidence: Double = 0.7
)
