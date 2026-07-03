package com.rajasudhan.taskmind.di

import android.content.Context
import android.content.SharedPreferences
import androidx.room.Room
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.rajasudhan.taskmind.data.local.TaskMindDao
import com.rajasudhan.taskmind.data.local.TaskMindDatabase
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
            // The Keystore master key can no longer decrypt these prefs (e.g. after a backup
            // restore or a keystore reset) — left unguarded this crashes the app at startup, since
            // the prefs are built here in the DI graph. Reset the unreadable store + its master key
            // so they can be regenerated. The DB encryption key lived here too, so the encrypted
            // database can't be opened either; drop it so a fresh one is created.
            runCatching {
                KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
                    .deleteEntry(MasterKey.DEFAULT_MASTER_KEY_ALIAS)
            }
            context.deleteSharedPreferences("secret_shared_prefs")
            context.deleteDatabase("taskmind_db")
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
    fun provideDatabase(@ApplicationContext context: Context, dbKey: ByteArray): TaskMindDatabase {
        // net.zetetic:sqlcipher-android ships 16 KB-page-aligned native libraries (the legacy
        // android-database-sqlcipher did not). Load the native lib before opening the encrypted DB.
        // The on-disk format is unchanged (both are SQLCipher 4), so existing databases reopen with
        // the same key — no data migration needed.
        System.loadLibrary("sqlcipher")

        fun build(): TaskMindDatabase =
            Room.databaseBuilder(context, TaskMindDatabase::class.java, "taskmind_db")
                .openHelperFactory(SupportOpenHelperFactory(dbKey))
                .addMigrations(
                    TaskMindDatabase.MIGRATION_1_2, TaskMindDatabase.MIGRATION_2_3,
                    TaskMindDatabase.MIGRATION_3_4, TaskMindDatabase.MIGRATION_4_5,
                    TaskMindDatabase.MIGRATION_5_6, TaskMindDatabase.MIGRATION_6_7,
                    TaskMindDatabase.MIGRATION_7_8, TaskMindDatabase.MIGRATION_8_9
                )
                .build()

        val db = build()
        return try {
            // Force the SQLCipher open now so a key/DB mismatch surfaces here, not as a crash on the
            // first query later. This covers the rare case where a backup restore was interrupted
            // between swapping the DB file and committing its key, leaving the stored key unable to
            // open the (already-restored) file.
            db.openHelper.readableDatabase
            db
        } catch (e: Exception) {
            // Unrecoverable: the stored key can't open the on-disk DB. Drop it so a fresh DB is created
            // rather than crash-looping on every launch. Mirrors the prefs self-heal above — the app
            // stays usable even if the encrypted data is lost.
            runCatching { db.close() }
            context.deleteDatabase("taskmind_db")
            build()
        }
    }

    @Provides
    fun provideDao(database: TaskMindDatabase): TaskMindDao {
        return database.taskMindDao()
    }
}
