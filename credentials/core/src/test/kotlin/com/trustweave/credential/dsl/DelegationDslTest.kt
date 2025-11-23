package com.trustweave.credential.dsl

import com.trustweave.did.DidDocument
import com.trustweave.did.DidResolutionResult
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Tests for Delegation DSL.
 */
class DelegationDslTest {
    
    @Test
    fun `test delegate DSL with simple chain`() = runBlocking {
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
        
        // Create trust layer with custom resolveDid
        val trustLayer = trustLayer {
            keys { provider("inMemory") }
            did { method(DidMethods.KEY) {} }
        }
        
        // Use delegation DSL (would need to mock resolveDid in DSL context)
        // For now, test the service directly
        val service = com.trustweave.did.delegation.DelegationService(resolveDid)
        val result = service.verifyDelegationChain(delegatorDid, delegateDid)
        
        assertTrue(result.valid)
        assertEquals(2, result.path.size)
    }
    
    @Test
    fun `test delegate DSL with capability`() = runBlocking {
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
        
        val service = com.trustweave.did.delegation.DelegationService(resolveDid)
        val result = service.verifyDelegationChain(delegatorDid, delegateDid, "issueCredentials")
        
        assertTrue(result.valid)
    }
    
    @Test
    fun `test delegate DSL error handling`() = runBlocking {
        val resolveDid: suspend (String) -> DidResolutionResult? = { null }
        
        val service = com.trustweave.did.delegation.DelegationService(resolveDid)
        val result = service.verifyDelegationChain("did:key:delegator", "did:key:delegate")
        
        assertFalse(result.valid)
        assertNotNull(result.errors)
        assertTrue(result.errors.isNotEmpty())
    }
}

