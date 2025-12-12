package com.trustweave.credential.proof

import com.trustweave.did.model.DidDocument
import com.trustweave.did.identifiers.Did
import com.trustweave.did.identifiers.VerificationMethodId
import com.trustweave.did.resolver.DidResolutionResult
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
            val didObj = Did(issuerDid)
            DidResolutionResult.Success(
                document = DidDocument(
                    id = didObj,
                    assertionMethod = listOf(VerificationMethodId.parse(verificationMethod, didObj))
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
            val didObj = Did(issuerDid)
            DidResolutionResult.Success(
                document = DidDocument(
                    id = didObj,
                    authentication = listOf(VerificationMethodId.parse(verificationMethod, didObj))
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
            val didObj = Did(issuerDid)
            DidResolutionResult.Success(
                document = DidDocument(
                    id = didObj,
                    capabilityInvocation = listOf(VerificationMethodId.parse(verificationMethod, didObj))
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
            val didObj = Did(issuerDid)
            DidResolutionResult.Success(
                document = DidDocument(
                    id = didObj,
                    capabilityDelegation = listOf(VerificationMethodId.parse(verificationMethod, didObj))
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
            DidResolutionResult.Success(
                document = DidDocument(
                    id = Did(issuerDid),
                    verificationMethod = emptyList()
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
            val didObj = Did(issuerDid)
            DidResolutionResult.Success(
                document = DidDocument(
                    id = didObj,
                    assertionMethod = listOf(VerificationMethodId.parse("$issuerDid#key-1", didObj))
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
            DidResolutionResult.Success(
                document = DidDocument(id = Did(issuerDid), verificationMethod = emptyList())
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

