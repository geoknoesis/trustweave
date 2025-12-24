package org.trustweave.did

import org.trustweave.did.identifiers.Did
import org.trustweave.did.identifiers.VerificationMethodId
import org.trustweave.did.model.DidDocument
import org.trustweave.did.model.DidDocumentMetadata
import org.trustweave.did.model.DidService
import org.trustweave.did.model.VerificationMethod
import org.trustweave.did.resolver.DidResolutionResult
import kotlin.test.*
import kotlinx.datetime.Instant

/**
 * Comprehensive edge case tests for DID models (VerificationMethod, DidService, DidDocument, DidResolutionResult).
 */
class DidModelsEdgeCasesTest {

    @Test
    fun `test VerificationMethod with relative ID`() {
        val did = Did("did:key:issuer")
        val vm = VerificationMethod(
            id = VerificationMethodId.parse("#key-1", baseDid = did),
            type = "Ed25519VerificationKey2020",
            controller = did
        )

        assertEquals("did:key:issuer#key-1", vm.id.value)
        assertEquals("did:key:issuer", vm.controller.value)
    }

    @Test
    fun `test VerificationMethod with absolute ID`() {
        val did = Did("did:key:issuer")
        val vm = VerificationMethod(
            id = VerificationMethodId.parse("did:key:issuer#key-1"),
            type = "Ed25519VerificationKey2020",
            controller = did
        )

        assertEquals("did:key:issuer#key-1", vm.id.value)
    }

    @Test
    fun `test VerificationMethod with empty publicKeyJwk`() {
        val did = Did("did:key:issuer")
        val vm = VerificationMethod(
            id = VerificationMethodId.parse("did:key:issuer#key-1"),
            type = "Ed25519VerificationKey2020",
            controller = did,
            publicKeyJwk = emptyMap<String, Any?>()
        )

        assertNotNull(vm.publicKeyJwk)
        assertTrue(vm.publicKeyJwk!!.isEmpty())
    }

    @Test
    fun `test VerificationMethod with complex publicKeyJwk`() {
        val did = Did("did:key:issuer")
        val jwk = mapOf<String, Any?>(
            "kty" to "OKP",
            "crv" to "Ed25519",
            "x" to "base64url-encoded-public-key",
            "kid" to "key-1"
        )
        val vm = VerificationMethod(
            id = VerificationMethodId.parse("did:key:issuer#key-1"),
            type = "Ed25519VerificationKey2020",
            controller = did,
            publicKeyJwk = jwk
        )

        assertEquals(4, vm.publicKeyJwk?.size)
    }

