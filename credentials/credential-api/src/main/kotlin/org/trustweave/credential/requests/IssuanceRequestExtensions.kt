package org.trustweave.credential.requests

import org.trustweave.credential.format.ProofSuiteId
import org.trustweave.credential.model.CredentialType
import org.trustweave.credential.model.vc.CredentialSubject
import org.trustweave.credential.model.vc.Issuer
import org.trustweave.did.identifiers.Did
import org.trustweave.did.identifiers.VerificationMethodId
import kotlinx.serialization.json.JsonElement
import java.time.Duration as JavaDuration
import kotlin.time.Duration
import kotlinx.datetime.Instant
import kotlinx.datetime.Clock

/**
 * Convenience functions and extensions for creating IssuanceRequest.
 */

/**
 * Create an IssuanceRequest with smart defaults.
 * 
 * Automatically includes "VerifiableCredential" in the type list if not present.
 * Defaults issuedAt to current time.
 * 
 * **Example:**
 * ```kotlin
 * val request = issuanceRequest(
 *     format = ProofSuiteId.VC_LD,
 *     issuer = issuerDid.asIssuer(),
 *     subject = subjectDid.asCredentialSubject(claims),
 *     type = "PersonCredential"  // Auto-adds VerifiableCredential
 * )
 * ``` 
 */
fun issuanceRequest(
    format: ProofSuiteId,
    issuer: Issuer,
    credentialSubject: CredentialSubject,
    type: String,
    issuedAt: Instant = Clock.System.now(),
    validUntil: Instant? = null,
    issuerKeyId: VerificationMethodId? = null
): IssuanceRequest {
    val types = if (type == "VerifiableCredential") {
        listOf(CredentialType.VerifiableCredential)
    } else {
        listOf(CredentialType.VerifiableCredential, CredentialType.Custom(type))
    }
    
    return IssuanceRequest(
        format = format,
        issuer = issuer,
        issuerKeyId = issuerKeyId,
        credentialSubject = credentialSubject,
        type = types,
        issuedAt = issuedAt,
        validUntil = validUntil
    )
}

/**
 * Create an IssuanceRequest with multiple types.
 */
fun issuanceRequest(
    format: ProofSuiteId,
    issuer: Issuer,
    credentialSubject: CredentialSubject,
    types: List<String>,
    issuedAt: Instant = Clock.System.now(),
    validUntil: Instant? = null,
    issuerKeyId: VerificationMethodId? = null
): IssuanceRequest {
    val credentialTypes = types.map { type ->
        CredentialType.fromString(type)
    }
    
    // Ensure VerifiableCredential is included
    val finalTypes = if (credentialTypes.any { it.value == "VerifiableCredential" }) {
        credentialTypes
    } else {
        listOf(CredentialType.VerifiableCredential) + credentialTypes
    }
    
    return IssuanceRequest(
        format = format,
        issuer = issuer,
        issuerKeyId = issuerKeyId,
        credentialSubject = credentialSubject,
        type = finalTypes,
        issuedAt = issuedAt,
        validUntil = validUntil
    )
}

/**
 * Helper to create credential types list, auto-adding VerifiableCredential.
 */
fun credentialTypes(vararg types: String): List<CredentialType> {
    val credentialTypes = types.map { CredentialType.fromString(it) }
    
    // Auto-add VerifiableCredential if not present
    return if (credentialTypes.any { it.value == "VerifiableCredential" }) {
        credentialTypes
    } else {
        listOf(CredentialType.VerifiableCredential) + credentialTypes
    }
}

/**
 * Extension to calculate expiration from duration.
 */
fun IssuanceRequest.withExpiration(duration: JavaDuration): IssuanceRequest {
    val kotlinDuration = Duration.parse(duration.toString())
    return copy(validUntil = issuedAt.plus(kotlinDuration))
}

