package com.rajasudhan.taskmind.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.rajasudhan.taskmind.data.model.Note
import com.rajasudhan.taskmind.data.model.NoteEmbedding
import com.rajasudhan.taskmind.data.model.RejectedPattern
import com.rajasudhan.taskmind.data.model.Suggestion

@Database(
    entities = [Note::class, Suggestion::class, RejectedPattern::class, NoteEmbedding::class],
    version = 18,
    exportSchema = true
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

        /** v6 adds a `priority` column to notes ("low"/"normal"/"high"); existing rows default to normal. */
        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE notes ADD COLUMN priority TEXT NOT NULL DEFAULT 'normal'")
            }
        }

        /**
         * v7 adds a `priority` column to suggestions ("normal"/"high" — the model's suggested
         * priority, copied onto the note on approval); existing rows default to normal.
         */
        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE suggestions ADD COLUMN priority TEXT NOT NULL DEFAULT 'normal'")
            }
        }

        /** v8 adds a `nag` flag to notes (re-fire the reminder until done); existing rows default off. */
        val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE notes ADD COLUMN nag INTEGER NOT NULL DEFAULT 0")
            }
        }

        /**
         * v9 adds a nullable `counterparty` column to both notes and suggestions — the other party a
         * "waiting_on"/commitment item involves. Nullable, so no DEFAULT (mirrors the entity fields).
         */
        val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE notes ADD COLUMN counterparty TEXT")
                db.execSQL("ALTER TABLE suggestions ADD COLUMN counterparty TEXT")
            }
        }

        /**
         * v10 adds the `note_embeddings` table — one semantic vector (BLOB) per note, for semantic
         * search + near-duplicate detection. Cascades on note delete so vectors never orphan.
         */
        val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `note_embeddings` (" +
                        "`noteId` INTEGER NOT NULL, `vector` BLOB NOT NULL, PRIMARY KEY(`noteId`), " +
                        "FOREIGN KEY(`noteId`) REFERENCES `notes`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE)"
                )
            }
        }

        /**
         * v11 adds a nullable `pendingConfirmSince` column to notes — the timestamp a "waiting_on"
         * item's counterparty got in touch, marking it as awaiting the user's confirmation that they
         * actually delivered. Nullable, so no DEFAULT (mirrors the entity field).
         */
        val MIGRATION_10_11 = object : Migration(10, 11) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE notes ADD COLUMN pendingConfirmSince INTEGER")
            }
        }

        /**
         * v12 adds a nullable `recurrenceAnchorDay` column to notes — the intended day-of-month a
         * monthly reminder should keep, so stepping doesn't drift the 29th–31st down to the 28th after
         * February. Backfills existing monthly reminders from their stored day-of-month (best effort:
         * one already drifted keeps its current day, but future stepping is anchored from here on).
         */
        val MIGRATION_11_12 = object : Migration(11, 12) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE notes ADD COLUMN recurrenceAnchorDay INTEGER")
                db.execSQL(
                    "UPDATE notes SET recurrenceAnchorDay = CAST(substr(dueDate, 9, 2) AS INTEGER) " +
                        "WHERE recurrence = 'monthly' AND dueDate IS NOT NULL AND length(dueDate) = 10"
                )
            }
        }

        /**
         * v13 adds a `nagFiring` flag to notes — whether a nag reminder's re-fire chain is currently
         * active — so "nag until done" resumes after a reboot even for a recurring reminder (whose date
         * advances on fire, hiding the fact that it fired). Existing rows default off (NOT NULL DEFAULT 0,
         * mirroring the entity); nothing is mid-nag across an upgrade.
         */
        val MIGRATION_12_13 = object : Migration(12, 13) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE notes ADD COLUMN nagFiring INTEGER NOT NULL DEFAULT 0")
            }
        }

        /**
         * v14 adds a nullable `tags` column to both notes and suggestions — 0–2 auto-tags from the
         * closed taxonomy (#123), stored comma-separated. Nullable (null = untagged), so no DEFAULT
         * (mirrors the entity fields); existing rows are simply untagged until re-extracted.
         */
        val MIGRATION_13_14 = object : Migration(13, 14) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE notes ADD COLUMN tags TEXT")
                db.execSQL("ALTER TABLE suggestions ADD COLUMN tags TEXT")
            }
        }

        /**
         * v15 adds a nullable `possibleDuplicateOf` column to suggestions — the title of an existing
         * item a capture is likely a re-capture of, for the non-destructive "possible duplicate" flag
         * (#145). Suggestions only; notes never carry it. Nullable (null = not flagged), so no DEFAULT.
         */
        val MIGRATION_14_15 = object : Migration(14, 15) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE suggestions ADD COLUMN possibleDuplicateOf TEXT")
            }
        }

        /**
         * v16 adds an `archived` flag to notes — Task Fade / bankruptcy (#125): a stale item the user
         * batch-archived instead of finishing or deleting it. Existing rows default off (NOT NULL
         * DEFAULT 0, mirroring the entity); nothing is archived across an upgrade.
         */
        val MIGRATION_15_16 = object : Migration(15, 16) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE notes ADD COLUMN archived INTEGER NOT NULL DEFAULT 0")
            }
        }

        /**
         * v17 adds a `repeatFromCompletion` flag to both notes and suggestions — completion-based
         * recurrence (#124): a repeating reminder whose next occurrence is scheduled from when it's
         * COMPLETED, not from its due date. Existing rows default off (NOT NULL DEFAULT 0, mirroring the
         * entities), so every current repeat stays date-based across the upgrade.
         */
        val MIGRATION_16_17 = object : Migration(16, 17) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE notes ADD COLUMN repeatFromCompletion INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE suggestions ADD COLUMN repeatFromCompletion INTEGER NOT NULL DEFAULT 0")
            }
        }

        /**
         * v18 adds a nullable `calendarEventId` column to notes — the device-calendar event a note
         * mirrors (#119), so the one-way mirror can update/delete it as the note changes instead of being
         * write-once. Nullable (null = nothing mirrored), so no DEFAULT.
         */
        val MIGRATION_17_18 = object : Migration(17, 18) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE notes ADD COLUMN calendarEventId INTEGER")
            }
        }
    }
}
