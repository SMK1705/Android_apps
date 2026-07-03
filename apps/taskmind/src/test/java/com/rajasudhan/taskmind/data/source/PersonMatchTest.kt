package com.rajasudhan.taskmind.data.source

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The conservative whole-word name matcher shared by the on-contact features. */
class PersonMatchTest {

    @Test
    fun matchesAFirstNameInsideAFullSenderName() {
        assertTrue(PersonMatch.matches("John Doe", "John"))
        assertTrue(PersonMatch.matches("Sarah Connor", "Sarah"))
    }

    @Test
    fun matchesRegardlessOfCaseAndAThePrefix() {
        assertTrue(PersonMatch.matches("Landlord", "the landlord"))
        assertTrue(PersonMatch.matches("PRIYA", "priya"))
    }

    @Test
    fun requiresAllCounterpartyWords() {
        assertTrue(PersonMatch.matches("John Doe", "John Doe"))
        assertFalse(PersonMatch.matches("John", "John Doe")) // "Doe" missing from the sender
    }

    @Test
    fun doesNotMatchOnALooseSubstring() {
        assertFalse(PersonMatch.matches("David", "Dave"))   // whole-word, not substring
        assertFalse(PersonMatch.matches("Johnson", "John"))
    }

    @Test
    fun rejectsUnrelatedNamesAndTooShortTokens() {
        assertFalse(PersonMatch.matches("Amazon", "Priya"))
        assertFalse(PersonMatch.matches("Bo", "Bo"))        // 2-char token guarded out
        assertFalse(PersonMatch.matches("", "Sarah"))
    }
}
