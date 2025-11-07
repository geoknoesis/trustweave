package io.geoknoesis.vericore.testkit.credential

import io.geoknoesis.vericore.credential.models.VerifiableCredential
import io.geoknoesis.vericore.credential.wallet.CredentialFilter
import io.geoknoesis.vericore.credential.wallet.CredentialQueryBuilder
import io.geoknoesis.vericore.credential.wallet.Wallet
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.util.concurrent.ConcurrentHashMap
import java.util.UUID

/**
 * In-memory credential wallet implementation for testing.
 * 
 * @deprecated Use [BasicWallet] instead. This class is kept for backward compatibility
 * but will be removed in a future version.
 * 
 * Provides thread-safe in-memory storage of credentials.
 * Useful for testing and development scenarios.
 * 
 * **Example Usage**:
 * ```kotlin
 * val wallet = BasicWallet() // Use BasicWallet instead
 * 
 * val credentialId = wallet.store(credential)
 * val retrieved = wallet.get(credentialId)
 * 
 * val credentials = wallet.list(
 *     filter = CredentialFilter(issuer = issuerDid)
 * )
 * ```
 */
@Deprecated("Use BasicWallet instead", ReplaceWith("BasicWallet()"))
class InMemoryCredentialWallet(
    override val walletId: String = UUID.randomUUID().toString()
) : Wallet {
    private val credentials = ConcurrentHashMap<String, VerifiableCredential>()
    
    override suspend fun store(credential: VerifiableCredential): String {
        val id = credential.id ?: UUID.randomUUID().toString()
        credentials[id] = credential
        return id
    }
    
    override suspend fun get(credentialId: String): VerifiableCredential? {
        return credentials[credentialId]
    }
    
    override suspend fun list(filter: CredentialFilter?): List<VerifiableCredential> {
        val allCredentials = credentials.values.toList()
        
        if (filter == null) {
            return allCredentials
        }
        
        return allCredentials.filter { credential ->
            (filter.issuer == null || credential.issuer == filter.issuer) &&
            (filter.type == null || filter.type.any { credential.type.contains(it) }) &&
            (filter.subjectId == null || {
                credential.credentialSubject.jsonObject["id"]?.jsonPrimitive?.content == filter.subjectId
            }()) &&
            (filter.expired == null || {
                credential.expirationDate?.let { expirationDate ->
                    try {
                        val expiration = java.time.Instant.parse(expirationDate)
                        val isExpired = java.time.Instant.now().isAfter(expiration)
                        isExpired == filter.expired
                    } catch (e: Exception) {
                        false
                    }
                } ?: (filter.expired == false)
            }()) &&
            (filter.revoked == null || {
                val isRevoked = credential.credentialStatus != null // TODO: Check actual revocation
                isRevoked == filter.revoked
            }())
        }
    }
    
    override suspend fun delete(credentialId: String): Boolean {
        return credentials.remove(credentialId) != null
    }
    
    override suspend fun query(query: CredentialQueryBuilder.() -> Unit): List<VerifiableCredential> {
        val builder = CredentialQueryBuilder()
        builder.query()
        val predicate = builder.build()
        
        return credentials.values.filter(predicate)
    }
    
    /**
     * Clear all stored credentials.
     * Useful for testing.
     */
    fun clear() {
        credentials.clear()
    }
    
    /**
     * Get the number of stored credentials.
     */
    fun size(): Int = credentials.size
}

