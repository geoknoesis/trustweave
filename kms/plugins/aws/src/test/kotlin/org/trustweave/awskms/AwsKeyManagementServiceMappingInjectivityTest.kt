package org.trustweave.awskms

import org.junit.jupiter.api.Test
import kotlin.test.assertTrue

/**
 * Injectivity of the AWS KMS algorithm mapping.
 *
 * This provider has no decode function, so a true round-trip cannot be checked - but the property
 * that actually protects callers still can. If two TrustWeave algorithms map to the same key spec,
 * a caller asking for one gets a key of the other, silently: nothing throws, and the mismatch
 * surfaces only as a signature that will not verify, long after the key was created.
 *
 * The companion check is totality: every algorithm the provider *advertises* must map, and must
 * have a signing algorithm. Advertising support the mapping cannot honour becomes a runtime failure
 * at first use, for a caller who did exactly what the API said was supported.
 *
 * Runs without credentials, which is the point - the mapping is the part of a cloud KMS plugin that
 * can be verified offline.
 */
class AwsKeyManagementServiceMappingInjectivityTest {
    private val supported = AwsKeyManagementService.SUPPORTED_ALGORITHMS

    @Test
    fun `distinct algorithms map to distinct key specs`() {
        val collisions = supported.groupBy { AlgorithmMapping.toAwsKeySpec(it) }.filterValues { it.size > 1 }

        assertTrue(
            collisions.isEmpty(),
            "two algorithms share one key spec, so one would silently produce the other: $collisions",
        )
    }

    @Test
    fun `every advertised algorithm maps without throwing`() {
        supported.forEach { algorithm -> AlgorithmMapping.toAwsKeySpec(algorithm) }
    }

    @Test
    fun `every advertised algorithm has a signing algorithm`() {
        // Key creation succeeding while signing has no mapping fails at the first signature -
        // after the key exists and the caller believes setup worked.
        supported.forEach { algorithm -> AlgorithmMapping.toAwsSigningAlgorithm(algorithm) }
    }
}
