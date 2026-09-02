package org.trustweave.kms.cyberark

import org.junit.jupiter.api.Test
import org.trustweave.kms.Algorithm
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The first tests this provider has had.
 *
 * A KMS plugin cannot be exercised end to end without live credentials, but its algorithm mapping
 * can — and that is where a silent, high-consequence bug lives. If `toConjurAlgorithm` and
 * `fromConjurAlgorithm` disagree, a key is created as one type and later interpreted as another:
 * a P-384 key read back as P-256, or an RSA-3072 key treated as RSA-2048. Nothing throws; the
 * signature is simply made with, or verified against, the wrong key material.
 *
 * The other invariant checked here is agreement between what the provider *advertises* and what it
 * can actually map. A provider that lists an algorithm in `SUPPORTED_ALGORITHMS` but throws when
 * asked to map it fails at first use, in production, for a caller who did exactly what the API
 * told them was supported.
 */
class CyberArkAlgorithmMappingTest {
    private val supported = CyberArkKeyManagementService.SUPPORTED_ALGORITHMS

    @Test
    fun `every advertised algorithm round-trips through the Conjur mapping`() {
        supported.forEach { algorithm ->
            val encoded = AlgorithmMapping.toConjurAlgorithm(algorithm)
            val decoded = AlgorithmMapping.fromConjurAlgorithm(encoded)

            assertEquals(
                algorithm,
                decoded,
                "Algorithm $algorithm encoded to '$encoded' but decoded back as $decoded — " +
                    "a key created as one type would be read back as another",
            )
        }
    }

    @Test
    fun `every advertised algorithm can actually be mapped`() {
        // Advertising support the mapping cannot honour turns into a runtime failure at first use.
        supported.forEach { algorithm ->
            val encoded = AlgorithmMapping.toConjurAlgorithm(algorithm)
            assertTrue(encoded.isNotBlank(), "$algorithm mapped to a blank identifier")
        }
    }

    @Test
    fun `distinct algorithms map to distinct identifiers`() {
        // Two algorithms sharing an identifier makes the decode ambiguous, and one of them will be
        // silently resolved as the other.
        val byIdentifier = supported.groupBy { AlgorithmMapping.toConjurAlgorithm(it) }
        val collisions = byIdentifier.filterValues { it.size > 1 }

        assertTrue(collisions.isEmpty(), "identifier collisions: $collisions")
    }

    @Test
    fun `an unsupported RSA key size is refused rather than silently downgraded`() {
        assertFailsWith<IllegalArgumentException> {
            AlgorithmMapping.toConjurAlgorithm(Algorithm.RSA(1024))
        }
    }

    @Test
    fun `an unknown Conjur identifier decodes to null rather than a guess`() {
        assertNull(AlgorithmMapping.fromConjurAlgorithm("not-an-algorithm"))
        assertNull(AlgorithmMapping.fromConjurAlgorithm(""))
    }

    @Test
    fun `decoding is case-insensitive and accepts both punctuation forms`() {
        // Conjur secrets are written by humans and by other tools; the decoder deliberately accepts
        // `EC:secp256r1` and `EC-P256`. Pin that, so a tidy-up does not quietly drop one form.
        assertEquals(Algorithm.P256, AlgorithmMapping.fromConjurAlgorithm("ec:secp256r1"))
        assertEquals(Algorithm.P256, AlgorithmMapping.fromConjurAlgorithm("EC-P256"))
        assertEquals(Algorithm.RSA.RSA_3072, AlgorithmMapping.fromConjurAlgorithm("rsa-3072"))
        assertNotNull(AlgorithmMapping.fromConjurAlgorithm("ED25519"))
    }
}
