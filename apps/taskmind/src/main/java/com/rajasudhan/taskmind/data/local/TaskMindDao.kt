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
}
