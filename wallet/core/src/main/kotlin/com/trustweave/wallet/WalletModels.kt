package com.trustweave.wallet

import com.trustweave.credential.models.VerifiableCredential
import java.time.Instant

/**
 * Credential collection model.
 * 
 * @param id Collection ID
 * @param name Collection name
 * @param description Optional description
 * @param createdAt Creation timestamp
 * @param credentialCount Number of credentials in collection
 */
data class CredentialCollection(
    val id: String,
    val name: String,
    val description: String? = null,
    val createdAt: Instant = Instant.now(),
    val credentialCount: Int = 0
)

/**
 * Credential metadata model.
 * 
 * @param credentialId Credential ID
 * @param notes Optional notes
 * @param tags Set of tags
 * @param metadata Custom metadata map
 * @param createdAt Creation timestamp
 * @param updatedAt Last update timestamp
 */
data class CredentialMetadata(
    val credentialId: String,
    val notes: String? = null,
    val tags: Set<String> = emptySet(),
    val metadata: Map<String, Any> = emptyMap(),
    val createdAt: Instant = Instant.now(),
    val updatedAt: Instant = Instant.now()
)

/**
 * Key information model.
 * 
 * @param id Key ID
 * @param algorithm Key algorithm (e.g., "Ed25519", "secp256k1")
 * @param publicKeyJwk Public key in JWK format (optional)
 * @param publicKeyMultibase Public key in multibase format (optional)
 * @param createdAt Creation timestamp
 */
data class KeyInfo(
    val id: String,
    val algorithm: String,
    val publicKeyJwk: Map<String, Any?>? = null,
    val publicKeyMultibase: String? = null,
    val createdAt: Instant = Instant.now()
)

/**
 * Wallet statistics model.
 * 
 * @param totalCredentials Total number of credentials
 * @param validCredentials Number of valid credentials (not expired, not revoked, has proof)
 * @param expiredCredentials Number of expired credentials
 * @param revokedCredentials Number of revoked credentials
 * @param collectionsCount Number of collections (if organization supported)
 * @param tagsCount Number of unique tags (if organization supported)
 * @param archivedCount Number of archived credentials (if lifecycle supported)
 */
data class WalletStatistics(
    val totalCredentials: Int = 0,
    val validCredentials: Int = 0,
    val expiredCredentials: Int = 0,
    val revokedCredentials: Int = 0,
    val collectionsCount: Int = 0,
    val tagsCount: Int = 0,
    val archivedCount: Int = 0
)

