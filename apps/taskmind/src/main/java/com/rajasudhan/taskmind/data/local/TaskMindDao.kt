package com.rajasudhan.taskmind.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.rajasudhan.taskmind.data.model.Note
import com.rajasudhan.taskmind.data.model.RejectedPattern
import com.rajasudhan.taskmind.data.model.Suggestion
import kotlinx.coroutines.flow.Flow

@Dao
interface TaskMindDao {
    // All pending suggestions. Snooze is applied in the ViewModel (against a ticking clock) so a
    // snoozed item auto-resurfaces when its time passes, without needing a table change.
    @Query("SELECT * FROM suggestions WHERE status = 'pending' ORDER BY confidence DESC")
    fun getPendingSuggestions(): Flow<List<Suggestion>>

    @Insert
    suspend fun insertSuggestion(suggestion: Suggestion)

    @Update
    suspend fun updateSuggestion(suggestion: Suggestion)

    @Query("SELECT * FROM notes ORDER BY createdDate DESC")
    fun getAllNotes(): Flow<List<Note>>

    @Query("SELECT * FROM notes WHERE completed = 0 ORDER BY createdDate DESC")
    fun getActiveNotes(): Flow<List<Note>>

    @Query("SELECT * FROM notes WHERE completed = 1 ORDER BY completedDate DESC")
    fun getCompletedNotes(): Flow<List<Note>>

    /** Free-text search across title/summary/body. Caller passes a wildcarded term, e.g. "%milk%". */
    @Query("SELECT * FROM notes WHERE title LIKE :q OR summary LIKE :q OR body LIKE :q ORDER BY createdDate DESC")
    fun searchNotes(q: String): Flow<List<Note>>

    @Query("UPDATE notes SET completed = :completed, completedDate = :date WHERE id = :id")
    suspend fun setNoteCompleted(id: Int, completed: Boolean, date: Long?)

    @Query("UPDATE notes SET checklist = :checklist WHERE id = :id")
    suspend fun updateNoteChecklist(id: Int, checklist: String?)

    @Query("SELECT * FROM notes WHERE id = :id")
    fun getNoteById(id: Int): Flow<Note?>

    /** Returns the new row id so an approve can be undone by deleting exactly this note. */
    @Insert
    suspend fun insertNote(note: Note): Long

    @Update
    suspend fun updateNote(note: Note)

    @Delete
    suspend fun deleteNote(note: Note)

    @Query("DELETE FROM notes WHERE id = :id")
    suspend fun deleteNoteById(id: Int)

    @Query("DELETE FROM notes")
    suspend fun deleteAllNotes()

    @Query("DELETE FROM suggestions")
    suspend fun deleteAllSuggestions()

    /** One-shot snapshot of all notes (for export). */
    @Query("SELECT * FROM notes ORDER BY createdDate DESC")
    suspend fun getNotesList(): List<Note>

    /** Retention: drop notes created before [cutoff] (epoch millis). */
    @Query("DELETE FROM notes WHERE createdDate < :cutoff")
    suspend fun deleteNotesOlderThan(cutoff: Long)

    /** Cleanup: drop suggestions that are no longer pending (already approved/rejected). */
    @Query("DELETE FROM suggestions WHERE status != 'pending'")
    suspend fun deletePurgeableSuggestions()

    // ---- Learning memory (rejected senders/keywords) ----
    @Query("SELECT * FROM rejected_patterns WHERE kind = :kind AND value = :value")
    suspend fun rejectedPatternFor(kind: String, value: String): RejectedPattern?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertRejectedPattern(pattern: RejectedPattern)

    @Query("SELECT * FROM rejected_patterns")
    suspend fun allRejectedPatterns(): List<RejectedPattern>
}
