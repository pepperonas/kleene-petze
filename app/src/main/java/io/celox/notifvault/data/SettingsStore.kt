package io.celox.notifvault.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "nv_settings")

class SettingsStore(private val context: Context) {

    private val pkgKey = stringSetPreferencesKey("monitored_packages")
    private val captureAllKey = booleanPreferencesKey("capture_all")
    private val biometricKey = booleanPreferencesKey("biometric_lock")
    private val lastCaptureKey = longPreferencesKey("last_capture_at")
    private val retentionKey = intPreferencesKey("retention_days")
    private val lastPruneKey = longPreferencesKey("last_prune_at")

    val monitoredPackages: Flow<Set<String>> = context.dataStore.data
        .map { it[pkgKey] ?: DEFAULT_PACKAGES }

    val captureAll: Flow<Boolean> = context.dataStore.data
        .map { it[captureAllKey] ?: false }

    val biometricLock: Flow<Boolean> = context.dataStore.data
        .map { it[biometricKey] ?: false }

    /** Heartbeat: when the capture service last processed a notification (0 = never). */
    val lastCaptureAt: Flow<Long> = context.dataStore.data
        .map { it[lastCaptureKey] ?: 0L }

    /** Auto-delete messages older than this many days; 0 = keep forever (default). */
    val retentionDays: Flow<Int> = context.dataStore.data
        .map { it[retentionKey] ?: 0 }

    /** Throttle so the retention prune runs at most once per day across all entry points. */
    val lastPruneAt: Flow<Long> = context.dataStore.data
        .map { it[lastPruneKey] ?: 0L }

    suspend fun setMonitored(packages: Set<String>) {
        context.dataStore.edit { it[pkgKey] = packages }
    }

    suspend fun setCaptureAll(value: Boolean) {
        context.dataStore.edit { it[captureAllKey] = value }
    }

    suspend fun setBiometricLock(value: Boolean) {
        context.dataStore.edit { it[biometricKey] = value }
    }

    suspend fun setLastCaptureAt(value: Long) {
        context.dataStore.edit { it[lastCaptureKey] = value }
    }

    suspend fun setRetentionDays(value: Int) {
        context.dataStore.edit { it[retentionKey] = value }
    }

    suspend fun setLastPruneAt(value: Long) {
        context.dataStore.edit { it[lastPruneKey] = value }
    }

    companion object {
        val DEFAULT_PACKAGES = setOf("com.whatsapp", "com.whatsapp.w4b")

        // Known messengers the user can toggle in Settings.
        val KNOWN_MESSENGERS = linkedMapOf(
            "com.whatsapp" to "WhatsApp",
            "com.whatsapp.w4b" to "WhatsApp Business",
            "org.thoughtcrime.securesms" to "Signal",
            "org.telegram.messenger" to "Telegram",
            "com.instagram.android" to "Instagram",
            "com.facebook.orca" to "Messenger",
            "com.google.android.apps.messaging" to "Google Messages"
        )
    }
}
