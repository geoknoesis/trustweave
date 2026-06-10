package org.trustweave.credential.proof.internal.engines

import org.trustweave.credential.model.vc.VerifiableCredential

/**
 * Filters credential subject claims for selective disclosure presentations.
 *
 * Extracted from [VcLdProofEngine] to give selective-disclosure logic its own
 * focused home.  Note: this is a basic claim-filter implementation. True
 * zero-knowledge selective disclosure (e.g. BBS+ proofs) would require a
 * different cryptographic approach.
 */
internal object SelectiveDisclosureFilter {

    /**
     * Return a copy of [credential] that only contains the [disclosedClaims].
     *
     * If [disclosedClaims] is empty the credential is returned unchanged.
     * The returned credential has `proof = null` because the derived document
     * must be re-signed (or have a ZK proof applied) before distribution.
     *
     * @param credential The original credential
     * @param disclosedClaims Set of claim names to keep (supports bare names and
     *   `credentialSubject.<name>` prefixed forms)
     * @return A credential with filtered claims and no proof
     */
    fun filter(credential: VerifiableCredential, disclosedClaims: Set<String>): VerifiableCredential {
        if (disclosedClaims.isEmpty()) return credential

        val subject = credential.credentialSubject
        val filteredClaims = subject.claims.filterKeys { claimName ->
            disclosedClaims.contains(claimName) ||
            disclosedClaims.contains("credentialSubject.$claimName")
        }

        return credential.copy(
            credentialSubject = subject.copy(claims = filteredClaims),
            proof = null
        )
    }
}
