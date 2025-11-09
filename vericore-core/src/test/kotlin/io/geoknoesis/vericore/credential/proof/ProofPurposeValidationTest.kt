package io.geoknoesis.vericore.credential.proof

import io.geoknoesis.vericore.did.DidDocument
import io.geoknoesis.vericore.did.DidResolutionResult
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Tests for Proof Purpose Validation.
 */
class ProofPurposeValidationTest {
    
    @Test
    fun `test validate assertionMethod proof purpose`() = runBlocking {
        val issuerDid = "did:key:issuer"
        val verificationMethod = "$issuerDid#key-1"
        
        val resolveDid: suspend (String) -> DidResolutionResult? = { did ->
            DidResolutionResult(
                document = DidDocument(
                    id = issuerDid,
                    assertionMethod = listOf(verificationMethod)
                )
            )
        }
        
        val validator = ProofValidator(resolveDid)
        val result = validator.validateProofPurpose(
            proofPurpose = "assertionMethod",
            verificationMethod = verificationMethod,
            issuerDid = issuerDid
        )
        
        assertTrue(result.valid)
        assertTrue(result.errors.isEmpty())
    }
    
    @Test
    fun `test validate authentication proof purpose`() = runBlocking {
        val issuerDid = "did:key:issuer"
        val verificationMethod = "$issuerDid#key-1"
        
        val resolveDid: suspend (String) -> DidResolutionResult? = { did ->
            DidResolutionResult(
                document = DidDocument(
                    id = issuerDid,
                    authentication = listOf(verificationMethod)
                )
            )
        }
        
        val validator = ProofValidator(resolveDid)
        val result = validator.validateProofPurpose(
            proofPurpose = "authentication",
            verificationMethod = verificationMethod,
            issuerDid = issuerDid
        )
        
        assertTrue(result.valid)
    }
    
    @Test
    fun `test validate capabilityInvocation proof purpose`() = runBlocking {
        val issuerDid = "did:key:issuer"
        val verificationMethod = "$issuerDid#key-1"
        
        val resolveDid: suspend (String) -> DidResolutionResult? = { did ->
            DidResolutionResult(
                document = DidDocument(
                    id = issuerDid,
                    capabilityInvocation = listOf(verificationMethod)
                )
            )
        }
        
        val validator = ProofValidator(resolveDid)
        val result = validator.validateProofPurpose(
            proofPurpose = "capabilityInvocation",
            verificationMethod = verificationMethod,
            issuerDid = issuerDid
        )
        
        assertTrue(result.valid)
    }
    
    @Test
    fun `test validate capabilityDelegation proof purpose`() = runBlocking {
        val issuerDid = "did:key:issuer"
        val verificationMethod = "$issuerDid#key-1"
        
        val resolveDid: suspend (String) -> DidResolutionResult? = { did ->
            DidResolutionResult(
                document = DidDocument(
                    id = issuerDid,
                    capabilityDelegation = listOf(verificationMethod)
                )
            )
        }
        
        val validator = ProofValidator(resolveDid)
        val result = validator.validateProofPurpose(
            proofPurpose = "capabilityDelegation",
            verificationMethod = verificationMethod,
            issuerDid = issuerDid
        )
        
        assertTrue(result.valid)
    }
    
    @Test
    fun `test validate proof purpose fails when not in relationship`() = runBlocking {
        val issuerDid = "did:key:issuer"
        val verificationMethod = "$issuerDid#key-1"
        
        val resolveDid: suspend (String) -> DidResolutionResult? = { did ->
            DidResolutionResult(
                document = DidDocument(
                    id = issuerDid,
                    assertionMethod = listOf(verificationMethod),
                    // capabilityInvocation is empty
                    capabilityInvocation = emptyList()
                )
            )
        }
        
        val validator = ProofValidator(resolveDid)
        val result = validator.validateProofPurpose(
            proofPurpose = "capabilityInvocation",
            verificationMethod = verificationMethod,
            issuerDid = issuerDid
        )
        
        assertFalse(result.valid)
        assertTrue(result.errors.isNotEmpty())
    }
    
    @Test
    fun `test validate proof purpose fails when DID not resolved`() = runBlocking {
        val resolveDid: suspend (String) -> DidResolutionResult? = { null }
        
        val validator = ProofValidator(resolveDid)
        val result = validator.validateProofPurpose(
            proofPurpose = "assertionMethod",
            verificationMethod = "did:key:issuer#key-1",
            issuerDid = "did:key:issuer"
        )
        
        assertFalse(result.valid)
        assertTrue(result.errors.any { it.contains("Failed to resolve") })
    }
    
    @Test
    fun `test validate proof purpose with relative verification method reference`() = runBlocking {
        val issuerDid = "did:key:issuer"
        
        val resolveDid: suspend (String) -> DidResolutionResult? = { did ->
            DidResolutionResult(
                document = DidDocument(
                    id = issuerDid,
                    assertionMethod = listOf("#key-1") // Relative reference
                )
            )
        }
        
        val validator = ProofValidator(resolveDid)
        val result = validator.validateProofPurpose(
            proofPurpose = "assertionMethod",
            verificationMethod = "$issuerDid#key-1", // Full reference
            issuerDid = issuerDid
        )
        
        assertTrue(result.valid)
    }
    
    @Test
    fun `test validate unknown proof purpose`() = runBlocking {
        val issuerDid = "did:key:issuer"
        
        val resolveDid: suspend (String) -> DidResolutionResult? = { did ->
            DidResolutionResult(
                document = DidDocument(id = issuerDid)
            )
        }
        
        val validator = ProofValidator(resolveDid)
        val result = validator.validateProofPurpose(
            proofPurpose = "unknownPurpose",
            verificationMethod = "$issuerDid#key-1",
            issuerDid = issuerDid
        )
        
        assertFalse(result.valid)
        assertTrue(result.errors.any { it.contains("Unknown proof purpose") })
    }
}

