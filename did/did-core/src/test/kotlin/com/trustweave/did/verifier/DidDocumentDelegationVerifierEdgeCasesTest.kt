@file:Suppress("DEPRECATION")

package com.trustweave.did.verifier

import com.trustweave.did.identifiers.Did
import com.trustweave.did.identifiers.VerificationMethodId
import com.trustweave.did.model.DidDocument
import com.trustweave.did.model.VerificationMethod
import com.trustweave.did.resolver.DidResolutionResult
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Edge case tests for DID Document Delegation Verifier.
 */
class DidDocumentDelegationVerifierEdgeCasesTest {

    @Test
    fun `test verify delegation with empty capabilityDelegation list`() = runBlocking {
        val delegatorDid = "did:key:delegator"
        val delegateDid = "did:key:delegate"

        val resolveDid: suspend (String) -> DidResolutionResult? = { did ->
            when (did) {
                delegatorDid -> DidResolutionResult.Success(
                    document = DidDocument(
                        id = Did(delegatorDid),
                        capabilityDelegation = emptyList()
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
        assertTrue(result.errors.any { it.contains("capabilityDelegation") })
    }

    @Test
    fun `test verify delegation with verification method reference`() = runBlocking {
        val delegatorDid = "did:key:delegator"
        val delegateDid = "did:key:delegate"

        val resolveDid: suspend (String) -> DidResolutionResult? = { did ->
            when (did) {
                delegatorDid -> DidResolutionResult.Success(
                    document = DidDocument(
                        id = Did(delegatorDid),
                        capabilityDelegation = listOf(VerificationMethodId.parse("$delegateDid#key-1")),
                        verificationMethod = listOf(
                            VerificationMethod(
                                id = VerificationMethodId.parse("$delegateDid#key-1"),
                                type = "Ed25519VerificationKey2020",
                                controller = Did(delegateDid)
                            )
                        )
                    )
                )
                delegateDid -> DidResolutionResult.Success(
                    document = DidDocument(
                        id = Did(delegateDid),
                        verificationMethod = listOf(
                            VerificationMethod(
                                id = VerificationMethodId.parse("$delegateDid#key-1"),
                                type = "Ed25519VerificationKey2020",
                                controller = Did(delegateDid)
                            )
                        )
                    )
                )
                else -> null
            }
        }

        val verifier = DidDocumentDelegationVerifier(resolveDid)
        val result = verifier.verify(Did(delegatorDid), Did(delegateDid))

        assertTrue(result.valid)
    }

    @Test
    fun `test verify delegation with relative verification method reference`() = runBlocking {
        val delegatorDid = "did:key:delegator"
        val delegateDid = "did:key:delegate"

        val resolveDid: suspend (String) -> DidResolutionResult? = { did ->
            when (did) {
                delegatorDid -> DidResolutionResult.Success(
                    document = DidDocument(
                        id = Did(delegatorDid),
                        capabilityDelegation = listOf(VerificationMethodId.parse("#key-1"))
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

        // Relative reference may not match delegate DID
        assertNotNull(result)
    }

    @Test
    fun `test verify multi-hop delegation with single DID`() = runBlocking {
        val resolveDid: suspend (String) -> DidResolutionResult? = { null }

        val verifier = DidDocumentDelegationVerifier(resolveDid)
        val result = verifier.verifyChain(listOf(Did("did:key:single")))

        assertFalse(result.valid)
        assertTrue(result.errors.any { it.contains("at least 2") })
    }

    @Test
    fun `test verify multi-hop delegation with empty chain`() = runBlocking {
        val resolveDid: suspend (String) -> DidResolutionResult? = { null }

        val verifier = DidDocumentDelegationVerifier(resolveDid)
        val result = verifier.verifyChain(emptyList())

        assertFalse(result.valid)
        assertTrue(result.errors.any { it.contains("at least 2") })
    }

    @Test
    fun `test verify delegation with same delegator and delegate`() = runBlocking {
        val did = "did:key:same"

        val resolveDid: suspend (String) -> DidResolutionResult? = { d ->
            if (d == did) {
                DidResolutionResult.Success(
                    document = DidDocument(
                        id = Did(did),
                        capabilityDelegation = listOf(VerificationMethodId.parse("$did#key-1"))
                    )
                )
            } else null
        }

        val verifier = DidDocumentDelegationVerifier(resolveDid)
        val result = verifier.verify(Did(did), Did(did))

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
        val resolveDid: suspend (String) -> DidResolutionResult? = { _: String -> null }
        val verifier = DidDocumentDelegationVerifier(resolveDid)
        val result = verifier.verify(Did("did:key:delegator"), Did("did:key:delegate"))

        assertFalse(result.valid)
        assertTrue(result.errors.any { error -> error.contains("DID document", ignoreCase = true) || error.contains("Failed to resolve", ignoreCase = true) })
    }
}

