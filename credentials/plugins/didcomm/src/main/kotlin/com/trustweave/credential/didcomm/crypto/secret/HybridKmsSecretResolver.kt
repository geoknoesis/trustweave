package com.trustweave.credential.didcomm.crypto.secret

import com.trustweave.kms.KeyManagementService
import org.didcommx.didcomm.secret.Secret
import org.didcommx.didcomm.secret.SecretResolver
import kotlinx.coroutines.runBlocking

/**
 * Hybrid SecretResolver that uses local keys for DIDComm operations.
 * 
 * This is the recommended approach for production:
 * - DIDComm keys are generated and stored locally (for ECDH operations)
 * - Other keys use cloud KMS (for signing, etc.)
 * - Best of both worlds: security + functionality
 * 
 * **Why Hybrid?**
 * - Many cloud KMS (AWS KMS, Azure Key Vault) don't expose private keys
 * - DIDComm requires private keys for ECDH key agreement
 * - Solution: Store DIDComm keys locally (encrypted), use cloud KMS for other operations
 * 
 * **Security Considerations:**
 * - Local keys must be encrypted at rest
 * - Use secure storage (encrypted file, secure vault, etc.)
 * - Implement key rotation policies
 * - Limit access to key storage
 * 
 * **Example Usage:**
 * ```kotlin
 * val localKeyStore = EncryptedFileLocalKeyStore(
 *     keyFile = File("/secure/didcomm-keys.enc"),
 *     encryptionKey = deriveMasterKey()
 * )
 * val resolver = HybridKmsSecretResolver(localKeyStore)
 * ```
 */
class HybridKmsSecretResolver(
    private val localKeyStore: LocalKeyStore, // For DIDComm keys
    private val cloudKms: KeyManagementService? = null // For other operations (optional)
) : SecretResolver {
    
    private val keyCache = mutableMapOf<String, Secret>()
    
    /**
     * Resolves a secret by key ID.
     * 
     * Always checks local key store first (for DIDComm keys).
     * Falls back to cloud KMS if needed (though this won't work for ECDH).
     * 
     * @param secretId Key ID
     * @return Secret, or null if not found
     */
    override fun resolve(secretId: String): Secret? {
        return runBlocking {
            // Check cache first
            keyCache[secretId]?.let { return@runBlocking it }
            
            // Always check local key store first (for DIDComm keys)
            localKeyStore.get(secretId)?.let {
                keyCache[secretId] = it
                return@runBlocking it
            }
            
            // If not found locally, could fall back to cloud KMS
            // But for DIDComm, we need local keys for ECDH
            // So return null if not found locally
            null
        }
    }
    
    /**
     * Clears the key cache.
     */
    fun clearCache() {
        keyCache.clear()
    }
}

