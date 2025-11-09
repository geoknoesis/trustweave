package io.geoknoesis.vericore.did

import kotlin.test.*
import java.time.Instant

/**
 * Comprehensive edge case tests for DID models (VerificationMethodRef, Service, DidDocument, DidResolutionResult).
 */
class DidModelsEdgeCasesTest {

    @Test
    fun `test VerificationMethodRef with relative ID`() {
        val vm = VerificationMethodRef(
            id = "#key-1",
            type = "Ed25519VerificationKey2020",
            controller = "did:key:issuer"
        )
        
        assertEquals("#key-1", vm.id)
        assertEquals("did:key:issuer", vm.controller)
    }

    @Test
    fun `test VerificationMethodRef with absolute ID`() {
        val vm = VerificationMethodRef(
            id = "did:key:issuer#key-1",
            type = "Ed25519VerificationKey2020",
            controller = "did:key:issuer"
        )
        
        assertEquals("did:key:issuer#key-1", vm.id)
    }

    @Test
    fun `test VerificationMethodRef with empty publicKeyJwk`() {
        val vm = VerificationMethodRef(
            id = "did:key:issuer#key-1",
            type = "Ed25519VerificationKey2020",
            controller = "did:key:issuer",
            publicKeyJwk = emptyMap()
        )
        
        assertNotNull(vm.publicKeyJwk)
        assertTrue(vm.publicKeyJwk!!.isEmpty())
    }

    @Test
    fun `test VerificationMethodRef with complex publicKeyJwk`() {
        val jwk = mapOf(
            "kty" to "OKP",
            "crv" to "Ed25519",
            "x" to "base64url-encoded-public-key",
            "kid" to "key-1"
        )
        val vm = VerificationMethodRef(
            id = "did:key:issuer#key-1",
            type = "Ed25519VerificationKey2020",
            controller = "did:key:issuer",
            publicKeyJwk = jwk
        )
        
        assertEquals(4, vm.publicKeyJwk?.size)
    }

    @Test
    fun `test Service with array endpoint`() {
        val endpoints = listOf("https://example.com", "https://backup.example.com")
        val service = Service(
            id = "did:web:example.com#service-1",
            type = "LinkedDomains",
            serviceEndpoint = endpoints
        )
        
        assertTrue(service.serviceEndpoint is List<*>)
    }

    @Test
    fun `test Service with nested object endpoint`() {
        val endpoint = mapOf(
            "uri" to "https://example.com",
            "routingKeys" to listOf("key1", "key2"),
            "accept" to listOf("didcomm/v2", "didcomm/aip2;env=rfc587")
        )
        val service = Service(
            id = "did:web:example.com#service-1",
            type = "DIDCommMessaging",
            serviceEndpoint = endpoint
        )
        
        assertTrue(service.serviceEndpoint is Map<*, *>)
    }

    @Test
    fun `test DidDocument with multiple verification methods`() {
        val vm1 = VerificationMethodRef(
            id = "did:key:issuer#key-1",
            type = "Ed25519VerificationKey2020",
            controller = "did:key:issuer"
        )
        val vm2 = VerificationMethodRef(
            id = "did:key:issuer#key-2",
            type = "JsonWebKey2020",
            controller = "did:key:issuer"
        )
        
        val doc = DidDocument(
            id = "did:key:issuer",
            verificationMethod = listOf(vm1, vm2)
        )
        
        assertEquals(2, doc.verificationMethod.size)
    }

    @Test
    fun `test DidDocument with multiple services`() {
        val service1 = Service(
            id = "did:key:issuer#service-1",
            type = "LinkedDomains",
            serviceEndpoint = "https://example.com"
        )
        val service2 = Service(
            id = "did:key:issuer#service-2",
            type = "DIDCommMessaging",
            serviceEndpoint = mapOf("uri" to "https://messaging.example.com")
        )
        
        val doc = DidDocument(
            id = "did:key:issuer",
            service = listOf(service1, service2)
        )
        
        assertEquals(2, doc.service.size)
    }

    @Test
    fun `test DidDocument with multiple controllers`() {
        val doc = DidDocument(
            id = "did:key:issuer",
            controller = listOf("did:key:controller1", "did:key:controller2")
        )
        
        assertEquals(2, doc.controller.size)
    }

