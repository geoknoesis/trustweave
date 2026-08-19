package org.trustweave.conformance

import kotlinx.coroutines.runBlocking
import kotlinx.datetime.Instant
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.trustweave.did.DidCreationOptions
import org.trustweave.did.DidMethod
import org.trustweave.did.KeyAlgorithm
import org.trustweave.did.identifiers.Did
import org.trustweave.did.model.DidDocument
import org.trustweave.did.model.DidDocumentMetadata
import org.trustweave.did.registry.DidMethodRegistry
import org.trustweave.did.representation.DidMediaTypes
import org.trustweave.did.resolution.DidResolutionResultJson
import org.trustweave.did.resolution.ResolutionOptions
import org.trustweave.did.resolver.DidErrorType
import org.trustweave.did.resolver.DidResolutionResult
import org.trustweave.did.resolver.RegistryBasedResolver
import org.trustweave.did.resolver.documentMetadata
import org.trustweave.did.resolver.errorType
import org.trustweave.did.util.toXmlDateTime
import org.trustweave.keydid.KeyDidMethod
import org.trustweave.testkit.kms.InMemoryKeyManagementService
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Conformance suite for W3C DID Resolution 1.0 (Candidate Recommendation, 2026-08-06):
 * https://www.w3.org/TR/2026/CR-did-resolution-1.0-20260806/
 *
 * One `TC-xx` test per normative statement exercised by Tasks 1-15's implementation:
 * [org.trustweave.did.resolver.DidErrorType], [org.trustweave.did.resolver.DidResolutionError],
 * [ResolutionOptions], [DidResolutionResult], [DidResolutionResultJson], [DidMediaTypes],
 * [toXmlDateTime] and [RegistryBasedResolver] (the section 4.4 resolution algorithm).
 */
@Tag("conformance")
@Tag("did-resolution-1.0")
class DidResolution10ConformanceTest {

    private val kms = InMemoryKeyManagementService()
    private val method = KeyDidMethod(kms)
    private val resolver = RegistryBasedResolver(DidMethodRegistry().apply { register(method) })

    private fun newDid(): Did = runBlocking {
        method.createDid(DidCreationOptions(algorithm = KeyAlgorithm.ED25519)).id
    }

    /** A method whose DIDs never exist, to exercise the NOT_FOUND branch of section 4.4. */
    private class MissingDidMethod : DidMethod {
        override val method: String = "missing"
        override suspend fun createDid(options: DidCreationOptions): DidDocument =
            throw UnsupportedOperationException("not needed")
        override suspend fun resolveDid(did: Did): DidResolutionResult =
            DidResolutionResult.Failure.NotFound(did)
        override suspend fun updateDid(did: Did, updater: (DidDocument) -> DidDocument): DidDocument =
            throw UnsupportedOperationException("not needed")
        override suspend fun deactivateDid(did: Did): Boolean = false
    }

    /**
     * A method that always resolves to a document whose id differs from the requested DID, to
     * exercise section 4's id-integrity guard directly. did:key is self-certifying and
     * deterministic: its resolved document id always equals the DID that produced it, so it can
     * never reach this branch. A stub method that misbehaves is required to pin the guard itself
     * rather than only the happy path.
     */
    private class MismatchedIdDidMethod : DidMethod {
        override val method: String = "mismatched"
        override suspend fun createDid(options: DidCreationOptions): DidDocument =
            throw UnsupportedOperationException("not needed")
        override suspend fun resolveDid(did: Did): DidResolutionResult =
            DidResolutionResult.Success(DidDocument(id = Did("did:mismatched:someone-else")))
        override suspend fun updateDid(did: Did, updater: (DidDocument) -> DidDocument): DidDocument =
            throw UnsupportedOperationException("not needed")
        override suspend fun deactivateDid(did: Did): Boolean = false
    }

    @Test
    fun `TC-01 section 4 a mismatched document id is rejected with INVALID_DID_DOCUMENT`() = runBlocking {
        val registry = DidMethodRegistry().apply { register(MismatchedIdDidMethod()) }
        val result = RegistryBasedResolver(registry).resolve(Did("did:mismatched:123"))
        assertEquals(DidErrorType.INVALID_DID_DOCUMENT, result.errorType)
    }

    @Test
    fun `TC-02 section 4-4 step 2 an unsupported method yields METHOD_NOT_SUPPORTED`() = runBlocking {
        assertEquals(
            DidErrorType.METHOD_NOT_SUPPORTED,
            resolver.resolve(Did("did:unsupportedmethod:123")).errorType
        )
    }

    @Test
    fun `TC-03 section 4-4 step 3 an unsupported option yields FEATURE_NOT_SUPPORTED`() = runBlocking {
        assertEquals(
            DidErrorType.FEATURE_NOT_SUPPORTED,
            resolver.resolve(newDid(), ResolutionOptions(versionId = "3")).errorType
        )
    }

