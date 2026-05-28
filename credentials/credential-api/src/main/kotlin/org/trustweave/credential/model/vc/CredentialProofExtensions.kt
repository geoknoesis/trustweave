package org.trustweave.credential.model.vc

import org.trustweave.credential.format.ProofSuiteId

/**
 * Extension functions for CredentialProof.
 */

/**
 * Get the proof suite ID from a CredentialProof.
 *
 * Maps proof types to their corresponding proof suite identifiers:
 * - LinkedDataProof → VC_LD
 * - JwtProof → VC_JWT
 * - SdJwtVcProof → SD_JWT_VC
 * - MdocProof → MDOC
 * - JAdES → JADES
 */
fun CredentialProof.getFormatId(): ProofSuiteId {
    return when (this) {
        is CredentialProof.LinkedDataProof -> ProofSuiteId.VC_LD
        is CredentialProof.JwtProof -> ProofSuiteId.VC_JWT
        is CredentialProof.SdJwtVcProof -> ProofSuiteId.SD_JWT_VC
        is CredentialProof.MdocProof -> ProofSuiteId.MDOC
        is CredentialProof.JAdES -> ProofSuiteId.JADES
    }
}

