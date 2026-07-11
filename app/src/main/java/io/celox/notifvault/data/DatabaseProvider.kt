package io.celox.notifvault.data

import android.content.Context
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import androidx.sqlite.db.SupportSQLiteDatabase
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import java.security.SecureRandom

/**
 * Builds a single, AES-256 (SQLCipher) encrypted Room database.
 * The random 32-byte passphrase is generated once and stored inside
 * EncryptedSharedPreferences, which itself wraps values with AES-256-GCM
 * backed by the Android Keystore. The DB file on disk is unreadable without it.
 */
object DatabaseProvider {

    @Volatile private var instance: AppDatabase? = null

    fun get(context: Context): AppDatabase =
        instance ?: synchronized(this) {
            instance ?: build(context.applicationContext).also { instance = it }
        }

    private fun build(context: Context): AppDatabase {
        // Load native SQLCipher libs.
        System.loadLibrary("sqlcipher")

        val passphrase = passphrase(context)
        val factory = SupportOpenHelperFactory(passphrase)

        return Room.databaseBuilder(context, AppDatabase::class.java, "vault.db")
            .openHelperFactory(factory)
            .addMigrations(MIGRATION_2_3, MIGRATION_3_4)
            // Destructive ONLY from v1 (intentional clean slate: old rows were grouped by the
            // unreliable title). Every later schema bump needs a real migration — a blanket
            // fallbackToDestructiveMigration() would silently wipe the whole vault.
            .fallbackToDestructiveMigrationFrom(1)
            .build()
    }

    // v3: replace the single-column conversationKey index (and the conversation index, unused
    // since grouping moved to conversationKey) with the composite index the overview and
    // per-chat queries actually need. Names/DDL must match app/schemas/…/3.json exactly.
    private val MIGRATION_2_3 = object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("DROP INDEX IF EXISTS `index_messages_conversationKey`")
            db.execSQL("DROP INDEX IF EXISTS `index_messages_conversation`")
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_messages_conversationKey_packageName_messageTime` " +
                    "ON `messages` (`conversationKey`, `packageName`, `messageTime`)"
            )
        }
    }

    // v4: editSuperseded marks earlier versions of edited messages. DDL matches
    // app/schemas/…/4.json (NOT NULL + DEFAULT 0, no new index).
    private val MIGRATION_3_4 = object : Migration(3, 4) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE `messages` ADD COLUMN `editSuperseded` INTEGER NOT NULL DEFAULT 0")
        }
    }

    private fun passphrase(context: Context): ByteArray {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

        val prefs = EncryptedSharedPreferences.create(
            context,
            "nv_secure_prefs",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )

        val existing = prefs.getString(KEY_DB_PASS, null)
        if (existing != null) return existing.toByteArray(Charsets.ISO_8859_1)

        val raw = ByteArray(32).also { SecureRandom().nextBytes(it) }
        val asString = String(raw, Charsets.ISO_8859_1)
        prefs.edit().putString(KEY_DB_PASS, asString).apply()
        return raw
    }

    private const val KEY_DB_PASS = "db_passphrase"
}
