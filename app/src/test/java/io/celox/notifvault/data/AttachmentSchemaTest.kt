package io.celox.notifvault.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Holds the hand-written v4→v5 migration against the schema Room generated for v5.
 *
 * Without this, the only way to notice a mismatch is a device refusing to open the database —
 * and by then the vault is on the far side of an app that will not start. Room exports the
 * expected schema at build time; comparing against that file costs nothing and catches the
 * mistake at `./gradlew test`.
 */
class AttachmentSchemaTest {

    // Unit tests run with the module directory (app/) as the working directory.
    private val schemaFile =
        File("schemas/io.celox.notifvault.data.AppDatabase/5.json")

    private fun entityBlock(table: String): String {
        assertTrue(
            "Room's exported schema is missing — was exportSchema turned off? " +
                schemaFile.absolutePath,
            schemaFile.exists()
        )
        val json = schemaFile.readText()
        val marker = "\"tableName\": \"$table\""
        val start = json.indexOf(marker)
        assertTrue("no entity '$table' in ${schemaFile.name}", start >= 0)
        // The next entity starts at the following "tableName"; the block in between is ours.
        val next = json.indexOf("\"tableName\":", start + marker.length)
        return if (next < 0) json.substring(start) else json.substring(start, next)
    }

    /** Pulls a "createSql" value and puts the real table name back in. */
    private fun createSql(block: String, from: Int = 0): Pair<String, Int> {
        val key = "\"createSql\": \""
        val start = block.indexOf(key, from)
        assertTrue("no createSql found", start >= 0)
        val valueStart = start + key.length
        val end = block.indexOf('"', valueStart)
        val raw = block.substring(valueStart, end)
        return raw.replace("\${TABLE_NAME}", AttachmentSchema.TABLE) to end
    }

    @Test
    fun `migration creates exactly the table Room expects`() {
        val block = entityBlock(AttachmentSchema.TABLE)
        val (table, _) = createSql(block)
        assertEquals(table, AttachmentSchema.CREATE_TABLE)
    }

    @Test
    fun `migration creates exactly the index Room expects`() {
        val block = entityBlock(AttachmentSchema.TABLE)
        // The first createSql is the table, the second the index.
        val (_, afterTable) = createSql(block)
        val (index, _) = createSql(block, afterTable)
        assertEquals(index, AttachmentSchema.CREATE_INDEX)
        assertTrue(
            "index name must match the one Room generated",
            block.contains("\"name\": \"${AttachmentSchema.INDEX_NAME}\"")
        )
    }

    @Test
    fun `attachments cascade when their message is deleted`() {
        // Belt to the explicitly-paired deletes in MessageDao: if the foreign key is enforced,
        // removing a message must not leave its picture behind.
        assertTrue(AttachmentSchema.CREATE_TABLE.contains("ON DELETE CASCADE"))
        assertTrue(AttachmentSchema.CREATE_TABLE.contains("REFERENCES `messages`(`id`)"))
    }
}
