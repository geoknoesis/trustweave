package com.geoknoesis.vericore.did

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import java.time.Instant

/**
 * Comprehensive tests for DID Document with all W3C DID Core fields.
 */
class DidDocumentComprehensiveTest {
    
    @Test
    fun `test DID Document with all verification relationships`() {
        val doc = DidDocument(
            id = "did:key:test",
            context = listOf("https://www.w3.org/ns/did/v1"),
            verificationMethod = listOf(
                VerificationMethodRef(
                    id = "did:key:test#key-1",
                    type = "Ed25519VerificationKey2020",
                    controller = "did:key:test"
                )
            ),
            authentication = listOf("did:key:test#key-1"),
            assertionMethod = listOf("did:key:test#key-1"),
            keyAgreement = listOf("did:key:test#key-1"),
            capabilityInvocation = listOf("did:key:test#key-1"),
            capabilityDelegation = listOf("did:key:test#key-1")
        )
        
        assertEquals(1, doc.authentication.size)
        assertEquals(1, doc.assertionMethod.size)
        assertEquals(1, doc.keyAgreement.size)
        assertEquals(1, doc.capabilityInvocation.size)
        assertEquals(1, doc.capabilityDelegation.size)
    }
    
    @Test
    fun `test DID Document JSON-LD context handling`() {
        // Single context
        val doc1 = DidDocument(
            id = "did:key:test",
            context = listOf("https://www.w3.org/ns/did/v1")
        )
        assertEquals(1, doc1.context.size)
        
        // Multiple contexts
        val doc2 = DidDocument(
            id = "did:key:test",
            context = listOf(
                "https://www.w3.org/ns/did/v1",
                "https://example.com/context/v1"
            )
        )
        assertEquals(2, doc2.context.size)
        
        // Default context
        val doc3 = DidDocument(id = "did:key:test")
        assertEquals(1, doc3.context.size)
        assertEquals("https://www.w3.org/ns/did/v1", doc3.context[0])
    }
    
    @Test
    fun `test DID Document capability relationships`() {
        val doc = DidDocument(
            id = "did:key:test",
            capabilityInvocation = listOf(
                "did:key:test#key-1",
                "did:key:test#key-2"
            ),
            capabilityDelegation = listOf(
                "did:key:delegate#key-1",
                "did:key:delegate#key-2"
            )
        )
        
        assertEquals(2, doc.capabilityInvocation.size)
        assertEquals(2, doc.capabilityDelegation.size)
        assertTrue(doc.capabilityInvocation.contains("did:key:test#key-1"))
        assertTrue(doc.capabilityDelegation.contains("did:key:delegate#key-1"))
    }
    
    @Test
    fun `test DID Document copy with new fields`() {
        val original = DidDocument(
            id = "did:key:test",
            context = listOf("https://www.w3.org/ns/did/v1"),
            capabilityInvocation = listOf("did:key:test#key-1")
        )
        
        val copied = original.copy(
            capabilityInvocation = original.capabilityInvocation + "did:key:test#key-2",
            capabilityDelegation = listOf("did:key:delegate#key-1")
        )
        
        assertEquals(2, copied.capabilityInvocation.size)
        assertEquals(1, copied.capabilityDelegation.size)
        assertEquals(original.context, copied.context)
    }
    
    @Test
    fun `test DID Document equality with new fields`() {
        val doc1 = DidDocument(
            id = "did:key:test",
            context = listOf("https://www.w3.org/ns/did/v1"),
            capabilityInvocation = listOf("did:key:test#key-1")
        )
        
        val doc2 = DidDocument(
            id = "did:key:test",
            context = listOf("https://www.w3.org/ns/did/v1"),
            capabilityInvocation = listOf("did:key:test#key-1")
        )
        
        assertEquals(doc1, doc2)
    }
    
    @Test
    fun `test DID Document inequality with different capability relationships`() {
        val doc1 = DidDocument(
            id = "did:key:test",
            capabilityInvocation = listOf("did:key:test#key-1")
        )
        
        val doc2 = DidDocument(
            id = "did:key:test",
            capabilityInvocation = listOf("did:key:test#key-2")
        )
        
        assertFalse(doc1 == doc2)
    }
}

