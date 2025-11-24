package com.trustweave.did.verifier

import com.trustweave.did.DidDocument
import com.trustweave.did.resolver.DidResolutionResult
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
                delegatorDid -> DidResolutionResult(
                    document = DidDocument(
                        id = delegatorDid,
                        capabilityDelegation = listOf("$delegateDid#key-1")
                    )
                )
                delegateDid -> DidResolutionResult(
                    document = DidDocument(id = delegateDid)
                )
                else -> null
            }
        }
        
        val verifier = DidDocumentDelegationVerifier(resolveDid)
        val result = verifier.verify(delegatorDid, delegateDid)
        
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
                delegatorDid -> DidResolutionResult(
                    document = DidDocument(
                        id = delegatorDid,
                        capabilityDelegation = emptyList() // No delegation
                    )
                )
                delegateDid -> DidResolutionResult(
                    document = DidDocument(id = delegateDid)
                )
                else -> null
            }
        }
        
        val verifier = DidDocumentDelegationVerifier(resolveDid)
        val result = verifier.verify(delegatorDid, delegateDid)
        
        assertFalse(result.valid)
        assertTrue(result.errors.isNotEmpty())
    }
    
    @Test
    fun `test verify delegation chain fails when delegator not resolved`() = runBlocking {
        val resolveDid: suspend (String) -> DidResolutionResult? = { null }
        
        val verifier = DidDocumentDelegationVerifier(resolveDid)
        val result = verifier.verify("did:key:delegator", "did:key:delegate")
        
        assertFalse(result.valid)
        assertTrue(result.errors.any { it.contains("Failed to resolve delegator") })
    }
    
    @Test
    fun `test verify multi-hop delegation chain`() = runBlocking {
        val chain = listOf("did:key:ceo", "did:key:director", "did:key:manager")
        
        val resolveDid: suspend (String) -> DidResolutionResult? = { did ->
            when (did) {
                "did:key:ceo" -> DidResolutionResult(
                    document = DidDocument(
                        id = "did:key:ceo",
                        capabilityDelegation = listOf("did:key:director#key-1")
                    )
                )
                "did:key:director" -> DidResolutionResult(
                    document = DidDocument(
                        id = "did:key:director",
                        capabilityDelegation = listOf("did:key:manager#key-1")
                    )
                )
                "did:key:manager" -> DidResolutionResult(
                    document = DidDocument(id = "did:key:manager")
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
        val chain = listOf("did:key:ceo", "did:key:director", "did:key:manager")
        
        val resolveDid: suspend (String) -> DidResolutionResult? = { did ->
            when (did) {
                "did:key:ceo" -> DidResolutionResult(
                    document = DidDocument(
                        id = "did:key:ceo",
                        capabilityDelegation = listOf("did:key:director#key-1")
                    )
                )
                "did:key:director" -> DidResolutionResult(
                    document = DidDocument(
                        id = "did:key:director",
                        capabilityDelegation = emptyList() // Broken link
                    )
                )
                "did:key:manager" -> DidResolutionResult(
                    document = DidDocument(id = "did:key:manager")
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
                delegatorDid -> DidResolutionResult(
                    document = DidDocument(
                        id = delegatorDid,
                        capabilityDelegation = listOf("$delegateDid#key-1")
                    )
                )
                delegateDid -> DidResolutionResult(
                    document = DidDocument(id = delegateDid)
                )
                else -> null
            }
        }
        
        val verifier = DidDocumentDelegationVerifier(resolveDid)
        val result = verifier.verify(delegatorDid, delegateDid)
        
        assertTrue(result.valid)
    }
}

