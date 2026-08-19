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
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Conformance suite for W3C DID Resolution 1.0 (Candidate Recommendation, 2026-08-06):
 * https://www.w3.org/TR/2026/CR-did-resolution-1.0-20260806/
 *
 * One `TC-xx` test per normative statement exercised by Tasks 1-15's implementation:
 * [org.trustweave.did.resolver.DidErrorType], [org.trustweave.did.resolver.DidResolutionError],
 * [ResolutionOptions], [DidResolutionResult], [DidResolutionResultJson], [DidMediaTypes],
 * [toXmlDateTime] and [RegistryBasedResolver] (the §4.4 resolution algorithm).
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

    /** A method whose DIDs never exist, to exercise the NOT_FOUND branch of §4.4. */
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

    @Test
    fun `TC-01 section 4 resolve returns a document whose id equals the input DID`() = runBlocking {
        val did = newDid()
        val result = resolver.resolve(did) as DidResolutionResult.Success
        assertEquals(did, result.document.id)
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
        val options = ResolutionOptions(
            versionId = "3",
            versionTime = Instant.parse("2021-05-10T17:00:00Z")
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
    fun `TC-06 section 11 every error type is an absolute w3c URI`() = runBlocking {
        val error = resolver.resolve(Did("did:unsupportedmethod:123")).errorType
        assertNotNull(error)
        assertTrue(error.startsWith("https://www.w3.org/ns/did#"), "error type must be a spec URI: $error")
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
        assertEquals(DidMediaTypes.DID, result.resolutionMetadata.contentType)
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
