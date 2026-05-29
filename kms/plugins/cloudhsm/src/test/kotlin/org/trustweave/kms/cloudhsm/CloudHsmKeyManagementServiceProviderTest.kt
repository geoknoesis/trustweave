package org.trustweave.kms.cloudhsm

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.trustweave.kms.Algorithm

/**
 * Unit tests for [CloudHsmKeyManagementServiceProvider].
 *
 * Verifies SPI metadata and the create-time validation that fires *before* any
 * PKCS#11 library load is attempted. Live cluster connectivity is covered by the
 * integration tests gated on AWS_CLOUDHSM_* env vars.
 */
class CloudHsmKeyManagementServiceProviderTest {

    @Test
    fun `provider name is cloudhsm`() {
        assertEquals("cloudhsm", CloudHsmKeyManagementServiceProvider().name)
    }

    @Test
    fun `provider advertises CloudHSM algorithm set`() {
        val provider = CloudHsmKeyManagementServiceProvider()
        assertEquals(CloudHsmKeyManagementService.SUPPORTED_ALGORITHMS, provider.supportedAlgorithms)
        assertTrue(provider.supportsAlgorithm(Algorithm.Ed25519))
        assertTrue(provider.supportsAlgorithm(Algorithm.P256))
        assertTrue(provider.supportsAlgorithm(Algorithm.RSA.RSA_2048))
        // Secp256k1 is intentionally not on the PKCS#11/CloudHSM upper-bound set.
        assertTrue(!provider.supportsAlgorithm(Algorithm.Secp256k1))
    }

    @Test
    fun `provider declares required environment variables`() {
        val provider = CloudHsmKeyManagementServiceProvider()
        val required = provider.requiredEnvironmentVariables
        assertTrue("AWS_CLOUDHSM_CLUSTER_ID" in required)
        assertTrue("AWS_CLOUDHSM_HSM_USER" in required)
        assertTrue("AWS_CLOUDHSM_HSM_PASSWORD" in required)
        assertTrue("AWS_CLOUDHSM_PKCS11_LIBRARY" in required)
    }

    @Test
    fun `provider create rejects missing clusterId in options`() {
        // No clusterId key and no AWS_CLOUDHSM_CLUSTER_ID env -> must fail with clear message
        if (System.getenv("AWS_CLOUDHSM_CLUSTER_ID") != null) {
            // Skip: env-driven path would succeed (no way to scrub the live env).
            return
        }
        val provider = CloudHsmKeyManagementServiceProvider()
        val ex = assertThrows<IllegalArgumentException> { provider.create(emptyMap()) }
        assertTrue(ex.message!!.contains(CloudHsmKmsConfig.KEY_CLUSTER_ID))
    }

    @Test
    fun `provider create rejects malformed clusterId`() {
        val provider = CloudHsmKeyManagementServiceProvider()
        val ex = assertThrows<IllegalArgumentException> {
            provider.create(
                mapOf(
                    CloudHsmKmsConfig.KEY_CLUSTER_ID to "not-a-cluster",
                    CloudHsmKmsConfig.KEY_HSM_USER to "trustweave-cu",
                ),
            )
        }
        assertTrue(ex.message!!.contains("cluster-"))
    }
}
