package com.trustweave.credential.crypto.secret

import org.didcommx.didcomm.secret.Secret

/**
 * Local key store interface for storing cryptographic secrets.
 * 
 * Used by protocols that need to store keys locally (DIDComm, OIDC4VCI, etc.).
 * 
 * **Example Usage:**
 * ```kotlin
 * val keyStore: LocalKeyStore = EncryptedFileLocalKeyStore(...)
 * 
 * val secret = Secret(...)
 * keyStore.store("did:key:issuer#key-1", secret)
 * val retrieved = keyStore.get("did:key:issuer#key-1")
 * ```
 */
interface LocalKeyStore {
    /**
     * Gets a secret by key ID.
     * 
     * @param keyId Key identifier
     * @return Secret, or null if not found
     */
    suspend fun get(keyId: String): Secret?
    
    /**
     * Stores a secret.
     * 
     * @param keyId Key identifier
     * @param secret Secret to store
     */
    suspend fun store(keyId: String, secret: Secret)
    
    /**
     * Deletes a secret.
     * 
     * @param keyId Key identifier
     * @return true if deleted, false if not found
     */
    suspend fun delete(keyId: String): Boolean
    
    /**
     * Lists all key IDs.
     * 
     * @return List of key IDs
     */
    suspend fun list(): List<String>
}

