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
import net.sqlcipher.database.SQLiteDatabase
import net.sqlcipher.database.SupportFactory
import java.security.SecureRandom
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideEncryptedPrefs(@ApplicationContext context: Context): SharedPreferences {
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
        // Required by the legacy net.zetetic:android-database-sqlcipher library:
        // the native libs must be loaded before any encrypted DB is opened.
        SQLiteDatabase.loadLibs(context)
        val factory = SupportFactory(dbKey)
        return Room.databaseBuilder(
            context,
            TaskMindDatabase::class.java,
            "taskmind_db"
        )
        .openHelperFactory(factory)
        .addMigrations(TaskMindDatabase.MIGRATION_1_2, TaskMindDatabase.MIGRATION_2_3)
        .build()
    }

    @Provides
    fun provideDao(database: TaskMindDatabase): TaskMindDao {
        return database.taskMindDao()
    }
}
