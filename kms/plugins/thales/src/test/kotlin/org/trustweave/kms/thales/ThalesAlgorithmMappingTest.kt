package org.trustweave.kms.thales

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The first tests this provider has had.
 *
 * A KMS plugin cannot be exercised end to end without live credentials, but its algorithm mapping
 * can — and that is where a silent, high-consequence bug lives. If `toThalesKeyAlgorithm` and
 * `fromThalesKeyAlgorithm` disagree, a key is created as one type and later interpreted as another:
 * a P-384 key read back as P-256, or RSA-3072 treated as RSA-2048. Nothing throws; the signature is
 * simply produced with, or checked against, the wrong key material.
 */
class ThalesAlgorithmMappingTest {
    private val supported = ThalesKeyManagementService.SUPPORTED_ALGORITHMS

    @Test
    fun `every advertised algorithm round-trips through the key-algorithm mapping`() {
        supported.forEach { algorithm ->
            val encoded = AlgorithmMapping.toThalesKeyAlgorithm(algorithm)
            val decoded = AlgorithmMapping.fromThalesKeyAlgorithm(encoded)

            assertEquals(
                algorithm,
                decoded,
                "Algorithm $algorithm encoded to '$encoded' but decoded back as $decoded — " +
                    "a key created as one type would be read back as another",
            )
        }
    }

    @Test
    fun `distinct algorithms map to distinct key algorithms`() {
        // A shared identifier makes decoding ambiguous, and one algorithm silently resolves as another.
        val collisions =
            supported.groupBy { AlgorithmMapping.toThalesKeyAlgorithm(it) }.filterValues { it.size > 1 }

        assertTrue(collisions.isEmpty(), "identifier collisions: $collisions")
    }

    @Test
    fun `every advertised algorithm has a signing algorithm`() {
        // Key creation succeeding while signing has no mapping would fail at first signature —
        // after the key exists and callers believe the setup worked.
        supported.forEach { algorithm ->
            val signing = AlgorithmMapping.toThalesSigningAlgorithm(algorithm)
            assertTrue(signing.isNotBlank(), "$algorithm has no signing algorithm")
        }
    }

    @Test
    fun `an unknown key algorithm decodes to null rather than a guess`() {
        assertNull(AlgorithmMapping.fromThalesKeyAlgorithm("not-an-algorithm"))
        assertNull(AlgorithmMapping.fromThalesKeyAlgorithm(""))
    }
}
