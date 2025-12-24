package org.trustweave.credential.didcomm.crypto.secret

import org.didcommx.didcomm.secret.Secret

/**
 * Local key store for DIDComm keys.
 *
 * Stores keys locally for ECDH operations. Keys should be encrypted at rest.
 *
 * **Security Considerations:**
 * - Keys must be encrypted at rest
 * - Keys should be stored in a secure location
 * - Consider key rotation policies
 * - Implement access control
 *
 * **Example Usage:**
 * ```kotlin
 * val keyStore = InMemoryLocalKeyStore()
 * val secret = Secret(...)
 * keyStore.store("did:key:issuer#key-1", secret)
 * val retrieved = keyStore.get("did:key:issuer#key-1")
 * ```
 */
interface LocalKeyStore {
    /**
     * Gets a secret by key ID.
     *
     * @param keyId Key ID (format: did:method:id#key-id or just key-id)
     * @return Secret, or null if not found
     */
    suspend fun get(keyId: String): Secret?

    /**
     * Stores a secret.
     *
     * @param keyId Key ID
     * @param secret Secret to store
     */
    suspend fun store(keyId: String, secret: Secret)

    /**
     * Deletes a secret.
     *
     * @param keyId Key ID
     * @return true if deleted, false if not found
     */
    suspend fun delete(keyId: String): Boolean

    /**
     * Lists all key IDs in the store.
     *
     * @return List of key IDs
     */
    suspend fun list(): List<String>
}

/**
 * In-memory local key store (for testing).
 *
 * ⚠️ **WARNING**: Keys are stored in plain memory. Not suitable for production.
 * Use EncryptedFileLocalKeyStore or similar for production.
 */
class InMemoryLocalKeyStore : LocalKeyStore {
    private val keys = java.util.concurrent.ConcurrentHashMap<String, Secret>()

    override suspend fun get(keyId: String): Secret? = keys[keyId]

    override suspend fun store(keyId: String, secret: Secret) {
        keys[keyId] = secret
    }

    override suspend fun delete(keyId: String): Boolean {
        return keys.remove(keyId) != null
    }

    override suspend fun list(): List<String> = keys.keys.toList()
}

