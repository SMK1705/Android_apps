package com.rajasudhan.taskmind.data.source

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import com.rajasudhan.taskmind.data.local.TaskMindDatabase
import dagger.hilt.android.qualifiers.ApplicationContext
import net.zetetic.database.sqlcipher.SQLiteDatabase
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Passphrase-encrypted backup & restore of the whole TaskMind database.
 *
 * The on-device DB is already SQLCipher-encrypted with a random per-install key kept in
 * EncryptedSharedPreferences ([com.rajasudhan.taskmind.di.DatabaseModule], `db_key`). A backup must
 * therefore bundle BOTH the encrypted DB file AND that key, or it can never be opened again. To keep
 * the resulting file safe at rest — it travels off-device via the Storage Access Framework — the
 * whole bundle is itself encrypted with AES-256-GCM under a key derived from a user passphrase
 * (PBKDF2). Nothing readable leaves the device without the passphrase.
 *
 * File layout: ["TMBK1"][version:1][salt:16][iv:12][AES-GCM ciphertext of ZIP{db_key, taskmind_db}].
 */
@Singleton
class BackupManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val encryptedPrefs: SharedPreferences,
    private val database: TaskMindDatabase
) {
    sealed interface Result {
        data class Success(val message: String) : Result
        data class Failure(val message: String) : Result
    }

    /** Writes an encrypted backup of the DB to [uri] (from ACTION_CREATE_DOCUMENT). */
    fun backup(uri: Uri, passphrase: CharArray): Result {
        return try {
            // Flush the WAL into the main file so a plain file copy is a complete snapshot.
            runCatching {
                database.openHelper.writableDatabase.query("PRAGMA wal_checkpoint(TRUNCATE)").close()
            }

            val dbKey = encryptedPrefs.getString(DB_KEY_PREF, null)
                ?: return Result.Failure("No database key found — nothing to back up yet.")
            val dbFile = context.getDatabasePath(DB_NAME)
            if (!dbFile.exists()) return Result.Failure("Database file not found.")

            val bundle = ByteArrayOutputStream().use { bytes ->
                ZipOutputStream(bytes).use { zip ->
                    zip.putNextEntry(ZipEntry(ENTRY_KEY))
                    zip.write(dbKey.toByteArray(Charsets.UTF_8))
                    zip.closeEntry()
                    zip.putNextEntry(ZipEntry(ENTRY_DB))
                    dbFile.inputStream().use { it.copyTo(zip) }
                    zip.closeEntry()
                }
                bytes.toByteArray()
            }

            val envelope = BackupCrypto.seal(bundle, passphrase)

            context.contentResolver.openOutputStream(uri)?.use { out ->
                out.write(envelope)
            } ?: return Result.Failure("Couldn't open the backup file for writing.")

            Result.Success("✓ Encrypted backup saved. Keep the passphrase safe — it can't be recovered.")
        } catch (e: Exception) {
            Result.Failure("Backup failed: ${e.message ?: e::class.java.simpleName}")
        } finally {
            passphrase.fill('\u0000')
        }
    }

    /**
     * Restores the DB from [uri]. On success the on-disk file is atomically replaced and the matching
     * key stored; the caller must restart the process (see [scheduleRestartAndExit]) so Room reopens
     * the restored file. A wrong passphrase or bad file returns Failure and leaves the live DB
     * untouched. The live DB is intentionally NOT closed here — see the swap comment below.
     */
    fun restore(uri: Uri, passphrase: CharArray): Result {
        return try {
            val envelope = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                ?: return Result.Failure("Couldn't open the backup file.")

            val bundle = try {
                BackupCrypto.open(envelope, passphrase)
            } catch (e: BackupCrypto.BadBackupException) {
                return Result.Failure(e.message ?: "Couldn't read the backup file.")
            }

            var restoredKey: String? = null
            var dbBytes: ByteArray? = null
            ZipInputStream(ByteArrayInputStream(bundle)).use { zip ->
                var entry: ZipEntry? = zip.nextEntry
                while (entry != null) {
                    // Path-traversal guard: accept only our two known basenames, ignore any path.
                    when (entry.name.substringAfterLast('/')) {
                        ENTRY_KEY -> restoredKey = zip.readBytes().toString(Charsets.UTF_8)
                        ENTRY_DB -> dbBytes = zip.readBytes()
                    }
                    zip.closeEntry()
                    entry = zip.nextEntry
                }
            }
            val key = restoredKey
            val data = dbBytes
            if (key.isNullOrBlank() || data == null) {
                return Result.Failure("Backup is missing data — not restoring.")
            }

            val dbFile = context.getDatabasePath(DB_NAME)
            dbFile.parentFile?.mkdirs()

            // Stage the restored DB beside the live one and verify it actually opens with the bundled
            // key BEFORE touching anything. If staging or the check fails, the live DB and key are left
            // exactly as they were — a bad restore can't corrupt a working install.
            val staged = File(dbFile.path + ".restore")
            try {
                staged.outputStream().use { it.write(data) }
                if (!opensWithKey(staged.path, key)) {
                    return Result.Failure("Backup couldn't be opened with its key — not restoring.")
                }

                // Park the matching key in a pending slot BEFORE the swap. If the process dies in the
                // window between the swap and the primary-key commit below, the next launch finds the
                // {restored file, still-old primary key} pair and would fail to open — but DatabaseModule
                // then tries this pending key, and on success promotes it. That turns the old
                // "swap-then-commit" data-loss window into a fully recoverable restore.
                encryptedPrefs.edit().putString(DB_KEY_PENDING, key).commit()

                // Staged file is good. Swap it in atomically so the database on disk is only ever the
                // complete old file or the complete new one — never a half-written mix, even if the
                // process is killed mid-restore (the old in-place overwrite could leave a torn file).
                //
                // We deliberately do NOT close the live DB first. An atomic rename gives the new file a
                // fresh inode, so Room's still-open handle keeps serving the old (now-unlinked) inode
                // until the app restarts. That avoids reopening the restored file in-process with the
                // stale in-memory key (which would crash) and avoids leaving a closed, unusable handle
                // behind if a later step fails. The caller shows a mandatory "restart to load restored
                // data" prompt; the restart is what actually brings up the new file.
                try {
                    Files.move(
                        staged.toPath(), dbFile.toPath(),
                        StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE
                    )
                } catch (e: java.nio.file.AtomicMoveNotSupportedException) {
                    // Rare on internal storage; fall back to a plain replace.
                    Files.move(staged.toPath(), dbFile.toPath(), StandardCopyOption.REPLACE_EXISTING)
                }
                // Cosmetic cleanup of the old DB's WAL/SHM siblings — never let it fail the restore now
                // that the swap has already happened.
                runCatching {
                    File(dbFile.path + "-wal").delete()
                    File(dbFile.path + "-shm").delete()
                }
                // Promote the key to primary and clear the pending slot, durably (commit, not apply),
                // so the next launch opens the restored file with the right key straight away.
                encryptedPrefs.edit().putString(DB_KEY_PREF, key).remove(DB_KEY_PENDING).commit()
            } finally {
                staged.delete() // no-op once the move consumed it; cleans up on any failure path
            }

            Result.Success("✓ Restore complete.")
        } catch (e: Exception) {
            Result.Failure("Restore failed: ${e.message ?: e::class.java.simpleName}")
        } finally {
            passphrase.fill('\u0000')
        }
    }

    /**
     * True if the SQLCipher database at [path] opens and reads with [keyBase64] (the same base64 raw
     * key Room uses). Read-only and self-contained: opens its own connection, runs a trivial query,
     * and closes. Used to validate a staged restore before swapping it over the live DB.
     */
    private fun opensWithKey(path: String, keyBase64: String): Boolean = try {
        System.loadLibrary("sqlcipher") // no-op if already loaded by the DI graph
        val rawKey = android.util.Base64.decode(keyBase64, android.util.Base64.NO_WRAP)
        // Read-only open (no hook) so a bad key fails instead of creating/altering the staged file.
        val db = SQLiteDatabase.openDatabase(path, rawKey, null, SQLiteDatabase.OPEN_READONLY, null)
        try {
            db.rawQuery("SELECT count(*) FROM sqlite_master", null).use { it.moveToFirst() }
            true
        } finally {
            db.close()
        }
    } catch (t: Throwable) {
        false
    }

    /** Relaunches the app shortly after exiting — used after a restore so Room reopens cleanly. */
    fun scheduleRestartAndExit() {
        context.packageManager.getLaunchIntentForPackage(context.packageName)?.let { launch ->
            val pending = PendingIntent.getActivity(
                context, 0, launch,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_CANCEL_CURRENT
            )
            context.getSystemService(AlarmManager::class.java)
                ?.set(AlarmManager.RTC, System.currentTimeMillis() + 400, pending)
        }
        Runtime.getRuntime().exit(0)
    }

    companion object {
        private const val DB_NAME = "taskmind_db"
        private const val DB_KEY_PREF = "db_key"

        /**
         * Pref slot holding a restore's key during the swap→commit window, so an interrupted restore
         * is recoverable on next launch (see [restore] and `DatabaseModule.provideDatabase`). Public
         * because the DB open path reads it to promote a pending key.
         */
        const val DB_KEY_PENDING = "db_key_pending"
        private const val ENTRY_KEY = "db_key"
        private const val ENTRY_DB = "taskmind_db"
    }
}
