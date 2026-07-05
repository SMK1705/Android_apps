package com.rajasudhan.taskmind.data.source

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * The prefs snapshot codec (#121). Robolectric because it exercises org.json + the SharedPreferences
 * API; a plain (unencrypted) SharedPreferences stands in for EncryptedSharedPreferences (which needs a
 * real device Keystore — that path is an instrumentation-test concern, not a JVM unit test).
 */
@RunWith(RobolectricTestRunner::class)
class PrefsSnapshotTest {

    @Test
    fun encodeDecode_roundTrips_everyPreferenceType() {
        val original = mapOf(
            "llm_api_key" to "sk-abc",
            "use_on_device_llm" to true,
            "scan_frequency_minutes" to 30,
            "last_processed_sms_id" to 42L,
            "some_float" to 1.5f,
            "gmail_accounts" to setOf("a@x.com", "b@y.com"),
        )

        val decoded = PrefsSnapshot.decode(PrefsSnapshot.encode(original))

        assertEquals("sk-abc", decoded["llm_api_key"])
        assertEquals(true, decoded["use_on_device_llm"])
        assertEquals(30, decoded["scan_frequency_minutes"])
        assertEquals(42L, decoded["last_processed_sms_id"])
        assertEquals(1.5f, decoded["some_float"])
        assertEquals(setOf("a@x.com", "b@y.com"), decoded["gmail_accounts"])
    }

    @Test
    fun encode_excludesTheDatabaseKeySlots_butKeepsOrdinarySettings() {
        val decoded = PrefsSnapshot.decode(
            PrefsSnapshot.encode(
                mapOf("db_key" to "SECRET", "db_key_pending" to "PENDING", "theme_mode" to "DARK")
            )
        )
        assertNull(decoded["db_key"])         // the SQLCipher key never rides in the prefs snapshot…
        assertNull(decoded["db_key_pending"])
        assertEquals("DARK", decoded["theme_mode"]) // …but the rest of the settings do
    }

    @Test
    fun apply_writesDecodedValuesIntoPreferences_withCorrectTypes() {
        val prefs = ApplicationProvider.getApplicationContext<Context>()
            .getSharedPreferences("test_prefs", Context.MODE_PRIVATE)
        val values = PrefsSnapshot.decode(
            PrefsSnapshot.encode(
                mapOf("k_str" to "hi", "k_bool" to true, "k_int" to 7, "k_long" to 99L, "k_set" to setOf("x"))
            )
        )

        prefs.edit().also { PrefsSnapshot.apply(it, values) }.commit()

        assertEquals("hi", prefs.getString("k_str", null))
        assertTrue(prefs.getBoolean("k_bool", false))
        assertEquals(7, prefs.getInt("k_int", 0))
        assertEquals(99L, prefs.getLong("k_long", 0))
        assertEquals(setOf("x"), prefs.getStringSet("k_set", null))
    }
}
