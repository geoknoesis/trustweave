package com.geoknoesis.vericore.versioning

import com.geoknoesis.vericore.credential.models.VerifiableCredential
import kotlinx.serialization.Serializable
import java.time.Instant
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Credential version information.
 */
@Serializable
data class CredentialVersion(
    val versionId: String,
    val credentialId: String,
    val version: Int,
    val credential: VerifiableCredential,
    val createdAt: String, // ISO 8601
    val createdBy: String? = null, // DID or user identifier
    val changeReason: String? = null,
    val previousVersionId: String? = null
)

/**
 * Credential versioning service.
 * 
 * Tracks versions and history of credentials, allowing rollback and lineage tracking.
 * 
 * **Example Usage:**
 * ```kotlin
 * val versioning = CredentialVersioningService()
 * 
 * // Create initial version
 * val v1 = versioning.createVersion(credential, "Initial issuance")
 * 
 * // Update credential and create new version
 * val updatedCredential = credential.copy(expirationDate = newDate)
 * val v2 = versioning.createVersion(updatedCredential, "Extended expiration", previousVersionId = v1.versionId)
 * 
 * // Get version history
 * val history = versioning.getVersionHistory(credential.id)
 * 
 * // Rollback to previous version
 * val rolledBack = versioning.rollbackToVersion(credential.id, v1.versionId)
 * ```
 */
interface CredentialVersioning {
    /**
     * Create a new version of a credential.
     * 
     * @param credential The credential to version
     * @param changeReason Reason for the change
     * @param createdBy Who created this version
     * @param previousVersionId Previous version ID (for linking)
     * @return Created version
     */
    suspend fun createVersion(
        credential: VerifiableCredential,
        changeReason: String? = null,
        createdBy: String? = null,
        previousVersionId: String? = null
    ): CredentialVersion
    
    /**
     * Get version history for a credential.
     * 
     * @param credentialId Credential ID
     * @return List of versions (newest first)
     */
    suspend fun getVersionHistory(credentialId: String): List<CredentialVersion>
    
    /**
     * Get a specific version.
     * 
     * @param versionId Version ID
     * @return Version, or null if not found
     */
    suspend fun getVersion(versionId: String): CredentialVersion?
    
    /**
     * Get the latest version of a credential.
     * 
     * @param credentialId Credential ID
     * @return Latest version, or null if not found
     */
    suspend fun getLatestVersion(credentialId: String): CredentialVersion?
    
    /**
     * Rollback to a previous version.
     * 
     * @param credentialId Credential ID
     * @param versionId Version to rollback to
     * @return New version created from rollback
     */
    suspend fun rollbackToVersion(
        credentialId: String,
        versionId: String,
        changeReason: String = "Rollback to version $versionId"
    ): CredentialVersion?
    
    /**
     * Get credential lineage (all related credentials).
     * 
     * @param credentialId Credential ID
     * @return List of all versions in lineage
     */
    suspend fun getLineage(credentialId: String): List<CredentialVersion>
}

/**
 * In-memory credential versioning implementation.
 */
class InMemoryCredentialVersioning : CredentialVersioning {
    private val versions = ConcurrentHashMap<String, MutableList<CredentialVersion>>()
    private val versionIndex = ConcurrentHashMap<String, CredentialVersion>()
    private val lock = Any()
    
    override suspend fun createVersion(
        credential: VerifiableCredential,
        changeReason: String?,
        createdBy: String?,
        previousVersionId: String?
    ): CredentialVersion = withContext(Dispatchers.IO) {
        val credentialId = credential.id ?: UUID.randomUUID().toString()
        val versionId = UUID.randomUUID().toString()
        
        synchronized(lock) {
            val existingVersions = versions[credentialId] ?: mutableListOf()
            val nextVersion = existingVersions.size + 1
            
            val version = CredentialVersion(
                versionId = versionId,
                credentialId = credentialId,
                version = nextVersion,
                credential = credential.copy(id = credentialId),
                createdAt = Instant.now().toString(),
                createdBy = createdBy,
                changeReason = changeReason,
                previousVersionId = previousVersionId
            )
            
            versions.computeIfAbsent(credentialId) { mutableListOf() }.add(version)
            versionIndex[versionId] = version
            
            version
        }
    }
    
    override suspend fun getVersionHistory(credentialId: String): List<CredentialVersion> = withContext(Dispatchers.IO) {
        synchronized(lock) {
            versions[credentialId]?.sortedByDescending { it.version } ?: emptyList()
        }
    }
    
    override suspend fun getVersion(versionId: String): CredentialVersion? = withContext(Dispatchers.IO) {
        versionIndex[versionId]
    }
    
    override suspend fun getLatestVersion(credentialId: String): CredentialVersion? = withContext(Dispatchers.IO) {
        synchronized(lock) {
            versions[credentialId]?.maxByOrNull { it.version }
        }
    }
    
    override suspend fun rollbackToVersion(
        credentialId: String,
        versionId: String,
        changeReason: String
    ): CredentialVersion? = withContext(Dispatchers.IO) {
        val targetVersion = getVersion(versionId) ?: return@withContext null
        if (targetVersion.credentialId != credentialId) return@withContext null
        
        createVersion(
            credential = targetVersion.credential,
            changeReason = changeReason,
            previousVersionId = versionId
        )
    }
    
    override suspend fun getLineage(credentialId: String): List<CredentialVersion> = withContext(Dispatchers.IO) {
        getVersionHistory(credentialId)
    }
}

