package com.rajasudhan.taskmind.data.source

import android.content.SharedPreferences
import com.rajasudhan.taskmind.data.source.understanding.AskIntent
import com.squareup.moshi.Moshi
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Persists the Ask conversation (#317) so it survives process death and a return to the tab. The thread
 * is memory-only otherwise — reopen the app and everything you asked is gone.
 *
 * Stored in the app's EncryptedSharedPreferences (AES-256, same store as the API key), so it's encrypted
 * at rest like the rest of the user's data and never leaves the device — persistence adds no egress. Cards
 * are stored by note id, NOT by copying the note content: on restore the notes are re-read from Room, so a
 * card can't drift from (or resurrect) a note the user has since edited or deleted.
 */
@Singleton
class AskConversationStore @Inject constructor(
    private val encryptedPrefs: SharedPreferences,
    moshi: Moshi,
) {
    /** One persisted turn: the user's line, or an assistant answer with the ids of the cards it showed. */
    data class StoredTurn(
        val fromUser: Boolean,
        val text: String,
        val kind: String? = null,            // AskResultKind name, for an assistant turn
        val answeredFromNotes: Boolean = false,
        val noteIds: List<Int> = emptyList(), // rehydrated from Room on restore, never the note content
    )

    data class StoredConversation(
        val turns: List<StoredTurn> = emptyList(),
        val intentHistory: List<AskIntent> = emptyList(), // #318 fold window, so multi-turn survives a restart
    )

    private val adapter = moshi.adapter(StoredConversation::class.java)

    fun load(): StoredConversation =
        encryptedPrefs.getString(KEY, null)
            ?.let { runCatching { adapter.fromJson(it) }.getOrNull() }
            ?: StoredConversation()

    fun save(conversation: StoredConversation) {
        // Cap what we keep: a thread is a convenience, not an archive, and the blob shouldn't grow forever.
        val trimmed = conversation.copy(turns = conversation.turns.takeLast(MAX_TURNS))
        encryptedPrefs.edit().putString(KEY, adapter.toJson(trimmed)).apply()
    }

    /** Wipe it — the "clear conversation" affordance, and part of "Delete all private data". */
    fun clear() {
        encryptedPrefs.edit().remove(KEY).apply()
    }

    companion object {
        private const val KEY = "ask_conversation"
        const val MAX_TURNS = 60
    }
}
