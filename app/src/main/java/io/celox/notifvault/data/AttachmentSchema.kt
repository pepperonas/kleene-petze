package io.celox.notifvault.data

/**
 * The DDL of the v5 `attachments` table, as plain strings.
 *
 * Room compares a hash of the real schema against the one it generated whenever the database is
 * opened, and refuses to open on a mismatch — on an encrypted vault that means the app stops
 * starting, with the data still in there. Hand-written migration SQL therefore has to match
 * `app/schemas/…/5.json` character for character.
 *
 * Keeping it here rather than inline in [DatabaseProvider] is what lets `AttachmentSchemaTest`
 * compare these strings against that generated JSON on every test run, instead of the comparison
 * being something a person remembers to do.
 */
object AttachmentSchema {

    const val TABLE = "attachments"

    const val INDEX_NAME = "index_attachments_conversationKey_packageName"

    const val CREATE_TABLE =
        "CREATE TABLE IF NOT EXISTS `attachments` (`messageId` TEXT NOT NULL, " +
            "`packageName` TEXT NOT NULL, `conversationKey` TEXT NOT NULL, " +
            "`mimeType` TEXT NOT NULL, `width` INTEGER NOT NULL, `height` INTEGER NOT NULL, " +
            "`bytes` BLOB NOT NULL, `capturedAt` INTEGER NOT NULL, PRIMARY KEY(`messageId`), " +
            "FOREIGN KEY(`messageId`) REFERENCES `messages`(`id`) " +
            "ON UPDATE NO ACTION ON DELETE CASCADE )"

    const val CREATE_INDEX =
        "CREATE INDEX IF NOT EXISTS `index_attachments_conversationKey_packageName` " +
            "ON `attachments` (`conversationKey`, `packageName`)"
}
