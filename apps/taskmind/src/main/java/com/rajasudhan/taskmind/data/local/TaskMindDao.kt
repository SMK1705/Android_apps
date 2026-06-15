package com.rajasudhan.taskmind.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.rajasudhan.taskmind.data.model.Note
import com.rajasudhan.taskmind.data.model.Suggestion
import kotlinx.coroutines.flow.Flow

@Dao
interface TaskMindDao {
    @Query("SELECT * FROM suggestions WHERE status = 'pending' ORDER BY confidence DESC")
    fun getPendingSuggestions(): Flow<List<Suggestion>>

    @Insert
    suspend fun insertSuggestion(suggestion: Suggestion)

    @Update
    suspend fun updateSuggestion(suggestion: Suggestion)

    @Query("SELECT * FROM notes ORDER BY createdDate DESC")
    fun getAllNotes(): Flow<List<Note>>

    @Query("SELECT * FROM notes WHERE id = :id")
    fun getNoteById(id: Int): Flow<Note?>

    @Insert
    suspend fun insertNote(note: Note)

    @Update
    suspend fun updateNote(note: Note)

    @Delete
    suspend fun deleteNote(note: Note)

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
}
