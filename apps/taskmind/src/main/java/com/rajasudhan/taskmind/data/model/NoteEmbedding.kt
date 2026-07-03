package com.rajasudhan.taskmind.data.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey

/**
 * A note's semantic vector, kept in its own table so the (potentially large) BLOB isn't loaded on
 * every notes query — only when searching or de-duplicating. Cascades on note delete so a removed
 * note never leaves an orphan vector behind. One row per note.
 */
@Entity(
    tableName = "note_embeddings",
    foreignKeys = [
        ForeignKey(
            entity = Note::class,
            parentColumns = ["id"],
            childColumns = ["noteId"],
            onDelete = ForeignKey.CASCADE,
        )
    ],
)
data class NoteEmbedding(
    @PrimaryKey val noteId: Int,
    val vector: ByteArray, // little-endian packed floats (see Vectors.toBytes)
) {
    // Room stores this as a BLOB; equals/hashCode are content-based so the ByteArray compares by value.
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is NoteEmbedding) return false
        return noteId == other.noteId && vector.contentEquals(other.vector)
    }

    override fun hashCode(): Int = 31 * noteId + vector.contentHashCode()
}
