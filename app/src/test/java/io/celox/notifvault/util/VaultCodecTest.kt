package io.celox.notifvault.util

import io.celox.notifvault.data.CapturedMessage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class VaultCodecTest {

    private fun msg(
        id: String = "id-1",
        text: String = "Hallo",
        sender: String = "Alice",
        deleted: Boolean = false,
        edited: Boolean = false
    ) = CapturedMessage(
        id = id,
        packageName = "com.whatsapp",
        appLabel = "WhatsApp",
        conversationKey = "key-1",
        conversation = "Alice",
        sender = sender,
        isGroup = false,
        text = text,
        messageTime = 1_700_000_000_000,
        capturedAt = 1_700_000_000_123,
        deletionSuspected = deleted,
        editSuperseded = edited
    )

    @Test
    fun `round-trips a plain message losslessly`() {
        val original = listOf(msg())
        assertEquals(original, VaultCodec.decode(VaultCodec.encode(original)))
    }

    @Test
    fun `round-trips control characters, separators and unicode in every text field`() {
        val nasty = msg(
            id = "id\\with\\backslashes",
            text = "tab\there\nnewline\rcr \\ backslash | pipe 👻 İstanbul",
            sender = "A\tB\nC"
        )
        val decoded = VaultCodec.decode(VaultCodec.encode(listOf(nasty)))
        assertEquals(listOf(nasty), decoded)
    }

    @Test
    fun `round-trips the verdict flags`() {
        val original = listOf(
            msg(id = "a", deleted = true),
            msg(id = "b", edited = true),
            msg(id = "c", deleted = true, edited = true)
        )
        assertEquals(original, VaultCodec.decode(VaultCodec.encode(original)))
    }

    @Test
    fun `round-trips an empty vault`() {
        assertEquals(emptyList<CapturedMessage>(), VaultCodec.decode(VaultCodec.encode(emptyList())))
    }

    @Test
    fun `round-trips many rows in order`() {
        val original = (1..250).map { msg(id = "id-$it", text = "Nachricht $it") }
        assertEquals(original, VaultCodec.decode(VaultCodec.encode(original)))
    }

    @Test
    fun `encode starts with the versioned header`() {
        assertTrue(VaultCodec.encode(emptyList()).startsWith("KPVAULT\t1\n"))
    }

    @Test
    fun `decode rejects payloads without the header`() {
        assertThrows(IllegalArgumentException::class.java) { VaultCodec.decode("not a backup") }
        assertThrows(IllegalArgumentException::class.java) { VaultCodec.decode("") }
    }

    @Test
    fun `decode rejects rows with the wrong column count`() {
        assertThrows(IllegalArgumentException::class.java) {
            VaultCodec.decode("KPVAULT\t1\nonly\tthree\tcolumns\n")
        }
    }

    @Test
    fun `decode rejects non-numeric timestamps instead of silently zeroing them`() {
        val row = VaultCodec.encode(listOf(msg())).lines()[1].split('\t').toMutableList()
        row[8] = "keine-zahl"
        assertThrows(IllegalArgumentException::class.java) {
            VaultCodec.decode("KPVAULT\t1\n" + row.joinToString("\t") + "\n")
        }
    }

    @Test
    fun `decode survives a payload without a trailing newline`() {
        val payload = VaultCodec.encode(listOf(msg())).trimEnd('\n')
        assertEquals(listOf(msg()), VaultCodec.decode(payload))
    }

    @Test
    fun `an unknown escape keeps the escaped character rather than failing`() {
        // Forward compatibility: a future writer's escape must not brick an older restore.
        val decoded = VaultCodec.decode(
            "KPVAULT\t1\nid\tpkg\tApp\tkey\tChat\tAlice\t0\tHallo\\q\t1\t2\t0\t0\n"
        )
        assertEquals("Halloq", decoded.single().text)
    }

    @Test
    fun `flag columns other than 1 read as false`() {
        val decoded = VaultCodec.decode(
            "KPVAULT\t1\nid\tpkg\tApp\tkey\tChat\tAlice\t0\tHallo\t1\t2\t0\tx\n"
        )
        assertTrue(!decoded.single().deletionSuspected && !decoded.single().editSuperseded)
    }
}
