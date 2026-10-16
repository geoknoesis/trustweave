package org.trustweave.credential.internal

import org.trustweave.credential.model.vc.CredentialProof
import org.trustweave.credential.model.vc.VerifiableCredential
import org.trustweave.credential.requests.VerificationOptions
import org.trustweave.credential.results.VerificationResult

/**
 * Opt-in bearer rejection ([VerificationOptions.requireHolderBinding]): an SD-JWT VC whose issuer-signed JWT
 * carries no `cnf` claim is refused. Only meaningful after the credential's issuer signature was verified.
 *
 * Returns `null` when the option is off, for other formats, and for credentials that carry a `cnf` claim
 * (an unenforceable `cnf` is rejected separately, whatever this option says).
 */
internal fun requireSdJwtHolderBinding(
    credential: VerifiableCredential,
    options: VerificationOptions,
): VerificationResult.Invalid.InvalidProof? {
    if (!options.requireHolderBinding) return null
    val proof = credential.proof as? CredentialProof.SdJwtVcProof ?: return null
    if (PresentationVerification.extractCnfBinding(proof.sdJwtVc) != null) return null
    return VerificationResult.Invalid.InvalidProof(
        credential = credential,
        reason = "SD-JWT credential carries no 'cnf' holder binding and holder binding is required",
        errors = listOf("requireHolderBinding is set: bearer SD-JWT credentials without an issuer-signed cnf claim are rejected"),
    )
}
