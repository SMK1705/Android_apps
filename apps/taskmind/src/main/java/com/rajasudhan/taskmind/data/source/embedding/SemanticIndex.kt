package com.rajasudhan.taskmind.data.source.embedding

import com.rajasudhan.taskmind.data.local.TaskMindDao
import com.rajasudhan.taskmind.data.model.NoteEmbedding
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The app's semantic layer: embeds notes (via [Embedder]), stores their vectors, and answers the two
 * questions that need meaning rather than keywords — "is this new capture a near-duplicate of
 * something I already saved?" (dedup) and "which notes are relevant to this query?" (search).
 */
@Singleton
class SemanticIndex @Inject constructor(
    private val embedder: Embedder,
    private val dao: TaskMindDao,
) {
    /** The text that represents a note/item for embedding: its title plus one-line summary. */
    fun textFor(title: String, summary: String?): String =
        listOf(title.trim(), summary?.trim().orEmpty()).filter { it.isNotBlank() }.joinToString(". ")

    /** Embed a note's [title]+[summary] and store the vector under [noteId]. No-op if it can't embed. */
    suspend fun index(noteId: Int, title: String, summary: String?) {
        val vec = embedder.embed(textFor(title, summary)) ?: return
        dao.upsertEmbedding(NoteEmbedding(noteId, Vectors.toBytes(vec)))
    }

    /** Embed any notes that don't have a vector yet — a cheap catch-up for notes saved before this. */
    suspend fun backfill() {
        val have = dao.embeddedNoteIds().toHashSet()
        for (note in dao.getNotesList()) {
            if (note.id !in have) index(note.id, note.title, note.summary)
        }
    }

    /**
     * Relevance scores of stored notes against [query]: note-id → cosine, for notes scoring above
     * [floor]. Empty when [query] can't be embedded or nothing is indexed.
     */
    suspend fun scores(query: String, floor: Float): Map<Int, Float> {
        val q = embedder.embed(query) ?: return emptyMap()
        val out = HashMap<Int, Float>()
        for (e in dao.getAllEmbeddings()) {
            val sim = Vectors.cosine(q, Vectors.fromBytes(e.vector))
            if (sim >= floor) out[e.noteId] = sim
        }
        return out
    }

    companion object {
        /** A note scoring at/above this to a search query is "related enough" to show without a keyword hit. */
        const val SEARCH_FLOOR = 0.35f
    }
}
