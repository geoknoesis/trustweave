package org.trustweave.credential.model.vc

import kotlinx.serialization.Contextual
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.trustweave.credential.identifiers.CredentialId
import org.trustweave.credential.model.CredentialType
import org.trustweave.credential.model.Evidence
import kotlin.time.Clock
import kotlin.time.Instant

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
    val type: List<CredentialType>, // Must include "VerifiableCredential"
    val issuer: Issuer, // VC issuer (IRI - typically DID, but can be URI/URN - or object with id/name)
    @SerialName("issuanceDate")
    @Contextual val issuanceDate: Instant? = null,
    @SerialName("credentialSubject")
    val credentialSubject: CredentialSubject, // VC subject (IRI id + claims - typically DID but can be URI/URN)
    // Optional VC fields
    // VC 2.0: replaces issuanceDate; both serialised so parsers can read either version
    @SerialName("validFrom")
    @Contextual val validFrom: Instant? = null,
    // VC 1.1 expiry; use validUntil for VC 2.0
    @SerialName("expirationDate")
    @Contextual val expirationDate: Instant? = null,
    // VC 2.0: replaces expirationDate
    @SerialName("validUntil")
    @Contextual val validUntil: Instant? = null,
    // VC 2.0: human-readable name and description
    val name: String? = null,
    val description: String? = null,
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
    val proof: CredentialProof? = null, // Optional in data model, required when verified
) {
    /**
     * The single source of truth for a credential's *temporal* validity at [instant].
     *
     * Honours VC 1.1 and VC 2.0 fields together: the credential expires at the **earliest** of
     * `expirationDate` and `validUntil` (expired once `instant` is strictly after it), and it is
     * not valid before the **latest** of `validFrom` and `issuanceDate`.
     *
     * This looks at dates only. It says nothing about revocation/status or about the proof; use
     * the credential verifier for those.
     */
    fun temporalValidity(instant: Instant = Clock.System.now()): TemporalValidity {
        val expiry = listOfNotNull(validUntil, expirationDate).minOrNull()
        val notBefore = listOfNotNull(validFrom, issuanceDate).maxOrNull()
        return when {
            expiry != null && instant > expiry -> TemporalValidity.EXPIRED
            notBefore != null && instant < notBefore -> TemporalValidity.NOT_YET_VALID
            else -> TemporalValidity.VALID
        }
    }

    /**
     * Check if the credential is within its validity period at the given instant.
     *
     * Temporal only (see [temporalValidity]): it does **not** check revocation/status or the
     * cryptographic proof.
     *
     * @param instant The instant to check validity at. Defaults to current time.
     * @return True if the instant is inside the credential's validity period.
     */
    fun isValid(instant: Instant = Clock.System.now()): Boolean = temporalValidity(instant) == TemporalValidity.VALID

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
        val validContexts =
            setOf(
                "https://www.w3.org/2018/credentials/v1", // VC 1.1
                "https://www.w3.org/ns/credentials/v2", // VC 2.0
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

/** Outcome of the date-based (temporal) validity check of a credential; see [VerifiableCredential.temporalValidity]. */
enum class TemporalValidity {
    /** Inside the validity period. */
    VALID,

    /** Before `validFrom` / `issuanceDate`. */
    NOT_YET_VALID,

    /** After the earliest of `expirationDate` / `validUntil`. */
    EXPIRED,
}
