package com.geoknoesis.vericore.credential.wallet

import com.geoknoesis.vericore.credential.models.VerifiableCredential

/**
 * Credential organization capabilities.
 * 
 * Optional interface for wallets that support collections, tags, and metadata.
 * 
 * **Example Usage**:
 * ```kotlin
 * val wallet: Wallet = createWallet()
 * 
 * if (wallet is CredentialOrganization) {
 *     // Create collection
 *     val collectionId = wallet.createCollection("My Credentials", "Personal credentials")
 *     
 *     // Add credential to collection
 *     wallet.addToCollection(credentialId, collectionId)
 *     
 *     // Tag credential
 *     wallet.tagCredential(credentialId, setOf("important", "verified"))
 *     
 *     // Add metadata
 *     wallet.addMetadata(credentialId, mapOf("source" to "issuer.com"))
 * }
 * ```
 */
interface CredentialOrganization {
    // Collections
    /**
     * Create a new credential collection.
     * 
     * @param name Collection name
     * @param description Optional description
     * @return Collection ID
     */
    suspend fun createCollection(name: String, description: String? = null): String
    
    /**
     * Get a collection by ID.
     * 
     * @param collectionId Collection ID
     * @return Collection, or null if not found
     */
    suspend fun getCollection(collectionId: String): CredentialCollection?
    
    /**
     * List all collections.
     * 
     * @return List of collections
     */
    suspend fun listCollections(): List<CredentialCollection>
    
    /**
     * Delete a collection.
     * 
     * @param collectionId Collection ID
     * @return true if deleted, false if not found
     */
    suspend fun deleteCollection(collectionId: String): Boolean
    
    /**
     * Add a credential to a collection.
     * 
     * @param credentialId Credential ID
     * @param collectionId Collection ID
     * @return true if added, false if credential or collection not found
     */
    suspend fun addToCollection(credentialId: String, collectionId: String): Boolean
    
    /**
     * Remove a credential from a collection.
     * 
     * @param credentialId Credential ID
     * @param collectionId Collection ID
     * @return true if removed, false if not found
     */
    suspend fun removeFromCollection(credentialId: String, collectionId: String): Boolean
    
    /**
     * Get all credentials in a collection.
     * 
     * @param collectionId Collection ID
     * @return List of credentials in the collection
     */
    suspend fun getCredentialsInCollection(collectionId: String): List<VerifiableCredential>
    
    // Tags
    /**
     * Add tags to a credential.
     * 
     * @param credentialId Credential ID
     * @param tags Set of tags to add
     * @return true if tags were added, false if credential not found
     */
    suspend fun tagCredential(credentialId: String, tags: Set<String>): Boolean
    
    /**
     * Remove tags from a credential.
     * 
     * @param credentialId Credential ID
     * @param tags Set of tags to remove
     * @return true if tags were removed, false if credential not found
     */
    suspend fun untagCredential(credentialId: String, tags: Set<String>): Boolean
    
    /**
     * Get all tags for a credential.
     * 
     * @param credentialId Credential ID
     * @return Set of tags, or empty set if credential not found
     */
    suspend fun getTags(credentialId: String): Set<String>
    
    /**
     * Get all tags used in the wallet.
     * 
     * @return Set of all tags
     */
    suspend fun getAllTags(): Set<String>
    
    /**
     * Find credentials by tag.
     * 
     * @param tag Tag to search for
     * @return List of credentials with the tag
     */
    suspend fun findByTag(tag: String): List<VerifiableCredential>
    
    // Metadata
    /**
     * Add metadata to a credential.
     * 
     * @param credentialId Credential ID
     * @param metadata Metadata map
     * @return true if metadata was added, false if credential not found
     */
    suspend fun addMetadata(credentialId: String, metadata: Map<String, Any>): Boolean
    
    /**
     * Get metadata for a credential.
     * 
     * @param credentialId Credential ID
     * @return Credential metadata, or null if credential not found
     */
    suspend fun getMetadata(credentialId: String): CredentialMetadata?
    
    /**
     * Update notes for a credential.
     * 
     * @param credentialId Credential ID
     * @param notes Notes text, or null to clear notes
     * @return true if notes were updated, false if credential not found
     */
    suspend fun updateNotes(credentialId: String, notes: String?): Boolean
}

