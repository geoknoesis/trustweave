package com.trustweave.wallet

import com.trustweave.did.DidCreationOptions
import com.trustweave.did.DidCreationOptionsBuilder
import com.trustweave.did.DidDocument
import com.trustweave.did.didCreationOptions

/**
 * DID management capability for wallets.
 * 
 * Wallets can implement this to provide DID operations.
 * 
 * **Example Usage**:
 * ```kotlin
 * val wallet: Wallet = createWallet()
 * 
 * if (wallet is DidManagement) {
 *     // Create DID
 *     val did = wallet.createDid("key")
 *     
 *     // Get wallet DID
 *     println("Wallet DID: ${wallet.walletDid}")
 *     
 *     // Resolve DID
 *     val document = wallet.resolveDid(did)
 * }
 * ```
 */
interface DidManagement {
    /**
     * Wallet's own DID.
     */
    val walletDid: String
    
    /**
     * Holder DID (the DID that holds credentials in this wallet).
     */
    val holderDid: String
    
    /**
     * Create a new DID.
     * 
     * @param method DID method (e.g., "key", "web", "ion")
     * @param options Method-specific options
     * @return Created DID string
     */
    suspend fun createDid(method: String, options: DidCreationOptions = DidCreationOptions()): String

    suspend fun createDid(method: String, configure: DidCreationOptionsBuilder.() -> Unit): String =
        createDid(method, didCreationOptions(configure))
    
    /**
     * Get all DIDs managed by this wallet.
     * 
     * @return List of DID strings
     */
    suspend fun getDids(): List<String>
    
    /**
     * Get the primary DID for this wallet.
     * 
     * @return Primary DID string
     */
    suspend fun getPrimaryDid(): String
    
    /**
     * Set the primary DID.
     * 
     * @param did DID string to set as primary
     * @return true if set successfully, false if DID not found
     */
    suspend fun setPrimaryDid(did: String): Boolean
    
    /**
     * Resolve a DID to its DID Document.
     * 
     * @param did DID string to resolve
     * @return DID Document, or null if resolution failed
     */
    suspend fun resolveDid(did: String): DidDocument?
}

/**
 * Key management capability for wallets.
 * 
 * Wallets can implement this to provide key operations.
 * 
 * **Example Usage**:
 * ```kotlin
 * val wallet: Wallet = createWallet()
 * 
 * if (wallet is KeyManagement) {
 *     // Generate key
 *     val keyId = wallet.generateKey("Ed25519")
 *     
 *     // Get keys
 *     val keys = wallet.getKeys()
 *     
 *     // Sign data
 *     val signature = wallet.sign(keyId, data)
 * }
 * ```
 */
interface KeyManagement {
    /**
     * Generate a new cryptographic key.
     * 
     * @param algorithm Key algorithm (e.g., "Ed25519", "secp256k1")
     * @param options Key generation options
     * @return Key ID
     */
    suspend fun generateKey(algorithm: String, options: Map<String, Any?> = emptyMap()): String
    
    /**
     * Get all keys managed by this wallet.
     * 
     * @return List of key information
     */
    suspend fun getKeys(): List<KeyInfo>
    
    /**
     * Get key information by ID.
     * 
     * @param keyId Key ID
     * @return Key information, or null if not found
     */
    suspend fun getKey(keyId: String): KeyInfo?
    
    /**
     * Delete a key.
     * 
     * @param keyId Key ID
     * @return true if deleted, false if not found
     */
    suspend fun deleteKey(keyId: String): Boolean
    
    /**
     * Sign data with a key.
     * 
     * @param keyId Key ID
     * @param data Data to sign
     * @return Signature bytes
     */
    suspend fun sign(keyId: String, data: ByteArray): ByteArray
}

/**
 * Credential issuance capability for wallets.
 * 
 * Wallets can implement this to issue credentials.
 * 
 * **Example Usage**:
 * ```kotlin
 * val wallet: Wallet = createWallet()
 * 
 * if (wallet is CredentialIssuance) {
 *     val credential = wallet.issueCredential(
 *         subjectDid = "did:key:subject",
 *         credentialType = "PersonCredential",
 *         claims = mapOf("name" to "Alice", "email" to "alice@example.com"),
 *         options = CredentialIssuanceOptions(...)
 *     )
 * }
 * ```
 */
interface CredentialIssuance {
    /**
     * Issue a verifiable credential.
     * 
     * @param subjectDid DID of the credential subject
     * @param credentialType Credential type (e.g., "PersonCredential")
     * @param claims Credential claims
     * @param options Issuance options (proof type, key ID, etc.)
     * @return Issued verifiable credential
     */
    suspend fun issueCredential(
        subjectDid: String,
        credentialType: String,
        claims: Map<String, Any>,
        options: com.trustweave.credential.CredentialIssuanceOptions
    ): com.trustweave.credential.models.VerifiableCredential
}

