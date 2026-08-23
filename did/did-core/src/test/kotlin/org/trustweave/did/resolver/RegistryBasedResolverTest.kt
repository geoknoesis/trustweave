package org.trustweave.did.resolver

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.trustweave.did.DidCreationOptions
import org.trustweave.did.DidMethod
import org.trustweave.did.exception.DidException
import org.trustweave.did.identifiers.Did
import org.trustweave.did.model.DidDocument
import org.trustweave.did.registry.DidMethodRegistry
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Correctness tests for [RegistryBasedResolver]'s exception-to-error-type mapping.
 *
 * A [DidException] thrown out of a registered method's `resolveDid` must be reflected as the
 * matching §11 error type in [DidResolutionMetadata.error], not always `INTERNAL_ERROR` —
 * §12.1 requires 400 for an invalid DID and 404 for not-found, both of which map to 500 if the
 * exception subtype is discarded.
 */
class RegistryBasedResolverTest {

    private fun throwingMethod(methodName: String, exception: DidException): DidMethod =
        object : DidMethod {
            override val method: String = methodName
            override suspend fun createDid(options: DidCreationOptions): DidDocument {
                throw UnsupportedOperationException()
            }
            override suspend fun resolveDid(did: Did): DidResolutionResult {
                throw exception
            }
            override suspend fun updateDid(did: Did, updater: (DidDocument) -> DidDocument): DidDocument {
                throw UnsupportedOperationException()
            }
            override suspend fun deactivateDid(did: Did): Boolean {
                throw UnsupportedOperationException()
            }
        }

    @Test
    fun `DidException DidNotFound surfaces NOT_FOUND`() = runBlocking<Unit> {
        val did = Did("did:test:missing")
        val registry = DidMethodRegistry()
        registry.register(throwingMethod("test", DidException.DidNotFound(did = did)))

        val result = RegistryBasedResolver(registry).resolve(did)

        assertTrue(result is DidResolutionResult.Failure.ResolutionError)
        val metadata = (result as DidResolutionResult.Failure.ResolutionError).resolutionMetadata
        assertEquals(DidErrorType.NOT_FOUND, metadata.error?.type)
        assertEquals(404, metadata.error?.httpStatus)
    }

    @Test
    fun `DidException InvalidDidFormat surfaces INVALID_DID`() = runBlocking<Unit> {
        val did = Did("did:test:example")
        val registry = DidMethodRegistry()
        registry.register(
            throwingMethod(
                "test",
                DidException.InvalidDidFormat(did = did.value, reason = "malformed identifier")
            )
        )

        val result = RegistryBasedResolver(registry).resolve(did)

        assertTrue(result is DidResolutionResult.Failure.ResolutionError)
        val metadata = (result as DidResolutionResult.Failure.ResolutionError).resolutionMetadata
        assertEquals(DidErrorType.INVALID_DID, metadata.error?.type)
        assertEquals(400, metadata.error?.httpStatus)
    }

    @Test
    fun `unmapped DidException subtype falls back to INTERNAL_ERROR`() = runBlocking<Unit> {
        val did = Did("did:test:example")
        val registry = DidMethodRegistry()
        registry.register(
            throwingMethod(
                "test",
                DidException.DidResolutionFailed(did = did, reason = "driver crashed")
            )
        )

        val result = RegistryBasedResolver(registry).resolve(did)

        assertTrue(result is DidResolutionResult.Failure.ResolutionError)
        val metadata = (result as DidResolutionResult.Failure.ResolutionError).resolutionMetadata
        assertEquals(DidErrorType.INTERNAL_ERROR, metadata.error?.type)
        assertEquals(500, metadata.error?.httpStatus)
    }

    @Test
    fun `DidException DidMethodNotRegistered surfaces METHOD_NOT_SUPPORTED`() = runBlocking<Unit> {
        val did = Did("did:test:example")
        val registry = DidMethodRegistry()
        registry.register(
            throwingMethod(
                "test",
                DidException.DidMethodNotRegistered(method = "test", availableMethods = listOf("key"))
            )
        )

        val result = RegistryBasedResolver(registry).resolve(did)

        assertTrue(result is DidResolutionResult.Failure.ResolutionError)
        val metadata = (result as DidResolutionResult.Failure.ResolutionError).resolutionMetadata
        assertEquals(DidErrorType.METHOD_NOT_SUPPORTED, metadata.error?.type)
    }
}
