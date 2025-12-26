package org.trustweave.did.util

import org.junit.jupiter.api.Test
import org.trustweave.did.identifiers.Did
import org.trustweave.did.identifiers.VerificationMethodId
import org.trustweave.did.model.DidDocument
import org.trustweave.did.model.DidService
import org.trustweave.did.model.VerificationMethod
import kotlin.test.*

/**
 * Tests for DidUtils utility functions.
 */
class DidUtilsTest {

    @Test
    fun `test normalizeKeyId with full DID URL`() {
        val result = normalizeKeyId("did:key:z6Mk...#key-1")
        assertEquals("key-1", result)
    }

    @Test
    fun `test normalizeKeyId with fragment only`() {
        val result = normalizeKeyId("#key-1")
        assertEquals("key-1", result)
    }

    @Test
    fun `test normalizeKeyId with plain key ID`() {
        val result = normalizeKeyId("key-1")
        assertEquals("key-1", result)
    }

    @Test
    fun `test normalizeKeyId with DID without fragment`() {
        val result = normalizeKeyId("did:key:z6Mk...")
        assertEquals("did:key:z6Mk...", result)
    }

    @Test
    fun `test normalizeKeyId with empty string`() {
        val result = normalizeKeyId("")
        assertEquals("", result)
    }

    @Test
    fun `test normalizeKeyId with fragment at end`() {
        val result = normalizeKeyId("did:key:123#")
        assertEquals("did:key:123#", result)
    }

    @Test
    fun `test validateDid with valid DID`() {
        assertTrue(validateDid("did:key:123"))
        assertTrue(validateDid("did:web:example.com"))
    }

    @Test
    fun `test validateDid with invalid DID`() {
        assertFalse(validateDid("invalid"))
        assertFalse(validateDid(""))
        assertFalse(validateDid("not-a-did"))
    }

    @Test
    fun `test parseDidOrNull with valid DID`() {
        val did = parseDidOrNull("did:key:123")
        assertNotNull(did)
        assertEquals("did:key:123", did.value)
    }

    @Test
    fun `test parseDidOrNull with invalid DID`() {
        val did = parseDidOrNull("invalid")
        assertNull(did)
    }

    @Test
    fun `test extractAllVerificationMethodIds`() {
        val did = Did("did:test:123")
        val vmId1 = VerificationMethodId(did, org.trustweave.core.identifiers.KeyId("#key-1"))
        val vmId2 = VerificationMethodId(did, org.trustweave.core.identifiers.KeyId("#key-2"))
        
        val document = DidDocument(
            id = did,
            verificationMethod = listOf(
                VerificationMethod(vmId1, "Ed25519VerificationKey2020", did)
            ),
            authentication = listOf(vmId1, vmId2),
            assertionMethod = listOf(vmId2)
        )

        val allVmIds = extractAllVerificationMethodIds(document)
        assertEquals(2, allVmIds.size)
        assertTrue(allVmIds.contains(vmId1))
        assertTrue(allVmIds.contains(vmId2))
    }

    @Test
    fun `test findVerificationMethod`() {
        val did = Did("did:test:123")
        val vmId = VerificationMethodId(did, org.trustweave.core.identifiers.KeyId("#key-1"))
        
        val vm = VerificationMethod(
            id = vmId,
            type = "Ed25519VerificationKey2020",
            controller = did
        )
        
        val document = DidDocument(
            id = did,
            verificationMethod = listOf(vm)
        )

        val found = findVerificationMethod(document, vmId)
        assertNotNull(found)
        assertEquals(vmId, found.id)
    }

    @Test
    fun `test findVerificationMethod not found`() {
        val did = Did("did:test:123")
        val vmId = VerificationMethodId(did, org.trustweave.core.identifiers.KeyId("#key-1"))
        
        val document = DidDocument(id = did)

        val found = findVerificationMethod(document, vmId)
        assertNull(found)
    }

    @Test
    fun `test hasVerificationMethod`() {
        val did = Did("did:test:123")
        val vmId = VerificationMethodId(did, org.trustweave.core.identifiers.KeyId("#key-1"))
        
        val document = DidDocument(
            id = did,
            verificationMethod = listOf(
                VerificationMethod(vmId, "Ed25519VerificationKey2020", did)
            )
        )

        assertTrue(hasVerificationMethod(document, vmId))
    }

    @Test
    fun `test hasVerificationMethod false`() {
        val did = Did("did:test:123")
        val vmId = VerificationMethodId(did, org.trustweave.core.identifiers.KeyId("#key-1"))
        
        val document = DidDocument(id = did)

        assertFalse(hasVerificationMethod(document, vmId))
    }

    @Test
    fun `test getServicesByType`() {
        val did = Did("did:test:123")
        val service1 = DidService("service-1", "LinkedDomains", "https://example.com")
        val service2 = DidService("service-2", "DIDCommMessaging", mapOf("uri" to "https://messaging.com"))
        val service3 = DidService("service-3", "LinkedDomains", "https://another.com")
        
        val document = DidDocument(
            id = did,
            service = listOf(service1, service2, service3)
        )

        val linkedDomains = getServicesByType(document, "LinkedDomains")
        assertEquals(2, linkedDomains.size)
        assertTrue(linkedDomains.contains(service1))
        assertTrue(linkedDomains.contains(service3))
    }

    @Test
    fun `test getServicesByType empty result`() {
        val did = Did("did:test:123")
        val document = DidDocument(id = did)

        val services = getServicesByType(document, "NonExistentType")
        assertTrue(services.isEmpty())
    }

    @Test
    fun `test hasServiceType`() {
        val did = Did("did:test:123")
        val document = DidDocument(
            id = did,
            service = listOf(
                DidService("service-1", "LinkedDomains", "https://example.com")
            )
        )

        assertTrue(hasServiceType(document, "LinkedDomains"))
        assertFalse(hasServiceType(document, "DIDCommMessaging"))
    }

    @Test
    fun `test hasServiceType false`() {
        val did = Did("did:test:123")
        val document = DidDocument(id = did)

        assertFalse(hasServiceType(document, "LinkedDomains"))
    }
}

