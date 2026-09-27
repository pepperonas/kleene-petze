package io.celox.notifvault

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The privacy promise lives in the manifest. Pinned here so a new permission, a re-enabled backup
 * or a restored WorkManager auto-initializer cannot slip in with an unrelated change.
 */
class ManifestTest {

    private val manifest =
        (File("src/main/AndroidManifest.xml").takeIf { it.exists() } ?: File("app/src/main/AndroidManifest.xml"))
            .readText()

    private val permissions =
        Regex("""<uses-permission\s+android:name="([^"]+)"""").findAll(manifest).map { it.groupValues[1] }.toSet()

    @Test
    fun `exactly the expected permissions`() {
        assertEquals(
            setOf(
                "android.permission.BIND_NOTIFICATION_LISTENER_SERVICE",
                "android.permission.RECEIVE_BOOT_COMPLETED",
                "android.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS",
                "android.permission.USE_BIOMETRIC",
                // Only for the opt-in update check (off by default) — see UpdatePolicy.
                "android.permission.INTERNET",
                "android.permission.POST_NOTIFICATIONS",
            ),
            permissions
        )
    }

    @Test
    fun `the app never installs anything itself and reads no storage`() {
        listOf(
            "REQUEST_INSTALL_PACKAGES", "READ_EXTERNAL_STORAGE", "MANAGE_EXTERNAL_STORAGE",
            "READ_CONTACTS", "ACCESS_NETWORK_STATE"
        ).forEach { assertTrue("$it must not be requested", permissions.none { p -> p.endsWith(it) }) }
    }

    @Test
    fun `backups stay off - the vault must not leave the phone that way either`() {
        assertTrue(manifest.contains("android:allowBackup=\"false\""))
        assertTrue(manifest.contains("android:fullBackupContent=\"false\""))
    }

    @Test
    fun `WorkManager's auto-initializer stays removed`() {
        // Otherwise NotifVaultApp's Configuration (job-id range away from the watchdog) is ignored.
        val block = Regex(
            """<meta-data\s+android:name="androidx\.work\.WorkManagerInitializer"[^>]*tools:node="remove""""
        )
        assertTrue(block.containsMatchIn(manifest))
    }

    @Test
    fun `the per-app language setting is wired up`() {
        assertTrue(manifest.contains("android:localeConfig=\"@xml/locales_config\""))
    }
}
