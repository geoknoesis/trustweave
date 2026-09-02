package org.trustweave.kms.utimaco

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.trustweave.core.identifiers.KeyId
import org.trustweave.kms.Algorithm
import org.trustweave.kms.results.DeleteKeyResult
import org.trustweave.kms.results.GenerateKeyResult
import org.trustweave.kms.results.GetPublicKeyResult
import org.trustweave.kms.results.SignResult
import kotlin.test.assertTrue

/**
 * The first tests this provider has had — and they pin the one property that matters for a stub.
 *
 * Utimaco is deliberately unimplemented: it needs the vendor SDK and HSM access, neither of which
 * exists here, so every operation returns a Failure explaining that. Advertising a non-empty
 * SUPPORTED_ALGORITHMS while implementing none of them is the honest shape, but it is also a trap:
 * if a later change makes any operation return Success — a partially filled-in method, a refactor
 * that defaults a result — callers would believe keys were generated and signatures produced while
 * nothing happened at all. For a KMS that is the worst possible failure, because it is silent.
 *
 * These tests fail the moment a stub starts claiming success.
 */
class UtimacoStubFailsClosedTest {
    private val kms =
        UtimacoKeyManagementService(
            UtimacoKmsConfig(
                hsmAddress = "hsm.invalid:1500",
                partitionId = "partition",
                partitionPassword = "password",
            ),
        )

    @Test
    fun `generateKey never reports success`() =
        runBlocking<Unit> {
            val result = kms.generateKey(Algorithm.P256, emptyMap())
            assertTrue(result !is GenerateKeyResult.Success, "a stub must not claim to have generated a key")
        }

    @Test
    fun `sign never reports success`() =
        runBlocking<Unit> {
            val result = kms.sign(KeyId("any-key"), "payload".toByteArray(), Algorithm.P256)
            assertTrue(result !is SignResult.Success, "a stub must not claim to have produced a signature")
        }

    @Test
    fun `getPublicKey never reports success`() =
        runBlocking<Unit> {
            val result = kms.getPublicKey(KeyId("any-key"))
            assertTrue(result !is GetPublicKeyResult.Success, "a stub must not return key material")
        }

    @Test
    fun `deleteKey never reports success`() =
        runBlocking<Unit> {
            val result = kms.deleteKey(KeyId("any-key"))
            // DeleteKeyResult has no Success: Deleted and NotFound are both non-failure outcomes,
            // and a stub that deleted nothing must claim neither.
            assertTrue(result is DeleteKeyResult.Failure, "a stub must not report a delete outcome")
        }

    @Test
    fun `the advertised algorithms are documentation, not a promise this stub keeps`() =
        runBlocking<Unit> {
            // Non-empty on purpose: it records what a real implementation should support. The tests
            // above are what stop that list from being mistaken for working support.
            assertTrue(kms.getSupportedAlgorithms().isNotEmpty())
        }
}
