package org.trustweave.did.dsl

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.trustweave.did.exception.DidException
import org.trustweave.did.identifiers.Did
import org.trustweave.did.model.DidDocument
import org.trustweave.did.resolver.DidResolutionResult
import org.trustweave.did.resolver.DidResolver
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

/**
 * Pins how the `resolveOrThrow` / `resolveOrNull` / `resolveOrDefault` convenience extensions —
 * on both [Did] and [DidResolver] — handle [DidResolutionResult.Deactivated] (§4.4).
 *
 * A deactivated DID is a revoked identity, not an absent one. All three extensions must throw
 * [DidException.DidResolutionFailed] for a deactivated DID rather than silently returning `null`
 * or a caller-supplied default, which would make a revoked DID indistinguishable from one that
 * was never registered.
 */
class DeactivatedResolutionExtensionsTest {
    private val did = Did("did:test:deactivated")

    private fun deactivatedResolver(): DidResolver = DidResolver { DidResolutionResult.Deactivated(did) }

    private fun notFoundResolver(): DidResolver = DidResolver { DidResolutionResult.Failure.NotFound(did) }

    // --- Did.resolveOrThrow / DidResolver.resolveOrThrow ---------------------------------

    @Test
    fun `Did resolveOrThrow throws for a deactivated DID`() =
        runBlocking<Unit> {
            val exception =
                assertFailsWith<DidException.DidResolutionFailed> {
                    did.resolveOrThrow(deactivatedResolver())
                }
            assertEquals(did, exception.did)
        }

    @Test
    fun `DidResolver resolveOrThrow throws for a deactivated DID`() =
        runBlocking<Unit> {
            val exception =
                assertFailsWith<DidException.DidResolutionFailed> {
                    deactivatedResolver().resolveOrThrow(did)
                }
            assertEquals(did, exception.did)
        }

    // --- Did.resolveOrNull / DidResolver.resolveOrNull ------------------------------------

    @Test
    fun `Did resolveOrNull throws for a deactivated DID rather than returning null`() =
        runBlocking<Unit> {
            val exception =
                assertFailsWith<DidException.DidResolutionFailed> {
                    did.resolveOrNull(deactivatedResolver())
                }
            assertEquals(did, exception.did)
        }

    @Test
    fun `Did resolveOrNull still returns null for an ordinary not-found DID`() =
        runBlocking<Unit> {
            assertNull(did.resolveOrNull(notFoundResolver()))
        }

    @Test
    fun `DidResolver resolveOrNull throws for a deactivated DID rather than returning null`() =
        runBlocking<Unit> {
            val exception =
                assertFailsWith<DidException.DidResolutionFailed> {
                    deactivatedResolver().resolveOrNull(did)
                }
            assertEquals(did, exception.did)
        }

    @Test
    fun `DidResolver resolveOrNull still returns null for an ordinary not-found DID`() =
        runBlocking<Unit> {
            assertNull(notFoundResolver().resolveOrNull(did))
        }

    // --- Did.resolveOrDefault --------------------------------------------------------------

    @Test
    fun `Did resolveOrDefault throws for a deactivated DID rather than returning the default`() =
        runBlocking<Unit> {
            val default = DidDocument(id = did)
            val exception =
                assertFailsWith<DidException.DidResolutionFailed> {
                    did.resolveOrDefault(deactivatedResolver(), default)
                }
            assertEquals(did, exception.did)
        }

    @Test
    fun `Did resolveOrDefault still returns the default for an ordinary not-found DID`() =
        runBlocking<Unit> {
            val default = DidDocument(id = did)
            assertEquals(default, did.resolveOrDefault(notFoundResolver(), default))
        }
}
