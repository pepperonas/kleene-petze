package io.celox.notifvault

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The release workflow takes its notes from CHANGELOG.md (scripts/release-notes.sh) and the product
 * page mirrors it. A version without a section would publish an empty release page — so the build
 * fails first, here.
 */
class ChangelogTest {

    private val changelog = File("../CHANGELOG.md").takeIf { it.exists() } ?: File("CHANGELOG.md")
    private val text by lazy { changelog.readText() }

    @Test
    fun `the running version has a changelog section`() {
        val heading = "## [${BuildConfig.VERSION_NAME}] - "
        assertTrue("CHANGELOG.md has no section '$heading'", text.lines().any { it.startsWith(heading) })
    }

    @Test
    fun `the newest section is the running version`() {
        val first = Regex("""^## \[([^\]]+)]""", RegexOption.MULTILINE).find(text)?.groupValues?.get(1)
        assertEquals(BuildConfig.VERSION_NAME, first)
    }

    @Test
    fun `every section is dated`() {
        val headings = text.lines().filter { it.startsWith("## [") }
        assertTrue(headings.isNotEmpty())
        headings.forEach { assertTrue("undated: $it", Regex("""^## \[[0-9.]+] - \d{4}-\d{2}-\d{2}$""").matches(it)) }
    }
}
