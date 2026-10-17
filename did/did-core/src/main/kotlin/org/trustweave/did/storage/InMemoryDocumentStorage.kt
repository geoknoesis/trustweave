package org.trustweave.did.storage

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.trustweave.core.exception.TrustWeaveException
import org.trustweave.did.identifiers.Did
import org.trustweave.did.model.DidDocument
import org.trustweave.did.model.DidDocumentMetadata
import kotlin.time.Clock

/**
 * In-memory implementation of [DidDocumentStorage].
 *
 * Uses a [Mutex] to keep document and metadata changes atomic. Documents are stored in memory
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

    private data class Entry(
        val document: DidDocument,
        val metadata: DidDocumentMetadata,
    )

    // Keep document and metadata together so every mutation replaces one complete record.
    private val entries = mutableMapOf<String, Entry>()
    private val mutex = Mutex()

    override suspend fun store(
        did: Did,
        document: DidDocument,
        metadata: DidDocumentMetadata?,
    ) {
        mutex.withLock {
            entries[did.value] = Entry(document, metadata ?: DidDocumentMetadata())
        }
    }

    override suspend fun get(did: Did): Pair<DidDocument, DidDocumentMetadata>? =
        mutex.withLock {
            entries[did.value]?.let { it.document to it.metadata }
        }

    override suspend fun update(
        did: Did,
        updater: (DidDocument) -> DidDocument,
    ): DidDocument =
        mutex.withLock {
            val current =
                entries[did.value]
                    ?: throw TrustWeaveException.NotFound(resource = did.value)
            val updated = updater(current.document)
            entries[did.value] =
                Entry(updated, current.metadata.copy(updated = Clock.System.now()))
            updated
        }

    override suspend fun deactivate(
        did: Did,
        deactivatedDocument: DidDocument,
    ) {
        mutex.withLock {
            val current = entries[did.value] ?: return
            entries[did.value] =
                Entry(deactivatedDocument, current.metadata.copy(updated = Clock.System.now()))
        }
    }

    override suspend fun exists(did: Did): Boolean = mutex.withLock { entries.containsKey(did.value) }

    override suspend fun delete(did: Did): Boolean = mutex.withLock { entries.remove(did.value) != null }

    /** Clears all documents and their metadata. */
    suspend fun clear() {
        mutex.withLock { entries.clear() }
    }

    /** Returns the number of stored documents. */
    suspend fun size(): Int = mutex.withLock { entries.size }
}

/**
 * Canonical alias for [InMemoryDocumentStorage].
 *
 * Prefer this name when referencing the in-memory storage implementation by its full
 * interface name (`InMemoryDidDocumentStorage` mirrors the `DidDocumentStorage` interface).
 */
typealias InMemoryDidDocumentStorage = InMemoryDocumentStorage
