package io.celox.notifvault

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * English (values/) is the default, German (values-de/) the translation. A key present in only
 * one of them silently shows the other language on the device, and a placeholder that differs
 * (%1$s vs. %1$d, or a missing %2$s) crashes at runtime — both caught here instead.
 */
class LocalizationTest {

    private val res = File("src/main/res").takeIf { it.exists() } ?: File("app/src/main/res")

    /** name → text (for plurals: name#quantity → text), from every strings*.xml in a folder. */
    private fun load(folder: String): Map<String, String> {
        val out = linkedMapOf<String, String>()
        val files = File(res, folder).listFiles { f -> f.name.startsWith("strings") && f.name.endsWith(".xml") }
            .orEmpty()
        for (f in files) {
            val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(f)
            val strings = doc.getElementsByTagName("string")
            for (i in 0 until strings.length) {
                val e = strings.item(i) as org.w3c.dom.Element
                if (e.getAttribute("translatable") == "false") continue
                out[e.getAttribute("name")] = e.textContent
            }
            val plurals = doc.getElementsByTagName("plurals")
            for (i in 0 until plurals.length) {
                val p = plurals.item(i) as org.w3c.dom.Element
                val items = p.getElementsByTagName("item")
                // Only "other" is compared: German and English both use one/other, but the
                // placeholders must agree in the form every count can hit.
                for (j in 0 until items.length) {
                    val it = items.item(j) as org.w3c.dom.Element
                    if (it.getAttribute("quantity") == "other") out[p.getAttribute("name") + "#other"] = it.textContent
                }
            }
        }
        return out
    }

    private val placeholder = Regex("""%(\d+\$)?[sdf]""")

    @Test
    fun `English and German have exactly the same keys`() {
        val en = load("values")
        val de = load("values-de")
        assertTrue("no English strings found in $res", en.isNotEmpty())
        assertEquals("missing in German", emptySet<String>(), en.keys - de.keys)
        assertEquals("missing in English", emptySet<String>(), de.keys - en.keys)
    }

    @Test
    fun `placeholders agree between the languages`() {
        val en = load("values")
        val de = load("values-de")
        for ((key, text) in en) {
            val other = de[key] ?: continue
            assertEquals(
                "placeholders differ for $key",
                placeholder.findAll(text).map { it.value }.sorted().toList(),
                placeholder.findAll(other).map { it.value }.sorted().toList()
            )
        }
    }

    @Test
    fun `both languages are declared for the per-app language setting`() {
        val config = File(res, "xml/locales_config.xml").readText()
        assertTrue(config.contains("android:name=\"en\"") && config.contains("android:name=\"de\""))
    }

    @Test
    fun `the English default carries no German`() {
        // An untranslated German text in values/ would show on every non-German phone.
        val umlaut = Regex("[äöüÄÖÜß„]")
        val leaks = load("values").filterValues { umlaut.containsMatchIn(it) }.keys
        assertEquals(emptySet<String>(), leaks)
    }

    @Test
    fun `UI code has no hard-coded German left`() {
        // Every user-visible text is a resource now; a German literal in a screen is a regression.
        // Only the UI layer is scanned — the notification markers in notif/ are detection data.
        val src = File(res.parentFile, "java/io/celox/notifvault")
        val files = File(src, "ui").walkTopDown().filter { it.extension == "kt" } + File(src, "MainActivity.kt")
        val literal = Regex("\"([^\"\\\\]*[äöüÄÖÜß][^\"\\\\]*)\"")
        val hits = files.flatMap { f ->
            f.readLines().mapIndexedNotNull { n, line ->
                val code = line.substringBefore("//")
                literal.find(code)?.let { "${f.name}:${n + 1}: ${it.value}" }
            }
        }.toList()
        assertEquals(emptyList<String>(), hits)
    }
}
