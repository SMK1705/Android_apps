package com.rajasudhan.taskmind.data.source

import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

/**
 * Serialises the DECRYPTED contents of EncryptedSharedPreferences (settings, LLM/STT API keys, Gmail
 * accounts, watermarks) to portable typed JSON, and writes them back on the target device (#121).
 *
 * Why not just copy the `secret_shared_prefs.xml`: EncryptedSharedPreferences is sealed with an Android
 * Keystore master key that is device-bound and non-exportable, so the raw ciphertext is undecryptable on
 * any other phone. We therefore snapshot the plaintext values (the whole snapshot then rides inside the
 * passphrase-encrypted backup envelope) and, on restore, re-store them so they get re-encrypted under the
 * NEW device's Keystore key — the same decrypt-and-re-seal pattern the DB key already uses.
 *
 * SharedPreferences is a heterogeneous `Map<String, *>` (String/Boolean/Int/Long/Float/Set<String>), so
 * each value carries a one-letter type tag to reconstruct the exact type on the way back in.
 */
object PrefsSnapshot {

    /**
     * Never carried in the snapshot: the SQLCipher DB key has its own dedicated backup entry and staged
     * pending-key restore path, and `db_key_pending` is transient restore state — snapshotting either
     * here would risk the prefs-merge clobbering the authoritative restored key.
     */
    private val EXCLUDED = setOf("db_key", "db_key_pending")

    /** Typed-JSON snapshot of [all] (typically `encryptedPrefs.all`), minus the excluded key slots. */
    fun encode(all: Map<String, *>): ByteArray {
        val root = JSONObject()
        for ((key, value) in all) {
            if (key in EXCLUDED) continue
            val entry = when (value) {
                is String -> JSONObject().put("t", "s").put("v", value)
                is Boolean -> JSONObject().put("t", "b").put("v", value)
                is Int -> JSONObject().put("t", "i").put("v", value)
                is Long -> JSONObject().put("t", "l").put("v", value)
                is Float -> JSONObject().put("t", "f").put("v", value.toDouble())
                is Set<*> -> JSONObject().put("t", "ss").put("v", JSONArray(value.map { it.toString() }))
                else -> continue // unknown pref type — skip rather than guess a serialization
            }
            root.put(key, entry)
        }
        return root.toString().toByteArray(Charsets.UTF_8)
    }

    /** Parses [bytes] from [encode] back into a typed key→value map. Unknown tags are skipped. */
    fun decode(bytes: ByteArray): Map<String, Any?> {
        val root = JSONObject(String(bytes, Charsets.UTF_8))
        val out = LinkedHashMap<String, Any?>()
        for (key in root.keys()) {
            if (key in EXCLUDED) continue // defensive — a hand-edited backup can't smuggle a DB key in
            val entry = root.getJSONObject(key)
            val value: Any? = when (entry.getString("t")) {
                "s" -> entry.getString("v")
                "b" -> entry.getBoolean("v")
                "i" -> entry.getInt("v")
                "l" -> entry.getLong("v")
                "f" -> entry.getDouble("v").toFloat()
                "ss" -> entry.getJSONArray("v").let { arr ->
                    (0 until arr.length()).map { arr.getString(it) }.toSet()
                }
                else -> continue
            }
            out[key] = value
        }
        return out
    }

    /** Writes a decoded [values] map into [editor] with the correct typed put per value. */
    fun apply(editor: SharedPreferences.Editor, values: Map<String, Any?>) {
        for ((key, value) in values) {
            when (value) {
                is String -> editor.putString(key, value)
                is Boolean -> editor.putBoolean(key, value)
                is Int -> editor.putInt(key, value)
                is Long -> editor.putLong(key, value)
                is Float -> editor.putFloat(key, value)
                is Set<*> -> editor.putStringSet(key, value.map { it.toString() }.toSet())
            }
        }
    }
}
