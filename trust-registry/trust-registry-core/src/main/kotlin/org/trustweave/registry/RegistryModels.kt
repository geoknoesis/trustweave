package org.trustweave.registry

import kotlinx.datetime.Instant
import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable

@Serializable
data class IssuerRegistration(
    val did: String,
    val name: String,
    val description: String? = null,
    val credentialTypes: List<String> = emptyList(),
    val serviceEndpoint: String? = null,
    val metadata: Map<String, String> = emptyMap(),
)

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
)

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
)

@Serializable
data class VerifierUpdate(
    val name: String? = null,
    val description: String? = null,
    val serviceEndpoint: String? = null,
    val metadata: Map<String, String>? = null,
)

@Serializable
enum class AccreditationStatus { ACTIVE, REVOKED, SUSPENDED, PENDING }

@Serializable
data class RegistryFilter(
    val status: AccreditationStatus? = null,
    val credentialType: String? = null,
    val nameContains: String? = null,
)
