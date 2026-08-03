package io.celox.notifvault.service

/**
 * When the capture service needs to be nudged back to life — the decisions on their own, free of
 * framework classes, so they can be unit-tested.
 *
 * Background: a [android.service.notification.NotificationListenerService] is bound by the system,
 * never by us, and it stops delivering in three situations the service cannot notice from the
 * inside — because in all three it is simply gone:
 *
 *  - **the app was updated in place** — the system unbinds the old code and sometimes fails to
 *    bind the new one (`notification listener could not be unbound`),
 *  - **the OEM killed the process** — Samsung's "Tiefschlaf" does not disconnect politely, so
 *    `onListenerDisconnected()` never runs and the in-service rebind never fires,
 *  - **the device rebooted** and the binding was not restored.
 *
 * The only remedy is `NotificationListenerService.requestRebind()` called from *outside* the
 * service — hence a boot receiver and a periodic job.
 */
object WatchdogPolicy {

    /** JobScheduler's floor for periodic jobs. Asking for less does not make it run sooner. */
    const val INTERVAL_MS = 15 * 60 * 1000L

    const val JOB_ID = 8231

    /**
     * `requestRebind` fails for a listener without notification access, and re-binding a
     * connected listener would only interrupt a working capture — so neither is attempted.
     */
    fun shouldRequestRebind(accessGranted: Boolean, listenerConnected: Boolean): Boolean =
        accessGranted && !listenerConnected

    /** The watchdog exists only to repair the binding; without access there is nothing to repair. */
    fun shouldSchedule(autoStart: Boolean, accessGranted: Boolean): Boolean =
        autoStart && accessGranted

    /**
     * True when the watchdog itself stopped running. That is the one symptom no rebind can fix:
     * it means the battery manager froze the whole app, and only the user can exempt it.
     *
     * [lastRunAt] `0` means "never ran", which is not yet a symptom (it may have just been
     * enabled). A [lastRunAt] in the future — the clock moved back — yields a negative age and
     * therefore reads as "just ran" rather than as overdue forever.
     */
    fun isWatchdogOverdue(
        lastRunAt: Long,
        now: Long,
        intervalMs: Long = INTERVAL_MS
    ): Boolean = lastRunAt > 0L && now - lastRunAt > OVERDUE_FACTOR * intervalMs

    /** Two missed runs are a hiccup (Doze batches jobs); three mean the app is being held down. */
    private const val OVERDUE_FACTOR = 3
}
