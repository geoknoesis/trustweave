package com.trustweave.credential.proof

import com.trustweave.did.DidDocument
import com.trustweave.did.resolver.DidResolutionResult
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Edge case tests for Proof Purpose Validation.
 */
class ProofPurposeValidationEdgeCasesTest {
    
    @Test
    fun `test validate proof purpose with empty verification method`() = runBlocking {
        val issuerDid = "did:key:issuer"
        
        val resolveDid: suspend (String) -> DidResolutionResult? = { did ->
            DidResolutionResult(
                document = DidDocument(
                    id = issuerDid,
                    assertionMethod = listOf("$issuerDid#key-1")
                )
            )
        }
        
        val validator = ProofValidator(resolveDid)
        val result = validator.validateProofPurpose(
            proofPurpose = "assertionMethod",
            verificationMethod = "",
            issuerDid = issuerDid
        )
        
        assertFalse(result.valid)
    }
    
    @Test
    fun `test validate proof purpose with verification method not in document`() = runBlocking {
        val issuerDid = "did:key:issuer"
        
        val resolveDid: suspend (String) -> DidResolutionResult? = { did ->
            DidResolutionResult(
                document = DidDocument(
                    id = issuerDid,
                    assertionMethod = listOf("$issuerDid#key-1")
                )
            )
        }
        
        val validator = ProofValidator(resolveDid)
        val result = validator.validateProofPurpose(
            proofPurpose = "assertionMethod",
            verificationMethod = "$issuerDid#key-2", // Not in document
            issuerDid = issuerDid
        )
        
        assertFalse(result.valid)
        assertTrue(result.errors.isNotEmpty(), "Should have errors when verification method not found")
    }
    
    @Test
    fun `test validate proof purpose with multiple matching references`() = runBlocking {
        val issuerDid = "did:key:issuer"
        val verificationMethod = "$issuerDid#key-1"
        
        val resolveDid: suspend (String) -> DidResolutionResult? = { did ->
            DidResolutionResult(
                document = DidDocument(
                    id = issuerDid,
                    assertionMethod = listOf(
                        verificationMethod,
                        "#key-1", // Relative reference
                        "$issuerDid#key-1" // Full reference
                    )
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
    }
    
    @Test
    fun `test validate proof purpose with keyAgreement relationship`() = runBlocking {
        val issuerDid = "did:key:issuer"
        val verificationMethod = "$issuerDid#key-1"
        
        val resolveDid: suspend (String) -> DidResolutionResult? = { did ->
            DidResolutionResult(
                document = DidDocument(
                    id = issuerDid,
                    keyAgreement = listOf(verificationMethod)
                )
            )
        }
        
        val validator = ProofValidator(resolveDid)
        val result = validator.validateProofPurpose(
            proofPurpose = "keyAgreement",
            verificationMethod = verificationMethod,
            issuerDid = issuerDid
        )
        
        assertTrue(result.valid)
    }
    
    @Test
    fun `test validate proof purpose with invalid issuer DID format`() = runBlocking {
        val resolveDid: suspend (String) -> DidResolutionResult? = { null }
        
        val validator = ProofValidator(resolveDid)
        val result = validator.validateProofPurpose(
            proofPurpose = "assertionMethod",
            verificationMethod = "did:key:issuer#key-1",
            issuerDid = "invalid-did"
        )
        
        assertFalse(result.valid)
        assertTrue(result.errors.any { it.contains("Failed to resolve") })
    }
    
    @Test
    fun `test validate proof purpose with blank proof purpose`() = runBlocking {
        val issuerDid = "did:key:issuer"
        
        val resolveDid: suspend (String) -> DidResolutionResult? = { did ->
            DidResolutionResult(
                document = DidDocument(id = issuerDid)
            )
        }
        
        val validator = ProofValidator(resolveDid)
        val result = validator.validateProofPurpose(
            proofPurpose = "",
            verificationMethod = "$issuerDid#key-1",
            issuerDid = issuerDid
        )
        
        assertFalse(result.valid)
        assertTrue(result.errors.any { it.contains("Unknown proof purpose") })
    }
    
    @Test
    fun `test validate proof purpose result with multiple errors`() {
        val result = ProofPurposeValidationResult(
            valid = false,
            errors = listOf(
                "Verification method not found",
                "Proof purpose mismatch"
            )
        )
        
        assertFalse(result.valid)
        assertEquals(2, result.errors.size)
    }
    
    @Test
    fun `test normalize verification method reference with various formats`() = runBlocking {
        val issuerDid = "did:key:issuer"
        
        val resolveDid: suspend (String) -> DidResolutionResult? = { did ->
            DidResolutionResult(
                document = DidDocument(
                    id = issuerDid,
                    assertionMethod = listOf(
                        "$issuerDid#key-1",
                        "#key-1"
                    )
                )
            )
        }
        
        val validator = ProofValidator(resolveDid)
        
        // Test full DID URL
        val result1 = validator.validateProofPurpose(
            proofPurpose = "assertionMethod",
            verificationMethod = "$issuerDid#key-1",
            issuerDid = issuerDid
        )
        assertTrue(result1.valid)
        
        // Test relative reference
        val result2 = validator.validateProofPurpose(
            proofPurpose = "assertionMethod",
            verificationMethod = "#key-1",
            issuerDid = issuerDid
        )
        assertTrue(result2.valid)
    }
}

