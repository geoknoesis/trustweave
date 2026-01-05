package org.trustweave.did

import org.trustweave.did.identifiers.Did
import org.trustweave.did.identifiers.VerificationMethodId
import org.trustweave.did.model.DidDocument
import org.trustweave.did.model.DidDocumentMetadata
import org.trustweave.did.model.DidService
import org.trustweave.did.model.VerificationMethod
import org.trustweave.did.resolver.DidResolutionResult
import org.trustweave.did.resolver.DidResolutionMetadata
import org.trustweave.did.exception.DidException.InvalidDidFormat
import org.junit.jupiter.api.Test
import kotlin.test.*
import kotlinx.datetime.Instant

/**
 * Branch coverage tests for Did models.
 */
class DidModelsBranchCoverageTest {

    @Test
    fun `test Did toString formats correctly`() {
        val did = Did("did:web:example.com")
        assertEquals("did:web:example.com", did.value)
    }

    @Test
    fun `test Did parse with valid DID`() {
        val did = Did("did:key:z6MkhaXgBZDvotDkL5257faiztiGiC2QtKLGpbnnEGta2doK")
        assertEquals("did:key:z6MkhaXgBZDvotDkL5257faiztiGiC2QtKLGpbnnEGta2doK", did.value)
    }

    @Test
    fun `test Did parse throws when not starting with did prefix`() {
        assertFailsWith<IllegalArgumentException> {
            Did("not-a-did")
        }
    }

    @Test
    fun `test Did parse throws when missing method`() {
        assertFailsWith<IllegalArgumentException> {
            Did("did:")
        }
    }

    @Test
    fun `test Did parse throws when missing id`() {
        // Did("did:key:") should fail validation with empty identifier
        assertFailsWith<IllegalArgumentException> {
            Did("did:key:")
        }
    }

    @Test
    fun `test Did parse with complex id`() {
        val did = Did("did:web:example.com:path:to:resource")
        assertEquals("did:web:example.com:path:to:resource", did.value)
    }

    @Test
    fun `test VerificationMethod constructor with all fields`() {
        val did = Did("did:key:123")
        val vm = VerificationMethod(
            id = VerificationMethodId.parse("did:key:123#key-1"),
            type = "Ed25519VerificationKey2020",
            controller = did,
            publicKeyJwk = mapOf("kty" to "OKP", "crv" to "Ed25519"),
            publicKeyMultibase = "z6MkhaXgBZDvotDkL5257faiztiGiC2QtKLGpbnnEGta2doK"
        )

        assertEquals("did:key:123#key-1", vm.id.value)
        assertEquals("Ed25519VerificationKey2020", vm.type)
        assertNotNull(vm.publicKeyJwk)
        assertNotNull(vm.publicKeyMultibase)
    }

    @Test
    fun `test VerificationMethod constructor with minimal fields`() {
        val did = Did("did:key:123")
        val vm = VerificationMethod(
            id = VerificationMethodId.parse("did:key:123#key-1"),
            type = "Ed25519VerificationKey2020",
            controller = did
        )

        assertNull(vm.publicKeyJwk)
        assertNull(vm.publicKeyMultibase)
    }

    @Test
    fun `test Service constructor with string endpoint`() {
        val service = DidService(
            id = "did:key:123#service-1",
            type = "LinkedDomains",
            serviceEndpoint = "https://example.com"
        )

        assertEquals("https://example.com", service.serviceEndpoint)
    }

    @Test
    fun `test Service constructor with object endpoint`() {
        val endpoint = mapOf("uri" to "https://example.com", "routingKeys" to listOf("key1"))
        val service = DidService(
            id = "did:key:123#service-1",
            type = "DIDCommMessaging",
            serviceEndpoint = endpoint
        )

        assertEquals(endpoint, service.serviceEndpoint)
    }

    @Test
    fun `test Service constructor with array endpoint`() {
        val endpoint = listOf("https://example.com", "https://backup.com")
        val service = DidService(
            id = "did:key:123#service-1",
            type = "LinkedDomains",
            serviceEndpoint = endpoint
        )

        assertEquals(endpoint, service.serviceEndpoint)
    }

    @Test
    fun `test DidDocument constructor with all fields`() {
        val did = Did("did:key:123")
        val vmId = VerificationMethodId.parse("did:key:123#key-1")
        val doc = DidDocument(
            id = did,
            alsoKnownAs = listOf(Did("did:web:example.com")),
            controller = listOf(Did("did:key:controller")),
            verificationMethod = listOf(
                VerificationMethod(
                    id = vmId,
                    type = "Ed25519VerificationKey2020",
                    controller = did
                )
            ),
            authentication = listOf(vmId),
            assertionMethod = listOf(vmId),
            keyAgreement = listOf(VerificationMethodId.parse("did:key:123#key-2")),
            service = listOf(
                DidService(
                    id = "did:key:123#service-1",
                    type = "LinkedDomains",
                    serviceEndpoint = "https://example.com"
                )
            )
        )

        assertEquals("did:key:123", doc.id.value)
        assertEquals(1, doc.alsoKnownAs.size)
        assertEquals(1, doc.verificationMethod.size)
        assertEquals(1, doc.service.size)
    }

    @Test
    fun `test DidDocument constructor with minimal fields`() {
        val doc = DidDocument(id = Did("did:key:123"))

        assertEquals("did:key:123", doc.id.value)
        assertTrue(doc.alsoKnownAs.isEmpty())
        assertTrue(doc.verificationMethod.isEmpty())
        assertTrue(doc.service.isEmpty())
    }

    @Test
    fun `test DidResolutionResult constructor with document`() {
        val doc = DidDocument(id = Did("did:key:123"))
        val result = DidResolutionResult.Success(
            document = doc,
            documentMetadata = DidDocumentMetadata(
                created = kotlinx.datetime.Instant.parse("2024-01-01T00:00:00Z")
            ),
            resolutionMetadata = DidResolutionMetadata(duration = 100L)
        )

        assertNotNull(result.document)
        assertNotNull(result.documentMetadata.created)
        assertEquals(100L, result.resolutionMetadata.duration)
    }

    @Test
    fun `test DidResolutionResult constructor without document`() {
        val result = DidResolutionResult.Failure.NotFound(
            did = Did("did:key:test"),
            resolutionMetadata = DidResolutionMetadata(
                error = "notFound",
                errorMessage = "notFound"
            )
        )

        assertTrue(result is DidResolutionResult.Failure.NotFound)
        assertEquals("notFound", result.resolutionMetadata.error)
    }

    @Test
    fun `test DidResolutionResult constructor with defaults`() {
        val doc = DidDocument(id = Did("did:key:123"))
        val result = DidResolutionResult.Success(document = doc)

        assertNotNull(result.document)
        assertNull(result.documentMetadata.created)
        assertNull(result.resolutionMetadata.error)
    }
}

