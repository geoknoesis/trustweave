package org.trustweave.trust.dsl

import org.trustweave.did.model.DidDocument
import org.trustweave.did.resolver.DidResolutionResult
import org.trustweave.did.resolver.DidResolver
import org.trustweave.did.identifiers.Did
import org.trustweave.did.identifiers.VerificationMethodId
import org.trustweave.did.verifier.DidDocumentDelegationVerifier
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Delegation chain verification uses [DidDocumentDelegationVerifier] (wired through
 * [org.trustweave.trust.TrustWeave.delegate] via [org.trustweave.trust.dsl.did.DelegationBuilder]).
 */
class DelegationDslTest {

    @Test
    fun `delegation verifier accepts valid delegator to delegate link`() = runBlocking {
        val delegatorDid = "did:key:delegator"
        val delegateDid = "did:key:delegate"

        val resolveDid: suspend (String) -> DidResolutionResult? = { did ->
            when (did) {
                delegatorDid -> DidResolutionResult.Success(
                    document = DidDocument(
                        id = Did(delegatorDid),
                        capabilityDelegation = listOf(
                            VerificationMethodId.parse("$delegateDid#key-1", Did(delegatorDid)),
                        ),
                    ),
                )
                delegateDid -> DidResolutionResult.Success(
                    document = DidDocument(id = Did(delegateDid)),
                )
                else -> null
            }
        }

        val verifier = DidDocumentDelegationVerifier(DidResolver { did -> resolveDid(did.value) ?: DidResolutionResult.Failure.NotFound(did = did, reason = "DID not found") })
        val result = verifier.verify(Did(delegatorDid), Did(delegateDid))

        assertTrue(result.valid)
        assertTrue(result.path.size >= 2)
        assertTrue(result.path.contains(delegatorDid))
        assertTrue(result.path.contains(delegateDid))
    }

    @Test
    fun `delegation verifier accepts capability delegation reference`() = runBlocking {
        val delegatorDid = "did:key:delegator"
        val delegateDid = "did:key:delegate"

        val resolveDid: suspend (String) -> DidResolutionResult? = { did ->
            when (did) {
                delegatorDid -> DidResolutionResult.Success(
                    document = DidDocument(
                        id = Did(delegatorDid),
                        capabilityDelegation = listOf(
                            VerificationMethodId.parse("$delegateDid#key-1", Did(delegatorDid)),
                        ),
                    ),
                )
                delegateDid -> DidResolutionResult.Success(
                    document = DidDocument(id = Did(delegateDid)),
                )
                else -> null
            }
        }

        val verifier = DidDocumentDelegationVerifier(DidResolver { did -> resolveDid(did.value) ?: DidResolutionResult.Failure.NotFound(did = did, reason = "DID not found") })
        val result = verifier.verify(Did(delegatorDid), Did(delegateDid))
        assertTrue(result.valid)
    }

    @Test
    fun `delegation verifier fails when resolution returns nothing`() = runBlocking {
        val resolveDid: suspend (String) -> DidResolutionResult? = { null }

        val verifier = DidDocumentDelegationVerifier(DidResolver { did -> resolveDid(did.value) ?: DidResolutionResult.Failure.NotFound(did = did, reason = "DID not found") })
        val result = verifier.verify(Did("did:key:delegator"), Did("did:key:delegate"))

        assertFalse(result.valid)
        assertTrue(result.errors.isNotEmpty())
    }
}