    @Test
    fun `test DidDocument with multiple alsoKnownAs`() {
        val doc = DidDocument(
            id = "did:key:issuer",
            alsoKnownAs = listOf(
                "did:web:example.com",
                "did:ion:example",
                "https://example.com/identity"
            )
        )
        
        assertEquals(3, doc.alsoKnownAs.size)
    }

    @Test
    fun `test DidDocument with all authentication methods`() {
        val doc = DidDocument(
            id = "did:key:issuer",
            authentication = listOf(
                "did:key:issuer#key-1",
                "did:key:issuer#key-2"
            )
        )
        
        assertEquals(2, doc.authentication.size)
    }

    @Test
    fun `test DidDocument with all assertion methods`() {
        val doc = DidDocument(
            id = "did:key:issuer",
            assertionMethod = listOf(
                "did:key:issuer#key-1",
                "did:key:issuer#key-2",
                "did:key:issuer#key-3"
            )
        )
        
        assertEquals(3, doc.assertionMethod.size)
    }

    @Test
    fun `test DidDocument with key agreement methods`() {
        val doc = DidDocument(
            id = "did:key:issuer",
            keyAgreement = listOf(
                "did:key:issuer#key-agreement-1"
            )
        )
        
        assertEquals(1, doc.keyAgreement.size)
    }

    @Test
    fun `test DidResolutionResult with empty metadata`() {
        val doc = DidDocument(id = "did:key:issuer")
        val result = DidResolutionResult(
            document = doc,
            documentMetadata = DidDocumentMetadata(),
            resolutionMetadata = emptyMap()
        )
        
        assertNotNull(result.document)
        assertNull(result.documentMetadata.created)
        assertTrue(result.resolutionMetadata.isEmpty())
    }

    @Test
    fun `test DidResolutionResult with complex metadata`() {
        val doc = DidDocument(id = "did:key:issuer")
        val result = DidResolutionResult(
            document = doc,
            documentMetadata = DidDocumentMetadata(
                created = Instant.parse("2024-01-01T00:00:00Z"),
                updated = Instant.parse("2024-01-02T00:00:00Z"),
                versionId = "1",
                nextUpdate = Instant.parse("2024-02-01T00:00:00Z")
            ),
            resolutionMetadata = mapOf(
                "duration" to 150L,
                "cached" to true,
                "cacheExpires" to "2024-01-01T01:00:00Z",
                "resolver" to "did-resolver-v1"
            )
        )
        
        assertNotNull(result.documentMetadata.created)
        assertNotNull(result.documentMetadata.updated)
        assertEquals("1", result.documentMetadata.versionId)
        assertEquals(4, result.resolutionMetadata.size)
    }

    @Test
    fun `test DidResolutionResult with null document and error metadata`() {
        val result = DidResolutionResult(
            document = null,
            resolutionMetadata = mapOf(
                "error" to "notFound",
                "errorMessage" to "DID not found in registry"
            )
        )
        
        assertNull(result.document)
        assertEquals("notFound", result.resolutionMetadata["error"])
    }

    @Test
    fun `test DidDocument equality`() {
        val doc1 = DidDocument(id = "did:key:issuer")
        val doc2 = DidDocument(id = "did:key:issuer")
        
        assertEquals(doc1, doc2)
    }

    @Test
    fun `test VerificationMethodRef equality`() {
        val vm1 = VerificationMethodRef(
            id = "did:key:issuer#key-1",
            type = "Ed25519VerificationKey2020",
            controller = "did:key:issuer"
        )
        val vm2 = VerificationMethodRef(
            id = "did:key:issuer#key-1",
            type = "Ed25519VerificationKey2020",
            controller = "did:key:issuer"
        )
        
        assertEquals(vm1, vm2)
    }

    @Test
    fun `test Service equality`() {
        val service1 = Service(
            id = "did:web:example.com#service-1",
            type = "LinkedDomains",
            serviceEndpoint = "https://example.com"
        )
        val service2 = Service(
            id = "did:web:example.com#service-1",
            type = "LinkedDomains",
            serviceEndpoint = "https://example.com"
        )
        
        assertEquals(service1, service2)
    }
}


