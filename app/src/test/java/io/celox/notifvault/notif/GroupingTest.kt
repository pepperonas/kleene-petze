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

    // ---- slotKeyOf ----

    @Test
    fun `the slot key is per package and notification id`() {
        assertEquals("slot:com.whatsapp:42", slotKeyOf("com.whatsapp", 42))
        assert(slotKeyOf("com.whatsapp", 42) != slotKeyOf("com.whatsapp", 43))
        assert(slotKeyOf("com.whatsapp", 42) != slotKeyOf("org.telegram.messenger", 42))
    }

    // ---- chatIdentityOf ----

    private fun group(
        shortcutId: String? = null,
        tag: String? = null,
        conversationTitle: String? = "Familie",
        notificationTitle: String? = null,
        sender: String = "Alice",
        slot: String = slotKeyOf("com.whatsapp", 7)
    ) = chatIdentityOf(
        shortcutId, tag, slot, isGroup = true,
        conversationTitle = conversationTitle, notificationTitle = notificationTitle,
        sender = sender, appLabel = "WhatsApp"
    )

    private fun direct(
        shortcutId: String? = null,
        tag: String? = null,
        conversationTitle: String? = null,
        sender: String = "Alice",
        slot: String = slotKeyOf("com.whatsapp", 7)
    ) = chatIdentityOf(
        shortcutId, tag, slot, isGroup = false,
        conversationTitle = conversationTitle, notificationTitle = null,
        sender = sender, appLabel = "WhatsApp"
    )

    @Test
    fun `the messenger's own ids win for groups and one-to-one alike`() {
        assertEquals("sc-1", group(shortcutId = "sc-1", tag = "t").key)
        assertEquals("1234@g.us", group(tag = "1234@g.us").key)
        assertEquals("sc-1", direct(shortcutId = "sc-1", tag = "t").key)
        assertEquals("49151@s.whatsapp.net", direct(tag = "49151@s.whatsapp.net").key)
    }

    // The actual bug: a group without shortcutId/tag *and* without a conversation title used to
    // fall back to the sender — splitting the group per sender and merging it into the 1:1 chats
    // with the same people, so the group never appeared as its own chat.
    @Test
    fun `a group without ids is never keyed or titled by the sender`() {
        val fromAlice = group(conversationTitle = null, sender = "Alice")
        val fromBob = group(conversationTitle = null, sender = "Bob")
        assertEquals(fromAlice.key, fromBob.key)
        assert(fromAlice.key != "Alice" && fromAlice.key != "Bob") { "keyed by sender: ${fromAlice.key}" }
        assert(fromAlice.title != "Alice") { "titled by sender: ${fromAlice.title}" }
    }

    @Test
    fun `a group without ids does not collide with the one-to-one chat of its members`() {
        val g = group(conversationTitle = null, sender = "Alice")
        val d = direct(sender = "Alice")
        assert(g.key != d.key) { "group merged into the 1:1 chat: ${g.key}" }
        assertEquals("Alice", d.key)
    }

    // The group name is present in one post and missing in the next, so it must not decide the
    // key — otherwise the very same chat would split in two.
    @Test
    fun `a group without ids keeps one key whether or not the group name arrives`() {
        assertEquals(
            group(conversationTitle = null).key,
            group(conversationTitle = "Familie").key
        )
        assertEquals(slotKeyOf("com.whatsapp", 7), group(conversationTitle = "Familie").key)
    }

    @Test
    fun `group titles prefer the conversation title, then the notification title`() {
        assertEquals("Familie", group(conversationTitle = "Familie", notificationTitle = "Egal").title)
        assertEquals("Nachbarn", group(conversationTitle = null, notificationTitle = "Nachbarn").title)
        assertEquals("WhatsApp", group(conversationTitle = null, notificationTitle = null).title)
        assertEquals("WhatsApp", group(conversationTitle = "  ", notificationTitle = "   ").title)
    }

    @Test
    fun `one-to-one chats keep the legacy sender fallback`() {
        val d = direct(sender = "Alice")
        assertEquals("Alice", d.key)
        assertEquals("Alice", d.title)
        // A named 1:1 conversation title still wins for display.
        assertEquals("Alice Müller", direct(conversationTitle = "Alice Müller", sender = "Alice").title)
    }

    @Test
    fun `chat keys are never blank`() {
        assert(group(conversationTitle = null, slot = "").key.isNotBlank())
        assert(direct(sender = "", slot = "").key.isNotBlank())
    }
}
