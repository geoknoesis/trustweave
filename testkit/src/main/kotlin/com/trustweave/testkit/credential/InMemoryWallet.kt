package com.trustweave.testkit.credential

import com.trustweave.credential.model.vc.VerifiableCredential
import com.trustweave.credential.model.vc.VerifiablePresentation
import com.trustweave.credential.model.CredentialType
import com.trustweave.wallet.CredentialCollection
import com.trustweave.wallet.CredentialFilter
import com.trustweave.wallet.CredentialLifecycle
import com.trustweave.wallet.CredentialMetadata
import com.trustweave.wallet.CredentialOrganization
import com.trustweave.wallet.CredentialPresentation
import com.trustweave.wallet.CredentialQueryBuilder
import com.trustweave.wallet.DidManagement
import com.trustweave.wallet.Wallet
import com.trustweave.did.model.DidDocument
import com.trustweave.did.DidCreationOptions
import com.trustweave.did.identifiers.Did
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.datetime.Instant
import kotlinx.datetime.Clock
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * In-memory full-featured wallet for testing.
 *
 * Implements all capability interfaces: CredentialOrganization, CredentialLifecycle,
 * CredentialPresentation, and DidManagement. Perfect for testing and development.
 *
 * **Example Usage**:
 * ```kotlin
 * val wallet = InMemoryWallet()
 *
 * // Core operations
 * val id = wallet.store(credential)
 *
 * // Organization features
 * val collectionId = wallet.createCollection("My Collection")
 * wallet.addToCollection(id, collectionId)
 * wallet.tagCredential(id, setOf("important"))
 *
 * // Lifecycle features
 * wallet.archive(id)
 * val archived = wallet.getArchived()
 *
 * // Presentation features
 * val presentation = wallet.createPresentation(
 *     credentialIds = listOf(id),
 *     holderDid = wallet.holderDid,
 *     // options parameter removed - use holderDid directly
 * )
 * ```
 */
