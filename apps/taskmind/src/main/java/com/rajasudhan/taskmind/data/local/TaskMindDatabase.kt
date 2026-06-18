package com.rajasudhan.taskmind.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.rajasudhan.taskmind.data.model.Note
import com.rajasudhan.taskmind.data.model.RejectedPattern
import com.rajasudhan.taskmind.data.model.Suggestion

@Database(
    entities = [Note::class, Suggestion::class, RejectedPattern::class],
    version = 5,
    exportSchema = false
)
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

        /**
         * v3 adds task/checklist/recurrence/location columns to notes, a snooze column to
         * suggestions, and the rejected_patterns learning table. Nullable columns are added without a
         * DEFAULT; `completed` mirrors its entity `@ColumnInfo(defaultValue = "0")`.
         */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE notes ADD COLUMN completed INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE notes ADD COLUMN completedDate INTEGER")
                db.execSQL("ALTER TABLE notes ADD COLUMN recurrence TEXT")
                db.execSQL("ALTER TABLE notes ADD COLUMN checklist TEXT")
                db.execSQL("ALTER TABLE notes ADD COLUMN locationLat REAL")
                db.execSQL("ALTER TABLE notes ADD COLUMN locationLng REAL")
                db.execSQL("ALTER TABLE notes ADD COLUMN locationRadius REAL")
                db.execSQL("ALTER TABLE notes ADD COLUMN locationLabel TEXT")
                db.execSQL("ALTER TABLE suggestions ADD COLUMN snoozedUntil INTEGER")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS rejected_patterns (" +
                        "kind TEXT NOT NULL, value TEXT NOT NULL, count INTEGER NOT NULL, " +
                        "updatedAt INTEGER NOT NULL, PRIMARY KEY(kind, value))"
                )
            }
        }

        /** v4 adds a nullable `location` column to suggestions (the place named in the source text). */
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE suggestions ADD COLUMN location TEXT")
            }
        }

        /** v5 adds a nullable `recurrence` column to suggestions ("daily"/"weekly"/"monthly"). */
        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE suggestions ADD COLUMN recurrence TEXT")
            }
        }
    }
}
