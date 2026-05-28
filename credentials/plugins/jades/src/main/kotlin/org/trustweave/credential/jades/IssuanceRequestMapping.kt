package org.trustweave.credential.jades

import org.trustweave.credential.model.vc.VerifiableCredential
import org.trustweave.credential.requests.IssuanceRequest

/**
 * Build a [VerifiableCredential] from an [IssuanceRequest] **without** attaching a proof.
 *
 * This is the canonical payload that gets fed into the JAdES signer: the W3C VC fields exactly
 * as the issuer asserts them, with no `proof` member. The JAdES JWS produced over these bytes
 * binds the issuer's signing key to that exact JSON, which the verifier later re-renders and
 * re-hashes.
 */
internal fun IssuanceRequest.toCredentialWithoutProof(): VerifiableCredential = VerifiableCredential(
    id = id,
    type = type,
    issuer = issuer,
    issuanceDate = issuedAt,
    credentialSubject = credentialSubject,
    validFrom = validFrom,
    validUntil = validUntil,
    credentialStatus = credentialStatus,
    credentialSchema = credentialSchema,
    evidence = evidence,
    proof = null,
)
