package io.celox.notifvault.update

/**
 * The update check's decisions, free of framework classes (see CLAUDE.md: decision logic lives in
 * plain objects the JVM tests can call).
 */
object UpdatePolicy {

    enum class Outcome { DISABLED, FAILED, UP_TO_DATE, ALREADY_NOTIFIED, NOTIFY }

    /**
     * What one check should do. [notified] is the version the user was already told about, so a
     * release is announced once — not every day until they install it.
     */
    fun decide(enabled: Boolean, latest: String?, installed: String, notified: String?): Outcome = when {
        !enabled -> Outcome.DISABLED
        latest == null || AppVersion.parse(latest) == null -> Outcome.FAILED
        !AppVersion.isNewer(latest, installed) -> Outcome.UP_TO_DATE
        notified == latest -> Outcome.ALREADY_NOTIFIED
        else -> Outcome.NOTIFY
    }

    /** The version the in-app banner offers, or null. Off means off: no stale banner either. */
    fun bannerVersion(enabled: Boolean, latestSeen: String?, installed: String): String? =
        if (enabled && latestSeen != null && AppVersion.isNewer(latestSeen, installed))
            AppVersion.display(latestSeen) else null

    /**
     * Whether app start may (re)create the periodic check. This is the privacy guarantee in one
     * line: without the opt-in, no work is ever enqueued, so nothing can reach the network.
     */
    fun scheduleOnStart(enabled: Boolean): Boolean = enabled
}
