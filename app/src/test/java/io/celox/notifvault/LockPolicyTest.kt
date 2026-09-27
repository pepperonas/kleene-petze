package io.celox.notifvault

import androidx.biometric.BiometricManager
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LockPolicyTest {

    @Test
    fun `a device that can authenticate is always asked`() {
        assertFalse(LockPolicy.skipAuthentication(BiometricManager.BIOMETRIC_SUCCESS))
    }

    @Test
    fun `transient failures do not open the vault`() {
        assertFalse(LockPolicy.skipAuthentication(BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE))
        assertFalse(LockPolicy.skipAuthentication(BiometricManager.BIOMETRIC_STATUS_UNKNOWN))
        assertFalse(LockPolicy.skipAuthentication(BiometricManager.BIOMETRIC_ERROR_SECURITY_UPDATE_REQUIRED))
    }

    @Test
    fun `only a permanent inability to authenticate skips the lock`() {
        assertTrue(LockPolicy.skipAuthentication(BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED))
        assertTrue(LockPolicy.skipAuthentication(BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE))
        assertTrue(LockPolicy.skipAuthentication(BiometricManager.BIOMETRIC_ERROR_UNSUPPORTED))
    }
}
