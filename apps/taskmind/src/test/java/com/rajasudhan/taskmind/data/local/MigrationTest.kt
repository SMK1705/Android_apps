package com.rajasudhan.taskmind.data.local

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import com.rajasudhan.taskmind.data.local.TaskMindDatabase.Companion.MIGRATION_1_2
import com.rajasudhan.taskmind.data.local.TaskMindDatabase.Companion.MIGRATION_2_3
import com.rajasudhan.taskmind.data.local.TaskMindDatabase.Companion.MIGRATION_3_4
import com.rajasudhan.taskmind.data.local.TaskMindDatabase.Companion.MIGRATION_4_5
import com.rajasudhan.taskmind.data.local.TaskMindDatabase.Companion.MIGRATION_5_6
import com.rajasudhan.taskmind.data.local.TaskMindDatabase.Companion.MIGRATION_6_7
import com.rajasudhan.taskmind.data.local.TaskMindDatabase.Companion.MIGRATION_7_8
import com.rajasudhan.taskmind.data.local.TaskMindDatabase.Companion.MIGRATION_8_9
import com.rajasudhan.taskmind.data.local.TaskMindDatabase.Companion.MIGRATION_9_10
import com.rajasudhan.taskmind.data.local.TaskMindDatabase.Companion.MIGRATION_10_11
import com.rajasudhan.taskmind.data.local.TaskMindDatabase.Companion.MIGRATION_11_12
import com.rajasudhan.taskmind.data.local.TaskMindDatabase.Companion.MIGRATION_12_13
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Exercises the real MIGRATION_1_2 … MIGRATION_10_11 chain end to end.
 *
 * There are no exported schemas for v1–v4 (export was only enabled at v5), so instead of
 * MigrationTestHelper we create a genuine v1 database with raw SQL (the original columns minus
 * everything the migrations add), seed a row in each table, then open it through Room with every
 * migration. Opening triggers the migration chain **and** Room's own schema validation against the
 * current entities — a wrong migration throws here — and we assert the seeded rows survive with the
 * new columns defaulted as designed.
 */
