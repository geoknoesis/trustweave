package org.trustweave.wallet

import org.trustweave.credential.model.vc.VerifiableCredential

/**
 * Narrowed capability interface for wallets that support credential collections.
 *
 * Wallets may implement either [CredentialCollections], [CredentialTagging], or the combined
 * [CredentialOrganization], giving callers the ability to depend only on the capability they need.
 */
interface CredentialCollections {
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
}

/**
 * Narrowed capability interface for wallets that support credential tagging and metadata.
 *
 * Wallets may implement either [CredentialCollections], [CredentialTagging], or the combined
 * [CredentialOrganization], giving callers the ability to depend only on the capability they need.
 */
interface CredentialTagging {
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

/**
 * Combined credential organization capabilities for wallets that support both collections and tagging.
 *
 * Extends [CredentialCollections] and [CredentialTagging] so wallets can implement a single
 * interface to provide both capabilities. Code that only needs one capability should prefer
 * the narrower interface.
 *
 * **Example Usage**:
 * ```kotlin
 * val wallet: Wallet = createWallet()
 *
 * // Use narrower interface when possible
 * if (wallet is CredentialCollections) {
 *     val collectionId = wallet.createCollection("My Credentials", "Personal credentials")
 *     wallet.addToCollection(credentialId, collectionId)
 * }
 * if (wallet is CredentialTagging) {
 *     wallet.tagCredential(credentialId, setOf("important", "verified"))
 *     wallet.addMetadata(credentialId, mapOf("source" to "issuer.com"))
 * }
 *
 * // Or use CredentialOrganization for full access
 * if (wallet is CredentialOrganization) {
 *     wallet.createCollection("My Credentials")
 *     wallet.tagCredential(credentialId, setOf("important"))
 * }
 * ```
 */
interface CredentialOrganization : CredentialCollections, CredentialTagging

