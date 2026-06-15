package com.rajasudhan.taskmind.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.rajasudhan.taskmind.data.model.Note
import com.rajasudhan.taskmind.data.model.Suggestion

@Database(entities = [Note::class, Suggestion::class], version = 2, exportSchema = false)
abstract class TaskMindDatabase : RoomDatabase() {
    abstract fun taskMindDao(): TaskMindDao

    companion object {
        /** v2 adds a one-line `summary` column to both tables (existing rows default to ""). */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE suggestions ADD COLUMN summary TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE notes ADD COLUMN summary TEXT NOT NULL DEFAULT ''")
            }
        }
    }
}
