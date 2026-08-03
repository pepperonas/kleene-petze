package io.celox.notifvault.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import io.celox.notifvault.data.SettingsStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Restores capture at the two moments Android is known to drop the listener binding for good:
 * after a reboot, and after this app was updated in place. In both cases nothing inside the
 * service runs any more, so the rebind has to come from here.
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action !in HANDLED) return

        val app = context.applicationContext
        // Reading DataStore blocks; onReceive runs on the main thread, so hand it off properly.
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                if (SettingsStore(app).autoStartOnBoot.first()) {
                    ListenerWatchdog.requestRebind(app)
                    ListenerWatchdog.schedule(app)
                }
            } catch (_: Throwable) {
                // Never let a boot broadcast crash: there is nothing to recover here, and the
                // persisted periodic job tries again on its own.
            } finally {
                pending.finish()
            }
        }
    }

    private companion object {
        val HANDLED = setOf(
            Intent.ACTION_BOOT_COMPLETED,
            // The system rebinds listeners after an update only sometimes — this is why capture
            // silently stopped after installing a new version.
            Intent.ACTION_MY_PACKAGE_REPLACED,
            // Some OEMs send this instead of BOOT_COMPLETED after a "fast boot".
            "android.intent.action.QUICKBOOT_POWERON"
        )
    }
}
