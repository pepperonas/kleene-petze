package io.celox.notifvault.data

import androidx.room.Database
import androidx.room.RoomDatabase

// v2 introduced the stable CapturedMessage.conversationKey used for grouping. There is no
// 1→2 migration on purpose: the old rows were grouped by the unreliable title, so we took a
// clean slate (destructive fallback from v1 only) and let every chat re-populate with a
// proper stable key. v3 reworks the indexes (composite conversationKey+packageName+messageTime)
// via a real, data-preserving MIGRATION_2_3 in DatabaseProvider. v4 adds the editSuperseded
// flag (MIGRATION_3_4). Schema JSON is exported to app/schemas as the reference for
// hand-written migrations.
@Database(entities = [CapturedMessage::class], version = 4, exportSchema = true)
abstract class AppDatabase : RoomDatabase() {
    abstract fun messageDao(): MessageDao
}
