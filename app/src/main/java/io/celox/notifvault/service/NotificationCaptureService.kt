package io.celox.notifvault.service

import android.content.ComponentName
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import io.celox.notifvault.data.DatabaseProvider
import io.celox.notifvault.data.RetentionPruner
import io.celox.notifvault.data.SettingsStore
import io.celox.notifvault.notif.MessageExtractor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Receives every notification posted on the device. We persist messages from the
 * monitored apps the moment they arrive — so even if the sender later deletes the
 * message in WhatsApp (which sends no removal notification), our copy survives.
 */
class NotificationCaptureService : NotificationListenerService() {

    companion object {
        // Listener health, readable by the UI (same process). "Access granted" alone doesn't
        // mean the listener is alive — OEMs kill it — so Home shows a hint when this is false.
        private val _listenerConnected = MutableStateFlow(false)
        val listenerConnected: StateFlow<Boolean> = _listenerConnected
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var extractor: MessageExtractor
    private lateinit var settings: SettingsStore

    // Settings cached in-memory (null until DataStore's first emission) so we don't collect
    // the DataStore flow from scratch for every posted notification.
    private lateinit var captureAll: StateFlow<Boolean?>
    private lateinit var monitored: StateFlow<Set<String>?>

    // Notifications are processed strictly in post order through this queue: a deletion
    // placeholder must never be applied before the insert of the original it flags, and
    // concurrent per-notification coroutines (even on a single-threaded dispatcher) could
    // interleave at suspension points.
    private val queue = Channel<StatusBarNotification>(Channel.UNLIMITED)

    override fun onCreate() {
        super.onCreate()
        extractor = MessageExtractor(packageManager)
        settings = SettingsStore(applicationContext)
        captureAll = settings.captureAll.map { it as Boolean? }
            .stateIn(scope, SharingStarted.Eagerly, null)
        monitored = settings.monitoredPackages.map { it as Set<String>? }
            .stateIn(scope, SharingStarted.Eagerly, null)
        // runCatching: one bad notification must not kill the consumer loop for good.
        scope.launch { for (sbn in queue) runCatching { process(sbn) } }
        // Retention runs from here too: the service starts even when the app UI never opens.
        scope.launch { runCatching { RetentionPruner.pruneIfDue(applicationContext) } }
    }

    override fun onDestroy() {
        _listenerConnected.value = false
        queue.close()
        scope.cancel()
        super.onDestroy()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        val notification = sbn ?: return
        queue.trySend(notification)
    }

    // "Last capture" heartbeat, written at most once a minute (no DataStore I/O per notification).
    private var lastHeartbeatWrite = 0L

    private suspend fun process(sbn: StatusBarNotification) {
        // filterNotNull().first() waits for DataStore's initial load once, then is free.
        val all = captureAll.filterNotNull().first()
        val pkgs = monitored.filterNotNull().first()
        if (!all && sbn.packageName !in pkgs) return

        val result = extractor.extract(sbn)
        val dao = DatabaseProvider.get(applicationContext).messageDao()
        if (result.messages.isNotEmpty()) {
            val rowIds = dao.insertAll(result.messages)
            // Edit detection: a row that actually got inserted (id != -1) may be the edited
            // version of an existing message — same chat + sender + timestamp, new text.
            // Flag those older siblings; for a genuinely new message this matches nothing.
            for ((i, m) in result.messages.withIndex()) {
                if (rowIds[i] != -1L) {
                    dao.markEditSuperseded(m.conversationKey, m.packageName, m.sender, m.messageTime, m.id)
                }
            }
        }
        // A deleted-while-unread message arrived as a placeholder: flag the stored original.
        // (After an edit, the placeholder keeps the original timestamp and thus flags every
        // stored version of that message — intended: they were all deleted.)
        for (d in result.deletions) dao.markDeleted(d.conversationKey, d.sender, d.messageTime)

        val now = System.currentTimeMillis()
        if (now - lastHeartbeatWrite > 60_000) {
            lastHeartbeatWrite = now
            settings.setLastCaptureAt(now)
        }
    }

    // WhatsApp does not post a notification when a message is deleted, so onNotificationRemoved
    // is intentionally not used for capture — the original is already safely stored.

    override fun onListenerConnected() {
        super.onListenerConnected()
        _listenerConnected.value = true
        // Snapshot anything currently in the shade on (re)connect. activeNotifications can
        // throw if the listener is racing a disconnect.
        runCatching { activeNotifications }.getOrNull()?.forEach { onNotificationPosted(it) }
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        _listenerConnected.value = false
        // Samsung One UI aggressively kills listeners; ask the system to rebind us.
        requestRebind(ComponentName(this, NotificationCaptureService::class.java))
    }
}
