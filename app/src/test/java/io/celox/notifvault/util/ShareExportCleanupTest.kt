package io.celox.notifvault.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * A shared chat export is the only plaintext copy of messages outside the encrypted database.
 * It must not outlive the next export / app start.
 */
class ShareExportCleanupTest {

    @get:Rule val tmp = TemporaryFolder()

    @Test
    fun `every leftover export is deleted, the folder stays`() {
        val dir = tmp.newFolder("exports")
        File(dir, "kleene-petze-Familie.csv").writeText("secret")
        File(dir, "kleene-petze-Alice.json").writeText("{}")
        val result = clearExportsIn(dir)
        assertEquals(dir, result)
        assertTrue(dir.isDirectory)
        assertEquals(0, dir.listFiles()!!.size)
    }

    @Test
    fun `a missing folder is not an error`() {
        val dir = File(tmp.root, "never-created")
        clearExportsIn(dir) // must not throw
    }

    @Test
    fun `the cleaned folder is the one the FileProvider shares`() {
        val res = File("src/main/res").takeIf { it.exists() } ?: File("app/src/main/res")
        val paths = File(res, "xml/file_paths.xml").readText()
        assertTrue(
            "FileProvider must share exactly the folder that gets cleaned ($EXPORT_DIR/)",
            paths.contains("path=\"$EXPORT_DIR/\"")
        )
    }
}
