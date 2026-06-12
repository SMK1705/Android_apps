package com.rajasudhan.taskmind.data.source

import android.content.SharedPreferences
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SettingsManager @Inject constructor(
    private val encryptedPrefs: SharedPreferences
) {
    companion object {
        private const val KEY_LLM_API_KEY = "llm_api_key"
        private const val KEY_STT_API_KEY = "stt_api_key"
        private const val KEY_USE_ON_DEVICE_LLM = "use_on_device_llm"
        private const val KEY_EVENT_DURATION_MINUTES = "event_duration_minutes"
        private const val KEY_CALENDAR_ID = "calendar_id"
        private const val KEY_ON_DEVICE_MODEL_PATH = "on_device_model_path"
        private const val KEY_GMAIL_ACCOUNT = "gmail_account"

        const val CALENDAR_ID_AUTO = -1L
        const val DEFAULT_EVENT_DURATION_MINUTES = 60
    }

    var llmApiKey: String
        get() = encryptedPrefs.getString(KEY_LLM_API_KEY, "") ?: ""
        set(value) = encryptedPrefs.edit().putString(KEY_LLM_API_KEY, value).apply()

    var sttApiKey: String
        get() = encryptedPrefs.getString(KEY_STT_API_KEY, "") ?: ""
        set(value) = encryptedPrefs.edit().putString(KEY_STT_API_KEY, value).apply()

    var useOnDeviceLlm: Boolean
        // On-device is the privacy-preserving default.
        get() = encryptedPrefs.getBoolean(KEY_USE_ON_DEVICE_LLM, true)
        set(value) = encryptedPrefs.edit().putBoolean(KEY_USE_ON_DEVICE_LLM, value).apply()

    /** Length (in minutes) of timed calendar events created on approve. */
    var eventDurationMinutes: Int
        get() = encryptedPrefs.getInt(KEY_EVENT_DURATION_MINUTES, DEFAULT_EVENT_DURATION_MINUTES)
        set(value) = encryptedPrefs.edit().putInt(KEY_EVENT_DURATION_MINUTES, value).apply()

    /** Target calendar id for new events, or [CALENDAR_ID_AUTO] to auto-pick the primary calendar. */
    var calendarId: Long
        get() = encryptedPrefs.getLong(KEY_CALENDAR_ID, CALENDAR_ID_AUTO)
        set(value) = encryptedPrefs.edit().putLong(KEY_CALENDAR_ID, value).apply()

    /** Custom path to the on-device LLM .task file; blank = use the app's default location. */
    var onDeviceModelPath: String
        get() = encryptedPrefs.getString(KEY_ON_DEVICE_MODEL_PATH, "") ?: ""
        set(value) = encryptedPrefs.edit().putString(KEY_ON_DEVICE_MODEL_PATH, value).apply()

    /** Connected Gmail account address; blank = not connected. (OAuth tokens are NOT stored here.) */
    var gmailAccount: String
        get() = encryptedPrefs.getString(KEY_GMAIL_ACCOUNT, "") ?: ""
        set(value) = encryptedPrefs.edit().putString(KEY_GMAIL_ACCOUNT, value).apply()

    /**
     * Clears all user-facing settings (API keys, provider choice, calendar prefs).
     * Deliberately leaves the DB encryption key intact so the (now-emptied) database stays readable.
     */
    fun clearSettings() {
        encryptedPrefs.edit()
            .remove(KEY_LLM_API_KEY)
            .remove(KEY_STT_API_KEY)
            .remove(KEY_USE_ON_DEVICE_LLM)
            .remove(KEY_EVENT_DURATION_MINUTES)
            .remove(KEY_CALENDAR_ID)
            .remove(KEY_ON_DEVICE_MODEL_PATH)
            .remove(KEY_GMAIL_ACCOUNT)
            .apply()
    }
}
