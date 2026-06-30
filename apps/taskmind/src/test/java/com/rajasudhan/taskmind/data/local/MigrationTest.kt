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
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Exercises the real MIGRATION_1_2 … MIGRATION_4_5 chain end to end.
 *
 * There are no exported schemas for v1–v4 (export was only enabled at v5), so instead of
 * MigrationTestHelper we create a genuine v1 database with raw SQL (the v5 columns minus everything
 * the migrations add), seed a row in each table, then open it through Room with all four migrations.
 * Opening triggers the migration chain **and** Room's own schema validation against the current
 * entities — a wrong migration throws here — and we assert the seeded rows survive with the new
 * columns defaulted as designed.
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
    fun migrate1To5_preservesData_andRoomValidatesSchema() = runTest {
        createV1Database()

        val db = Room.databaseBuilder(context, TaskMindDatabase::class.java, testDb)
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
            .allowMainThreadQueries()
            .build()
        // Opening runs the 1→5 chain and validates the resulting schema against the current entities.
        db.openHelper.writableDatabase

        val dao = db.taskMindDao()

        val notes = dao.getNotesList()
        assertEquals(1, notes.size)
        assertEquals("v1 note", notes[0].title)
        assertEquals("", notes[0].summary)   // added in v2 with DEFAULT ''
        assertFalse(notes[0].completed)      // added in v3 with DEFAULT 0
        assertNull(notes[0].recurrence)      // added in v3, nullable

        val sug = dao.getSuggestionById(1)
        assertNotNull(sug)
        assertEquals("v1 sug", sug!!.extractedTitle)
        assertEquals("", sug.summary)        // v2
        assertNull(sug.snoozedUntil)         // v3
        assertNull(sug.location)             // v4
        assertNull(sug.recurrence)           // v5

        db.close()
    }
}
