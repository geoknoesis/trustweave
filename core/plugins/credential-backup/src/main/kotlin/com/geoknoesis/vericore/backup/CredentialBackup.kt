package com.geoknoesis.vericore.backup

import com.geoknoesis.vericore.credential.models.VerifiableCredential
import com.geoknoesis.vericore.credential.wallet.Wallet
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Backup data model.
 */
@Serializable
data class CredentialBackup(
    val id: String,
    val version: String = "1.0",
    val createdAt: String, // ISO 8601
    val walletId: String? = null,
    val credentials: List<VerifiableCredential>,
    val metadata: Map<String, String> = emptyMap()
)

/**
 * Credential backup and recovery service.
 * 
 * Provides export/import functionality for credentials and wallets.
 * 
 * **Example Usage:**
 * ```kotlin
 * val backupService = CredentialBackupService()
 * 
 * // Export credentials from wallet
 * val backup = backupService.exportWallet(wallet)
 * val backupJson = backupService.serializeBackup(backup)
 * 
 * // Save to file or send to cloud storage
 * File("backup.json").writeText(backupJson)
 * 
 * // Import credentials
 * val restoredBackup = backupService.deserializeBackup(backupJson)
 * backupService.importToWallet(wallet, restoredBackup)
 * ```
 */
interface CredentialBackupService {
    /**
     * Export all credentials from a wallet.
     * 
     * @param wallet Wallet to export
     * @return Backup data
     */
    suspend fun exportWallet(wallet: Wallet): CredentialBackup
    
    /**
     * Export specific credentials.
     * 
     * @param credentials Credentials to export
     * @param walletId Optional wallet ID
     * @return Backup data
     */
    suspend fun exportCredentials(
        credentials: List<VerifiableCredential>,
        walletId: String? = null
    ): CredentialBackup
    
    /**
     * Serialize backup to JSON string.
     * 
     * @param backup Backup to serialize
     * @return JSON string
     */
    fun serializeBackup(backup: CredentialBackup): String
    
    /**
     * Deserialize backup from JSON string.
     * 
     * @param json JSON string
     * @return Backup data
     */
    fun deserializeBackup(json: String): CredentialBackup
    
    /**
     * Import credentials from backup to wallet.
     * 
     * @param wallet Target wallet
     * @param backup Backup data
     * @param overwrite Whether to overwrite existing credentials
     * @return Number of credentials imported
     */
    suspend fun importToWallet(
        wallet: Wallet,
        backup: CredentialBackup,
        overwrite: Boolean = false
    ): Int
    
    /**
     * Validate backup integrity.
     * 
     * @param backup Backup to validate
     * @return true if valid
     */
    suspend fun validateBackup(backup: CredentialBackup): Boolean
}

/**
 * Simple credential backup service implementation.
 */
class SimpleCredentialBackupService(
    private val json: Json = Json { prettyPrint = true; encodeDefaults = false }
) : CredentialBackupService {
    
    override suspend fun exportWallet(wallet: Wallet): CredentialBackup = withContext(Dispatchers.IO) {
        val credentials = wallet.list()
        
        CredentialBackup(
            id = UUID.randomUUID().toString(),
            version = "1.0",
            createdAt = Instant.now().toString(),
            walletId = wallet.walletId,
            credentials = credentials,
            metadata = mapOf(
                "walletType" to wallet.javaClass.simpleName,
                "credentialCount" to credentials.size.toString()
            )
        )
    }
    
    override suspend fun exportCredentials(
        credentials: List<VerifiableCredential>,
        walletId: String?
    ): CredentialBackup = withContext(Dispatchers.IO) {
        CredentialBackup(
            id = UUID.randomUUID().toString(),
            version = "1.0",
            createdAt = Instant.now().toString(),
            walletId = walletId,
            credentials = credentials,
            metadata = mapOf(
                "credentialCount" to credentials.size.toString()
            )
        )
    }
    
    override fun serializeBackup(backup: CredentialBackup): String {
        return json.encodeToString(CredentialBackup.serializer(), backup)
    }
    
    override fun deserializeBackup(json: String): CredentialBackup {
        return this.json.decodeFromString(CredentialBackup.serializer(), json)
    }
    
    override suspend fun importToWallet(
        wallet: Wallet,
        backup: CredentialBackup,
        overwrite: Boolean
    ): Int = withContext(Dispatchers.IO) {
        var imported = 0
        
        backup.credentials.forEach { credential ->
            try {
                val existing = credential.id?.let { wallet.get(it) }
                
                if (existing == null || overwrite) {
                    wallet.store(credential)
                    imported++
                }
            } catch (e: Exception) {
                // Log error but continue
                println("Failed to import credential ${credential.id}: ${e.message}")
            }
        }
        
        imported
    }
    
    override suspend fun validateBackup(backup: CredentialBackup): Boolean = withContext(Dispatchers.IO) {
        try {
            // Basic validation
            if (backup.id.isBlank()) return@withContext false
            if (backup.version.isBlank()) return@withContext false
            if (backup.createdAt.isBlank()) return@withContext false
            
            // Validate credentials
            backup.credentials.forEach { credential ->
                if (credential.issuer.isBlank()) return@withContext false
                if (credential.type.isEmpty()) return@withContext false
                if (credential.issuanceDate.isBlank()) return@withContext false
            }
            
            true
        } catch (e: Exception) {
            false
        }
    }
}

