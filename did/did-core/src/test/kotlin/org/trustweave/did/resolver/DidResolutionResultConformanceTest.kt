package org.trustweave.did.resolver

import org.junit.jupiter.api.Test
import org.trustweave.did.identifiers.Did
import org.trustweave.did.model.DidDocumentMetadata
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DidResolutionResultConformanceTest {

    private val did = Did("did:example:123456789abcdefghi")

    @Test
    fun `a deactivated result carries no document`() {
        val result = DidResolutionResult.Deactivated(did)
        assertNull(result.documentOrNull)
        assertTrue(result.isDeactivated)
    }

    @Test
    fun `a deactivated result forces the deactivated flag`() {
        assertTrue(DidResolutionResult.Deactivated(did).documentMetadata.deactivated)
    }

    @Test
    fun `constructing a deactivated result with a non-deactivated metadata is rejected`() {
        assertFailsWith<IllegalArgumentException> {
            DidResolutionResult.Deactivated(did, DidDocumentMetadata(deactivated = false))
        }
    }

    @Test
    fun `a deactivated result carries no error`() {
        assertNull(DidResolutionResult.Deactivated(did).error)
    }

    @Test
    fun `not found defaults to the NOT_FOUND error URI`() {
        val result = DidResolutionResult.Failure.NotFound(did)
        assertEquals(DidErrorType.NOT_FOUND, result.error?.type)
    }

    @Test
    fun `invalid format defaults to the INVALID_DID error URI`() {
        val result = DidResolutionResult.Failure.InvalidFormat("did:", "missing method-specific id")
        assertEquals(DidErrorType.INVALID_DID, result.error?.type)
    }

    @Test
    fun `method not registered defaults to the METHOD_NOT_SUPPORTED error URI`() {
        val result = DidResolutionResult.Failure.MethodNotRegistered("nope")
        assertEquals(DidErrorType.METHOD_NOT_SUPPORTED, result.error?.type)
    }

    @Test
    fun `resolution error defaults to the INTERNAL_ERROR error URI`() {
        val result = DidResolutionResult.Failure.ResolutionError(did, "socket closed")
        assertEquals(DidErrorType.INTERNAL_ERROR, result.error?.type)
    }

    @Test
    fun `options error defaults to FEATURE_NOT_SUPPORTED and accepts INVALID_OPTIONS`() {
        assertEquals(
            DidErrorType.FEATURE_NOT_SUPPORTED,
            DidResolutionResult.Failure.OptionsError(did, "versionId is not supported").error?.type
        )
        assertEquals(
            DidErrorType.INVALID_OPTIONS,
            DidResolutionResult.Failure.OptionsError(
                did,
                "mutually exclusive",
                DidErrorType.INVALID_OPTIONS
            ).error?.type
        )
    }

    @Test
    fun `every failure exposes a non-null error`() {
        val failures: List<DidResolutionResult.Failure> = listOf(
            DidResolutionResult.Failure.NotFound(did),
            DidResolutionResult.Failure.InvalidFormat("did:", "bad"),
            DidResolutionResult.Failure.MethodNotRegistered("nope"),
            DidResolutionResult.Failure.ResolutionError(did, "boom"),
            DidResolutionResult.Failure.OptionsError(did, "nope")
        )
        failures.forEach { failure ->
            assertTrue(failure.error != null, "${failure::class.simpleName} must carry an error object")
            assertNull(failure.documentOrNull, "${failure::class.simpleName} must carry no document")
        }
    }
}
