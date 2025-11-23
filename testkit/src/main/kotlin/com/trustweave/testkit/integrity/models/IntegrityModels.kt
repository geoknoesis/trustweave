package com.trustweave.testkit.integrity.models

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/**
 * Data models for integrity verification testing.
 * These models represent the structure of Verifiable Credentials, Linksets, and related artifacts.
 */

/**
 * Represents a link in a Linkset with href and digestMultibase.
 */
@Serializable
data class Link(
    val href: String,
    val digestMultibase: String,
    val type: String? = null,
    val rel: String? = null
)

/**
 * Represents a Linkset with digestMultibase and array of links.
 */
@Serializable
data class Linkset(
    val id: String? = null,
    val digestMultibase: String,
    val links: List<Link>,
    val context: String? = "https://www.w3.org/ns/json-ld#"
)

/**
 * Represents blockchain anchor evidence embedded in a VC.
 */
@Serializable
data class BlockchainAnchorEvidence(
    val type: String = "BlockchainAnchorEvidence",
    val chainId: String,
    val network: String? = null,
    val txHash: String,
    val digestMultibase: String,
    val contract: String? = null
)

/**
 * Represents credential status pointing to a status service.
 */
@Serializable
data class CredentialStatus(
    val id: String,
    val type: String = "StatusList2021Entry",
    val statusPurpose: String? = "revocation",
    val statusListIndex: String? = null
)

/**
 * Represents a Verifiable Credential structure for testing.
 * Note: This is a simplified model for testing purposes.
 */
data class VerifiableCredential(
    val id: String? = null,
    val type: List<String> = listOf("VerifiableCredential"),
    val issuer: String,
    val credentialSubject: JsonElement,
    val digestMultibase: String,
    val evidence: List<BlockchainAnchorEvidence>? = null,
    val credentialStatus: CredentialStatus? = null,
    val issued: String? = null,
    val expirationDate: String? = null
)

/**
 * Represents an artifact (metadata, provenance, quality report, data file).
 */
data class Artifact(
    val id: String,
    val type: String,
    val content: JsonElement,
    val digestMultibase: String,
    val mediaType: String? = "application/json"
)

/**
 * Represents an anchor service endpoint in a DID document.
 */
@Serializable
data class AnchorServiceEndpoint(
    val chainId: String,
    val anchorLookup: String? = null,
    val baseUrl: String? = null
)

/**
 * Represents a DID service entry for anchor service.
 */
@Serializable
data class AnchorService(
    val id: String = "#anchor-service",
    val type: String = "AnchorService",
    val serviceEndpoint: AnchorServiceEndpoint
)

/**
 * Represents a status service response with anchor information.
 */
@Serializable
data class StatusResponse(
    val revocationStatus: String,
    val anchor: AnchorInfo? = null
)

/**
 * Represents anchor information returned by status service or registry.
 */
@Serializable
data class AnchorInfo(
    val chainId: String,
    val txHash: String,
    val digestMultibase: String,
    val timestamp: Long? = null,
    val contract: String? = null
)

/**
 * Represents an entry in an external anchor registry.
 */
@Serializable
data class AnchorRegistryEntry(
    val digestMultibase: String,
    val chainId: String,
    val txHash: String,
    val timestamp: Long,
    val contract: String? = null,
    val issuer: String? = null
)

/**
 * Represents a manifest for batch anchoring.
 */
@Serializable
data class AnchorManifest(
    val id: String,
    val anchorContext: AnchorContext,
    val anchoredDigests: List<String>,
    val issuer: String? = null,
    val timestamp: Long? = null
)

/**
 * Represents the anchoring context in a manifest.
 */
@Serializable
data class AnchorContext(
    val chainId: String,
    val contract: String? = null,
    val network: String? = null
)

