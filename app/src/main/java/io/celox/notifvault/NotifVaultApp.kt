package io.celox.notifvault

import android.app.Application
import io.celox.notifvault.data.DatabaseProvider
import io.celox.notifvault.data.RetentionPruner
import kotlinx.coroutines.runBlocking

class NotifVaultApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // Warm up the encrypted DB so the listener can write immediately — but off the main
        // thread: Keystore + EncryptedSharedPreferences work would otherwise delay app start.
        // DatabaseProvider.get is synchronized, so a racing caller simply waits for this one.
        // Afterwards (same background thread) apply the retention policy — throttled to once
        // a day inside pruneIfDue, shared with the service's entry point.
        Thread {
            DatabaseProvider.get(this)
            runCatching { runBlocking { RetentionPruner.pruneIfDue(this@NotifVaultApp) } }
        }.start()
    }
}
