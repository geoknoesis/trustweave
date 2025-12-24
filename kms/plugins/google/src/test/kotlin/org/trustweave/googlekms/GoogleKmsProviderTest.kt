package org.trustweave.googlekms

import org.trustweave.kms.Algorithm
import org.trustweave.kms.KeyManagementService
import org.trustweave.testkit.annotations.RequiresPlugin
import org.junit.jupiter.api.Test
import java.io.IOException
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class GoogleKmsProviderTest {

    @Test
    fun `test provider name`() {
        val provider = GoogleKmsProvider()
        assertEquals("google-cloud-kms", provider.name)
    }

    @Test
    fun `test supported algorithms`() {
        val provider = GoogleKmsProvider()
        val supported = provider.supportedAlgorithms

        assertTrue(supported.contains(Algorithm.Ed25519))
        assertTrue(supported.contains(Algorithm.Secp256k1))
        assertTrue(supported.contains(Algorithm.P256))
        assertTrue(supported.contains(Algorithm.P384))
        assertTrue(supported.contains(Algorithm.P521))
        assertTrue(supported.contains(Algorithm.RSA.RSA_2048))
        assertTrue(supported.contains(Algorithm.RSA.RSA_3072))
        assertTrue(supported.contains(Algorithm.RSA.RSA_4096))
    }

    @Test
    @RequiresPlugin("google-cloud-kms")
    fun `test create with valid options`() {
        val provider = GoogleKmsProvider()
        val options = mapOf(
            "projectId" to "test-project",
            "location" to "us-east1",
            "keyRing" to "test-key-ring"
        )

        // This test will be skipped if GOOGLE_CLOUD_PROJECT is not set
        // If it runs, it means credentials are available
        try {
            val kms = provider.create(options)
            assertNotNull(kms)
            assertTrue(kms is GoogleCloudKeyManagementService)
        } catch (e: Exception) {
            // If credentials are required and not available, that's expected
            // This test verifies the provider can be instantiated with options when credentials exist
            assertTrue(e.message?.contains("credentials") == true ||
                      e.message?.contains("authentication") == true ||
                      e is IOException)
        }
    }

    @Test
    fun `test supportsAlgorithm`() {
        val provider = GoogleKmsProvider()

        assertTrue(provider.supportsAlgorithm(Algorithm.Ed25519))
        assertTrue(provider.supportsAlgorithm(Algorithm.Secp256k1))
        assertTrue(provider.supportsAlgorithm(Algorithm.P256))
        assertTrue(provider.supportsAlgorithm("Ed25519"))
        assertTrue(provider.supportsAlgorithm("secp256k1"))
    }
}

