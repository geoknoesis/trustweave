package org.trustweave.kms.fortanix

import org.junit.jupiter.api.Test
import org.trustweave.kms.Algorithm
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The first tests this provider has had.
 *
 * Fortanix identifies a key by three separate values — key type, curve, and key size — so the
 * round-trip has more room to go wrong than a single identifier does. Encoding P-384 but decoding it
 * as P-256, or losing the key size on an RSA key, produces a key that is silently the wrong
 * strength: nothing throws, and the signature is simply made with different material than intended.
 */
class FortanixAlgorithmMappingTest {
    private val supported = FortanixKeyManagementService.SUPPORTED_ALGORITHMS

    @Test
    fun `every advertised algorithm round-trips through key type, curve and size`() {
        supported.forEach { algorithm ->
            val keyType = AlgorithmMapping.toFortanixKeyType(algorithm)
            val curve = AlgorithmMapping.toFortanixCurve(algorithm)
            val keySize = AlgorithmMapping.toFortanixKeySize(algorithm)

            val decoded = AlgorithmMapping.fromFortanixKeyType(keyType, curve, keySize)

            assertEquals(
                algorithm,
                decoded,
                "Algorithm $algorithm encoded to (type=$keyType, curve=$curve, size=$keySize) " +
                    "but decoded back as $decoded — a key created as one type would be read back as another",
            )
        }
    }

    @Test
    fun `the three-part encoding is unique per algorithm`() {
        // Two algorithms sharing an encoding makes decoding ambiguous; one resolves as the other.
        val collisions =
            supported
                .groupBy {
                    Triple(
                        AlgorithmMapping.toFortanixKeyType(it),
                        AlgorithmMapping.toFortanixCurve(it),
                        AlgorithmMapping.toFortanixKeySize(it),
                    )
                }.filterValues { it.size > 1 }

        assertTrue(collisions.isEmpty(), "encoding collisions: $collisions")
    }

    @Test
    fun `elliptic-curve algorithms carry a curve and RSA carries a size`() {
        // The two halves are not interchangeable: an EC key without a curve, or an RSA key without a
        // size, is under-specified and the provider would have to guess.
        assertTrue(AlgorithmMapping.toFortanixCurve(Algorithm.P256) != null, "P256 must carry a curve")
        assertTrue(AlgorithmMapping.toFortanixCurve(Algorithm.P521) != null, "P521 must carry a curve")
        assertTrue(AlgorithmMapping.toFortanixKeySize(Algorithm.RSA.RSA_4096) == 4096, "RSA must carry its size")
    }

    @Test
    fun `an unknown encoding decodes to null rather than a guess`() {
        assertNull(AlgorithmMapping.fromFortanixKeyType("NOTATYPE", null, null))
    }
}
