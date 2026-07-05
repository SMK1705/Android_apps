package com.rajasudhan.taskmind.data.source

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * The (unencrypted) ZIP payload of a backup, factored out of [BackupManager] so the assembly/parsing —
 * the format's backward-compatibility, its per-entry size cap, and its path-traversal guard — is pure
 * and unit-testable without a device (BackupManager itself can't be, as it touches Keystore-bound
 * EncryptedSharedPreferences).
 *
 * The whole bundle is sealed inside the passphrase envelope by [BackupCrypto]; this layer only cares
 * about which named entries are present. Entries are optional and matched by basename, so backups stay
 * forward/backward compatible: an older backup (DB + key only) restores on new code, and a newer backup
 * (with settings + datastore) restores on old code — the unknown entries are simply ignored either way.
 */
object BackupBundle {

    const val ENTRY_KEY = "db_key"
    const val ENTRY_DB = "taskmind_db"
    const val ENTRY_PREFS = "prefs.json"
    const val ENTRY_DATASTORE = "datastore.json"

    /** Upper bound on any single inflated entry — degrades a decompression bomb (or a corrupt-but-
     *  authenticated bundle) to a clean failure instead of an OOM. Far above any real backup entry. */
    private const val MAX_ENTRY_BYTES = 512L * 1024 * 1024

    data class Parts(
        val dbKey: String? = null,
        val db: ByteArray? = null,
        val prefs: ByteArray? = null,
        val datastore: ByteArray? = null,
    )

    /**
     * Assembles the ZIP, STREAMING the (large) DB file rather than buffering it whole. [prefs]/[datastore]
     * are optional (a fresh install may have no settings/datastore snapshot yet).
     */
    fun encode(dbKey: ByteArray, dbFile: File, prefs: ByteArray?, datastore: ByteArray?): ByteArray =
        ByteArrayOutputStream().use { bytes ->
            ZipOutputStream(bytes).use { zip ->
                writeEntry(zip, ENTRY_KEY, dbKey)
                zip.putNextEntry(ZipEntry(ENTRY_DB))
                dbFile.inputStream().use { it.copyTo(zip) }
                zip.closeEntry()
                if (prefs != null) writeEntry(zip, ENTRY_PREFS, prefs)
                if (datastore != null) writeEntry(zip, ENTRY_DATASTORE, datastore)
            }
            bytes.toByteArray()
        }

    /**
     * Parses a decrypted bundle into its parts. Matches entries by BASENAME only — a path-traversal
     * guard, so a maliciously-crafted backup can't smuggle a `../../…` path — caps each inflated entry,
     * and ignores any unknown entry, which is what keeps old and new backups mutually restorable.
     */
    fun decode(zipBytes: ByteArray): Parts {
        var dbKey: String? = null
        var db: ByteArray? = null
        var prefs: ByteArray? = null
        var datastore: ByteArray? = null
        ZipInputStream(ByteArrayInputStream(zipBytes)).use { zip ->
            var entry: ZipEntry? = zip.nextEntry
            while (entry != null) {
                when (entry.name.substringAfterLast('/')) {
                    ENTRY_KEY -> dbKey = zip.readCapped().toString(Charsets.UTF_8)
                    ENTRY_DB -> db = zip.readCapped()
                    ENTRY_PREFS -> prefs = zip.readCapped()
                    ENTRY_DATASTORE -> datastore = zip.readCapped()
                }
                zip.closeEntry()
                entry = zip.nextEntry
            }
        }
        return Parts(dbKey, db, prefs, datastore)
    }

    private fun writeEntry(zip: ZipOutputStream, name: String, data: ByteArray) {
        zip.putNextEntry(ZipEntry(name))
        zip.write(data)
        zip.closeEntry()
    }

    /** Reads the current entry, bailing out with a clear error past [MAX_ENTRY_BYTES] rather than OOM-ing. */
    private fun ZipInputStream.readCapped(): ByteArray {
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(64 * 1024)
        var total = 0L
        while (true) {
            val n = read(buffer)
            if (n < 0) break
            total += n
            if (total > MAX_ENTRY_BYTES) throw IllegalStateException("Backup entry too large — refusing to inflate.")
            out.write(buffer, 0, n)
        }
        return out.toByteArray()
    }
}