    @Test
    fun `test Service with array endpoint`() {
        val endpoints = listOf("https://example.com", "https://backup.example.com")
        val service = DidService(
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
        val service = DidService(
            id = "did:web:example.com#service-1",
            type = "DIDCommMessaging",
            serviceEndpoint = endpoint
        )

        assertTrue(service.serviceEndpoint is Map<*, *>)
    }

    @Test
    fun `test DidDocument with multiple verification methods`() {
        val did = Did("did:key:issuer")
        val vm1 = VerificationMethod(
            id = VerificationMethodId.parse("did:key:issuer#key-1"),
            type = "Ed25519VerificationKey2020",
            controller = did
        )
        val vm2 = VerificationMethod(
            id = VerificationMethodId.parse("did:key:issuer#key-2"),
            type = "JsonWebKey2020",
            controller = did
        )

        val doc = DidDocument(
            id = did,
            verificationMethod = listOf(vm1, vm2)
        )

        assertEquals(2, doc.verificationMethod.size)
    }

    @Test
    fun `test DidDocument with multiple services`() {
        val service1 = DidService(
            id = "did:key:issuer#service-1",
            type = "LinkedDomains",
            serviceEndpoint = "https://example.com"
        )
        val service2 = DidService(
            id = "did:key:issuer#service-2",
            type = "DIDCommMessaging",
            serviceEndpoint = mapOf("uri" to "https://messaging.example.com")
        )

        val doc = DidDocument(
            id = Did("did:key:issuer"),
            service = listOf(service1, service2)
        )

        assertEquals(2, doc.service.size)
    }

    @Test
    fun `test DidDocument with multiple controllers`() {
        val doc = DidDocument(
            id = Did("did:key:issuer"),
            controller = listOf(Did("did:key:controller1"), Did("did:key:controller2"))
        )

        assertEquals(2, doc.controller.size)
    }

    @Test
    fun `test DidDocument with multiple alsoKnownAs`() {
        val doc = DidDocument(
            id = Did("did:key:issuer"),
            alsoKnownAs = listOf(
                Did("did:web:example.com"),
                Did("did:ion:example")
                // Note: "https://example.com/identity" is not a DID, so it can't be in alsoKnownAs
            )
        )

        assertEquals(2, doc.alsoKnownAs.size)
    }

    @Test
    fun `test DidDocument with all authentication methods`() {
        val doc = DidDocument(
            id = Did("did:key:issuer"),
            authentication = listOf(
                VerificationMethodId.parse("did:key:issuer#key-1"),
                VerificationMethodId.parse("did:key:issuer#key-2")
            )
        )

        assertEquals(2, doc.authentication.size)
    }

    @Test
    fun `test DidDocument with all assertion methods`() {
        val doc = DidDocument(
            id = Did("did:key:issuer"),
            assertionMethod = listOf(
                VerificationMethodId.parse("did:key:issuer#key-1"),
                VerificationMethodId.parse("did:key:issuer#key-2"),
                VerificationMethodId.parse("did:key:issuer#key-3")
            )
        )

        assertEquals(3, doc.assertionMethod.size)
    }

    @Test
    fun `test DidDocument with key agreement methods`() {
        val doc = DidDocument(
            id = Did("did:key:issuer"),
            keyAgreement = listOf(
                VerificationMethodId.parse("did:key:issuer#key-agreement-1")
            )
        )

        assertEquals(1, doc.keyAgreement.size)
    }

    @Test
    fun `test DidResolutionResult with empty metadata`() {
        val doc = DidDocument(id = Did("did:key:issuer"))
        val result = DidResolutionResult.Success(
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
        val doc = DidDocument(id = Did("did:key:issuer"))
        val result = DidResolutionResult.Success(
            document = doc,
            documentMetadata = DidDocumentMetadata(
                created = kotlinx.datetime.Instant.parse("2024-01-01T00:00:00Z"),
                updated = kotlinx.datetime.Instant.parse("2024-01-02T00:00:00Z"),
                versionId = "1",
                nextUpdate = kotlinx.datetime.Instant.parse("2024-02-01T00:00:00Z")
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
        val result = DidResolutionResult.Failure.NotFound(
            did = Did("did:key:test"),
            resolutionMetadata = mapOf(
                "error" to "notFound",
                "errorMessage" to "DID not found in registry"
            )
        )

        assertTrue(result is DidResolutionResult.Failure.NotFound)
        assertEquals("notFound", result.resolutionMetadata["error"])
    }

    @Test
    fun `test DidDocument equality`() {
        val doc1 = DidDocument(id = Did("did:key:issuer"))
        val doc2 = DidDocument(id = Did("did:key:issuer"))

        assertEquals(doc1, doc2)
    }

    @Test
    fun `test VerificationMethod equality`() {
        val did = Did("did:key:issuer")
        val vm1 = VerificationMethod(
            id = VerificationMethodId.parse("did:key:issuer#key-1"),
            type = "Ed25519VerificationKey2020",
            controller = did
        )
        val vm2 = VerificationMethod(
            id = VerificationMethodId.parse("did:key:issuer#key-1"),
            type = "Ed25519VerificationKey2020",
            controller = did
        )

        assertEquals(vm1, vm2)
    }

    @Test
    fun `test Service equality`() {
        val service1 = DidService(
            id = "did:web:example.com#service-1",
            type = "LinkedDomains",
            serviceEndpoint = "https://example.com"
        )
        val service2 = DidService(
            id = "did:web:example.com#service-1",
            type = "LinkedDomains",
            serviceEndpoint = "https://example.com"
        )

        assertEquals(service1, service2)
    }
}


