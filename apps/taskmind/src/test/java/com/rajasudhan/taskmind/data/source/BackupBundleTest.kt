package com.rajasudhan.taskmind.data.source

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** The backup ZIP codec (#121): round-trip, optional entries, backward-compat, and path-traversal. */
class BackupBundleTest {

    @get:Rule
    val tmp = TemporaryFolder()

    /** The DB is streamed from a File (memory-safe for a large DB), so tests supply a temp file. */
    private fun dbFileOf(content: String): File = tmp.newFile().apply { writeText(content) }

    @Test
    fun encodeDecode_roundTripsAllFourParts() {
        val zip = BackupBundle.encode(
            dbKey = "KEY".toByteArray(), dbFile = dbFileOf("DBBYTES"),
            prefs = "PREFS".toByteArray(), datastore = "DS".toByteArray()
        )
        val parts = BackupBundle.decode(zip)
        assertEquals("KEY", parts.dbKey)
        assertArrayEquals("DBBYTES".toByteArray(), parts.db)
        assertArrayEquals("PREFS".toByteArray(), parts.prefs)
        assertArrayEquals("DS".toByteArray(), parts.datastore)
    }

    @Test
    fun encode_omitsOptionalEntries_whenNull() {
        val parts = BackupBundle.decode(
            BackupBundle.encode("KEY".toByteArray(), dbFileOf("DB"), prefs = null, datastore = null)
        )
        assertEquals("KEY", parts.dbKey)
        assertNull(parts.prefs)
        assertNull(parts.datastore)
    }

    @Test
    fun decode_oldTwoEntryBackup_stillRestores_withPrefsAndDatastoreNull() {
        // A backup made before #121 has only db_key + taskmind_db — it must still decode (backward compat).
        val old = zipOf("db_key" to "K", "taskmind_db" to "D")

        val parts = BackupBundle.decode(old)

        assertEquals("K", parts.dbKey)
        assertArrayEquals("D".toByteArray(), parts.db)
        assertNull(parts.prefs)
        assertNull(parts.datastore)
    }

    @Test
    fun decode_ignoresUnknownEntries_andMatchesByBasename_guardingPathTraversal() {
        // A crafted entry with a traversal path must be matched by basename only (the path is ignored),
        // and an unknown entry must be skipped rather than break the restore.
        val crafted = zipOf(
            "../../evil/db_key" to "K",
            "taskmind_db" to "D",
            "README.txt" to "junk",
        )

        val parts = BackupBundle.decode(crafted)

        assertEquals("K", parts.dbKey) // matched by basename despite the ../.. path
        assertArrayEquals("D".toByteArray(), parts.db)
    }

    private fun zipOf(vararg entries: Pair<String, String>): ByteArray =
        ByteArrayOutputStream().use { bytes ->
            ZipOutputStream(bytes).use { zip ->
                for ((name, content) in entries) {
                    zip.putNextEntry(ZipEntry(name)); zip.write(content.toByteArray()); zip.closeEntry()
                }
            }
            bytes.toByteArray()
        }
}
