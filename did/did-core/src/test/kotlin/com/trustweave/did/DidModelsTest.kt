package com.trustweave.did

import com.trustweave.did.identifiers.Did
import com.trustweave.did.identifiers.VerificationMethodId
import com.trustweave.did.model.DidDocument
import com.trustweave.did.model.DidDocumentMetadata
import com.trustweave.did.model.DidService
import com.trustweave.did.model.VerificationMethod
import com.trustweave.did.resolver.DidResolutionResult
import com.trustweave.did.exception.DidException.InvalidDidFormat
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.*
import kotlinx.datetime.Instant

/**
 * Comprehensive tests for Did models and DidRegistry.
 */
class DidModelsTest {

    // Basic Did constructor tests - comprehensive coverage in DidModelsBranchCoverageTest

    @Test
    fun `test VerificationMethod with all fields`() {
        val did = Did("did:key:issuer")
        val vm = VerificationMethod(
            id = VerificationMethodId.parse("did:key:issuer#key-1"),
            type = "Ed25519VerificationKey2020",
            controller = did,
            publicKeyJwk = mapOf<String, Any?>("kty" to "OKP", "crv" to "Ed25519"),
            publicKeyMultibase = "z6MkhaXgBZDvotDkL5257faiztiGiC2QtKLGpbnnEGta2doK"
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
        val vm = VerificationMethod(
            id = VerificationMethodId.parse("did:key:issuer#key-1"),
            type = "Ed25519VerificationKey2020",
            controller = did
        )

        assertNull(vm.publicKeyJwk)
        assertNull(vm.publicKeyMultibase)
    }

    @Test
    fun `test Service with URL endpoint`() {
        val service = DidService(
            id = "did:web:example.com#service-1",
            type = "LinkedDomains",
            serviceEndpoint = "https://example.com"
        )

        assertEquals("did:web:example.com#service-1", service.id)
        assertEquals("LinkedDomains", service.type)
        assertEquals("https://example.com", service.serviceEndpoint)
    }

    @Test
    fun `test Service with object endpoint`() {
        val endpoint = mapOf("uri" to "https://example.com", "routingKeys" to listOf("key1"))
        val service = DidService(
            id = "did:web:example.com#service-1",
            type = "DIDCommMessaging",
            serviceEndpoint = endpoint
        )

        assertTrue(service.serviceEndpoint is Map<*, *>)
    }

    @Test
    fun `test DidDocument with all fields`() {
        val did = Did("did:key:issuer")
        val vmId = VerificationMethodId.parse("did:key:issuer#key-1")
        val vm = VerificationMethod(
            id = vmId,
            type = "Ed25519VerificationKey2020",
            controller = did
        )
        val service = DidService(
            id = "did:key:issuer#service-1",
            type = "LinkedDomains",
            serviceEndpoint = "https://example.com"
        )

        val doc = DidDocument(
            id = did,
            alsoKnownAs = listOf(Did("did:web:example.com")),
            controller = listOf(Did("did:key:controller")),
            verificationMethod = listOf(vm),
            authentication = listOf(vmId),
            assertionMethod = listOf(vmId),
            keyAgreement = listOf(VerificationMethodId.parse("did:key:issuer#key-2")),
            service = listOf(service)
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
        val result = DidResolutionResult.Success(
            document = doc,
            documentMetadata = DidDocumentMetadata(
                created = kotlinx.datetime.Instant.parse("2024-01-01T00:00:00Z")
            ),
            resolutionMetadata = mapOf("duration" to 100L)
        )

        assertNotNull(result.document)
        assertNotNull(result.documentMetadata.created)
        assertEquals(1, result.resolutionMetadata.size)
    }

    @Test
    fun `test DidResolutionResult with defaults`() {
        val result = DidResolutionResult.Failure.NotFound(
            did = Did("did:key:test"),
            resolutionMetadata = emptyMap()
        )

        assertTrue(result is DidResolutionResult.Failure.NotFound)
        assertTrue(result.resolutionMetadata.isEmpty())
    }
}


