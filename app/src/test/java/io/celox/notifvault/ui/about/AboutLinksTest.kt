package io.celox.notifvault.ui.about

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.URI

class AboutLinksTest {

    @Test
    fun `the product page is the app's own domain`() {
        assertEquals("https://kleene-petze.celox.io", AboutLinks.PRODUCT_URL)
        assertEquals("https://github.com/pepperonas/kleene-petze", AboutLinks.REPO_URL)
    }

    @Test
    fun `the donate link is a PayPal donation to celox in euro`() {
        val uri = URI(AboutLinks.donateUrl())
        assertEquals("https", uri.scheme)
        assertEquals("www.paypal.com", uri.host)
        assertEquals("/donate/", uri.path)
        val q = uri.rawQuery.split('&').associate { it.substringBefore('=') to it.substringAfter('=') }
        assertEquals("martin.pfeffer@celox.io", q["business"])
        assertEquals("EUR", q["currency_code"])
        // A space must be %20 — PayPal shows a literal "+" otherwise.
        assertEquals("Kleene%20Petze", q["item_name"])
    }

    @Test
    fun `license and changelog point into the repository`() {
        assertTrue(AboutLinks.LICENSE_URL.startsWith(AboutLinks.REPO_URL))
        assertTrue(AboutLinks.CHANGELOG_URL.startsWith(AboutLinks.REPO_URL))
    }
}
