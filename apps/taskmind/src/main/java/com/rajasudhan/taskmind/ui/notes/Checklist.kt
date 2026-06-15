package com.rajasudhan.taskmind.ui.notes

/**
 * Pure helpers for turning a list-like note (e.g. "Milk, eggs, bread") into tickable items and
 * persisting their checked state as a compact "[x] text" / "[ ] text" block. No Android deps, so
 * it's covered by a plain JVM unit test.
 */
object Checklist {
    data class Item(val text: String, val checked: Boolean)

    /** Items parsed from a list-like string (newline- or comma-separated). Empty if not list-like. */
    fun derive(text: String): List<Item> {
        val byLine = text.lines()
            .map { it.trim().trimStart('-', '•', '*', '·').trim() }
            .filter { it.isNotBlank() }
        val tokens = if (byLine.size >= 2) byLine
            else text.split(",").map { it.trim() }.filter { it.isNotBlank() }
        return if (tokens.size >= 2) tokens.map { Item(it, false) } else emptyList()
    }

    /** Serialises items, one "[x] text" / "[ ] text" per line. */
    fun encode(items: List<Item>): String =
        items.joinToString("\n") { (if (it.checked) "[x] " else "[ ] ") + it.text }

    /** Parses what [encode] produced back into items. */
    fun decode(stored: String): List<Item> =
        stored.lines().filter { it.isNotBlank() }.map { line ->
            val t = line.trim()
            val checked = t.startsWith("[x]", ignoreCase = true)
            val text = t.removePrefix("[x]").removePrefix("[X]").removePrefix("[ ]").trim()
            Item(text, checked)
        }

    /** Toggle item [index]'s checked state and return the new encoded block. */
    fun toggleEncoded(items: List<Item>, index: Int): String {
        val updated = items.toMutableList()
        updated[index] = updated[index].copy(checked = !updated[index].checked)
        return encode(updated)
    }
}
