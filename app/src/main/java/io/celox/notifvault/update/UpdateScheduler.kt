package io.celox.notifvault.update

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

/** Starts and stops the checks. Off means no work is enqueued — nothing can reach the network. */
object UpdateScheduler {
    const val PERIODIC_WORK = "kp-update-check"
    const val IMMEDIATE_WORK = "kp-update-check-now"

    private val network = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

    fun setEnabled(context: Context, enabled: Boolean) {
        UpdateCheckStore.setEnabled(context, enabled)
        val wm = WorkManager.getInstance(context)
        if (enabled) {
            enqueuePeriodic(context)
            checkNow(context)
        } else {
            wm.cancelUniqueWork(PERIODIC_WORK)
            wm.cancelUniqueWork(IMMEDIATE_WORK)
            UpdateCheckStore.clearFindings(context)
        }
    }

    /** "Jetzt prüfen" — only ever runs with the switch on (the worker checks again). */
    fun checkNow(context: Context) {
        WorkManager.getInstance(context).enqueueUniqueWork(
            IMMEDIATE_WORK,
            ExistingWorkPolicy.REPLACE,
            OneTimeWorkRequestBuilder<UpdateCheckWorker>().setConstraints(network).build(),
        )
    }

    /** App start: re-creates the daily check if (and only if) it was switched on. */
    fun ensureScheduled(context: Context) {
        if (UpdatePolicy.scheduleOnStart(UpdateCheckStore.isEnabled(context))) enqueuePeriodic(context)
    }

    private fun enqueuePeriodic(context: Context) {
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            PERIODIC_WORK,
            ExistingPeriodicWorkPolicy.KEEP,
            // First periodic run a day out: switching on already enqueues an immediate check, and
            // two checks racing each other could both announce the same version.
            PeriodicWorkRequestBuilder<UpdateCheckWorker>(24, TimeUnit.HOURS)
                .setConstraints(network)
                .setInitialDelay(24, TimeUnit.HOURS)
                .build(),
        )
    }
}

class UpdateCheckWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        // A failed check is not retried early — the next daily run is soon enough.
        runCatching { UpdateChecker.check(applicationContext, ReleaseSource()) }
        Result.success()
    }
}
