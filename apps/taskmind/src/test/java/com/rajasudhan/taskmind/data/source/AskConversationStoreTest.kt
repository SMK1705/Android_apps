package com.rajasudhan.taskmind.data.source

import com.rajasudhan.taskmind.data.source.understanding.AskIntent
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

/** Persistence for the Ask thread (#317): round-trips through a real SharedPreferences, caps its size,
 *  and never throws on missing or corrupt data. */
@RunWith(RobolectricTestRunner::class)
class AskConversationStoreTest {

    private val prefs = RuntimeEnvironment.getApplication().getSharedPreferences("ask_test", 0)
    private val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
    private fun store() = AskConversationStore(prefs, moshi)

    private fun turn(text: String, user: Boolean = false, ids: List<Int> = emptyList()) =
        AskConversationStore.StoredTurn(fromUser = user, text = text, kind = "RESULTS", noteIds = ids)

    @Test
    fun save_thenLoad_roundTripsTurnsAndIntentHistory() {
        store().save(
            AskConversationStore.StoredConversation(
                turns = listOf(turn("what's overdue?", user = true), turn("Found 3.", ids = listOf(1, 2, 3))),
                intentHistory = listOf(AskIntent(action = "query", type = "todo", window = "overdue")),
            )
        )

        val loaded = store().load() // a fresh instance — proves it came off disk, not memory
        assertEquals(2, loaded.turns.size)
        assertEquals("Found 3.", loaded.turns[1].text)
        assertEquals(listOf(1, 2, 3), loaded.turns[1].noteIds)
        assertEquals("overdue", loaded.intentHistory.single().window)
    }

    @Test
    fun load_withNothingStored_returnsAnEmptyConversation() {
        assertTrue(store().load().turns.isEmpty())
    }

    @Test
    fun load_withCorruptJson_returnsEmpty_ratherThanThrowing() {
        prefs.edit().putString("ask_conversation", "{not valid json").apply()

        assertTrue(store().load().turns.isEmpty()) // a garbled blob must not crash Ask on launch
    }

    @Test
    fun clear_wipesTheStoredThread() {
        store().save(AskConversationStore.StoredConversation(turns = listOf(turn("hi", user = true))))
        store().clear()

        assertTrue(store().load().turns.isEmpty())
    }

    @Test
    fun save_capsTheThread_soTheBlobDoesNotGrowForever() {
        val many = (1..100).map { turn("turn $it", user = it % 2 == 0) }
        store().save(AskConversationStore.StoredConversation(turns = many))

        val loaded = store().load()
        assertEquals(AskConversationStore.MAX_TURNS, loaded.turns.size)
        assertEquals("turn 100", loaded.turns.last().text) // keeps the most recent
    }
}
