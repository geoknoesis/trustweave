package org.trustweave.did

import kotlinx.datetime.Instant
import org.junit.jupiter.api.Test
import org.trustweave.did.identifiers.Did
import org.trustweave.did.identifiers.VerificationMethodId
import org.trustweave.did.model.DidDocument
import org.trustweave.did.model.DidDocumentMetadata
import org.trustweave.did.model.DidOrUrl
import org.trustweave.did.model.DidService
import org.trustweave.did.model.ServiceEndpoint
import org.trustweave.did.model.VerificationMethod
import org.trustweave.did.resolver.DidErrorType
import org.trustweave.did.resolver.DidResolutionMetadata
import org.trustweave.did.resolver.DidResolutionResult
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Comprehensive tests for Did models and DidRegistry.
 */
class DidModelsTest {
    // Basic Did constructor tests - comprehensive coverage in DidModelsBranchCoverageTest

    @Test
    fun `test VerificationMethod with all fields`() {
        val did = Did("did:key:issuer")
        val vm =
            VerificationMethod(
                id = VerificationMethodId.parse("did:key:issuer#key-1"),
                type = "Ed25519VerificationKey2020",
                controller = did,
                publicKeyJwk = mapOf<String, Any?>("kty" to "OKP", "crv" to "Ed25519"),
                publicKeyMultibase = "z6MkhaXgBZDvotDkL5257faiztiGiC2QtKLGpbnnEGta2doK",
            )

        assertEquals("did:key:issuer#key-1", vm.id.value)
        assertEquals("Ed25519VerificationKey2020", vm.type)
        assertEquals("did:key:issuer", vm.controller.value)
        assertNotNull(vm.publicKeyJwk)
        assertNotNull(vm.publicKeyMultibase)
    }

    @Test
    fun `test VerificationMethod with defaults`() {
        val did = Did("did:key:issuer")
        val vm =
            VerificationMethod(
                id = VerificationMethodId.parse("did:key:issuer#key-1"),
                type = "Ed25519VerificationKey2020",
                controller = did,
            )

        assertNull(vm.publicKeyJwk)
        assertNull(vm.publicKeyMultibase)
    }

    @Test
    fun `test Service with URL endpoint`() {
        val service =
            DidService(
                id = "did:web:example.com#service-1",
                type = listOf("LinkedDomains"),
                serviceEndpoint = ServiceEndpoint.Url("https://example.com"),
            )

        assertEquals("did:web:example.com#service-1", service.id)
        assertEquals(listOf("LinkedDomains"), service.type)
        assertEquals(ServiceEndpoint.Url("https://example.com"), service.serviceEndpoint)
    }

    @Test
    fun `test Service with object endpoint`() {
        val endpoint = mapOf("uri" to "https://example.com", "routingKeys" to listOf("key1"))
        val service =
            DidService(
                id = "did:web:example.com#service-1",
                type = listOf("DIDCommMessaging"),
                serviceEndpoint = ServiceEndpoint.ObjectEndpoint(endpoint),
            )

        assertTrue(service.serviceEndpoint is ServiceEndpoint.ObjectEndpoint)
    }

    @Test
    fun `test DidDocument with all fields`() {
        val did = Did("did:key:issuer")
        val vmId = VerificationMethodId.parse("did:key:issuer#key-1")
        val vm =
            VerificationMethod(
                id = vmId,
                type = "Ed25519VerificationKey2020",
                controller = did,
            )
        val service =
            DidService(
                id = "did:key:issuer#service-1",
                type = listOf("LinkedDomains"),
                serviceEndpoint = ServiceEndpoint.Url("https://example.com"),
            )

        val doc =
            DidDocument(
                id = did,
                alsoKnownAs = listOf(DidOrUrl.AsDid(Did("did:web:example.com"))),
                controller = listOf(Did("did:key:controller")),
                verificationMethod = listOf(vm),
                authentication = listOf(vmId),
                assertionMethod = listOf(vmId),
                keyAgreement = listOf(VerificationMethodId.parse("did:key:issuer#key-2")),
                service = listOf(service),
            )

        assertEquals("did:key:issuer", doc.id.value)
        assertEquals(1, doc.alsoKnownAs.size)
        assertEquals(1, doc.verificationMethod.size)
        assertEquals(1, doc.service.size)
    }

    @Test
    fun `test DidDocument with defaults`() {
        val doc = DidDocument(id = Did("did:key:issuer"))

        assertTrue(doc.alsoKnownAs.isEmpty())
        assertTrue(doc.controller.isEmpty())
        assertTrue(doc.verificationMethod.isEmpty())
        assertTrue(doc.authentication.isEmpty())
        assertTrue(doc.assertionMethod.isEmpty())
        assertTrue(doc.keyAgreement.isEmpty())
        assertTrue(doc.service.isEmpty())
    }

    @Test
    fun `test DidResolutionResult with document`() {
        val doc = DidDocument(id = Did("did:key:issuer"))
        val result =
            DidResolutionResult.Success(
                document = doc,
                documentMetadata =
                    DidDocumentMetadata(
                        created = kotlinx.datetime.Instant.parse("2024-01-01T00:00:00Z"),
                    ),
                resolutionMetadata = DidResolutionMetadata(duration = 100L),
            )

        assertNotNull(result.document)
        assertNotNull(result.documentMetadata.created)
        assertEquals(100L, result.resolutionMetadata.duration)
    }

    @Test
    fun `test DidResolutionResult with defaults`() {
        // No resolutionMetadata passed at all — exercises NotFound's own default, which must
        // synthesize a NOT_FOUND error (§4: every Failure carries a non-null error, enforced by
        // an init check on each Failure subtype). This test previously overrode resolutionMetadata
        // with an empty one and asserted error was null — the exact shape the invariant now rejects.
        val result = DidResolutionResult.Failure.NotFound(did = Did("did:key:test"))

        assertTrue(result is DidResolutionResult.Failure.NotFound)
        assertEquals(DidErrorType.NOT_FOUND, result.resolutionMetadata.error?.type)
    }
}
