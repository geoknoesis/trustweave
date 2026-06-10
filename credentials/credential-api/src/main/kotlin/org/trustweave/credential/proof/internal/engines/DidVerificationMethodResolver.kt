package org.trustweave.credential.proof.internal.engines

import org.trustweave.core.identifiers.Iri
import org.trustweave.did.model.VerificationMethod
import org.trustweave.did.resolver.DidResolver

/**
 * Resolves DID verification methods used during VC-LD proof verification.
 *
 * Delegates to [ProofEngineUtils] for the actual DID resolution so that
 * [VcLdProofEngine] no longer holds DID resolver lookup logic inline.
 */
internal class DidVerificationMethodResolver(
    private val didResolver: DidResolver?
) {
    /**
     * Resolve a verification method from a DID document.
     *
     * @param issuerIri The issuer IRI (must be a DID)
     * @param verificationMethodId The verification method ID (full or fragment)
     * @param expectedProofPurpose Optional proof purpose the verification method must be
     *   authorized for in the resolved DID document (e.g. `assertionMethod`)
     * @return The [VerificationMethod], or `null` if resolution fails, the issuer is not a
     *   DID, or the method is not authorized for [expectedProofPurpose]
     */
    suspend fun resolve(
        issuerIri: Iri,
        verificationMethodId: String,
        expectedProofPurpose: String? = null
    ): VerificationMethod? {
        if (!issuerIri.isDid) return null
        val resolver = didResolver ?: return null
        return ProofEngineUtils.resolveVerificationMethod(
            issuerIri = issuerIri,
            verificationMethodId = verificationMethodId,
            didResolver = resolver,
            expectedProofPurpose = expectedProofPurpose
        )
    }

    /**
     * Construct a verification method reference string for issuance.
     *
     * If [keyId] already contains `#`, it is returned as-is; otherwise
     * it is appended to [issuerIri] as a fragment.
     *
     * @param issuerIri The issuer IRI string
     * @param keyId The key fragment (e.g. "key-1") or full ID
     * @return A fully-qualified verification method reference
     */
    fun buildVerificationMethodId(issuerIri: String, keyId: String?): String =
        keyId?.let { "$issuerIri#$it" } ?: "$issuerIri#key-1"
}
