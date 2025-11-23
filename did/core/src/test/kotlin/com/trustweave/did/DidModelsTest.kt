package com.trustweave.did

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.*
import java.time.Instant

/**
 * Comprehensive tests for Did models and DidRegistry.
 */
class DidModelsTest {

    @Test
    fun `test Did parse`() {
        val did = Did.parse("did:web:example.com")
        
        assertEquals("web", did.method)
        assertEquals("example.com", did.id)
        assertEquals("did:web:example.com", did.toString())
    }

    @Test
    fun `test Did parse with complex id`() {
        val did = Did.parse("did:key:z6MkhaXgBZDvotDkL5257faiztiGiC2QtKLGpbnnEGta2doK")
        
        assertEquals("key", did.method)
        assertTrue(did.id.startsWith("z6Mk"))
    }

    @Test
    fun `test Did parse fails without did prefix`() {
        assertFailsWith<IllegalArgumentException> {
            Did.parse("web:example.com")
        }
    }

    @Test
    fun `test Did parse fails with invalid format`() {
        assertFailsWith<IllegalArgumentException> {
            Did.parse("did:web")
        }
    }

    @Test
    fun `test VerificationMethodRef with all fields`() {
        val vm = VerificationMethodRef(
            id = "did:key:issuer#key-1",
            type = "Ed25519VerificationKey2020",
            controller = "did:key:issuer",
            publicKeyJwk = mapOf("kty" to "OKP", "crv" to "Ed25519"),
            publicKeyMultibase = "z6MkhaXgBZDvotDkL5257faiztiGiC2QtKLGpbnnEGta2doK"
        )
        
        assertEquals("did:key:issuer#key-1", vm.id)
        assertEquals("Ed25519VerificationKey2020", vm.type)
        assertEquals("did:key:issuer", vm.controller)
        assertNotNull(vm.publicKeyJwk)
        assertNotNull(vm.publicKeyMultibase)
    }

    @Test
    fun `test VerificationMethodRef with defaults`() {
        val vm = VerificationMethodRef(
            id = "did:key:issuer#key-1",
            type = "Ed25519VerificationKey2020",
            controller = "did:key:issuer"
        )
        
        assertNull(vm.publicKeyJwk)
        assertNull(vm.publicKeyMultibase)
    }

    @Test
    fun `test Service with URL endpoint`() {
        val service = Service(
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
        val service = Service(
            id = "did:web:example.com#service-1",
            type = "DIDCommMessaging",
            serviceEndpoint = endpoint
        )
        
        assertTrue(service.serviceEndpoint is Map<*, *>)
    }

    @Test
    fun `test DidDocument with all fields`() {
        val vm = VerificationMethodRef(
            id = "did:key:issuer#key-1",
            type = "Ed25519VerificationKey2020",
            controller = "did:key:issuer"
        )
        val service = Service(
            id = "did:key:issuer#service-1",
            type = "LinkedDomains",
            serviceEndpoint = "https://example.com"
        )
        
        val doc = DidDocument(
            id = "did:key:issuer",
            alsoKnownAs = listOf("did:web:example.com"),
            controller = listOf("did:key:controller"),
            verificationMethod = listOf(vm),
            authentication = listOf("did:key:issuer#key-1"),
            assertionMethod = listOf("did:key:issuer#key-1"),
            keyAgreement = listOf("did:key:issuer#key-2"),
            service = listOf(service)
        )
        
        assertEquals("did:key:issuer", doc.id)
        assertEquals(1, doc.alsoKnownAs.size)
        assertEquals(1, doc.verificationMethod.size)
        assertEquals(1, doc.service.size)
    }

    @Test
    fun `test DidDocument with defaults`() {
        val doc = DidDocument(id = "did:key:issuer")
        
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
        val doc = DidDocument(id = "did:key:issuer")
        val result = DidResolutionResult(
            document = doc,
            documentMetadata = DidDocumentMetadata(
                created = Instant.parse("2024-01-01T00:00:00Z")
            ),
            resolutionMetadata = mapOf("duration" to 100L)
        )
        
        assertNotNull(result.document)
        assertNotNull(result.documentMetadata.created)
        assertEquals(1, result.resolutionMetadata.size)
    }

    @Test
    fun `test DidResolutionResult with defaults`() {
        val result = DidResolutionResult(document = null)
        
        assertNull(result.document)
        assertNull(result.documentMetadata.created)
        assertTrue(result.resolutionMetadata.isEmpty())
    }
}


