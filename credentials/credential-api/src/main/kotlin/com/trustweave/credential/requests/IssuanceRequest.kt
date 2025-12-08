package com.trustweave.credential.requests

import com.trustweave.credential.format.ProofSuiteId
import com.trustweave.credential.model.vc.CredentialStatus
import com.trustweave.credential.model.vc.CredentialSubject
import com.trustweave.credential.model.vc.Issuer
import com.trustweave.credential.model.vc.CredentialSchema
import com.trustweave.credential.model.Evidence
import com.trustweave.credential.identifiers.CredentialId
import com.trustweave.credential.model.CredentialType
import com.trustweave.credential.proof.ProofOptions
import com.trustweave.did.identifiers.VerificationMethodId
import kotlinx.datetime.Instant
import kotlinx.datetime.Clock

/**
 * Request for Verifiable Credential issuance.
 * 
 * Aligned with W3C VC Data Model. Uses VC-specific types (Issuer, CredentialSubject).
 * This is the data class only. Builder DSL is available via extension functions in the trust module.
 */
data class IssuanceRequest(
    val format: ProofSuiteId,  // VC proof format (vc-ld, vc-jwt, sd-jwt-vc)
    val issuer: Issuer,  // VC issuer (IRI-based)
    val issuerKeyId: VerificationMethodId? = null,  // Optional verification method for signing
    val credentialSubject: CredentialSubject,  // VC subject (IRI + claims)
    val type: List<CredentialType>,  // Must include "VerifiableCredential"
    val id: CredentialId? = null,  // Optional credential ID
    val issuedAt: Instant = Clock.System.now(),  // Defaults to current time
    val validFrom: Instant? = null,  // Maps to validFrom in VC (VC 2.0, notBefore equivalent)
    val validUntil: Instant? = null,  // Maps to expirationDate in VC
    val credentialStatus: CredentialStatus? = null,  // VC status
    val credentialSchema: CredentialSchema? = null,  // VC schema
    val evidence: List<Evidence>? = null,
    val proofOptions: ProofOptions? = null  // Proof-specific options (includes proof suite-specific options in additionalOptions)
)

