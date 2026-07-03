package com.rajasudhan.taskmind.di

import android.content.Context
import android.content.SharedPreferences
import androidx.room.Room
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.rajasudhan.taskmind.data.local.DatabaseRecovery
import com.rajasudhan.taskmind.data.local.TaskMindDao
import com.rajasudhan.taskmind.data.local.TaskMindDatabase
import com.rajasudhan.taskmind.data.source.BackupManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import java.security.KeyStore
import java.security.SecureRandom
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideEncryptedPrefs(@ApplicationContext context: Context): SharedPreferences {
        fun build(): SharedPreferences {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            return EncryptedSharedPreferences.create(
                context,
                "secret_shared_prefs",
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        }
        return try {
            build()
        } catch (e: Exception) {
            // The Keystore master key can no longer decrypt these prefs (e.g. after a keystore reset)
            // — left unguarded this crashes the app at startup, since the prefs are built here in the
            // DI graph. Reset the unreadable store + its master key so they can be regenerated. The DB
            // encryption key lived here too, so the encrypted database can no longer be opened either;
            // QUARANTINE it (rename aside, never silently delete) so its bytes survive for recovery,
            // and a fresh DB is created.
            runCatching {
                KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
                    .deleteEntry(MasterKey.DEFAULT_MASTER_KEY_ALIAS)
            }
            context.deleteSharedPreferences("secret_shared_prefs")
            DatabaseRecovery.quarantine(context.getDatabasePath("taskmind_db"), System.currentTimeMillis())
            build()
        }
    }

    @Provides
    @Singleton
    fun provideDatabaseKey(sharedPreferences: SharedPreferences): ByteArray {
        var dbKey = sharedPreferences.getString("db_key", null)
        if (dbKey == null) {
            val random = SecureRandom()
            val bytes = ByteArray(32)
            random.nextBytes(bytes)
            dbKey = android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
            sharedPreferences.edit().putString("db_key", dbKey).apply()
        }
        return android.util.Base64.decode(dbKey, android.util.Base64.NO_WRAP)
    }

    @Provides
    @Singleton
    fun provideDatabase(
        @ApplicationContext context: Context,
        sharedPreferences: SharedPreferences,
        dbKey: ByteArray,
    ): TaskMindDatabase {
        // net.zetetic:sqlcipher-android ships 16 KB-page-aligned native libraries (the legacy
        // android-database-sqlcipher did not). Load the native lib before opening the encrypted DB.
        // The on-disk format is unchanged (both are SQLCipher 4), so existing databases reopen with
        // the same key — no data migration needed.
        System.loadLibrary("sqlcipher")

        fun build(key: ByteArray): TaskMindDatabase =
            Room.databaseBuilder(context, TaskMindDatabase::class.java, "taskmind_db")
                .openHelperFactory(SupportOpenHelperFactory(key))
                .addMigrations(
                    TaskMindDatabase.MIGRATION_1_2, TaskMindDatabase.MIGRATION_2_3,
                    TaskMindDatabase.MIGRATION_3_4, TaskMindDatabase.MIGRATION_4_5,
                    TaskMindDatabase.MIGRATION_5_6, TaskMindDatabase.MIGRATION_6_7,
                    TaskMindDatabase.MIGRATION_7_8, TaskMindDatabase.MIGRATION_8_9,
                    TaskMindDatabase.MIGRATION_9_10, TaskMindDatabase.MIGRATION_10_11
                )
                .build()

        // Force the SQLCipher open now so a key/DB mismatch surfaces here, not as a crash on the first
        // query later. Returns the db if it opens, else null (leaving it closed).
        fun openOrNull(key: ByteArray): TaskMindDatabase? {
            val db = build(key)
            return if (runCatching { db.openHelper.readableDatabase }.isSuccess) db
            else { runCatching { db.close() }; null }
        }

        openOrNull(dbKey)?.let { db ->
            // Opened with the primary key: clear any stale pending-restore key so it can't later be
            // mistaken for this DB's key.
            if (sharedPreferences.contains(BackupManager.DB_KEY_PENDING)) {
                sharedPreferences.edit().remove(BackupManager.DB_KEY_PENDING).apply()
            }
            return db
        }

        // The stored key can't open the on-disk file. Before giving up, try a pending restore key: a
        // restore interrupted between swapping the DB file and committing its key leaves the RESTORED
        // file next to the OLD key, with the matching key parked in the pending slot. If it opens the
        // file, the restore is recovered with no data loss — promote it to the primary key.
        sharedPreferences.getString(BackupManager.DB_KEY_PENDING, null)?.let { pendingB64 ->
            runCatching { android.util.Base64.decode(pendingB64, android.util.Base64.NO_WRAP) }.getOrNull()
                ?.let { pendingKey ->
                    openOrNull(pendingKey)?.let { db ->
                        sharedPreferences.edit()
                            .putString("db_key", pendingB64)
                            .remove(BackupManager.DB_KEY_PENDING)
                            .commit()
                        return db
                    }
                }
        }

        // Genuinely unopenable with any known key. QUARANTINE the file (rename aside — never silently
        // delete, the failure that once cost a user every note) so its encrypted bytes survive for
        // possible recovery, then start fresh so the app stays usable.
        DatabaseRecovery.quarantine(context.getDatabasePath("taskmind_db"), System.currentTimeMillis())
        sharedPreferences.edit().remove(BackupManager.DB_KEY_PENDING).apply()
        return build(dbKey)
    }

    @Provides
    fun provideDao(database: TaskMindDatabase): TaskMindDao {
        return database.taskMindDao()
    }
}
