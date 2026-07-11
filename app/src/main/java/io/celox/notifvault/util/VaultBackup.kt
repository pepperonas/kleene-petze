package io.celox.notifvault.util

import java.io.ByteArrayOutputStream
import java.security.SecureRandom
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Passphrase-encrypted vault backup container (.kpvault).
 *
 * Layout: magic "KPV1" | salt(16) | iv(12) | AES-256-GCM ciphertext of gzip(payload).
 * Key = PBKDF2WithHmacSHA256(passphrase, salt, 210k iterations). Pure `javax.crypto` /
 * standard JVM — unit-testable without Android, no new dependency, no network.
 *
 * A wrong passphrase or any tampering fails the GCM tag check → [decrypt] throws.
 */
object VaultBackup {

    const val FILE_EXTENSION = "kpvault"
    const val MIN_PASSPHRASE_LENGTH = 8

    private val MAGIC = "KPV1".toByteArray(Charsets.US_ASCII)
    private const val SALT_LEN = 16
    private const val IV_LEN = 12
    private const val PBKDF2_ITERATIONS = 210_000
    private const val KEY_BITS = 256
    private const val GCM_TAG_BITS = 128

    fun encrypt(payload: String, passphrase: CharArray): ByteArray {
        val rnd = SecureRandom()
        val salt = ByteArray(SALT_LEN).also { rnd.nextBytes(it) }
        val iv = ByteArray(IV_LEN).also { rnd.nextBytes(it) }

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, deriveKey(passphrase, salt), GCMParameterSpec(GCM_TAG_BITS, iv))
        val ciphertext = cipher.doFinal(gzip(payload.toByteArray(Charsets.UTF_8)))

        return ByteArrayOutputStream(MAGIC.size + SALT_LEN + IV_LEN + ciphertext.size).apply {
            write(MAGIC); write(salt); write(iv); write(ciphertext)
        }.toByteArray()
    }

    /** @throws IllegalArgumentException if the file is no .kpvault container.
     *  @throws javax.crypto.AEADBadTagException on a wrong passphrase or tampered data. */
    fun decrypt(bytes: ByteArray, passphrase: CharArray): String {
        require(bytes.size > MAGIC.size + SALT_LEN + IV_LEN &&
            bytes.copyOfRange(0, MAGIC.size).contentEquals(MAGIC)) {
            "Keine Kleene-Petze-Backup-Datei"
        }
        var off = MAGIC.size
        val salt = bytes.copyOfRange(off, off + SALT_LEN); off += SALT_LEN
        val iv = bytes.copyOfRange(off, off + IV_LEN); off += IV_LEN
        val ciphertext = bytes.copyOfRange(off, bytes.size)

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, deriveKey(passphrase, salt), GCMParameterSpec(GCM_TAG_BITS, iv))
        return gunzip(cipher.doFinal(ciphertext)).toString(Charsets.UTF_8)
    }

    private fun deriveKey(passphrase: CharArray, salt: ByteArray): SecretKeySpec {
        val spec = PBEKeySpec(passphrase, salt, PBKDF2_ITERATIONS, KEY_BITS)
        val key = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        spec.clearPassword()
        return SecretKeySpec(key, "AES")
    }

    private fun gzip(data: ByteArray): ByteArray {
        val out = ByteArrayOutputStream(data.size / 3 + 64)
        GZIPOutputStream(out).use { it.write(data) }
        return out.toByteArray()
    }

    private fun gunzip(data: ByteArray): ByteArray =
        GZIPInputStream(data.inputStream()).use { it.readBytes() }
}