@RunWith(RobolectricTestRunner::class)
class MigrationTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val testDb = "migration-test-db"

    @After
    fun cleanup() {
        context.deleteDatabase(testDb)
    }

    /** Create the database at schema version 1 with the original (pre-migration) tables + a row each. */
    private fun createV1Database() {
        context.deleteDatabase(testDb)
        val config = SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(testDb)
            .callback(object : SupportSQLiteOpenHelper.Callback(1) {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    db.execSQL(
                        "CREATE TABLE IF NOT EXISTS `notes` (" +
                            "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                            "`title` TEXT NOT NULL, `body` TEXT NOT NULL, " +
                            "`dueDate` TEXT, `dueTime` TEXT, `source` TEXT NOT NULL, " +
                            "`createdDate` INTEGER NOT NULL, `type` TEXT NOT NULL)"
                    )
                    db.execSQL(
                        "CREATE TABLE IF NOT EXISTS `suggestions` (" +
                            "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                            "`source` TEXT NOT NULL, `rawSnippet` TEXT NOT NULL, " +
                            "`extractedTitle` TEXT NOT NULL, `dueDate` TEXT, `dueTime` TEXT, " +
                            "`type` TEXT NOT NULL, `confidence` REAL NOT NULL, `status` TEXT NOT NULL)"
                    )
                    db.execSQL(
                        "INSERT INTO notes (title, body, dueDate, dueTime, source, createdDate, type) " +
                            "VALUES ('v1 note', 'body', NULL, NULL, 'Manual entry', 100, 'note')"
                    )
                    db.execSQL(
                        "INSERT INTO suggestions " +
                            "(source, rawSnippet, extractedTitle, dueDate, dueTime, type, confidence, status) " +
                            "VALUES ('SMS from +1', 'raw', 'v1 sug', NULL, NULL, 'note', 0.9, 'pending')"
                    )
                }

                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {
                    // No-op: this helper only ever creates the v1 baseline.
                }
            })
            .build()
        val helper = FrameworkSQLiteOpenHelperFactory().create(config)
        helper.writableDatabase // triggers onCreate at version 1
        helper.close()
    }

    @Test
    fun migrate1To13_preservesData_andRoomValidatesSchema() = runTest {
        createV1Database()

        val db = Room.databaseBuilder(context, TaskMindDatabase::class.java, testDb)
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10, MIGRATION_10_11, MIGRATION_11_12, MIGRATION_12_13)
            .allowMainThreadQueries()
            .build()
        // Opening runs the 1→13 chain and validates the resulting schema against the current entities.
        db.openHelper.writableDatabase

        val dao = db.taskMindDao()

        val notes = dao.getNotesList()
        assertEquals(1, notes.size)
        assertEquals("v1 note", notes[0].title)
        assertEquals("", notes[0].summary)   // added in v2 with DEFAULT ''
        assertFalse(notes[0].completed)      // added in v3 with DEFAULT 0
        assertNull(notes[0].recurrence)      // added in v3, nullable
        assertEquals("normal", notes[0].priority) // added in v6 with DEFAULT 'normal'
        assertFalse(notes[0].nag)            // added in v8 with DEFAULT 0
        assertNull(notes[0].counterparty)    // added in v9, nullable
        assertNull(notes[0].pendingConfirmSince) // added in v11, nullable
        assertNull(notes[0].recurrenceAnchorDay) // added in v12, nullable (the note isn't monthly)
        assertFalse(notes[0].nagFiring)      // added in v13 with DEFAULT 0

        val sug = dao.getSuggestionById(1)
        assertNotNull(sug)
        assertEquals("v1 sug", sug!!.extractedTitle)
        assertEquals("", sug.summary)        // v2
        assertNull(sug.snoozedUntil)         // v3
        assertNull(sug.location)             // v4
        assertNull(sug.recurrence)           // v5
        assertEquals("normal", sug.priority) // added in v7 with DEFAULT 'normal'
        assertNull(sug.counterparty)         // added in v9, nullable

        // v10 added the note_embeddings table — it must exist and be queryable (empty at this point).
        assertEquals(0, dao.getAllEmbeddings().size)

        db.close()
    }

    @Test
    fun migration11To12_addsAnchorColumn_andBackfillsMonthlyRemindersFromTheirDay() {
        // Seed a minimal notes table (the columns the backfill reads) at "v11", then run the REAL
        // MIGRATION_11_12: it must add recurrenceAnchorDay and backfill it from the day-of-month for
        // monthly-with-a-date rows only.
        context.deleteDatabase(testDb)
        val config = SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(testDb)
            .callback(object : SupportSQLiteOpenHelper.Callback(11) {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    db.execSQL("CREATE TABLE notes (id INTEGER PRIMARY KEY, dueDate TEXT, recurrence TEXT)")
                    db.execSQL("INSERT INTO notes (id, dueDate, recurrence) VALUES (1, '2026-01-31', 'monthly')")
                    db.execSQL("INSERT INTO notes (id, dueDate, recurrence) VALUES (2, '2026-06-15', 'daily')")
                    db.execSQL("INSERT INTO notes (id, dueDate, recurrence) VALUES (3, NULL, 'monthly')")
                }
                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {}
            })
            .build()
        val helper = FrameworkSQLiteOpenHelperFactory().create(config)
        val db = helper.writableDatabase

        MIGRATION_11_12.migrate(db)

        db.query("SELECT id, recurrenceAnchorDay FROM notes ORDER BY id").use { c ->
            c.moveToNext(); assertEquals(1, c.getInt(0)); assertEquals(31, c.getInt(1))  // monthly Jan 31 -> 31
            c.moveToNext(); assertEquals(2, c.getInt(0)); assertTrue(c.isNull(1))         // daily -> null
            c.moveToNext(); assertEquals(3, c.getInt(0)); assertTrue(c.isNull(1))         // monthly, no date -> null
        }
        helper.close()
    }
}
