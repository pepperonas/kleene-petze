package io.celox.notifvault.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReleaseSourceTest {

    @Test
    fun `the product page is asked first`() {
        val asked = mutableListOf<String>()
        val source = ReleaseSource { url ->
            asked += url
            """{"version":"v1.10.0","url":"https://kleene-petze.celox.io/files/x.apk"}"""
        }
        assertEquals("v1.10.0", source.latestVersion())
        assertEquals(listOf(ReleaseSource.SITE_URL), asked)
    }

    @Test
    fun `github is the fallback when the page fails or answers garbage`() {
        for (siteAnswer in listOf(null, "<html>502</html>", """{"version":""}""")) {
            val source = ReleaseSource { url ->
                if (url == ReleaseSource.SITE_URL) siteAnswer else """{"tag_name":"v1.10.1"}"""
            }
            assertEquals("for site answer $siteAnswer", "v1.10.1", source.latestVersion())
        }
    }

    @Test
    fun `no usable answer anywhere yields null`() {
        assertNull(ReleaseSource { null }.latestVersion())
        assertNull(ReleaseSource { """{"message":"API rate limit exceeded"}""" }.latestVersion())
    }

    @Test
    fun `all endpoints belong to this app`() {
        assertEquals("https://kleene-petze.celox.io/latest.json", ReleaseSource.SITE_URL)
        assertEquals(
            "https://api.github.com/repos/pepperonas/kleene-petze/releases/latest",
            ReleaseSource.GITHUB_URL
        )
        assertEquals("https://kleene-petze.celox.io/download", ReleaseSource.DOWNLOAD_URL)
    }
}
