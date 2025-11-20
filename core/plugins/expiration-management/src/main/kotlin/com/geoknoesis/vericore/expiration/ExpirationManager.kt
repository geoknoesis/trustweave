package com.geoknoesis.vericore.expiration

import com.geoknoesis.vericore.credential.models.VerifiableCredential
import com.geoknoesis.vericore.credential.wallet.Wallet
import kotlinx.serialization.Serializable
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Expiration status.
 */
enum class ExpirationStatus {
    VALID,
    EXPIRING_SOON,
    EXPIRED
}

/**
 * Credential expiration info.
 */
@Serializable
data class ExpirationInfo(
    val credentialId: String,
    val expirationDate: String?,
    val status: ExpirationStatus,
    val daysUntilExpiration: Int?,
    val canRenew: Boolean = false,
    val renewalService: String? = null
)

/**
 * Expiration policy.
 */
data class ExpirationPolicy(
    val warningDays: Int = 30, // Warn when this many days until expiration
    val autoRenew: Boolean = false,
    val autoRenewDaysBefore: Int = 7
)

/**
 * Expiration management service.
 * 
 * Monitors credential expiration, sends warnings, and manages renewal workflows.
 * 
 * **Example Usage:**
 * ```kotlin
 * val manager = ExpirationManager(policy = ExpirationPolicy(warningDays = 30))
 * 
 * // Check expiration status
 * val status = manager.checkExpiration(credential)
 * 
 * // Get expiring credentials
 * val expiring = manager.getExpiringCredentials(wallet, days = 30)
 * 
 * // Renew credential
 * val renewed = manager.renewCredential(credential, newExpirationDate)
 * ```
 */
interface ExpirationManager {
    /**
     * Check expiration status of a credential.
     * 
     * @param credential Credential to check
     * @param policy Optional expiration policy
     * @return Expiration info
     */
    suspend fun checkExpiration(
        credential: VerifiableCredential,
        policy: ExpirationPolicy = ExpirationPolicy()
    ): ExpirationInfo
    
    /**
     * Get expiring credentials from a wallet.
     * 
     * @param wallet Wallet to check
     * @param days Number of days to look ahead
     * @param policy Optional expiration policy
     * @return List of expiring credentials with expiration info
     */
    suspend fun getExpiringCredentials(
        wallet: Wallet,
        days: Int = 30,
        policy: ExpirationPolicy = ExpirationPolicy()
    ): List<ExpirationInfo>
    
    /**
     * Get expired credentials from a wallet.
     * 
     * @param wallet Wallet to check
     * @return List of expired credentials
     */
    suspend fun getExpiredCredentials(wallet: Wallet): List<VerifiableCredential>
    
    /**
     * Renew a credential (creates new credential with extended expiration).
     * 
     * @param credential Credential to renew
     * @param newExpirationDate New expiration date (ISO 8601)
     * @return Renewed credential (without proof - must be issued separately)
     */
    suspend fun renewCredential(
        credential: VerifiableCredential,
        newExpirationDate: String
    ): VerifiableCredential
    
    /**
     * Batch renew credentials.
     * 
     * @param credentials Credentials to renew
     * @param newExpirationDate New expiration date
     * @return List of renewed credentials
     */
    suspend fun batchRenew(
        credentials: List<VerifiableCredential>,
        newExpirationDate: String
    ): List<VerifiableCredential>
}

/**
 * Simple expiration manager implementation.
 */
class SimpleExpirationManager : ExpirationManager {
    
    override suspend fun checkExpiration(
        credential: VerifiableCredential,
        policy: ExpirationPolicy
    ): ExpirationInfo = withContext(Dispatchers.IO) {
        val expirationDate = credential.expirationDate
        val credentialId = credential.id ?: "unknown"
        
        if (expirationDate == null) {
            return@withContext ExpirationInfo(
                credentialId = credentialId,
                expirationDate = null,
                status = ExpirationStatus.VALID,
                daysUntilExpiration = null,
                canRenew = false
            )
        }
        
        val expiration = Instant.parse(expirationDate)
        val now = Instant.now()
        val daysUntil = ChronoUnit.DAYS.between(now, expiration).toInt()
        
        val status = when {
            daysUntil < 0 -> ExpirationStatus.EXPIRED
            daysUntil <= policy.warningDays -> ExpirationStatus.EXPIRING_SOON
            else -> ExpirationStatus.VALID
        }
        
        val canRenew = credential.refreshService != null || status == ExpirationStatus.EXPIRING_SOON
        
        ExpirationInfo(
            credentialId = credentialId,
            expirationDate = expirationDate,
            status = status,
            daysUntilExpiration = daysUntil,
            canRenew = canRenew,
            renewalService = credential.refreshService?.serviceEndpoint
        )
    }
    
    override suspend fun getExpiringCredentials(
        wallet: Wallet,
        days: Int,
        policy: ExpirationPolicy
    ): List<ExpirationInfo> = withContext(Dispatchers.IO) {
        val credentials = wallet.list()
        val expiring = mutableListOf<ExpirationInfo>()
        
        credentials.forEach { credential ->
            val info = checkExpiration(credential, policy)
            if (info.status == ExpirationStatus.EXPIRING_SOON || 
                (info.daysUntilExpiration != null && info.daysUntilExpiration!! <= days && info.daysUntilExpiration!! >= 0)) {
                expiring.add(info)
            }
        }
        
        expiring.sortedBy { it.daysUntilExpiration ?: Int.MAX_VALUE }
    }
    
    override suspend fun getExpiredCredentials(wallet: Wallet): List<VerifiableCredential> = withContext(Dispatchers.IO) {
        val credentials = wallet.list()
        val now = Instant.now()
        
        credentials.filter { credential ->
            credential.expirationDate?.let { expirationDate ->
                Instant.parse(expirationDate).isBefore(now)
            } ?: false
        }
    }
    
    override suspend fun renewCredential(
        credential: VerifiableCredential,
        newExpirationDate: String
    ): VerifiableCredential = withContext(Dispatchers.IO) {
        credential.copy(
            expirationDate = newExpirationDate,
            issuanceDate = Instant.now().toString(),
            proof = null // Proof must be regenerated
        )
    }
    
    override suspend fun batchRenew(
        credentials: List<VerifiableCredential>,
        newExpirationDate: String
    ): List<VerifiableCredential> = withContext(Dispatchers.IO) {
        credentials.map { renewCredential(it, newExpirationDate) }
    }
}

