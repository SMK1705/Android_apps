package com.rajasudhan.taskmind.data.source

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/** Unit tests for the pure AES-GCM backup envelope. */
class BackupCryptoTest {

    private val plaintext = "the quick brown fox 🦊".toByteArray()

    @Test
    fun roundTripRecoversTheExactBytes() {
        val sealed = BackupCrypto.seal(plaintext, "correct horse".toCharArray())
        val opened = BackupCrypto.open(sealed, "correct horse".toCharArray())
        assertArrayEquals(plaintext, opened)
    }

    @Test
    fun wrongPassphraseIsRejected() {
        val sealed = BackupCrypto.seal(plaintext, "correct horse".toCharArray())
        assertThrows(BackupCrypto.BadBackupException::class.java) {
            BackupCrypto.open(sealed, "wrong horse".toCharArray())
        }
    }

    @Test
    fun nonBackupBytesAreRejected() {
        assertThrows(BackupCrypto.BadBackupException::class.java) {
            BackupCrypto.open("not a backup".toByteArray(), "whatever".toCharArray())
        }
    }

    @Test
    fun tamperedCiphertextIsRejected() {
        val sealed = BackupCrypto.seal(plaintext, "correct horse".toCharArray())
        sealed[sealed.size - 1] = (sealed[sealed.size - 1] + 1).toByte() // flip a tag byte
        assertThrows(BackupCrypto.BadBackupException::class.java) {
            BackupCrypto.open(sealed, "correct horse".toCharArray())
        }
    }

    @Test
    fun eachSealUsesFreshSaltAndIv() {
        // Same input + passphrase must not produce identical files (random salt/iv each time).
        val a = BackupCrypto.seal(plaintext, "pw".toCharArray())
        val b = BackupCrypto.seal(plaintext, "pw".toCharArray())
        assertTrue(!a.contentEquals(b))
    }
}
