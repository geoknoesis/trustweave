package org.trustweave.did

import org.trustweave.did.identifiers.Did
import org.trustweave.did.identifiers.VerificationMethodId
import org.trustweave.did.model.DidDocument
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Tests for extended DID Document with new W3C DID Core fields.
 */
class DidDocumentExtendedTest {

    @Test
    fun `test DID Document with context field`() {
        val doc = DidDocument(
            id = Did("did:key:test"),
            context = listOf("https://www.w3.org/ns/did/v1", "https://example.com/context/v1")
        )

        assertEquals(2, doc.context.size)
        assertTrue(doc.context.contains("https://www.w3.org/ns/did/v1"))
        assertTrue(doc.context.contains("https://example.com/context/v1"))
    }

    @Test
    fun `test DID Document with default context`() {
        val doc = DidDocument(id = Did("did:key:test"))

        assertEquals(1, doc.context.size)
        assertEquals("https://www.w3.org/ns/did/v1", doc.context[0])
    }

    @Test
    fun `test DID Document with capability invocation`() {
        val doc = DidDocument(
            id = Did("did:key:test"),
            capabilityInvocation = listOf(
                VerificationMethodId.parse("did:key:test#key-1"),
                VerificationMethodId.parse("did:key:test#key-2")
            )
        )

        assertEquals(2, doc.capabilityInvocation.size)
        assertTrue(doc.capabilityInvocation.any { it.value == "did:key:test#key-1" })
        assertTrue(doc.capabilityInvocation.any { it.value == "did:key:test#key-2" })
    }

    @Test
    fun `test DID Document with capability delegation`() {
        val doc = DidDocument(
            id = Did("did:key:test"),
            capabilityDelegation = listOf(
                VerificationMethodId.parse("did:key:test#key-1"),
                VerificationMethodId.parse("did:key:delegate#key-1")
            )
        )

        assertEquals(2, doc.capabilityDelegation.size)
        assertTrue(doc.capabilityDelegation.any { it.value == "did:key:test#key-1" })
        assertTrue(doc.capabilityDelegation.any { it.value == "did:key:delegate#key-1" })
    }

    @Test
    fun `test DID Document with all new fields`() {
        val doc = DidDocument(
            id = Did("did:key:test"),
            context = listOf("https://www.w3.org/ns/did/v1"),
            capabilityInvocation = listOf(VerificationMethodId.parse("did:key:test#key-1")),
            capabilityDelegation = listOf(VerificationMethodId.parse("did:key:test#key-2")),
            authentication = listOf(VerificationMethodId.parse("did:key:test#key-1")),
            assertionMethod = listOf(VerificationMethodId.parse("did:key:test#key-1")),
            keyAgreement = listOf(VerificationMethodId.parse("did:key:test#key-3"))
        )

        assertNotNull(doc.context)
        assertNotNull(doc.capabilityInvocation)
        assertNotNull(doc.capabilityDelegation)
        assertEquals(1, doc.capabilityInvocation.size)
        assertEquals(1, doc.capabilityDelegation.size)
    }

    @Test
    fun `test DID Document backward compatibility`() {
        // Test that existing code still works with default values
        val doc = DidDocument(
            id = Did("did:key:test"),
            authentication = listOf(VerificationMethodId.parse("did:key:test#key-1"))
        )

        // New fields should have defaults
        assertEquals(1, doc.context.size)
        assertEquals("https://www.w3.org/ns/did/v1", doc.context[0])
        assertTrue(doc.capabilityInvocation.isEmpty())
        assertTrue(doc.capabilityDelegation.isEmpty())

        // Existing fields should work
        assertEquals(1, doc.authentication.size)
    }
}

