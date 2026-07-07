package com.rajasudhan.taskmind.ui.capture

import org.junit.Assert.assertEquals
import org.junit.Test

/** The pure count-label logic behind the Inbox launcher shortcut (#122). */
class CaptureShortcutsTest {

    @Test
    fun inboxLabel_isPlain_whenNothingPending() {
        assertEquals("Inbox", CaptureShortcuts.inboxLabel(0))
    }

    @Test
    fun inboxLabel_foldsInTheCount_whenItemsPending() {
        assertEquals("Inbox · 1", CaptureShortcuts.inboxLabel(1))
        assertEquals("Inbox · 12", CaptureShortcuts.inboxLabel(12))
    }

    @Test
    fun inboxLabel_treatsANegativeCountAsNone() {
        // Defensive: a bad count must never render "Inbox · -1".
        assertEquals("Inbox", CaptureShortcuts.inboxLabel(-1))
    }
}