    @Test
    fun `TC-04 section 4-4 step 4 contradictory options yield INVALID_OPTIONS`() = runBlocking {
        // accept pins the step-4-before-step-3 ordering: without RegistryBasedResolver's own
        // step-4 check running first, the unsupported accept below would be reached first and
        // report REPRESENTATION_NOT_SUPPORTED instead of INVALID_OPTIONS.
        val options = ResolutionOptions(
            versionId = "3",
            versionTime = Instant.parse("2021-05-10T17:00:00Z"),
            accept = "application/did+cbor"
        )
        assertEquals(DidErrorType.INVALID_OPTIONS, resolver.resolve(newDid(), options).errorType)
    }

    @Test
    fun `TC-05 section 4-4 a DID that does not exist yields NOT_FOUND`() = runBlocking {
        // did:key is deterministic and never misses, so a method that reports non-existence is
        // registered to exercise the NOT_FOUND path of the algorithm.
        val registry = DidMethodRegistry().apply { register(MissingDidMethod()) }
        val result = RegistryBasedResolver(registry).resolve(Did("did:missing:123"))
        assertEquals(DidErrorType.NOT_FOUND, result.errorType)
    }

    @Test
    fun `TC-06 section 11 every error type is an absolute w3c URI`() {
        val allErrorTypes = listOf(
            DidErrorType.INVALID_DID,
            DidErrorType.INVALID_DID_DOCUMENT,
            DidErrorType.NOT_FOUND,
            DidErrorType.REPRESENTATION_NOT_SUPPORTED,
            DidErrorType.INVALID_DID_URL,
            DidErrorType.METHOD_NOT_SUPPORTED,
            DidErrorType.INVALID_OPTIONS,
            DidErrorType.INTERNAL_ERROR,
            DidErrorType.FEATURE_NOT_SUPPORTED
        )
        // Guards against a future constant being added to DidErrorType without this list (and
        // therefore this test's "every error type" claim) being updated to match.
        assertEquals(9, allErrorTypes.size, "DidErrorType gained/lost a constant; update this list")
        allErrorTypes.forEach { type ->
            assertTrue(type.startsWith("https://www.w3.org/ns/did#"), "error type must be a spec URI: $type")
        }
    }

    @Test
    fun `TC-07 section 4 a failed resolution carries no document`() = runBlocking {
        val result = resolver.resolve(Did("did:unsupportedmethod:123"))
        assertNull((result as? DidResolutionResult.Success)?.document)
    }

    @Test
    fun `TC-08 section 4 a failed resolution carries empty document metadata`() = runBlocking {
        val result = resolver.resolve(Did("did:unsupportedmethod:123"))
        assertEquals(DidDocumentMetadata(), result.documentMetadata)
    }

    @Test
    fun `TC-09 section 4-2 contentType defaults to application-did`() = runBlocking {
        val result = resolver.resolve(newDid()) as DidResolutionResult.Success
        // Literal, not DidMediaTypes.DID: DidResolutionMetadata.contentType's own default value
        // is that same constant, so comparing constant-to-constant would move together with a
        // regression and never fail.
        assertEquals("application/did", result.resolutionMetadata.contentType)
    }

    @Test
    fun `TC-09b section 4-2 contentType echoes a supported requested representation`() = runBlocking {
        val result = resolver.resolve(
            newDid(),
            ResolutionOptions(accept = DidMediaTypes.DID_LD_JSON)
        ) as DidResolutionResult.Success
        assertEquals("application/did+ld+json", result.resolutionMetadata.contentType)
    }

    @Test
    fun `TC-10 section 4-4 an unsupported accept yields REPRESENTATION_NOT_SUPPORTED`() = runBlocking {
        assertEquals(
            DidErrorType.REPRESENTATION_NOT_SUPPORTED,
            resolver.resolve(newDid(), ResolutionOptions(accept = "application/did+cbor")).errorType
        )
    }

    @Test
    fun `TC-11 section 3-1 datetimes are UTC without sub-second precision`() {
        assertEquals("2020-12-20T19:17:47Z", Instant.parse("2020-12-20T19:17:47.999999Z").toXmlDateTime())
    }

    @Test
    fun `TC-12 section 9 the resolution result carries all three members`() = runBlocking {
        val json = DidResolutionResultJson.toJson(resolver.resolve(newDid()))
        assertTrue(json.containsKey("didDocument"))
        assertTrue(json.containsKey("didResolutionMetadata"))
        assertTrue(json.containsKey("didDocumentMetadata"))
    }

    @Test
    fun `TC-13 section 9 the resolution result media type is application-did-resolution`() {
        assertEquals("application/did-resolution", DidResolutionResultJson.MEDIA_TYPE)
    }

    @Test
    fun `TC-14 section 4-1 expandRelativeUrls is accepted and does not fail resolution`() = runBlocking {
        val result = resolver.resolve(newDid(), ResolutionOptions(expandRelativeUrls = true))
        assertTrue(result is DidResolutionResult.Success)
    }

    @Test
    fun `TC-15 section 4 an empty options structure is accepted`() = runBlocking {
        assertTrue(resolver.resolve(newDid(), ResolutionOptions.EMPTY) is DidResolutionResult.Success)
    }
}
