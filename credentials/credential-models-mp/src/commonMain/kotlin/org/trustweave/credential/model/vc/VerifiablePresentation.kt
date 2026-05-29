package org.trustweave.credential.model.vc

import org.trustweave.credential.identifiers.CredentialId
import org.trustweave.credential.model.CredentialType
import org.trustweave.credential.model.vc.CredentialProof
import org.trustweave.core.identifiers.Iri
import org.trustweave.did.identifiers.Did
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName
import kotlinx.serialization.Contextual
import kotlinx.serialization.Transient
import kotlinx.datetime.Instant

/**
 * Verifiable Presentation as defined by W3C Verifiable Credentials Data Model.
 * 
 * Supports both W3C VC 1.1 and VC 2.0 Data Models.
 * A Verifiable Presentation is a collection of Verifiable Credentials with optional proof.
 * 
 * **VC Version Support:**
 * - **VC 1.1**: Uses `https://www.w3.org/2018/credentials/v1` context
 * - **VC 2.0**: Uses `https://www.w3.org/ns/credentials/v2` context (or both contexts for compatibility)
 * 
 * **Examples:**
 * ```kotlin
 * val vp = VerifiablePresentation(
 *     id = CredentialId("https://example.com/presentations/123"),
 *     type = listOf(CredentialType("VerifiablePresentation")),
 *     holder = Did("did:example:holder"),
 *     verifiableCredential = listOf(credential1, credential2),
 *     proof = CredentialProof.LinkedDataProof(...)
 * )
 * ```
 */
@Serializable
data class VerifiablePresentation(
    // Required VP fields
    @SerialName("@context")
    val context: List<String> = listOf("https://www.w3.org/2018/credentials/v1"),
    
    val id: CredentialId? = null,
    
    val type: List<CredentialType>,  // Must include "VerifiablePresentation"
    
    val holder: Iri,  // Holder IRI (typically DID, but can be URI/URN)
    
    @SerialName("verifiableCredential")
    val verifiableCredential: List<VerifiableCredential>,
    
    // Optional VP fields
    @Transient
    val expirationDate: Instant? = null,  // Serialized as ISO 8601 via manual JSON building
    
    val proof: CredentialProof? = null,  // Optional proof for the presentation
    
    // Presentation request fields (for authentication flows)
    val challenge: String? = null,  // Challenge string for authentication
    val domain: String? = null  // Domain string for authentication
) {
    /**
     * Check if the presentation type includes "VerifiablePresentation".
     * Per W3C VC spec, all VPs must include this base type.
     */
    val isVerifiablePresentation: Boolean
        get() = type.any { it.value == "VerifiablePresentation" }
    
    /**
     * Check if holder is a DID.
     */
    val holderIsDid: Boolean
        get() = holder.isDid
    
    companion object {
        /**
         * Create VerifiablePresentation with DID holder (convenience method).
         */
        fun fromDidHolder(
            holder: Did,
            verifiableCredential: List<VerifiableCredential>,
            id: CredentialId? = null,
            type: List<CredentialType> = listOf(CredentialType.fromString("VerifiablePresentation")),
            expirationDate: Instant? = null,
            proof: CredentialProof? = null,
            challenge: String? = null,
            domain: String? = null
        ): VerifiablePresentation {
            return VerifiablePresentation(
                id = id,
                type = type,
                holder = holder,  // Did extends Iri
                verifiableCredential = verifiableCredential,
                expirationDate = expirationDate,
                proof = proof,
                challenge = challenge,
                domain = domain
            )
        }
    }
}

