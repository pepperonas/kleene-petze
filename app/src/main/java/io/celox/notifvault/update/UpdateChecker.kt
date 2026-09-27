package io.celox.notifvault.update

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import io.celox.notifvault.BuildConfig
import io.celox.notifvault.R

/** One check: ask the source, remember the finding, notify once per new version. */
object UpdateChecker {

    private const val CHANNEL = "updates"
    private const val NOTIFICATION_ID = 0x4B50D47E

    fun check(context: Context, source: LatestVersionSource): UpdatePolicy.Outcome {
        if (!UpdateCheckStore.isEnabled(context)) return UpdatePolicy.Outcome.DISABLED
        val latest = source.latestVersion()
        val outcome = UpdatePolicy.decide(
            enabled = true,
            latest = latest,
            installed = BuildConfig.VERSION_NAME,
            notified = UpdateCheckStore.notifiedVersion(context)
        )
        if (outcome != UpdatePolicy.Outcome.FAILED && latest != null) {
            UpdateCheckStore.recordLatest(context, latest)
        }
        // Only a notification that was actually shown counts as "told" — otherwise a user who
        // grants the permission later would never hear about this version. The banner shows it
        // either way.
        if (outcome == UpdatePolicy.Outcome.NOTIFY && latest != null && notify(context, latest)) {
            UpdateCheckStore.markNotified(context, latest)
        }
        return outcome
    }

    /** The version the Home banner should offer, or null. */
    fun bannerVersion(context: Context): String? = UpdatePolicy.bannerVersion(
        UpdateCheckStore.isEnabled(context),
        UpdateCheckStore.latestSeen(context),
        BuildConfig.VERSION_NAME
    )

    fun downloadIntent(): Intent =
        Intent(Intent.ACTION_VIEW, Uri.parse(ReleaseSource.DOWNLOAD_URL))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    /** Opens the download page; false when the phone has no app for it (no browser). */
    fun openDownload(context: Context): Boolean = try {
        context.startActivity(downloadIntent())
        true
    } catch (_: android.content.ActivityNotFoundException) {
        false
    }

    private fun notify(context: Context, latest: String): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) return false
        val nm = NotificationManagerCompat.from(context)
        if (!nm.areNotificationsEnabled()) return false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.getSystemService(NotificationManager::class.java)?.createNotificationChannel(
                NotificationChannel(
                    CHANNEL, context.getString(R.string.update_channel_name),
                    NotificationManager.IMPORTANCE_DEFAULT
                )
            )
        }
        val tap = PendingIntent.getActivity(
            context, 0, downloadIntent(),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val n = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle(context.getString(R.string.update_notification_title, AppVersion.display(latest)))
            .setContentText(context.getString(R.string.update_notification_body))
            .setContentIntent(tap)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_RECOMMENDATION)
            .build()
        return runCatching { nm.notify(NOTIFICATION_ID, n) }.isSuccess
    }
}
