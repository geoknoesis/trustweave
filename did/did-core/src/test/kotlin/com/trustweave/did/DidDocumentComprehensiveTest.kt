package com.trustweave.did

import com.trustweave.did.identifiers.Did
import com.trustweave.did.identifiers.VerificationMethodId
import com.trustweave.did.model.DidDocument
import com.trustweave.did.model.VerificationMethod
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Comprehensive tests for DID Document with all W3C DID Core fields.
 */
class DidDocumentComprehensiveTest {

    @Test
    fun `test DID Document with all verification relationships`() {
        val did = Did("did:key:test")
        val vmId = VerificationMethodId.parse("did:key:test#key-1")
        val doc = DidDocument(
            id = did,
            context = listOf("https://www.w3.org/ns/did/v1"),
            verificationMethod = listOf(
                VerificationMethod(
                    id = vmId,
                    type = "Ed25519VerificationKey2020",
                    controller = did
                )
            ),
            authentication = listOf(vmId),
            assertionMethod = listOf(vmId),
            keyAgreement = listOf(vmId),
            capabilityInvocation = listOf(vmId),
            capabilityDelegation = listOf(vmId)
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
            id = Did("did:key:test"),
            context = listOf("https://www.w3.org/ns/did/v1")
        )
        assertEquals(1, doc1.context.size)

        // Multiple contexts
        val doc2 = DidDocument(
            id = Did("did:key:test"),
            context = listOf(
                "https://www.w3.org/ns/did/v1",
                "https://example.com/context/v1"
            )
        )
        assertEquals(2, doc2.context.size)

        // Default context
        val doc3 = DidDocument(id = Did("did:key:test"))
        assertEquals(1, doc3.context.size)
        assertEquals("https://www.w3.org/ns/did/v1", doc3.context[0])
    }

    @Test
    fun `test DID Document capability relationships`() {
        val did = Did("did:key:test")
        val delegateDid = Did("did:key:delegate")
        val doc = DidDocument(
            id = did,
            capabilityInvocation = listOf(
                VerificationMethodId.parse("did:key:test#key-1"),
                VerificationMethodId.parse("did:key:test#key-2")
            ),
            capabilityDelegation = listOf(
                VerificationMethodId.parse("did:key:delegate#key-1"),
                VerificationMethodId.parse("did:key:delegate#key-2")
            )
        )

        assertEquals(2, doc.capabilityInvocation.size)
        assertEquals(2, doc.capabilityDelegation.size)
        assertTrue(doc.capabilityInvocation.any { it.value == "did:key:test#key-1" })
        assertTrue(doc.capabilityDelegation.any { it.value == "did:key:delegate#key-1" })
    }

    @Test
    fun `test DID Document copy with new fields`() {
        val did = Did("did:key:test")
        val original = DidDocument(
            id = did,
            context = listOf("https://www.w3.org/ns/did/v1"),
            capabilityInvocation = listOf(VerificationMethodId.parse("did:key:test#key-1"))
        )

        val copied = original.copy(
            capabilityInvocation = original.capabilityInvocation + VerificationMethodId.parse("did:key:test#key-2"),
            capabilityDelegation = listOf(VerificationMethodId.parse("did:key:delegate#key-1"))
        )

        assertEquals(2, copied.capabilityInvocation.size)
        assertEquals(1, copied.capabilityDelegation.size)
        assertEquals(original.context, copied.context)
    }

    @Test
    fun `test DID Document equality with new fields`() {
        val did = Did("did:key:test")
        val vmId = VerificationMethodId.parse("did:key:test#key-1")
        val doc1 = DidDocument(
            id = did,
            context = listOf("https://www.w3.org/ns/did/v1"),
            capabilityInvocation = listOf(vmId)
        )

        val doc2 = DidDocument(
            id = did,
            context = listOf("https://www.w3.org/ns/did/v1"),
            capabilityInvocation = listOf(vmId)
        )

        assertEquals(doc1, doc2)
    }

    @Test
    fun `test DID Document inequality with different capability relationships`() {
        val did = Did("did:key:test")
        val doc1 = DidDocument(
            id = did,
            capabilityInvocation = listOf(VerificationMethodId.parse("did:key:test#key-1"))
        )

        val doc2 = DidDocument(
            id = did,
            capabilityInvocation = listOf(VerificationMethodId.parse("did:key:test#key-2"))
        )

        assertFalse(doc1 == doc2)
    }
}

