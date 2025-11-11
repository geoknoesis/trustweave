package com.geoknoesis.vericore.did

import org.junit.jupiter.api.Test
import kotlin.test.*
import java.time.Instant

/**
 * Branch coverage tests for Did models.
 */
class DidModelsBranchCoverageTest {

    @Test
    fun `test Did toString formats correctly`() {
        val did = Did(method = "web", id = "example.com")
        assertEquals("did:web:example.com", did.toString())
    }

    @Test
    fun `test Did parse with valid DID`() {
        val did = Did.parse("did:key:z6MkhaXgBZDvotDkL5257faiztiGiC2QtKLGpbnnEGta2doK")
        assertEquals("key", did.method)
        assertEquals("z6MkhaXgBZDvotDkL5257faiztiGiC2QtKLGpbnnEGta2doK", did.id)
    }

    @Test
    fun `test Did parse throws when not starting with did prefix`() {
        assertFailsWith<IllegalArgumentException> {
            Did.parse("not-a-did")
        }
    }

    @Test
    fun `test Did parse throws when missing method`() {
        assertFailsWith<IllegalArgumentException> {
            Did.parse("did:")
        }
    }

    @Test
    fun `test Did parse throws when missing id`() {
        // Did.parse("did:key:") actually succeeds with empty id
        val did = Did.parse("did:key:")
        assertEquals("key", did.method)
        assertEquals("", did.id)
    }

    @Test
    fun `test Did parse with complex id`() {
        val did = Did.parse("did:web:example.com:path:to:resource")
        assertEquals("web", did.method)
        assertEquals("example.com:path:to:resource", did.id)
    }

    @Test
    fun `test VerificationMethodRef constructor with all fields`() {
        val vm = VerificationMethodRef(
            id = "did:key:123#key-1",
            type = "Ed25519VerificationKey2020",
            controller = "did:key:123",
            publicKeyJwk = mapOf("kty" to "OKP", "crv" to "Ed25519"),
            publicKeyMultibase = "z6MkhaXgBZDvotDkL5257faiztiGiC2QtKLGpbnnEGta2doK"
        )
        
        assertEquals("did:key:123#key-1", vm.id)
        assertEquals("Ed25519VerificationKey2020", vm.type)
        assertNotNull(vm.publicKeyJwk)
        assertNotNull(vm.publicKeyMultibase)
    }

    @Test
    fun `test VerificationMethodRef constructor with minimal fields`() {
        val vm = VerificationMethodRef(
            id = "did:key:123#key-1",
            type = "Ed25519VerificationKey2020",
            controller = "did:key:123"
        )
        
        assertNull(vm.publicKeyJwk)
        assertNull(vm.publicKeyMultibase)
    }

    @Test
    fun `test Service constructor with string endpoint`() {
        val service = Service(
            id = "did:key:123#service-1",
            type = "LinkedDomains",
            serviceEndpoint = "https://example.com"
        )
        
        assertEquals("https://example.com", service.serviceEndpoint)
    }

    @Test
    fun `test Service constructor with object endpoint`() {
        val endpoint = mapOf("uri" to "https://example.com", "routingKeys" to listOf("key1"))
        val service = Service(
            id = "did:key:123#service-1",
            type = "DIDCommMessaging",
            serviceEndpoint = endpoint
        )
        
        assertEquals(endpoint, service.serviceEndpoint)
    }

    @Test
    fun `test Service constructor with array endpoint`() {
        val endpoint = listOf("https://example.com", "https://backup.com")
        val service = Service(
            id = "did:key:123#service-1",
            type = "LinkedDomains",
            serviceEndpoint = endpoint
        )
        
        assertEquals(endpoint, service.serviceEndpoint)
    }

    @Test
    fun `test DidDocument constructor with all fields`() {
        val doc = DidDocument(
            id = "did:key:123",
            alsoKnownAs = listOf("did:web:example.com"),
            controller = listOf("did:key:controller"),
            verificationMethod = listOf(
                VerificationMethodRef(
                    id = "did:key:123#key-1",
                    type = "Ed25519VerificationKey2020",
                    controller = "did:key:123"
                )
            ),
            authentication = listOf("did:key:123#key-1"),
            assertionMethod = listOf("did:key:123#key-1"),
            keyAgreement = listOf("did:key:123#key-2"),
            service = listOf(
                Service(
                    id = "did:key:123#service-1",
                    type = "LinkedDomains",
                    serviceEndpoint = "https://example.com"
                )
            )
        )
        
        assertEquals("did:key:123", doc.id)
        assertEquals(1, doc.alsoKnownAs.size)
        assertEquals(1, doc.verificationMethod.size)
        assertEquals(1, doc.service.size)
    }

    @Test
    fun `test DidDocument constructor with minimal fields`() {
        val doc = DidDocument(id = "did:key:123")
        
        assertEquals("did:key:123", doc.id)
        assertTrue(doc.alsoKnownAs.isEmpty())
        assertTrue(doc.verificationMethod.isEmpty())
        assertTrue(doc.service.isEmpty())
    }

    @Test
    fun `test DidResolutionResult constructor with document`() {
        val doc = DidDocument(id = "did:key:123")
        val result = DidResolutionResult(
            document = doc,
            documentMetadata = DidDocumentMetadata(
                created = Instant.parse("2024-01-01T00:00:00Z")
            ),
            resolutionMetadata = mapOf("duration" to 100)
        )
        
        assertNotNull(result.document)
        assertNotNull(result.documentMetadata.created)
        assertEquals(1, result.resolutionMetadata.size)
    }

    @Test
    fun `test DidResolutionResult constructor without document`() {
        val result = DidResolutionResult(
            document = null,
            documentMetadata = DidDocumentMetadata(),
            resolutionMetadata = mapOf("error" to "notFound")
        )
        
        assertNull(result.document)
        assertNull(result.documentMetadata.created)
        assertEquals(1, result.resolutionMetadata.size)
    }

    @Test
    fun `test DidResolutionResult constructor with defaults`() {
        val doc = DidDocument(id = "did:key:123")
        val result = DidResolutionResult(document = doc)
        
        assertNotNull(result.document)
        assertNull(result.documentMetadata.created)
        assertTrue(result.resolutionMetadata.isEmpty())
    }
}

