package io.celox.notifvault

import android.app.Application
import androidx.work.Configuration
import io.celox.notifvault.service.WatchdogPolicy
import io.celox.notifvault.data.DatabaseProvider
import io.celox.notifvault.data.NoiseCleanup
import io.celox.notifvault.data.RetentionPruner
import io.celox.notifvault.service.ListenerWatchdog
import io.celox.notifvault.update.UpdateScheduler
import io.celox.notifvault.util.clearShareExports
import kotlinx.coroutines.runBlocking

class NotifVaultApp : Application(), Configuration.Provider {

    // Keeps WorkManager's JobScheduler ids clear of the capture watchdog's (see WatchdogPolicy).
    // Takes effect because the default initializer is removed in the manifest.
    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setJobSchedulerJobIdRange(WatchdogPolicy.WORK_MANAGER_MIN_ID, WatchdogPolicy.WORK_MANAGER_MAX_ID)
            .build()

    override fun onCreate() {
        super.onCreate()
        // Warm up the encrypted DB so the listener can write immediately — but off the main
        // thread: Keystore + EncryptedSharedPreferences work would otherwise delay app start.
        // DatabaseProvider.get is synchronized, so a racing caller simply waits for this one.
        // Afterwards (same background thread) apply the retention policy — throttled to once
        // a day inside pruneIfDue, shared with the service's entry point — and purge the service
        // notifications captured before the noise filter existed (once per install).
        Thread {
            // Plaintext chat exports from an earlier share must not outlive their purpose.
            runCatching { clearShareExports(this) }
            DatabaseProvider.get(this)
            runCatching { runBlocking { RetentionPruner.pruneIfDue(this@NotifVaultApp) } }
            runCatching { runBlocking { NoiseCleanup.runOnce(this@NotifVaultApp) } }
            // Make sure the capture watchdog is scheduled — this covers the fresh install, where
            // neither a reboot nor an update broadcast has happened yet.
            runCatching { runBlocking { ListenerWatchdog.sync(this@NotifVaultApp) } }
            // The opt-in update check — re-created only when it was switched on.
            runCatching { UpdateScheduler.ensureScheduled(this@NotifVaultApp) }
        }.start()
    }
}
