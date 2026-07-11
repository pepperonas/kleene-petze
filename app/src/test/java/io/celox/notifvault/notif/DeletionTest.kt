package io.celox.notifvault.notif

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DeletionTest {

    @Test
    fun `recognizes WhatsApp deleted placeholders in both languages`() {
        assertTrue(isDeletionPlaceholder("This message was deleted"))
        assertTrue(isDeletionPlaceholder("Diese Nachricht wurde gelöscht"))
        assertTrue(isDeletionPlaceholder("You deleted this message"))
        assertTrue(isDeletionPlaceholder("Du hast diese Nachricht gelöscht"))
    }

    @Test
    fun `recognizes official WhatsApp placeholders in further languages`() {
        assertTrue(isDeletionPlaceholder("Se eliminó este mensaje"))                 // ES
        assertTrue(isDeletionPlaceholder("Eliminaste este mensaje"))                 // ES (own)
        assertTrue(isDeletionPlaceholder("Ce message a été supprimé"))               // FR
        assertTrue(isDeletionPlaceholder("Vous avez supprimé ce message"))           // FR (own)
        assertTrue(isDeletionPlaceholder("Questo messaggio è stato eliminato"))      // IT
        assertTrue(isDeletionPlaceholder("Hai eliminato questo messaggio"))          // IT (own)
        assertTrue(isDeletionPlaceholder("Essa mensagem foi apagada"))               // PT-BR
        assertTrue(isDeletionPlaceholder("Esta mensagem foi eliminada"))             // PT-PT
        assertTrue(isDeletionPlaceholder("Dit bericht is verwijderd"))               // NL
        assertTrue(isDeletionPlaceholder("Bu mesaj silindi"))                        // TR
        assertTrue(isDeletionPlaceholder("Ta wiadomość została usunięta"))           // PL
        assertTrue(isDeletionPlaceholder("Данное сообщение удалено"))                // RU
    }

    @Test
    fun `is case-insensitive and tolerates an emoji or prefix`() {
        assertTrue(isDeletionPlaceholder("THIS MESSAGE WAS DELETED"))
        assertTrue(isDeletionPlaceholder("🚫 This message was deleted"))
    }

    @Test
    fun `does not flag ordinary messages`() {
        assertFalse(isDeletionPlaceholder("Hallo, wie geht's?"))
        assertFalse(isDeletionPlaceholder("Ich habe die Datei gelöscht."))
        assertFalse(isDeletionPlaceholder(""))
    }
}
