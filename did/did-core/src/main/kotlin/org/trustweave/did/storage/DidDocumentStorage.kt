package org.trustweave.did.storage

import org.trustweave.did.identifiers.Did
import org.trustweave.did.model.DidDocument
import org.trustweave.did.model.DidDocumentMetadata

/**
 * Interface for DID document storage backends.
 *
 * Provides abstraction for document persistence, allowing different
 * storage implementations (in-memory, database, Redis, S3, IPFS, blockchain, etc.).
 *
 * **Storage Backend Types:**
 * - **In-Memory**: Fast, ephemeral (for testing)
 * - **Database**: Persistent, queryable (PostgreSQL, MySQL)
 * - **Redis**: Fast, cache-like (for high-performance scenarios)
 * - **S3/Cloud Storage**: Scalable, versioned (for large-scale deployments)
 * - **IPFS**: Decentralized, content-addressed (for decentralized storage)
 * - **Blockchain**: Immutable, tamper-proof (for anchoring)
 *
 * **Example Usage:**
 * ```kotlin
 * val storage: DidDocumentStorage = DatabaseDocumentStorage(dataSource)
 *
 * // Store a document
 * storage.store(
 *     did = Did("did:web:example.com"),
 *     document = didDocument,
 *     metadata = DidDocumentMetadata(created = Clock.System.now())
 * )
 *
 * // Retrieve a document
 * val (document, metadata) = storage.get(Did("did:web:example.com"))
 *     ?: throw Exception("DID not found")
 * ```
 */
interface DidDocumentStorage {
    /**
     * The storage backend type identifier.
     */
    val backend: StorageBackend
    
    /**
     * Stores a DID document.
     *
     * @param did The DID identifier (type-safe)
     * @param document The DID document to store
     * @param metadata Optional document metadata
     */
    suspend fun store(
        did: Did,
        document: DidDocument,
        metadata: DidDocumentMetadata? = null
    )
    
    /**
     * Retrieves a DID document by DID.
     *
     * @param did The DID identifier (type-safe)
     * @return The DID document and metadata, or null if not found
     */
    suspend fun get(did: Did): Pair<DidDocument, DidDocumentMetadata>?
    
    /**
     * Updates an existing DID document.
     *
     * Uses an updater function to ensure atomic updates.
     *
     * @param did The DID identifier (type-safe)
     * @param updater Function that transforms the current document to the new document
     * @return The updated DID document
     * @throws org.trustweave.core.exception.TrustWeaveException if DID not found
     */
    suspend fun update(
        did: Did,
        updater: (DidDocument) -> DidDocument
    ): DidDocument
    
    /**
     * Marks a DID document as deactivated.
     *
     * @param did The DID identifier (type-safe)
     * @param deactivatedDocument The deactivated document (with deactivated: true in metadata)
     */
    suspend fun deactivate(
        did: Did,
        deactivatedDocument: DidDocument
    )
    
    /**
     * Checks if a DID document exists.
     *
     * @param did The DID identifier (type-safe)
     * @return true if document exists
     */
    suspend fun exists(did: Did): Boolean
    
    /**
     * Deletes a DID document (for cleanup).
     *
     * **Note**: In production, prefer deactivation over deletion to maintain audit trail.
     *
     * @param did The DID identifier (type-safe)
     * @return true if deleted, false if not found
     */
    suspend fun delete(did: Did): Boolean
}

/**
 * Storage backend type identifiers.
 */
enum class StorageBackend {
    IN_MEMORY,
    DATABASE,
    REDIS,
    S3,
    IPFS,
    BLOCKCHAIN,
    HYBRID
}

