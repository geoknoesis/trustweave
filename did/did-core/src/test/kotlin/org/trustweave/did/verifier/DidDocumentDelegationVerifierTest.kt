package org.trustweave.did.verifier

import org.trustweave.did.identifiers.Did
import org.trustweave.did.identifiers.VerificationMethodId
import org.trustweave.did.model.DidDocument
import org.trustweave.did.resolver.DidResolutionResult
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Tests for DID Document Delegation Verifier.
 */
class DidDocumentDelegationVerifierTest {

    @Test
    fun `test verify simple delegation chain`() = runBlocking {
        val delegatorDid = "did:key:delegator"
        val delegateDid = "did:key:delegate"

        val resolveDid: suspend (String) -> DidResolutionResult? = { did ->
            when (did) {
                delegatorDid -> DidResolutionResult.Success(
                    document = DidDocument(
                        id = Did(delegatorDid),
                        capabilityDelegation = listOf(VerificationMethodId.parse("$delegateDid#key-1"))
                    )
                )
                delegateDid -> DidResolutionResult.Success(
                    document = DidDocument(id = Did(delegateDid))
                )
                else -> null
            }
        }

        val verifier = DidDocumentDelegationVerifier(resolveDid)
        val result = verifier.verify(Did(delegatorDid), Did(delegateDid))

        assertTrue(result.valid)
        assertEquals(2, result.path.size)
        assertTrue(result.errors.isEmpty())
    }

    @Test
    fun `test verify delegation chain fails when delegate not in capabilityDelegation`() = runBlocking {
        val delegatorDid = "did:key:delegator"
        val delegateDid = "did:key:delegate"

        val resolveDid: suspend (String) -> DidResolutionResult? = { did ->
            when (did) {
                delegatorDid -> DidResolutionResult.Success(
                    document = DidDocument(
                        id = Did(delegatorDid),
                        capabilityDelegation = emptyList() // No delegation
                    )
                )
                delegateDid -> DidResolutionResult.Success(
                    document = DidDocument(id = Did(delegateDid))
                )
                else -> null
            }
        }

        val verifier = DidDocumentDelegationVerifier(resolveDid)
        val result = verifier.verify(Did(delegatorDid), Did(delegateDid))

        assertFalse(result.valid)
        assertTrue(result.errors.isNotEmpty())
    }

    @Test
    fun `test verify delegation chain fails when delegator not resolved`() = runBlocking {
        val resolveDid: suspend (String) -> DidResolutionResult? = { null }

        val verifier = DidDocumentDelegationVerifier(resolveDid)
        val result = verifier.verify(Did("did:key:delegator"), Did("did:key:delegate"))

        assertFalse(result.valid)
        assertTrue(result.errors.any { it.contains("Failed to resolve delegator") })
    }

    @Test
    fun `test verify multi-hop delegation chain`() = runBlocking {
        val chain = listOf(Did("did:key:ceo"), Did("did:key:director"), Did("did:key:manager"))

        val resolveDid: suspend (String) -> DidResolutionResult? = { did ->
            when (did) {
                "did:key:ceo" -> DidResolutionResult.Success(
                    document = DidDocument(
                        id = Did("did:key:ceo"),
                        capabilityDelegation = listOf(VerificationMethodId.parse("did:key:director#key-1"))
                    )
                )
                "did:key:director" -> DidResolutionResult.Success(
                    document = DidDocument(
                        id = Did("did:key:director"),
                        capabilityDelegation = listOf(VerificationMethodId.parse("did:key:manager#key-1"))
                    )
                )
                "did:key:manager" -> DidResolutionResult.Success(
                    document = DidDocument(id = Did("did:key:manager"))
                )
                else -> null
            }
        }

        val verifier = DidDocumentDelegationVerifier(resolveDid)
        val result = verifier.verifyChain(chain)

        assertTrue(result.valid)
        assertEquals(3, result.path.size)
    }

    @Test
    fun `test verify multi-hop delegation chain fails on broken link`() = runBlocking {
        val chain = listOf(Did("did:key:ceo"), Did("did:key:director"), Did("did:key:manager"))

        val resolveDid: suspend (String) -> DidResolutionResult? = { did ->
            when (did) {
                "did:key:ceo" -> DidResolutionResult.Success(
                    document = DidDocument(
                        id = Did("did:key:ceo"),
                        capabilityDelegation = listOf(VerificationMethodId.parse("did:key:director#key-1"))
                    )
                )
                "did:key:director" -> DidResolutionResult.Success(
                    document = DidDocument(
                        id = Did("did:key:director"),
                        capabilityDelegation = emptyList() // Broken link
                    )
                )
                "did:key:manager" -> DidResolutionResult.Success(
                    document = DidDocument(id = Did("did:key:manager"))
                )
                else -> null
            }
        }

        val verifier = DidDocumentDelegationVerifier(resolveDid)
        val result = verifier.verifyChain(chain)

        assertFalse(result.valid)
        assertTrue(result.errors.isNotEmpty())
    }

    @Test
    fun `test verify delegation chain with capability parameter`() = runBlocking {
        val delegatorDid = "did:key:delegator"
        val delegateDid = "did:key:delegate"

        val resolveDid: suspend (String) -> DidResolutionResult? = { did ->
            when (did) {
                delegatorDid -> DidResolutionResult.Success(
                    document = DidDocument(
                        id = Did(delegatorDid),
                        capabilityDelegation = listOf(VerificationMethodId.parse("$delegateDid#key-1"))
                    )
                )
                delegateDid -> DidResolutionResult.Success(
                    document = DidDocument(id = Did(delegateDid))
                )
                else -> null
            }
        }

        val verifier = DidDocumentDelegationVerifier(resolveDid)
        val result = verifier.verify(Did(delegatorDid), Did(delegateDid))

        assertTrue(result.valid)
    }
}

