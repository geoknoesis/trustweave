package io.geoknoesis.vericore.credential.dsl

import io.geoknoesis.vericore.did.DidDocument
import io.geoknoesis.vericore.did.DidResolutionResult
import io.geoknoesis.vericore.testkit.kms.InMemoryKeyManagementService
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Comprehensive tests for Delegation DSL integration.
 */
class DelegationDslComprehensiveTest {
    
    @Test
    fun `test delegation DSL with complete workflow`() = runBlocking {
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
        
        val trustLayer = trustLayer {
            keys { provider("inMemory") }
            did { method(DidMethods.KEY) {} }
        }
        
        // Test delegation service directly (DSL would need resolveDid integration)
        val service = io.geoknoesis.vericore.did.delegation.DelegationService(resolveDid)
        val result = service.verifyDelegationChain(delegatorDid, delegateDid)
        
        assertTrue(result.valid)
        assertEquals(2, result.path.size)
        assertTrue(result.path.contains(delegatorDid))
        assertTrue(result.path.contains(delegateDid))
    }
    
    @Test
    fun `test delegation DSL with multi-hop chain`() = runBlocking {
        val chain = listOf("did:key:ceo", "did:key:director", "did:key:manager", "did:key:assistant")
        
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
                    document = DidDocument(
                        id = "did:key:manager",
                        capabilityDelegation = listOf("did:key:assistant#key-1")
                    )
                )
                "did:key:assistant" -> DidResolutionResult(
                    document = DidDocument(id = "did:key:assistant")
                )
                else -> null
            }
        }
        
        val service = io.geoknoesis.vericore.did.delegation.DelegationService(resolveDid)
        val result = service.verifyDelegationChainMultiHop(chain)
        
        assertTrue(result.valid)
        assertEquals(4, result.path.size)
        assertEquals(chain, result.path)
    }
    
    @Test
    fun `test delegation DSL error handling`() = runBlocking {
        val resolveDid: suspend (String) -> DidResolutionResult? = { null }
        
        val service = io.geoknoesis.vericore.did.delegation.DelegationService(resolveDid)
        val result = service.verifyDelegationChain("did:key:delegator", "did:key:delegate")
        
        assertFalse(result.valid)
        assertTrue(result.errors.isNotEmpty())
        assertTrue(result.errors.any { it.contains("Failed to resolve") })
    }
    
    @Test
    fun `test delegation with capability parameter`() = runBlocking {
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
        
        val service = io.geoknoesis.vericore.did.delegation.DelegationService(resolveDid)
        
        // Test with capability
        val result1 = service.verifyDelegationChain(delegatorDid, delegateDid, "issueCredentials")
        assertTrue(result1.valid)
        
        // Test without capability
        val result2 = service.verifyDelegationChain(delegatorDid, delegateDid, null)
        assertTrue(result2.valid)
    }
}

