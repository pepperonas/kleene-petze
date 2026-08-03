package io.celox.notifvault.service

import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import android.service.notification.NotificationListenerService
import io.celox.notifvault.data.SettingsStore
import io.celox.notifvault.util.PermissionUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Keeps the capture service bound. The framework half of [WatchdogPolicy] — see there for why a
 * notification listener stops delivering without ever noticing it itself.
 */
object ListenerWatchdog {

    /**
     * Asks the system to bind our listener again. This is the only call that is safe to make
     * while the service is *not* connected, and it is the only way back after an in-place app
     * update or an OEM process kill.
     *
     * @return false when it could not even be attempted (no notification access).
     */
    fun requestRebind(context: Context): Boolean {
        if (!PermissionUtils.hasNotificationAccess(context)) return false
        return runCatching {
            NotificationListenerService.requestRebind(
                ComponentName(context, NotificationCaptureService::class.java)
            )
        }.isSuccess
    }

    /**
     * Schedules the periodic check.
     *
     * `setPersisted` makes the job survive a reboot on its own, so capture recovers even when the
     * boot broadcast is delayed or dropped — the two mechanisms deliberately overlap.
     *
     * An already-pending job is left untouched: re-scheduling restarts the period, so calling
     * this on every app start would mean a frequently-opened app never reaches its own watchdog.
     */
    fun schedule(context: Context) {
        val scheduler = context.getSystemService(JobScheduler::class.java) ?: return
        if (runCatching { scheduler.getPendingJob(WatchdogPolicy.JOB_ID) }.getOrNull() != null) return
        // build() throws when setPersisted is used without RECEIVE_BOOT_COMPLETED, and schedule()
        // can fail on OEMs with their own job quotas — neither is worth taking the app down for.
        runCatching {
            scheduler.schedule(
                JobInfo.Builder(
                    WatchdogPolicy.JOB_ID,
                    ComponentName(context, WatchdogJobService::class.java)
                )
                    .setPeriodic(WatchdogPolicy.INTERVAL_MS)
                    .setPersisted(true)
                    .build()
            )
        }
    }

    fun cancel(context: Context) {
        runCatching { context.getSystemService(JobScheduler::class.java)?.cancel(WatchdogPolicy.JOB_ID) }
    }

    /** Brings the schedule in line with the current setting and permission state. */
    suspend fun sync(context: Context) {
        val app = context.applicationContext
        val should = WatchdogPolicy.shouldSchedule(
            autoStart = SettingsStore(app).autoStartOnBoot.first(),
            accessGranted = PermissionUtils.hasNotificationAccess(app)
        )
        if (should) schedule(app) else cancel(app)
    }
}

/**
 * Runs every [WatchdogPolicy.INTERVAL_MS] and rebinds the listener when it is gone. Also the
 * app's proof of life: if `lastWatchdogAt` stops advancing, background execution itself is being
 * blocked (see [WatchdogPolicy.isWatchdogOverdue]).
 */
class WatchdogJobService : JobService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onStartJob(params: JobParameters?): Boolean {
        scope.launch {
            runCatching {
                val app = applicationContext
                val settings = SettingsStore(app)
                val access = PermissionUtils.hasNotificationAccess(app)
                if (!WatchdogPolicy.shouldSchedule(settings.autoStartOnBoot.first(), access)) {
                    ListenerWatchdog.cancel(app)
                    return@runCatching
                }
                settings.setLastWatchdogAt(System.currentTimeMillis())
                // The service lives in this process, so its flow is the truth about the binding.
                // A job that had to start the process finds it false and asks for a rebind — at
                // worst redundantly, which the system ignores.
                if (WatchdogPolicy.shouldRequestRebind(
                        accessGranted = access,
                        listenerConnected = NotificationCaptureService.listenerConnected.value
                    )
                ) {
                    ListenerWatchdog.requestRebind(app)
                }
            }
            jobFinished(params, /* wantsReschedule = */ false)
        }
        return true // work continues on another thread
    }

    override fun onStopJob(params: JobParameters?): Boolean {
        // Cancel the children only: the same service instance serves the next period.
        scope.coroutineContext.cancelChildren()
        return true
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}
