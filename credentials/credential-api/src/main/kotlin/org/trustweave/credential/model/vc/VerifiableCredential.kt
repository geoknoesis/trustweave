package org.trustweave.credential.model.vc
import org.trustweave.credential.identifiers.CredentialId
import org.trustweave.credential.model.CredentialType
import org.trustweave.credential.model.Evidence
import org.trustweave.credential.model.vc.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName
import kotlinx.serialization.Contextual
import kotlinx.serialization.Transient
import kotlinx.datetime.Instant
import kotlinx.datetime.Clock

/**
 * Verifiable Credential as defined by W3C Verifiable Credentials Data Model.
 * 
 * Supports both W3C VC 1.1 and VC 2.0 Data Models.
 * 
 * **VC Version Support:**
 * - **VC 1.1**: Uses `https://www.w3.org/2018/credentials/v1` context
 * - **VC 2.0**: Uses `https://www.w3.org/ns/credentials/v2` context (or both contexts for compatibility)
 * 
 * Supports VC-LD (Linked Data Proofs), VC-JWT (JWT), and SD-JWT-VC proof formats.
 * 
 * **Examples:**
 * ```kotlin
 * // VC 1.1 credential
 * val vc11 = VerifiableCredential(
 *     context = listOf("https://www.w3.org/2018/credentials/v1"),
 *     type = listOf(CredentialType("VerifiableCredential")),
 *     issuer = Issuer.fromDid(Did("did:example:issuer")),
 *     issuanceDate = Instant.now(),
 *     credentialSubject = CredentialSubject.fromDid(...),
 *     proof = CredentialProof.LinkedDataProof(...)
 * )
 * 
 * // VC 2.0 credential
 * val vc20 = VerifiableCredential(
 *     context = listOf("https://www.w3.org/ns/credentials/v2"),
 *     type = listOf(CredentialType("VerifiableCredential")),
 *     issuer = Issuer.fromDid(Did("did:example:issuer")),
 *     issuanceDate = Instant.now(),
 *     credentialSubject = CredentialSubject.fromDid(...),
 *     proof = CredentialProof.LinkedDataProof(...)
 * )
 * 
 * // Compatible credential (both contexts)
 * val vcCompatible = VerifiableCredential(
 *     context = listOf(
 *         "https://www.w3.org/2018/credentials/v1",
 *         "https://www.w3.org/ns/credentials/v2"
 *     ),
 *     ...
 * )
 * ```
 */
@Serializable
data class VerifiableCredential(
    // Required VC fields
    @SerialName("@context")
    val context: List<String> = listOf("https://www.w3.org/2018/credentials/v1"),
    
    val id: CredentialId? = null,
    
    val type: List<CredentialType>,  // Must include "VerifiableCredential"
    
    val issuer: Issuer,  // VC issuer (IRI - typically DID, but can be URI/URN - or object with id/name)
    
    @SerialName("issuanceDate")
    @Contextual val issuanceDate: Instant,
    
    @SerialName("credentialSubject")
    val credentialSubject: CredentialSubject,  // VC subject (IRI id + claims - typically DID but can be URI/URN)
    
    // Optional VC fields
    @Transient
    val validFrom: Instant? = null,  // VC 2.0: When credential becomes valid (nbf equivalent) - serialized as ISO 8601 via manual JSON building
    
    @Transient
    val expirationDate: Instant? = null,  // Serialized as ISO 8601 via manual JSON building
    
    @SerialName("credentialStatus")
    val credentialStatus: CredentialStatus? = null,
    
    @SerialName("credentialSchema")
    val credentialSchema: CredentialSchema? = null,
    
    val evidence: List<Evidence>? = null,
    
    @SerialName("termsOfUse")
    val termsOfUse: List<TermsOfUse>? = null,
    
    @SerialName("refreshService")
    val refreshService: RefreshService? = null,
    
    // Proof (format-specific: VC-LD, VC-JWT, or SD-JWT-VC)
    val proof: CredentialProof? = null  // Optional in data model, required when verified
) {
    /**
     * Check if credential is currently valid at the given instant (not expired, not revoked).
     * Note: This only checks temporal validity, not cryptographic validity.
     * 
     * @param instant The instant to check validity at. Defaults to current time.
     * @return True if credential is valid at the given instant.
     */
    fun isValid(instant: Instant = Clock.System.now()): Boolean {
        return when {
            validFrom != null && instant < validFrom -> false
            expirationDate != null && instant > expirationDate -> false
            else -> true
        }
    }
    
    /**
     * Check if the credential type includes "VerifiableCredential".
     * Per W3C VC spec, all VCs must include this base type.
     */
    val isVerifiableCredential: Boolean
        get() = type.any { it.value == "VerifiableCredential" }
    
    /**
     * Validate that the context contains at least one valid W3C VC context.
     * 
     * @return True if context is valid (contains VC 1.1 or VC 2.0 context)
     */
    fun hasValidContext(): Boolean {
        val validContexts = setOf(
            "https://www.w3.org/2018/credentials/v1",  // VC 1.1
            "https://www.w3.org/ns/credentials/v2"      // VC 2.0
        )
        return context.any { it in validContexts }
    }
    
    /**
     * Check if credential uses VC 2.0 context.
     */
    val isVc2: Boolean
        get() = context.contains("https://www.w3.org/ns/credentials/v2")
    
    /**
     * Check if credential uses VC 1.1 context.
     */
    val isVc1: Boolean
        get() = context.contains("https://www.w3.org/2018/credentials/v1")
}

