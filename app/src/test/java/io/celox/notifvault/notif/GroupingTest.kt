package io.celox.notifvault.notif

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Grouping is what decides which stored rows form "a chat". Mixing two chats or splitting a
 * group per sender was a real bug once — these tests pin the resolution order.
 */
class GroupingTest {

    // ---- stableKeyOf ----

    @Test
    fun `shortcut id wins over the tag`() {
        assertEquals("shortcut", stableKeyOf("shortcut", "tag"))
    }

    @Test
    fun `falls back to the tag when there is no shortcut id`() {
        assertEquals("4915112345678@s.whatsapp.net", stableKeyOf(null, "4915112345678@s.whatsapp.net"))
    }

    @Test
    fun `blank ids do not count as identifiers`() {
        assertEquals("tag", stableKeyOf("", "tag"))
        assertEquals("tag", stableKeyOf("   ", "tag"))
        assertNull(stableKeyOf(null, null))
        assertNull(stableKeyOf("", "  "))
    }

    // ---- senderNameOf ----

    @Test
    fun `person name is preferred over the notification user`() {
        assertEquals("Alice", senderNameOf("Alice", "Ich"))
    }

    @Test
    fun `own outgoing messages fall back to the notification user`() {
        assertEquals("Ich", senderNameOf(null, "Ich"))
    }

    @Test
    fun `missing and blank names become Unbekannt`() {
        assertEquals("Unbekannt", senderNameOf(null, null))
        assertEquals("Unbekannt", senderNameOf("", null))
        assertEquals("Unbekannt", senderNameOf("   ", "  "))
    }

    // ---- displayTitleOf ----

    @Test
    fun `group title wins, one-to-one chats show the contact`() {
        assertEquals("Familie 👨‍👩‍👧", displayTitleOf("Familie 👨‍👩‍👧", "Alice", "WhatsApp"))
        assertEquals("Alice", displayTitleOf(null, "Alice", "WhatsApp"))
    }

    @Test
    fun `titles are trimmed and empty ones fall through to the app label`() {
        assertEquals("Familie", displayTitleOf("  Familie  ", "Alice", "WhatsApp"))
        assertEquals("Alice", displayTitleOf("   ", "Alice", "WhatsApp"))
        assertEquals("WhatsApp", displayTitleOf(null, "", "WhatsApp"))
    }

    // ---- conversationKeyOf ----

    @Test
    fun `the stable id is the key when present, regardless of the title`() {
        assertEquals("jid-1", conversationKeyOf("jid-1", "Alice", "WhatsApp"))
        // Same chat, title changed between posts (contact renamed) — key must not change.
        assertEquals("jid-1", conversationKeyOf("jid-1", "Alice Müller", "WhatsApp"))
    }

    @Test
    fun `without a stable id the title groups the chat (legacy behavior)`() {
        assertEquals("Alice", conversationKeyOf(null, "Alice", "WhatsApp"))
    }

    @Test
    fun `the key is never blank`() {
        assertEquals("WhatsApp", conversationKeyOf(null, "", "WhatsApp"))
        assertEquals("WhatsApp", conversationKeyOf(null, "   ", "WhatsApp"))
    }

    @Test
    fun `two chats in the same app with distinct ids stay separate`() {
        val a = conversationKeyOf(stableKeyOf(null, "a@s.whatsapp.net"), "Support", "WhatsApp")
        val b = conversationKeyOf(stableKeyOf(null, "b@s.whatsapp.net"), "Support", "WhatsApp")
        assert(a != b) { "distinct chats collapsed into one key: $a" }
    }
}
