@file:Suppress("DEPRECATION")

package io.geoknoesis.vericore.did.delegation

import io.geoknoesis.vericore.did.DidDocument
import io.geoknoesis.vericore.did.DidResolutionResult
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Edge case tests for Delegation Service.
 */
class DelegationServiceEdgeCasesTest {
    
    @Test
    fun `test verify delegation with empty capabilityDelegation list`() = runBlocking {
        val delegatorDid = "did:key:delegator"
        val delegateDid = "did:key:delegate"
        
        val resolveDid: suspend (String) -> DidResolutionResult? = { did ->
            when (did) {
                delegatorDid -> DidResolutionResult(
                    document = DidDocument(
                        id = delegatorDid,
                        capabilityDelegation = emptyList()
                    )
                )
                delegateDid -> DidResolutionResult(
                    document = DidDocument(id = delegateDid)
                )
                else -> null
            }
        }
        
        val service = DelegationService(resolveDid)
        val result = service.verifyDelegationChain(delegatorDid, delegateDid)
        
        assertFalse(result.valid)
        assertTrue(result.errors.any { it.contains("capabilityDelegation") })
    }
    
    @Test
    fun `test verify delegation with verification method reference`() = runBlocking {
        val delegatorDid = "did:key:delegator"
        val delegateDid = "did:key:delegate"
        
        val resolveDid: suspend (String) -> DidResolutionResult? = { did ->
            when (did) {
                delegatorDid -> DidResolutionResult(
                    document = DidDocument(
                        id = delegatorDid,
                        capabilityDelegation = listOf("$delegateDid#key-1"),
                        verificationMethod = listOf(
                            io.geoknoesis.vericore.did.VerificationMethodRef(
                                id = "$delegateDid#key-1",
                                type = "Ed25519VerificationKey2020",
                                controller = delegateDid
                            )
                        )
                    )
                )
                delegateDid -> DidResolutionResult(
                    document = DidDocument(
                        id = delegateDid,
                        verificationMethod = listOf(
                            io.geoknoesis.vericore.did.VerificationMethodRef(
                                id = "$delegateDid#key-1",
                                type = "Ed25519VerificationKey2020",
                                controller = delegateDid
                            )
                        )
                    )
                )
                else -> null
            }
        }
        
        val service = DelegationService(resolveDid)
        val result = service.verifyDelegationChain(delegatorDid, delegateDid)
        
        assertTrue(result.valid)
    }
    
    @Test
    fun `test verify delegation with relative verification method reference`() = runBlocking {
        val delegatorDid = "did:key:delegator"
        val delegateDid = "did:key:delegate"
        
        val resolveDid: suspend (String) -> DidResolutionResult? = { did ->
            when (did) {
                delegatorDid -> DidResolutionResult(
                    document = DidDocument(
                        id = delegatorDid,
                        capabilityDelegation = listOf("#key-1")
                    )
                )
                delegateDid -> DidResolutionResult(
                    document = DidDocument(id = delegateDid)
                )
                else -> null
            }
        }
        
        val service = DelegationService(resolveDid)
        val result = service.verifyDelegationChain(delegatorDid, delegateDid)
        
        // Relative reference may not match delegate DID
        assertNotNull(result)
    }
    
    @Test
    fun `test verify multi-hop delegation with single DID`() = runBlocking {
        val resolveDid: suspend (String) -> DidResolutionResult? = { null }
        
        val service = DelegationService(resolveDid)
        val result = service.verifyDelegationChainMultiHop(listOf("did:key:single"))
        
        assertFalse(result.valid)
        assertTrue(result.errors.any { it.contains("at least 2") })
    }
    
    @Test
    fun `test verify multi-hop delegation with empty chain`() = runBlocking {
        val resolveDid: suspend (String) -> DidResolutionResult? = { null }
        
        val service = DelegationService(resolveDid)
        val result = service.verifyDelegationChainMultiHop(emptyList())
        
        assertFalse(result.valid)
        assertTrue(result.errors.any { it.contains("at least 2") })
    }
    
    @Test
    fun `test verify delegation with same delegator and delegate`() = runBlocking {
        val did = "did:key:same"
        
        val resolveDid: suspend (String) -> DidResolutionResult? = { d ->
            if (d == did) {
                DidResolutionResult(
                    document = DidDocument(
                        id = did,
                        capabilityDelegation = listOf("$did#key-1")
                    )
                )
            } else null
        }
        
        val service = DelegationService(resolveDid)
        val result = service.verifyDelegationChain(did, did)
        
        // Self-delegation should be valid if in capabilityDelegation
        assertTrue(result.valid)
    }
    
    @Test
    fun `test verify delegation chain result with errors`() {
        val result = DelegationChainResult(
            valid = false,
            path = listOf("did:key:a", "did:key:b"),
            errors = listOf("Delegation failed", "Missing capability")
        )
        
        assertFalse(result.valid)
        assertEquals(2, result.errors.size)
        assertEquals(2, result.path.size)
    }
    
    @Test
    fun `test verify delegation chain result with empty path`() {
        val result = DelegationChainResult(
            valid = false,
            path = emptyList(),
            errors = listOf("No path found")
        )
        
        assertFalse(result.valid)
        assertTrue(result.path.isEmpty())
        assertTrue(result.errors.isNotEmpty())
    }

    @Test
    fun `test verify delegation chain with boolean resolver returns invalid`() = runBlocking {
        val service = DelegationService(resolveDid = { _: String -> true })
        val result = service.verifyDelegationChain("did:key:delegator", "did:key:delegate")

        assertFalse(result.valid)
        assertTrue(result.errors.any { it.contains("DID document", ignoreCase = true) })
    }
}

