package org.trustweave.did.storage

import org.trustweave.did.identifiers.Did
import org.trustweave.did.model.DidDocument
import org.trustweave.did.model.DidDocumentMetadata
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ConcurrentHashMap

/**
 * In-memory implementation of [DidDocumentStorage].
 *
 * Uses [ConcurrentHashMap] for thread-safe storage. Documents are stored in memory
 * and will be lost on application restart.
 *
 * **Use Cases:**
 * - Testing and development
 * - Single-instance deployments where persistence is not required
 * - Short-lived operations that don't need to survive restarts
 * - Caching layer for other storage backends
 *
 * **Limitations:**
 * - Not persistent across restarts
 * - Limited by available memory
 * - No distributed access (single instance only)
 *
 * **Example Usage:**
 * ```kotlin
 * val storage = InMemoryDocumentStorage()
 *
 * storage.store(
 *     did = Did("did:key:z6Mk..."),
 *     document = didDocument,
 *     metadata = DidDocumentMetadata(created = Clock.System.now())
 * )
 *
 * val (document, metadata) = storage.get(Did("did:key:z6Mk..."))
 * ```
 */
class InMemoryDocumentStorage : DidDocumentStorage {
    override val backend: StorageBackend = StorageBackend.IN_MEMORY
    
    private val documents = ConcurrentHashMap<String, DidDocument>()
    private val metadata = ConcurrentHashMap<String, DidDocumentMetadata>()
    private val mutex = Mutex()
    
    override suspend fun store(
        did: Did,
        document: DidDocument,
        metadata: DidDocumentMetadata?
    ) {
        mutex.withLock {
            documents[did.value] = document
            this.metadata[did.value] = metadata ?: DidDocumentMetadata()
        }
    }
    
    override suspend fun get(did: Did): Pair<DidDocument, DidDocumentMetadata>? {
        return mutex.withLock {
            val doc = documents[did.value] ?: return null
            val meta = metadata[did.value] ?: DidDocumentMetadata()
            doc to meta
        }
    }
    
    override suspend fun update(
        did: Did,
        updater: (DidDocument) -> DidDocument
    ): DidDocument {
        return mutex.withLock {
            val current = documents[did.value]
                ?: throw org.trustweave.core.exception.TrustWeaveException.NotFound(
                    resource = did.value
                )
            
            val updated = updater(current)
            documents[did.value] = updated
            
            // Update metadata
            val currentMeta = metadata[did.value] ?: DidDocumentMetadata()
            metadata[did.value] = currentMeta.copy(updated = kotlinx.datetime.Clock.System.now())
            
            updated
        }
    }
    
    override suspend fun deactivate(
        did: Did,
        deactivatedDocument: DidDocument
    ) {
        mutex.withLock {
            documents[did.value] = deactivatedDocument
            val currentMeta = metadata[did.value] ?: DidDocumentMetadata()
            metadata[did.value] = currentMeta.copy(
                updated = kotlinx.datetime.Clock.System.now()
            )
        }
    }
    
    override suspend fun exists(did: Did): Boolean {
        return mutex.withLock {
            documents.containsKey(did.value)
        }
    }
    
    override suspend fun delete(did: Did): Boolean {
        return mutex.withLock {
            documents.remove(did.value) != null && metadata.remove(did.value) != null
        }
    }
    
    /**
     * Clears all stored documents (useful for testing).
     */
    suspend fun clear() {
        mutex.withLock {
            documents.clear()
            metadata.clear()
        }
    }
    
    /**
     * Gets the number of stored documents.
     */
    suspend fun size(): Int {
        return mutex.withLock {
            documents.size
        }
    }
}

