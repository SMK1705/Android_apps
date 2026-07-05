package com.rajasudhan.taskmind.data.source

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import com.rajasudhan.taskmind.data.local.TaskMindDatabase
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.delay
import net.zetetic.database.sqlcipher.SQLiteDatabase
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Passphrase-encrypted backup & restore of the WHOLE TaskMind profile — the SQLCipher database, its
 * key, every setting/secret in EncryptedSharedPreferences, and the source-state DataStore (#121).
 *
 * The DB is SQLCipher-encrypted with a random per-install key kept in EncryptedSharedPreferences
 * (`db_key`). A backup bundles the encrypted DB file AND that key, plus the settings/DataStore, and
 * seals the whole bundle with AES-256-GCM under a key derived from a user passphrase (PBKDF2). Nothing
 * readable leaves the device without the passphrase.
 *
 * Secrets never travel as raw Keystore-bound ciphertext (it's device-locked and undecryptable
 * elsewhere): EncryptedSharedPreferences is snapshotted as decrypted typed JSON ([PrefsSnapshot]) and
 * re-sealed under the passphrase, and the DataStore is snapshotted/restored THROUGH its own API so the
 * live single-writer instance stays authoritative — never by copying its backing file, which a
 * concurrent writer would clobber before the post-restore restart.
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

    /** Writes an encrypted backup of the whole profile to [uri] (from ACTION_CREATE_DOCUMENT). */
    suspend fun backup(uri: Uri, passphrase: CharArray): Result {
        return try {
            // Fold the WAL into the main file so the single-file copy is a complete snapshot; a busy
            // reader can leave TRUNCATE incomplete, so retry rather than silently back up stale data.
            checkpointWal()

            val dbKey = encryptedPrefs.getString(DB_KEY_PREF, null)
                ?: return Result.Failure("No database key found — nothing to back up yet.")
            val dbFile = context.getDatabasePath(DB_NAME)
            if (!dbFile.exists()) return Result.Failure("Database file not found.")

            // Settings/secrets live in Keystore-bound EncryptedSharedPreferences (undecryptable off this
            // device), so snapshot the DECRYPTED values — minus the DB key, which has its own entry. The
            // DataStore (source toggles + processed-id watermarks) is read through its API so dynamic
            // per-account keys come along too; both snapshots ride inside the passphrase envelope.
            val prefsSnapshot = PrefsSnapshot.encode(encryptedPrefs.all)
            val dataStoreSnapshot = DataStoreSnapshot.encode(context.dataStore)

            val bundle = BackupBundle.encode(
                dbKey = dbKey.toByteArray(Charsets.UTF_8),
                dbFile = dbFile,
                prefs = prefsSnapshot,
                datastore = dataStoreSnapshot,
            )

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
     * Restores the whole profile from [uri]. The DB is atomically swapped and its key promoted (a bad
     * restore never corrupts the live install); the caller must then restart (see [scheduleRestartAndExit])
     * so Room reopens the restored file. Settings and DataStore are replaced afterwards, best-effort — a
     * failure there can't undo the committed DB restore, and is reported distinctly. The live DB is
     * intentionally NOT closed here — see the swap comment below.
     */
    suspend fun restore(uri: Uri, passphrase: CharArray): Result {
        return try {
            val envelope = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                ?: return Result.Failure("Couldn't open the backup file.")

            val bundle = try {
                BackupCrypto.open(envelope, passphrase)
            } catch (e: BackupCrypto.BadBackupException) {
                return Result.Failure(e.message ?: "Couldn't read the backup file.")
            }

            val parts = BackupBundle.decode(bundle)
            val key = parts.dbKey
            val data = parts.db
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

            // The DB is restored and its key promoted. Now replace settings + source state — only if the
            // backup carries them (older backups won't). Best-effort: a failure here must not undo the
            // committed DB restore, so each is guarded and its outcome tracked for an honest message.
            // Evaluate BOTH independently (no short-circuit) so a prefs failure can't skip the DataStore.
            val prefsOk = restoreSettings(parts.prefs)
            val dataStoreOk = restoreDataStore(parts.datastore)
            val settingsOk = prefsOk && dataStoreOk

            Result.Success(
                if (settingsOk) "✓ Restore complete."
                else "✓ Notes restored, but some settings couldn't be restored."
            )
        } catch (e: Exception) {
            Result.Failure("Restore failed: ${e.message ?: e::class.java.simpleName}")
        } finally {
            passphrase.fill('\u0000')
        }
    }

    /**
     * Replaces EncryptedSharedPreferences with the snapshot — a true REPLACE (clear first), so a foreign
     * account/API key already on the target device can't survive, matching the UI's "replaces all data"
     * promise. The just-promoted DB key is preserved across the clear (the snapshot excludes it). Returns
     * false (not throwing) if the snapshot is unreadable, so the restore reports a partial success.
     */
    private fun restoreSettings(prefsBytes: ByteArray?): Boolean {
        if (prefsBytes == null) return true // pre-#121 backup: nothing to restore, not a failure
        return runCatching {
            val dbKey = encryptedPrefs.getString(DB_KEY_PREF, null)
            val editor = encryptedPrefs.edit().clear()
            if (dbKey != null) editor.putString(DB_KEY_PREF, dbKey) // survive the clear
            PrefsSnapshot.apply(editor, PrefsSnapshot.decode(prefsBytes))
            editor.commit()
        }.isSuccess
    }

    /**
     * Replaces the source-state DataStore THROUGH its own API (clear + put), so the live single-writer
     * instance is the source of truth and a concurrent scan/notification write in the restore→restart
     * window can't clobber a raw-file swap. Takes effect immediately, no restart needed for this half.
     */
    private suspend fun restoreDataStore(datastoreBytes: ByteArray?): Boolean {
        if (datastoreBytes == null) return true
        return runCatching { DataStoreSnapshot.restore(context.dataStore, datastoreBytes) }.isSuccess
    }

    /**
     * Runs `wal_checkpoint(TRUNCATE)` and inspects its `busy` result, retrying briefly if a concurrent
     * reader keeps it busy — otherwise the WAL isn't fully folded into the main file and the single-file
     * backup would silently miss recent commits.
     */
    private suspend fun checkpointWal() {
        repeat(WAL_CHECKPOINT_TRIES) { attempt ->
            val busy = runCatching {
                database.openHelper.writableDatabase.query("PRAGMA wal_checkpoint(TRUNCATE)").use { c ->
                    if (c.moveToFirst()) c.getInt(0) else 0
                }
            }.getOrDefault(0)
            if (busy == 0) return
            if (attempt < WAL_CHECKPOINT_TRIES - 1) delay(WAL_CHECKPOINT_BACKOFF_MS)
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
        private const val WAL_CHECKPOINT_TRIES = 4
        private const val WAL_CHECKPOINT_BACKOFF_MS = 50L

        /**
         * Pref slot holding a restore's key during the swap→commit window, so an interrupted restore
         * is recoverable on next launch (see [restore] and `DatabaseModule.provideDatabase`). Public
         * because the DB open path reads it to promote a pending key.
         */
        const val DB_KEY_PENDING = "db_key_pending"
    }
}
