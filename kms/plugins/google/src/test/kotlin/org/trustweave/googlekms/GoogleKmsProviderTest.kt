package org.trustweave.googlekms

import org.junit.jupiter.api.Test
import org.trustweave.kms.Algorithm
import org.trustweave.testkit.annotations.RequiresPlugin
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

        // These are available on every supported SDK version.
        assertTrue(supported.contains(Algorithm.Secp256k1))
        assertTrue(supported.contains(Algorithm.P256))
        assertTrue(supported.contains(Algorithm.P384))
        assertTrue(supported.contains(Algorithm.RSA.RSA_2048))
        assertTrue(supported.contains(Algorithm.RSA.RSA_3072))
        assertTrue(supported.contains(Algorithm.RSA.RSA_4096))

        // Ed25519 and P-521 are deliberately NOT asserted here. Whether Google Cloud KMS offers
        // them depends on the SDK on the classpath - `EC_SIGN_ED25519` is absent from some
        // versions - and this test previously required them to be advertised regardless. That
        // pinned a false promise: the provider listed Ed25519 while the algorithm mapping threw
        // for it, so a caller who trusted the advertised set got an exception instead of a key.
        // The advertised set is now computed from what the mapping can actually honour, and the
        // property worth asserting is that promise-keeping, below.
        assertTrue(
            supported.all { runCatching { AlgorithmMapping.toGoogleKmsAlgorithm(it) }.isSuccess },
            "every advertised algorithm must actually be mappable: $supported",
        )
    }

    @Test
    @RequiresPlugin("google-cloud-kms")
    fun `test create with valid options`() {
        val provider = GoogleKmsProvider()
        val options =
            mapOf(
                "projectId" to "test-project",
                "location" to "us-east1",
                "keyRing" to "test-key-ring",
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
            assertTrue(
                e.message?.contains("credentials") == true ||
                    e.message?.contains("authentication") == true ||
                    e is IOException,
            )
        }
    }

    @Test
    fun `test supportsAlgorithm`() {
        val provider = GoogleKmsProvider()

        assertTrue(provider.supportsAlgorithm(Algorithm.Secp256k1))
        assertTrue(provider.supportsAlgorithm(Algorithm.P256))
        assertTrue(provider.supportsAlgorithm("secp256k1"))

        // supportsAlgorithm must agree with the advertised set, whatever the SDK offers - saying
        // yes to something the mapping refuses is the mismatch this file used to require.
        assertTrue(
            provider.supportsAlgorithm(Algorithm.Ed25519) ==
                provider.supportedAlgorithms.contains(Algorithm.Ed25519),
            "supportsAlgorithm and supportedAlgorithms must agree about Ed25519",
        )
    }
}
