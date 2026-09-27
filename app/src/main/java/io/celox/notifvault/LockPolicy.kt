package io.celox.notifvault

import androidx.biometric.BiometricManager

/**
 * The app lock's decisions, free of framework calls so they can be unit-tested.
 */
object LockPolicy {

    /**
     * Whether the lock may be skipped because authentication is *permanently* impossible on this
     * device — no biometric and no screen lock enrolled, or no hardware at all. Skipping there is
     * the only way not to lock the owner out of their own archive for good.
     *
     * Everything else — a sensor that is busy right now, a status the system cannot determine
     * yet — used to open the vault without a prompt as well. Those are transient: the prompt is
     * attempted, and on failure the lock screen stays up with a retry button.
     */
    fun skipAuthentication(canAuthenticate: Int): Boolean = when (canAuthenticate) {
        BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED,
        BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE,
        BiometricManager.BIOMETRIC_ERROR_UNSUPPORTED -> true
        else -> false
    }
}
