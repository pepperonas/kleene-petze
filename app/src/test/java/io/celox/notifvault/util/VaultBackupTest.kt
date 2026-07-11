package io.celox.notifvault.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertFalse
import org.junit.Test
import javax.crypto.AEADBadTagException

class VaultBackupTest {

    private val passphrase = "korrekt-pferd-batterie".toCharArray()

    @Test
    fun `encrypt then decrypt returns the original payload`() {
        val payload = "KPVAULT\t1\nsome secret content with umlauts äöü and 👻\n"
        val bytes = VaultBackup.encrypt(payload, passphrase)
        assertEquals(payload, VaultBackup.decrypt(bytes, passphrase))
    }

    @Test
    fun `ciphertext does not contain the plaintext`() {
        val payload = "eindeutig-geheimer-inhalt-1234567890"
        val bytes = VaultBackup.encrypt(payload, passphrase)
        assertFalse(String(bytes, Charsets.ISO_8859_1).contains(payload))
    }

    @Test
    fun `two backups of the same payload differ (random salt and iv)`() {
        val payload = "gleicher inhalt"
        val a = VaultBackup.encrypt(payload, passphrase)
        val b = VaultBackup.encrypt(payload, passphrase)
        assertFalse(a.contentEquals(b))
        // …but both still decrypt.
        assertEquals(payload, VaultBackup.decrypt(a, passphrase))
        assertEquals(payload, VaultBackup.decrypt(b, passphrase))
    }

    @Test
    fun `wrong passphrase fails the GCM tag check`() {
        val bytes = VaultBackup.encrypt("inhalt", passphrase)
        assertThrows(AEADBadTagException::class.java) {
            VaultBackup.decrypt(bytes, "falsche-passphrase".toCharArray())
        }
    }

    @Test
    fun `tampered ciphertext fails the GCM tag check`() {
        val bytes = VaultBackup.encrypt("inhalt", passphrase)
        bytes[bytes.size - 1] = (bytes[bytes.size - 1].toInt() xor 0x01).toByte()
        assertThrows(AEADBadTagException::class.java) { VaultBackup.decrypt(bytes, passphrase) }
    }

    @Test
    fun `a file without the magic header is rejected up front`() {
        assertThrows(IllegalArgumentException::class.java) {
            VaultBackup.decrypt("definitiv-kein-kpvault-container".toByteArray(), passphrase)
        }
        assertThrows(IllegalArgumentException::class.java) {
            VaultBackup.decrypt(ByteArray(4), passphrase)
        }
    }

    @Test
    fun `large payloads survive the gzip round-trip`() {
        val payload = buildString { repeat(20_000) { append("Zeile $it mit etwas Text\n") } }
        val bytes = VaultBackup.encrypt(payload, passphrase)
        assertEquals(payload, VaultBackup.decrypt(bytes, passphrase))
    }
}
