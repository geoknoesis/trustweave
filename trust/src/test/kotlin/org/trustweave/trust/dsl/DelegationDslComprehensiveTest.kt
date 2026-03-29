package org.trustweave.trust.dsl

import org.trustweave.did.model.DidDocument
import org.trustweave.did.identifiers.Did
import org.trustweave.did.identifiers.VerificationMethodId
import org.trustweave.did.resolver.DidResolutionResult
import org.trustweave.did.resolver.DidResolver
import org.trustweave.did.verifier.DidDocumentDelegationVerifier
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Multi-hop and edge cases for [DidDocumentDelegationVerifier] (same engine as [TrustWeave.delegate]).
 */
class DelegationDslComprehensiveTest {

    @Test
    fun `multi-hop delegation chain verifies`() = runBlocking {
        val chain = listOf("did:key:ceo", "did:key:director", "did:key:manager", "did:key:assistant")

        val ceoDid = Did("did:key:ceo")
        val directorDid = Did("did:key:director")
        val managerDid = Did("did:key:manager")
        val assistantDid = Did("did:key:assistant")

        val resolveDid: suspend (String) -> DidResolutionResult? = { did ->
            when (did) {
                "did:key:ceo" -> DidResolutionResult.Success(
                    document = DidDocument(
                        id = ceoDid,
                        capabilityDelegation = listOf(
                            VerificationMethodId.parse("did:key:director#key-1", ceoDid),
                        ),
                    ),
                )
                "did:key:director" -> DidResolutionResult.Success(
                    document = DidDocument(
                        id = directorDid,
                        capabilityDelegation = listOf(
                            VerificationMethodId.parse("did:key:manager#key-1", directorDid),
                        ),
                    ),
                )
                "did:key:manager" -> DidResolutionResult.Success(
                    document = DidDocument(
                        id = managerDid,
                        capabilityDelegation = listOf(
                            VerificationMethodId.parse("did:key:assistant#key-1", managerDid),
                        ),
                    ),
                )
                "did:key:assistant" -> DidResolutionResult.Success(
                    document = DidDocument(id = assistantDid),
                )
                else -> null
            }
        }

        val verifier = DidDocumentDelegationVerifier(DidResolver { did -> resolveDid(did.value) ?: DidResolutionResult.Failure.NotFound(did = did, reason = "DID not found") })
        val result = verifier.verifyChain(chain.map { Did(it) })

        assertTrue(result.valid)
        assertEquals(chain, result.path)
    }

    @Test
    fun `resolution failure yields invalid chain`() = runBlocking {
        val resolveDid: suspend (String) -> DidResolutionResult? = { null }
        val verifier = DidDocumentDelegationVerifier(DidResolver { did -> resolveDid(did.value) ?: DidResolutionResult.Failure.NotFound(did = did, reason = "DID not found") })
        val result = verifier.verify(Did("did:key:delegator"), Did("did:key:delegate"))
        assertFalse(result.valid)
        assertTrue(result.errors.any { it.contains("Failed to resolve", ignoreCase = true) })
    }

    @Test
    fun `single delegator delegate pair matches simple verifier`() = runBlocking {
        val delegatorDid = "did:key:delegator"
        val delegateDid = "did:key:delegate"
        val delegator = Did(delegatorDid)
        val delegate = Did(delegateDid)

        val resolveDid: suspend (String) -> DidResolutionResult? = { didStr ->
            when (didStr) {
                delegatorDid -> DidResolutionResult.Success(
                    document = DidDocument(
                        id = delegator,
                        capabilityDelegation = listOf(
                            VerificationMethodId.parse("$delegateDid#key-1", delegate),
                        ),
                    ),
                )
                delegateDid -> DidResolutionResult.Success(
                    document = DidDocument(id = delegate),
                )
                else -> null
            }
        }

        val verifier = DidDocumentDelegationVerifier(DidResolver { did -> resolveDid(did.value) ?: DidResolutionResult.Failure.NotFound(did = did, reason = "DID not found") })
        val result = verifier.verify(delegator, delegate)
        assertTrue(result.valid)
    }
}
