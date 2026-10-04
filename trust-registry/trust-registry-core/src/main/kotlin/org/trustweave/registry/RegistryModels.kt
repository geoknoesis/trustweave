package org.trustweave.registry

import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable
import kotlin.time.Instant

@Serializable
data class IssuerRegistration(
    val did: String,
    val name: String,
    val description: String? = null,
    val credentialTypes: List<String> = emptyList(),
    val serviceEndpoint: String? = null,
    val metadata: Map<String, String> = emptyMap(),
)

/**
 * @property revocationReason Reason supplied to [TrustRegistry.revokeIssuer]; cleared on
 *                            re-activation, null when never revoked or no reason was given.
 */
@Serializable
data class IssuerRecord(
    val did: String,
    val name: String,
    val description: String? = null,
    val credentialTypes: List<String> = emptyList(),
    val serviceEndpoint: String? = null,
    val status: AccreditationStatus,
    @Contextual val registeredAt: Instant,
    @Contextual val updatedAt: Instant,
    val metadata: Map<String, String> = emptyMap(),
    val revocationReason: String? = null,
) {
    /** Binary-compatible constructor from before [revocationReason] existed. */
    constructor(
        did: String,
        name: String,
        description: String?,
        credentialTypes: List<String>,
        serviceEndpoint: String?,
        status: AccreditationStatus,
        registeredAt: Instant,
        updatedAt: Instant,
        metadata: Map<String, String>,
    ) : this(did, name, description, credentialTypes, serviceEndpoint, status, registeredAt, updatedAt, metadata, null)
}

@Serializable
data class IssuerUpdate(
    val name: String? = null,
    val description: String? = null,
    val credentialTypes: List<String>? = null,
    val serviceEndpoint: String? = null,
    val metadata: Map<String, String>? = null,
)

@Serializable
data class VerifierRegistration(
    val did: String,
    val name: String,
    val description: String? = null,
    val serviceEndpoint: String? = null,
    val metadata: Map<String, String> = emptyMap(),
)

/**
 * @property revocationReason Reason supplied to [TrustRegistry.revokeVerifier]; cleared on
 *                            re-activation, null when never revoked or no reason was given.
 */
@Serializable
data class VerifierRecord(
    val did: String,
    val name: String,
    val description: String? = null,
    val serviceEndpoint: String? = null,
    val status: AccreditationStatus,
    @Contextual val registeredAt: Instant,
    @Contextual val updatedAt: Instant,
    val metadata: Map<String, String> = emptyMap(),
    val revocationReason: String? = null,
) {
    /** Binary-compatible constructor from before [revocationReason] existed. */
    constructor(
        did: String,
        name: String,
        description: String?,
        serviceEndpoint: String?,
        status: AccreditationStatus,
        registeredAt: Instant,
        updatedAt: Instant,
        metadata: Map<String, String>,
    ) : this(did, name, description, serviceEndpoint, status, registeredAt, updatedAt, metadata, null)
}

@Serializable
data class VerifierUpdate(
    val name: String? = null,
    val description: String? = null,
    val serviceEndpoint: String? = null,
    val metadata: Map<String, String>? = null,
)

/**
 * Accreditation status of a registry participant.
 *
 * [UNKNOWN] means the DID has never been registered — it is distinct from
 * [PENDING] (registered, awaiting approval) and MUST NOT be treated as such
 * by authorization decisions.
 */
@Serializable
enum class AccreditationStatus { ACTIVE, REVOKED, SUSPENDED, PENDING, UNKNOWN }

@Serializable
data class RegistryFilter(
    val status: AccreditationStatus? = null,
    val credentialType: String? = null,
    val nameContains: String? = null,
)

/** Which side of the registry a participant is registered on. */
@Serializable
enum class ParticipantRole { ISSUER, VERIFIER }

/**
 * One entry of a participant's accreditation history (see [TrustRegistry.statusHistory]).
 *
 * @property from Status before the change; null for the registration itself.
 * @property reason Revocation reason supplied with the change, if any.
 */
@Serializable
data class StatusChange(
    val did: String,
    val role: ParticipantRole,
    val from: AccreditationStatus?,
    val to: AccreditationStatus,
    val reason: String? = null,
    @Contextual val changedAt: Instant,
)
