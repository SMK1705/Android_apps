package com.rajasudhan.taskmind.data.source

import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
        private const val KEY_GMAIL_ACCOUNT = "gmail_account" // legacy single account (migrated below)
        private const val KEY_GMAIL_ACCOUNTS = "gmail_accounts"
        private const val KEY_RETENTION_DAYS = "retention_days"
        private const val KEY_SCAN_FREQUENCY_MINUTES = "scan_frequency_minutes"
        private const val KEY_LAST_SCAN_AT = "last_scan_at"
        private const val KEY_DYNAMIC_COLOR = "dynamic_color"

        const val CALENDAR_ID_AUTO = -1L
        const val DEFAULT_EVENT_DURATION_MINUTES = 60
        const val DEFAULT_SCAN_FREQUENCY_MINUTES = 30
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

    /**
     * All connected Gmail account addresses. Reads the multi-account set, seeding it once from the
     * legacy single-account key so existing users keep their connection. (OAuth tokens are NOT stored.)
     */
    val gmailAccounts: Set<String>
        get() {
            encryptedPrefs.getStringSet(KEY_GMAIL_ACCOUNTS, null)?.let { return it.toSet() }
            val legacy = encryptedPrefs.getString(KEY_GMAIL_ACCOUNT, "") ?: ""
            return if (legacy.isBlank()) emptySet() else setOf(legacy)
        }

    fun addGmailAccount(email: String) {
        if (email.isBlank()) return
        val updated = gmailAccounts + email
        encryptedPrefs.edit().putStringSet(KEY_GMAIL_ACCOUNTS, updated).apply()
    }

    fun removeGmailAccount(email: String) {
        val updated = gmailAccounts - email
        encryptedPrefs.edit()
            .putStringSet(KEY_GMAIL_ACCOUNTS, updated)
            // Clear the legacy key too, so a removed account isn't resurrected by the migration above.
            .remove(KEY_GMAIL_ACCOUNT)
            .apply()
    }

    /** Auto-delete notes older than this many days; 0 = keep forever (default). */
    var retentionDays: Int
        get() = encryptedPrefs.getInt(KEY_RETENTION_DAYS, 0)
        set(value) = encryptedPrefs.edit().putInt(KEY_RETENTION_DAYS, value).apply()

    /** How often the background scan runs, in minutes (WorkManager floor is 15). */
    var scanFrequencyMinutes: Int
        get() = encryptedPrefs.getInt(KEY_SCAN_FREQUENCY_MINUTES, DEFAULT_SCAN_FREQUENCY_MINUTES)
        set(value) = encryptedPrefs.edit().putInt(KEY_SCAN_FREQUENCY_MINUTES, value).apply()

    /**
     * Watermark (epoch ms) of the last successful recent-data scan, so each scan only covers what
     * arrived since — nothing in the gap between refreshes is missed. 0 = never scanned.
     */
    var lastScanAt: Long
        get() = encryptedPrefs.getLong(KEY_LAST_SCAN_AT, 0L)
        set(value) = encryptedPrefs.edit().putLong(KEY_LAST_SCAN_AT, value).apply()

    // ---- Material You (dynamic color) ----
    // Off by default to keep the brand violet identity; exposed as a StateFlow so the theme in
    // MainActivity re-themes live the moment the Settings toggle flips.
    private val _dynamicColor = MutableStateFlow(encryptedPrefs.getBoolean(KEY_DYNAMIC_COLOR, false))
    val dynamicColorFlow: StateFlow<Boolean> = _dynamicColor.asStateFlow()

    var dynamicColor: Boolean
        get() = _dynamicColor.value
        set(value) {
            encryptedPrefs.edit().putBoolean(KEY_DYNAMIC_COLOR, value).apply()
            _dynamicColor.value = value
        }

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
            .remove(KEY_GMAIL_ACCOUNTS)
            .remove(KEY_RETENTION_DAYS)
            .remove(KEY_SCAN_FREQUENCY_MINUTES)
            .remove(KEY_DYNAMIC_COLOR)
            .apply()
        _dynamicColor.value = false
    }
}
