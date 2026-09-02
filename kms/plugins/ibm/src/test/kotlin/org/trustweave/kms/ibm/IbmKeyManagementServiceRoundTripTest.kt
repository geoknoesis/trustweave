package org.trustweave.kms.ibm

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Round-trip fidelity for the IBM Key Protect algorithm mapping.
 *
 * The provider already has mapping tests, but none of them close the loop. That matters because a
 * mismatch between encode and decode is silent: a key is created as one type and later read back as
 * another - a P-384 key interpreted as P-256, or RSA-3072 as RSA-2048 - and nothing throws. The
 * signature is simply produced with, or verified against, material of the wrong shape.
 *
 * These run without credentials, which is the point: the mapping is the part of a cloud KMS plugin
 * that can be verified offline.
 */
class IbmKeyManagementServiceRoundTripTest {
    private val supported = IbmKeyManagementService.SUPPORTED_ALGORITHMS

    @Test
    fun `every advertised algorithm round-trips`() {
        supported.forEach { algorithm ->
            val encoded = AlgorithmMapping.toIbmKeyType(algorithm)
            val decoded = AlgorithmMapping.fromIbmKeyType(encoded)

            assertEquals(
                algorithm,
                decoded,
                "Algorithm $algorithm encoded to '$encoded' but decoded back as $decoded - " +
                    "a key created as one type would be read back as another",
            )
        }
    }

    @Test
    fun `distinct algorithms encode distinctly`() {
        // A shared encoding makes decoding ambiguous, and one algorithm silently resolves as another.
        val collisions = supported.groupBy { AlgorithmMapping.toIbmKeyType(it) }.filterValues { it.size > 1 }

        assertTrue(collisions.isEmpty(), "encoding collisions: $collisions")
    }
}
