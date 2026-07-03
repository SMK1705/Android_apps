package com.rajasudhan.taskmind.data.source

/**
 * Conservative name matching shared by the on-contact features (waiting-on auto-resolve,
 * person-context reminders): a counterparty matches a sender only when every name-word of the
 * counterparty appears as a whole word in the sender. Whole-word (not substring) matching keeps
 * "Dave" from matching a "David" notification, while "John" still matches a "John Doe" one.
 */
object PersonMatch {

    /** True when [counterparty] plausibly names [sender] — all of its name-tokens are whole words in it. */
    fun matches(sender: String, counterparty: String): Boolean {
        val s = tokens(sender)
        val cp = tokens(counterparty)
        return cp.isNotEmpty() && s.isNotEmpty() && cp.all { it in s }
    }

    /** The usable (>= [MIN_LEN]) lowercase name tokens of [name], with a leading "the " and punctuation stripped. */
    fun tokens(name: String): Set<String> =
        name.trim().lowercase().removePrefix("the ").trim()
            .split(WHITESPACE).map { it.trim(*PUNCT) }.filter { it.length >= MIN_LEN }.toSet()

    // Guards against trivially-short matches ("Al" matching "Alex", "PA" a payment alert).
    private const val MIN_LEN = 3
    private val WHITESPACE = Regex("\\s+")
    private val PUNCT = charArrayOf('.', ',', ':', ';', '!', '?', '(', ')', '"', '\'')
}
