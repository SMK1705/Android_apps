package com.rajasudhan.taskmind.data.source

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

val Context.dataStore by preferencesDataStore(name = "source_settings")

@Singleton
class SourceManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        val KEY_NOTIFICATIONS_ENABLED = booleanPreferencesKey("notifications_enabled")
        val KEY_SMS_ENABLED = booleanPreferencesKey("sms_enabled")
        val KEY_CALL_LOG_ENABLED = booleanPreferencesKey("call_log_enabled")
        val KEY_AUDIO_ENABLED = booleanPreferencesKey("audio_enabled")
        val KEY_IMAGES_ENABLED = booleanPreferencesKey("images_enabled")
        val KEY_CALENDAR_ENABLED = booleanPreferencesKey("calendar_enabled")
        val KEY_APP_USAGE_ENABLED = booleanPreferencesKey("app_usage_enabled")
        val KEY_EMAIL_ENABLED = booleanPreferencesKey("email_enabled")
        
        val KEY_CALL_RECORDING_PATH = stringPreferencesKey("call_recording_path")
        val KEY_VOICE_RECORDING_PATH = stringPreferencesKey("voice_recording_path")

        // Set of package names whose notifications we process. Empty = monitor all apps.
        val KEY_NOTIFICATION_ALLOWLIST = stringSetPreferencesKey("notification_allowlist")

        const val DEFAULT_CALL_RECORDING_PATH = "/storage/emulated/0/Recordings/Call/"
        const val DEFAULT_VOICE_RECORDING_PATH = "/storage/emulated/0/Recordings/Voice Recorder/"
    }

    val isNotificationsEnabled: Flow<Boolean> = context.dataStore.data.map { it[KEY_NOTIFICATIONS_ENABLED] ?: false }
    val isSmsEnabled: Flow<Boolean> = context.dataStore.data.map { it[KEY_SMS_ENABLED] ?: false }
    val isCallLogEnabled: Flow<Boolean> = context.dataStore.data.map { it[KEY_CALL_LOG_ENABLED] ?: false }
    val isAudioEnabled: Flow<Boolean> = context.dataStore.data.map { it[KEY_AUDIO_ENABLED] ?: false }
    val isImagesEnabled: Flow<Boolean> = context.dataStore.data.map { it[KEY_IMAGES_ENABLED] ?: false }
    val isCalendarEnabled: Flow<Boolean> = context.dataStore.data.map { it[KEY_CALENDAR_ENABLED] ?: false }
    val isAppUsageEnabled: Flow<Boolean> = context.dataStore.data.map { it[KEY_APP_USAGE_ENABLED] ?: false }
    val isEmailEnabled: Flow<Boolean> = context.dataStore.data.map { it[KEY_EMAIL_ENABLED] ?: false }

    val callRecordingPath: Flow<String> = context.dataStore.data.map { it[KEY_CALL_RECORDING_PATH] ?: DEFAULT_CALL_RECORDING_PATH }
    val voiceRecordingPath: Flow<String> = context.dataStore.data.map { it[KEY_VOICE_RECORDING_PATH] ?: DEFAULT_VOICE_RECORDING_PATH }

    suspend fun setSourceEnabled(key: androidx.datastore.preferences.core.Preferences.Key<Boolean>, enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[key] = enabled
        }
    }
    
    suspend fun setRecordingPath(key: androidx.datastore.preferences.core.Preferences.Key<String>, path: String) {
        context.dataStore.edit { preferences ->
            preferences[key] = path
        }
    }

    /** Package names whose notifications are processed. Empty set = all apps are monitored. */
    val notificationAllowlist: Flow<Set<String>> =
        context.dataStore.data.map { it[KEY_NOTIFICATION_ALLOWLIST] ?: emptySet() }

    suspend fun setNotificationAppEnabled(packageName: String, enabled: Boolean) {
        context.dataStore.edit { preferences ->
            val current = preferences[KEY_NOTIFICATION_ALLOWLIST]?.toMutableSet() ?: mutableSetOf()
            if (enabled) current.add(packageName) else current.remove(packageName)
            preferences[KEY_NOTIFICATION_ALLOWLIST] = current
        }
    }
}
