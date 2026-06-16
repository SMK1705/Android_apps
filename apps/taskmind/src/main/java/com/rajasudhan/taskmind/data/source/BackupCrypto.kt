package com.rajasudhan.taskmind.data.source

import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Pure AES-256-GCM envelope used by [BackupManager]. Kept free of Android dependencies so the
 * round-trip (and wrong-passphrase rejection) can be unit-tested on the JVM.
 *
 * Layout: ["TMBK1"][version:1][salt:16][iv:12][GCM ciphertext]. The key is derived from the user's
 * passphrase with PBKDF2-HMAC-SHA256, so the file is unreadable without it.
 */
object BackupCrypto {

    /** Thrown when an envelope isn't ours, is an unknown version, or the passphrase is wrong. */
    class BadBackupException(message: String) : Exception(message)

    /** Encrypts [plain] under [passphrase], returning the self-describing envelope bytes. */
    fun seal(plain: ByteArray, passphrase: CharArray): ByteArray {
        val salt = randomBytes(SALT_LEN)
        val iv = randomBytes(IV_LEN)
        val ciphertext = Cipher.getInstance(TRANSFORMATION).run {
            init(Cipher.ENCRYPT_MODE, deriveKey(passphrase, salt), GCMParameterSpec(TAG_BITS, iv))
            doFinal(plain)
        }
        return MAGIC + VERSION + salt + iv + ciphertext
    }

    /** Decrypts an [envelope] produced by [seal]; throws [BadBackupException] on any mismatch. */
    fun open(envelope: ByteArray, passphrase: CharArray): ByteArray {
        if (envelope.size < MAGIC.size + 1 + SALT_LEN + IV_LEN ||
            !envelope.copyOfRange(0, MAGIC.size).contentEquals(MAGIC)
        ) {
            throw BadBackupException("Not a TaskMind backup file.")
        }
        var offset = MAGIC.size
        val version = envelope[offset].toInt(); offset += 1
        if (version != 1) throw BadBackupException("Unsupported backup version.")
        val salt = envelope.copyOfRange(offset, offset + SALT_LEN); offset += SALT_LEN
        val iv = envelope.copyOfRange(offset, offset + IV_LEN); offset += IV_LEN
        val ciphertext = envelope.copyOfRange(offset, envelope.size)
        return try {
            Cipher.getInstance(TRANSFORMATION).run {
                init(Cipher.DECRYPT_MODE, deriveKey(passphrase, salt), GCMParameterSpec(TAG_BITS, iv))
                doFinal(ciphertext)
            }
        } catch (e: Exception) {
            // GCM tag mismatch (wrong key) or truncated ciphertext both land here.
            throw BadBackupException("Wrong passphrase, or the backup is corrupt.")
        }
    }

    private fun deriveKey(passphrase: CharArray, salt: ByteArray): SecretKeySpec {
        val spec = PBEKeySpec(passphrase, salt, PBKDF2_ITERATIONS, KEY_BITS)
        val keyBytes = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        return SecretKeySpec(keyBytes, "AES")
    }

    private fun randomBytes(n: Int) = ByteArray(n).also { SecureRandom().nextBytes(it) }

    private val MAGIC = "TMBK1".toByteArray(Charsets.US_ASCII)
    private val VERSION = byteArrayOf(1)
    private const val SALT_LEN = 16
    private const val IV_LEN = 12
    private const val TAG_BITS = 128
    private const val KEY_BITS = 256
    private const val PBKDF2_ITERATIONS = 150_000
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
}
