package com.trustweave.testkit.credential

import com.trustweave.credential.models.VerifiableCredential
import com.trustweave.wallet.CredentialFilter
import com.trustweave.wallet.CredentialQueryBuilder
import com.trustweave.wallet.CredentialStorage
import com.trustweave.wallet.Wallet
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Basic wallet implementation.
 *
 * Supports only credential storage - no organization, DID, or KMS features.
 * Perfect for simple use cases where you just need to store and retrieve credentials.
 *
 * **Example Usage**:
 * ```kotlin
 * val wallet = BasicWallet()
 *
 * val id = wallet.store(credential)
 * val credential = wallet.get(id)
 *
 * val credentials = wallet.query {
 *     byIssuer(issuerDid)
 *     notExpired()
 * }
 * ```
 */
class BasicWallet(
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

        val filterType = filter.type // Store in local variable to avoid smart cast issue
        return allCredentials.filter { credential ->
            (filter.issuer == null || credential.issuer == filter.issuer) &&
            (filterType == null || filterType.any { credential.type.contains(it) }) &&
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
                val isRevoked = credential.credentialStatus != null
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
        // Use reflection to call createPredicate() to work around caching issues
        val predicateMethod = builder::class.java.getMethod("createPredicate")
        @Suppress("UNCHECKED_CAST")
        val predicate = predicateMethod.invoke(builder) as (VerifiableCredential) -> Boolean

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

