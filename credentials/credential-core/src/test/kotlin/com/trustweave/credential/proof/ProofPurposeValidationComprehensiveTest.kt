package com.trustweave.credential.proof

import com.trustweave.did.model.DidDocument
import com.trustweave.did.resolver.DidResolutionResult
import com.trustweave.did.identifiers.Did
import com.trustweave.did.identifiers.VerificationMethodId
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Comprehensive tests for Proof Purpose Validation.
 */
class ProofPurposeValidationComprehensiveTest {

    @Test
    fun `test validate all proof purposes`() = runBlocking {
        val issuerDid = "did:key:issuer"
        val verificationMethod = "$issuerDid#key-1"

        val resolveDid: suspend (String) -> DidResolutionResult? = { did ->
            if (did == issuerDid) {
                val didObj = Did(issuerDid)
                val vmId = VerificationMethodId.parse(verificationMethod, didObj)
                DidResolutionResult.Success(
                    document = DidDocument(
                        id = didObj,
                        assertionMethod = listOf(vmId),
                        authentication = listOf(vmId),
                        keyAgreement = listOf(vmId),
                        capabilityInvocation = listOf(vmId),
                        capabilityDelegation = listOf(vmId)
                    )
                )
            } else null
        }

        val validator = ProofValidator(resolveDid)

        // Test all proof purposes
        val purposes = listOf(
            "assertionMethod",
            "authentication",
            "keyAgreement",
            "capabilityInvocation",
            "capabilityDelegation"
        )

        purposes.forEach { purpose ->
            val result = validator.validateProofPurpose(
                proofPurpose = purpose,
                verificationMethod = verificationMethod,
                issuerDid = issuerDid
            )
            assertTrue(result.valid, "Proof purpose $purpose should be valid")
        }
    }

    @Test
    fun `test validate proof purpose with multiple verification methods`() = runBlocking {
        val issuerDid = "did:key:issuer"

        val resolveDid: suspend (String) -> DidResolutionResult? = { did ->
            if (did == issuerDid) {
                val didObj = Did(issuerDid)
                DidResolutionResult.Success(
                    document = DidDocument(
                        id = didObj,
                        assertionMethod = listOf(
                            VerificationMethodId.parse("$issuerDid#key-1", didObj),
                            VerificationMethodId.parse("$issuerDid#key-2", didObj),
                            VerificationMethodId.parse("$issuerDid#key-3", didObj)
                        )
                    )
                )
            } else null
        }

        val validator = ProofValidator(resolveDid)

        // Test with different verification methods
        listOf("$issuerDid#key-1", "$issuerDid#key-2", "$issuerDid#key-3").forEach { vm ->
            val result = validator.validateProofPurpose(
                proofPurpose = "assertionMethod",
                verificationMethod = vm,
                issuerDid = issuerDid
            )
            assertTrue(result.valid, "Verification method $vm should be valid")
        }
    }

    @Test
    fun `test validate proof purpose with relative references`() = runBlocking {
        val issuerDid = "did:key:issuer"

        val resolveDid: suspend (String) -> DidResolutionResult? = { did ->
            if (did == issuerDid) {
                val didObj = Did(issuerDid)
                DidResolutionResult.Success(
                    document = DidDocument(
                        id = didObj,
                        assertionMethod = listOf(
                            VerificationMethodId.parse("#key-1", didObj),
                            VerificationMethodId.parse("$issuerDid#key-1", didObj)
                        )
                    )
                )
            } else null
        }

        val validator = ProofValidator(resolveDid)

        // Test with relative reference
        val result1 = validator.validateProofPurpose(
            proofPurpose = "assertionMethod",
            verificationMethod = "#key-1",
            issuerDid = issuerDid
        )
        assertTrue(result1.valid)

        // Test with full reference
        val result2 = validator.validateProofPurpose(
            proofPurpose = "assertionMethod",
            verificationMethod = "$issuerDid#key-1",
            issuerDid = issuerDid
        )
        assertTrue(result2.valid)
    }

    @Test
    fun `test validate proof purpose with verification method not in relationship`() = runBlocking {
        val issuerDid = "did:key:issuer"

        val resolveDid: suspend (String) -> DidResolutionResult? = { did ->
            if (did == issuerDid) {
                val didObj = Did(issuerDid)
                DidResolutionResult.Success(
                    document = DidDocument(
                        id = didObj,
                        assertionMethod = listOf(VerificationMethodId.parse("$issuerDid#key-1", didObj)),
                        // key-2 is not in assertionMethod
                        authentication = listOf(VerificationMethodId.parse("$issuerDid#key-2", didObj))
                    )
                )
            } else null
        }

        val validator = ProofValidator(resolveDid)

        // Test with key-2 for assertionMethod (should fail)
        val result1 = validator.validateProofPurpose(
            proofPurpose = "assertionMethod",
            verificationMethod = "$issuerDid#key-2",
            issuerDid = issuerDid
        )
        assertFalse(result1.valid)

        // Test with key-2 for authentication (should succeed)
        val result2 = validator.validateProofPurpose(
            proofPurpose = "authentication",
            verificationMethod = "$issuerDid#key-2",
            issuerDid = issuerDid
        )
        assertTrue(result2.valid)
    }

    @Test
    fun `test validate proof purpose error messages`() = runBlocking {
        val issuerDid = "did:key:issuer"

        val resolveDid: suspend (String) -> DidResolutionResult? = { null }

        val validator = ProofValidator(resolveDid)

        val result = validator.validateProofPurpose(
            proofPurpose = "assertionMethod",
            verificationMethod = "$issuerDid#key-1",
            issuerDid = issuerDid
        )

        assertFalse(result.valid)
        assertTrue(result.errors.isNotEmpty())
        assertTrue(result.errors.any { it.contains("Failed to resolve") })
    }
}

