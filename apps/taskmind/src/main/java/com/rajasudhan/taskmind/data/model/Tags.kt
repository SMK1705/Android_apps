package com.rajasudhan.taskmind.data.model

/**
 * The closed auto-tag taxonomy (#123) plus the encode/decode for the `tags` column on [Note] and
 * [Suggestion]. Tags come from a fixed 7-value list with no commas, so they're stored as a single
 * comma-separated string — no Room TypeConverter needed — and this stays pure Kotlin, unit-testable
 * on the plain JVM like [com.rajasudhan.taskmind.ui.notes.Checklist].
 *
 * "Auto-tags" means the extraction model assigns 0–2 of these per item so notes self-organise into
 * topic spaces with zero manual filing; the Notes filter row surfaces them as chips.
 */
object Tags {
    /** The only tags the app emits/stores. Order here defines the filter-chip display order. */
    val TAXONOMY = listOf("Money", "Health", "Family", "Work", "Shopping", "Travel", "Home")

    /** The model is asked for 0–2 tags; cap defensively so a runaway model can't over-tag an item. */
    const val MAX_TAGS = 2

    // Lower-cased lookup so a model that returns "money"/"WORK" still lands on the canonical casing.
    private val CANONICAL: Map<String, String> = TAXONOMY.associateBy { it.lowercase() }

    /** Canonicalises a raw model tag to the taxonomy's exact casing, or null if it isn't in the list. */
    fun canonical(raw: String): String? = CANONICAL[raw.trim().lowercase()]

    /** Serialises a tag list to the stored form, e.g. `["Money","Work"]` → `"Money,Work"`. */
    fun encode(tags: List<String>): String = tags.joinToString(",")

    /** Parses the stored form back to a tag list; empty (never null) for a null or blank column. */
    fun decode(stored: String?): List<String> =
        stored?.split(",")?.map { it.trim() }?.filter { it.isNotBlank() } ?: emptyList()
}
