package org.trustweave.azurekms

import org.junit.jupiter.api.Test
import org.trustweave.kms.Algorithm
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Injectivity of the Azure Key Vault algorithm mapping.
 *
 * Azure identifies a key by three values - key type, curve, and (for RSA) size - so the round-trip
 * spans all three. Key types alone are deliberately NOT unique: every RSA size maps to
 * (RSA, null), and the strength is passed separately at creation time. Checking only the type
 * would therefore flag a collision that is by design, while missing the mismatch that matters:
 * a key created as one algorithm and later read back as another.
 *
 * The companion check is totality: every algorithm the provider *advertises* must map, and must
 * have a signing algorithm. Advertising support the mapping cannot honour becomes a runtime failure
 * at first use, for a caller who did exactly what the API said was supported.
 *
 * Runs without credentials, which is the point - the mapping is the part of a cloud KMS plugin that
 * can be verified offline.
 */
class AzureKeyManagementServiceMappingInjectivityTest {
    private val supported = AzureKeyManagementService.SUPPORTED_ALGORITHMS

    @Test
    fun `every advertised algorithm round-trips through key type, curve and size`() {
        // Azure identifies a key by three values, not one: every RSA size maps to (RSA, null) and
        // the strength travels separately, which AzureKeyManagementService passes explicitly via
        // CreateRsaKeyOptions.setKeySize. So the meaningful invariant is not that key types are
        // unique - they are deliberately not - but that the full triple decodes back to what was
        // encoded. A mismatch here means a key created as one type is read back as another.
        supported.forEach { algorithm ->
            val (keyType, curve) = AlgorithmMapping.toAzureKeyType(algorithm)
            val keySize = (algorithm as? Algorithm.RSA)?.keySize

            val decoded = AlgorithmMapping.parseAlgorithmFromKeyType(keyType, curve, keySize)

            assertEquals(
                algorithm,
                decoded,
                "Algorithm $algorithm encoded to (type=$keyType, curve=$curve, size=$keySize) " +
                    "but decoded back as $decoded",
            )
        }
    }

    @Test
    fun `the full triple is unique per algorithm`() {
        val collisions =
            supported
                .groupBy {
                    val (keyType, curve) = AlgorithmMapping.toAzureKeyType(it)
                    Triple(keyType, curve, (it as? Algorithm.RSA)?.keySize)
                }.filterValues { it.size > 1 }

        assertTrue(collisions.isEmpty(), "encoding collisions: $collisions")
    }

    @Test
    fun `every advertised algorithm maps without throwing`() {
        supported.forEach { algorithm -> AlgorithmMapping.toAzureKeyType(algorithm) }
    }

    @Test
    fun `every advertised algorithm has a signing algorithm`() {
        // Key creation succeeding while signing has no mapping fails at the first signature -
        // after the key exists and the caller believes setup worked.
        supported.forEach { algorithm -> AlgorithmMapping.toAzureSignatureAlgorithm(algorithm) }
    }
}