class InMemoryWallet(
    override val walletId: String = UUID.randomUUID().toString(),
    walletDid: String? = null,
    holderDid: String? = null,
    walletDidObj: Did? = null,
    holderDidObj: Did? = null
) : Wallet,
    CredentialOrganization,
    CredentialLifecycle,
    CredentialPresentation,
    DidManagement {

    override val walletDid: String = walletDidObj?.value ?: walletDid ?: "did:key:test-wallet-$walletId"
    override val holderDid: String = holderDidObj?.value ?: holderDid ?: "did:key:test-holder-$walletId"

    // Storage
    private val credentials = ConcurrentHashMap<String, VerifiableCredential>()
    private val archivedCredentials = ConcurrentHashMap<String, VerifiableCredential>()

    // Organization
    private val collections = ConcurrentHashMap<String, CredentialCollection>()
    private val credentialCollections = ConcurrentHashMap<String, MutableSet<String>>() // credentialId -> collectionIds
    private val collectionCredentials = ConcurrentHashMap<String, MutableSet<String>>() // collectionId -> credentialIds
    private val credentialTags = ConcurrentHashMap<String, MutableSet<String>>()
    private val credentialMetadata = ConcurrentHashMap<String, CredentialMetadata>()

    // Storage implementation
    override suspend fun store(credential: VerifiableCredential): String {
        val id = credential.id?.value ?: UUID.randomUUID().toString()
        credentials[id] = credential
        if (!credentialMetadata.containsKey(id)) {
            credentialMetadata[id] = CredentialMetadata(
                credentialId = id,
                createdAt = Clock.System.now(),
                updatedAt = Clock.System.now()
            )
        }
        return id
    }

    override suspend fun get(credentialId: String): VerifiableCredential? {
        return credentials[credentialId] ?: archivedCredentials[credentialId]
    }

    override suspend fun list(filter: CredentialFilter?): List<VerifiableCredential> {
        val allCredentials = credentials.values.toList()
        return if (filter == null) {
            allCredentials
        } else {
            val filterType = filter.type // Store in local variable to avoid smart cast issue
            allCredentials.filter { credential ->
                (filter.issuer == null || credential.issuer.id.value == filter.issuer) &&
                (filterType == null || filterType.any { typeStr -> credential.type.any { ct -> ct.value == typeStr } }) &&
                (filter.subjectId == null || {
                    credential.credentialSubject.id?.value == filter.subjectId
                }()) &&
                (filter.expired == null || {
                    credential.expirationDate?.let { expirationDate ->
                        val isExpired = Clock.System.now() > expirationDate
                        isExpired == filter.expired
                    } ?: (filter.expired == false)
                }()) &&
                (filter.revoked == null || {
                    val isRevoked = credential.credentialStatus != null
                    isRevoked == filter.revoked
                }())
            }
        }
    }

    override suspend fun delete(credentialId: String): Boolean {
        val removed = credentials.remove(credentialId) != null || archivedCredentials.remove(credentialId) != null
        if (removed) {
            credentialCollections.remove(credentialId)
            credentialTags.remove(credentialId)
            credentialMetadata.remove(credentialId)
            collectionCredentials.values.forEach { it.remove(credentialId) }
        }
        return removed
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

    // Organization implementation
    override suspend fun createCollection(name: String, description: String?): String {
        val id = UUID.randomUUID().toString()
        collections[id] = CredentialCollection(
            id = id,
            name = name,
            description = description,
            createdAt = Clock.System.now(),
            credentialCount = 0
        )
        collectionCredentials[id] = mutableSetOf()
        return id
    }

    override suspend fun getCollection(collectionId: String): CredentialCollection? {
        val collection = collections[collectionId] ?: return null
        // Update credentialCount dynamically to match listCollections() behavior
        return collection.copy(credentialCount = collectionCredentials[collectionId]?.size ?: 0)
    }

    override suspend fun listCollections(): List<CredentialCollection> {
        return collections.values.map { collection ->
            collection.copy(credentialCount = collectionCredentials[collection.id]?.size ?: 0)
        }
    }

    override suspend fun deleteCollection(collectionId: String): Boolean {
        val removed = collections.remove(collectionId) != null
        if (removed) {
            collectionCredentials[collectionId]?.forEach { credentialId ->
                credentialCollections[credentialId]?.remove(collectionId)
            }
            collectionCredentials.remove(collectionId)
        }
        return removed
    }

    override suspend fun addToCollection(credentialId: String, collectionId: String): Boolean {
        if (!credentials.containsKey(credentialId) && !archivedCredentials.containsKey(credentialId)) {
            return false
        }
        if (!collections.containsKey(collectionId)) {
            return false
        }
        credentialCollections.getOrPut(credentialId) { mutableSetOf() }.add(collectionId)
        collectionCredentials.getOrPut(collectionId) { mutableSetOf() }.add(credentialId)
        return true
    }

    override suspend fun removeFromCollection(credentialId: String, collectionId: String): Boolean {
        credentialCollections[credentialId]?.remove(collectionId)
        collectionCredentials[collectionId]?.remove(credentialId)
        return true
    }

    override suspend fun getCredentialsInCollection(collectionId: String): List<VerifiableCredential> {
        val credentialIds = collectionCredentials[collectionId] ?: return emptyList()
        return credentialIds.mapNotNull { id -> credentials[id] ?: archivedCredentials[id] }
    }

    override suspend fun tagCredential(credentialId: String, tags: Set<String>): Boolean {
        if (!credentials.containsKey(credentialId) && !archivedCredentials.containsKey(credentialId)) {
            return false
        }
        credentialTags.getOrPut(credentialId) { mutableSetOf() }.addAll(tags)
        updateMetadata(credentialId) { it.copy(tags = credentialTags[credentialId] ?: emptySet()) }
        return true
    }

    override suspend fun untagCredential(credentialId: String, tags: Set<String>): Boolean {
        credentialTags[credentialId]?.removeAll(tags)
        updateMetadata(credentialId) { it.copy(tags = credentialTags[credentialId] ?: emptySet()) }
        return true
    }

    override suspend fun getTags(credentialId: String): Set<String> {
        return credentialTags[credentialId] ?: emptySet()
    }

    override suspend fun getAllTags(): Set<String> {
        return credentialTags.values.flatten().toSet()
    }

    override suspend fun findByTag(tag: String): List<VerifiableCredential> {
        return credentialTags.entries
            .filter { tag in it.value }
            .mapNotNull { (id, _) -> credentials[id] ?: archivedCredentials[id] }
    }

    override suspend fun addMetadata(credentialId: String, metadata: Map<String, Any>): Boolean {
        if (!credentials.containsKey(credentialId) && !archivedCredentials.containsKey(credentialId)) {
            return false
        }
        updateMetadata(credentialId) { existing ->
            existing.copy(
                metadata = existing.metadata + metadata,
                updatedAt = Clock.System.now()
            )
        }
        return true
    }

    override suspend fun getMetadata(credentialId: String): CredentialMetadata? {
        return credentialMetadata[credentialId]
    }

    override suspend fun updateNotes(credentialId: String, notes: String?): Boolean {
        if (!credentials.containsKey(credentialId) && !archivedCredentials.containsKey(credentialId)) {
            return false
        }
        updateMetadata(credentialId) { it.copy(notes = notes, updatedAt = Clock.System.now()) }
        return true
    }

    // Lifecycle implementation
    override suspend fun archive(credentialId: String): Boolean {
        val credential = credentials.remove(credentialId) ?: return false
        archivedCredentials[credentialId] = credential
        return true
    }

    override suspend fun unarchive(credentialId: String): Boolean {
        val credential = archivedCredentials.remove(credentialId) ?: return false
        credentials[credentialId] = credential
        return true
    }

    override suspend fun getArchived(): List<VerifiableCredential> {
        return archivedCredentials.values.toList()
    }

    override suspend fun refreshCredential(credentialId: String): VerifiableCredential? {
        // In-memory implementation: just return the credential as-is
        // Real implementation would call refresh service
        return credentials[credentialId] ?: archivedCredentials[credentialId]
    }

    // Presentation implementation
    override suspend fun createPresentation(
        credentialIds: List<String>,
        holderDid: String,
        options: com.trustweave.credential.proof.ProofOptions
    ): VerifiablePresentation {
        val credentialsToInclude = credentialIds.mapNotNull { id ->
            credentials[id] ?: archivedCredentials[id]
        }

        if (credentialsToInclude.size != credentialIds.size) {
            throw IllegalArgumentException("One or more credential IDs not found")
        }

        return VerifiablePresentation(
            id = null, // Will be generated if needed
            type = listOf(CredentialType.Custom("VerifiablePresentation")),
            verifiableCredential = credentialsToInclude,
            holder = com.trustweave.core.identifiers.Iri(holderDid),
            proof = null, // Proof generation would be handled by PresentationService
            challenge = options.challenge,
            domain = options.domain
        )
    }

    override suspend fun createSelectiveDisclosure(
        credentialIds: List<String>,
        disclosedFields: List<String>,
        holderDid: String,
        options: com.trustweave.credential.proof.ProofOptions
    ): VerifiablePresentation {
        // Simplified implementation - real selective disclosure would filter fields
        return createPresentation(credentialIds, holderDid, options)
    }

    // DidManagement implementation
    private val managedDids = mutableSetOf<String>()

    init {
        managedDids.add(this@InMemoryWallet.walletDid)
        managedDids.add(this@InMemoryWallet.holderDid)
    }

    override suspend fun createDid(method: String, options: DidCreationOptions): String {
        val did = "did:$method:test-${UUID.randomUUID()}"
        managedDids.add(did)
        return did
    }

    override suspend fun getDids(): List<String> {
        return managedDids.toList()
    }

    override suspend fun getPrimaryDid(): String {
        return holderDid
    }

    override suspend fun setPrimaryDid(did: String): Boolean {
        return if (managedDids.contains(did)) {
            managedDids.add(did) // Add if not present
            true
        } else {
            false
        }
    }

    override suspend fun resolveDid(did: String): DidDocument? {
        // Simplified - return null for now, real implementation would resolve DID
        return if (managedDids.contains(did)) {
            // Return a minimal mock DID document
            DidDocument(
                id = com.trustweave.did.identifiers.Did(did),
                verificationMethod = emptyList(),
                authentication = emptyList(),
                assertionMethod = emptyList()
            )
        } else {
            null
        }
    }

    // Helper methods
    private fun updateMetadata(credentialId: String, updater: (CredentialMetadata) -> CredentialMetadata) {
        val existing = credentialMetadata[credentialId] ?: CredentialMetadata(
            credentialId = credentialId,
            createdAt = Clock.System.now(),
            updatedAt = Clock.System.now()
        )
        credentialMetadata[credentialId] = updater(existing)
    }

    /**
     * Clear all data. Useful for testing.
     */
    fun clear() {
        credentials.clear()
        archivedCredentials.clear()
        collections.clear()
        credentialCollections.clear()
        collectionCredentials.clear()
        credentialTags.clear()
        credentialMetadata.clear()
    }

    /**
     * Get total number of credentials (including archived).
     */
    fun totalSize(): Int = credentials.size + archivedCredentials.size
}

