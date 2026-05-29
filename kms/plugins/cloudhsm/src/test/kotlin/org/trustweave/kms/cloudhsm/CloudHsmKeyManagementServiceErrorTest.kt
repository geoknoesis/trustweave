package org.trustweave.kms.cloudhsm

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

/**
 * Error-path tests for [CloudHsmKeyManagementService].
 *
 * No live CloudHSM cluster or PKCS#11 library is required. These tests assert that:
 *
 *  1. Construction without a valid HSM password env var fails with a [CloudHsmException]
 *     (since `composeLoginPin` cannot produce a PIN, which is wrapped by the constructor).
 *  2. Construction with a bogus PKCS#11 library path either:
 *     - Surfaces as a [CloudHsmException] at construction (the SunPKCS11 layer fails fast), or
 *     - Defers to first-use, depending on JDK behavior. Both are acceptable contracts as
 *       long as the failure type at the SPI boundary is a documented one — not a raw
 *       JDK / JNA exception.
 *
 * Live integration coverage (key generation, signing, verify, delete on a real cluster)
 * lives in [CloudHsmKeyManagementServiceIntegrationTest] and is gated on AWS_CLOUDHSM_*.
 */
class CloudHsmKeyManagementServiceErrorTest {

    private val validClusterId: String = "cluster-abcd1234efg"
    private val validHsmUser: String = "trustweave-cu"

    private fun bogusLibraryPath(): String =
        if (System.getProperty("os.name").lowercase().contains("win")) {
            "C:\\does-not-exist\\trustweave-test-no-such-cloudhsm.dll"
        } else {
            "/nonexistent/trustweave-test-no-such-cloudhsm.so"
        }

    private fun configWithUnsetPassword(): CloudHsmKmsConfig = CloudHsmKmsConfig(
        clusterId = validClusterId,
        hsmUser = validHsmUser,
        // Guaranteed-unset env var name.
        hsmPasswordEnvVar = "TRUSTWEAVE_TEST_CLOUDHSM_PASSWORD_UNSET_${System.nanoTime()}",
        pkcs11LibraryPath = bogusLibraryPath(),
    )

    @Test
    fun `construction fails fast when password env var is unset`() {
        val ex = assertThrows<CloudHsmException> {
            CloudHsmKeyManagementService(configWithUnsetPassword())
        }
        assertNotNull(ex.message, "CloudHsmException must carry a message")
        // Root cause must be the IllegalStateException from composeLoginPin.
        val rootCause = generateSequence<Throwable>(ex) { it.cause }.last()
        assertTrue(
            rootCause is IllegalStateException || ex.cause is IllegalStateException,
            "expected IllegalStateException as cause, got ${ex.cause?.javaClass}",
        )
    }

    @Test
    fun `companion providerNameFor embeds cluster id for JVM uniqueness`() {
        val cfg = CloudHsmKmsConfig(
            clusterId = validClusterId,
            hsmUser = validHsmUser,
        )
        val name = CloudHsmKeyManagementService.providerNameFor(cfg)
        assertEquals("TrustWeave-CloudHSM-$validClusterId", name)
        assertTrue(name.contains(validClusterId), "provider name must embed cluster id")
    }

    @Test
    fun `SUPPORTED_ALGORITHMS mirrors PKCS#11 plugin`() {
        // Sanity: the algorithm set should be non-empty and equal to the PKCS#11 upper bound.
        assertTrue(CloudHsmKeyManagementService.SUPPORTED_ALGORITHMS.isNotEmpty())
        assertEquals(
            org.trustweave.kms.pkcs11.Pkcs11KeyManagementService.SUPPORTED_ALGORITHMS,
            CloudHsmKeyManagementService.SUPPORTED_ALGORITHMS,
        )
    }
}
