package com.trustweave.did.base

import com.trustweave.core.exception.TrustWeaveException
import com.trustweave.did.*
import com.trustweave.did.identifiers.Did
import com.trustweave.did.model.DidDocument
import com.trustweave.did.model.DidDocumentMetadata
import com.trustweave.kms.KeyManagementService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.datetime.Instant
import kotlinx.datetime.Clock
import java.util.concurrent.ConcurrentHashMap

/**
 * Abstract base class for DID method implementations.
 *
 * Provides common functionality shared across DID method adapters:
 * - In-memory document storage for testing
 * - Common updateDid and deactivateDid implementations
 * - Document metadata helpers
 * - Error handling patterns
 *
 * Subclasses should implement:
 * - [createDid]: Create a new DID and return its initial DID Document
 * - [resolveDid]: Resolve a DID to its DID Document
 *
 * Pattern: Similar to `AbstractBlockchainAnchorClient` (~40% code reduction).
 *
 * **Example Usage:**
 * ```kotlin
 * class MyDidMethod(
 *     kms: KeyManagementService
 * ) : AbstractDidMethod("mymethod", kms) {
 *
 *     override suspend fun createDid(options: DidCreationOptions): DidDocument {
 *         // Implement method-specific creation logic
 *     }
 *
 *     override suspend fun resolveDid(did: Did): DidResolutionResult {
 *         // Implement method-specific resolution logic
 *     }
 * }
 * ```
 */
abstract class AbstractDidMethod(
    override val method: String,
    protected val kms: KeyManagementService
) : DidMethod {

    /**
     * In-memory storage for DID documents (for testing and fallback).
     * Used by default implementations of updateDid and deactivateDid.
     */
    protected val documents = ConcurrentHashMap<String, DidDocument>()

    /**
     * Document metadata storage.
     */
    protected val documentMetadata = ConcurrentHashMap<String, DidDocumentMetadata>()

    /**
     * Default implementation of updateDid using in-memory storage.
     *
     * Subclasses can override for methods that require external updates.
     */
    override suspend fun updateDid(
        did: Did,
        updater: (DidDocument) -> DidDocument
    ): DidDocument = withContext(Dispatchers.IO) {
        validateDidFormat(did)

        val didString = did.value
        val current = documents[didString]
            ?: throw com.trustweave.did.exception.DidException.DidNotFound(
                did = did,
                availableMethods = listOf(method)
            )

        val updated = updater(current)

        // Update document and metadata
        documents[didString] = updated
        val now = Clock.System.now()
        documentMetadata[didString] = (documentMetadata[didString] ?: DidDocumentMetadata(created = now))
            .copy(updated = now)

        updated
    }

    /**
     * Default implementation of deactivateDid using in-memory storage.
     *
     * Subclasses can override for methods that require external deactivation.
     */
    override suspend fun deactivateDid(did: Did): Boolean = withContext(Dispatchers.IO) {
        validateDidFormat(did)

        val didString = did.value
        val removed = documents.remove(didString) != null
        documentMetadata.remove(didString)
        removed
    }

    /**
     * Validates that the DID matches this method's format.
     *
     * @param did The DID to validate
     * @throws IllegalArgumentException if the DID format is invalid
     */
    protected fun validateDidFormat(did: Did) {
        if (!did.value.startsWith("did:$method:")) {
            throw IllegalArgumentException("Invalid DID format for method $method: expected did:$method:*, got ${did.value}")
        }
        if (did.method != method) {
            throw IllegalArgumentException("DID method mismatch: expected $method, got ${did.method}")
        }
    }

    /**
     * Stores a DID document in memory.
     *
     * Useful for methods that need to cache resolved documents.
     *
     * @param did The DID identifier (can be Did object or String)
     * @param document The DID document
     * @param created Optional creation timestamp (defaults to now)
     */
    protected fun storeDocument(did: Any, document: DidDocument, created: Instant? = null) {
        val didString = when (did) {
            is Did -> did.value
            is String -> did
            else -> throw IllegalArgumentException("did must be Did or String, got ${did::class}")
        }
        documents[didString] = document
        val now = created ?: Clock.System.now()
        documentMetadata[didString] = DidDocumentMetadata(
            created = now,
            updated = now
        )
    }

    /**
     * Gets a stored DID document.
     *
     * @param did The DID identifier (can be Did object or String)
     * @return The DID document or null if not found
     */
    protected fun getStoredDocument(did: Any): DidDocument? {
        val didString = when (did) {
            is Did -> did.value
            is String -> did
            else -> throw IllegalArgumentException("did must be Did or String, got ${did::class}")
        }
        return documents[didString]
    }

    /**
     * Gets document metadata.
     *
     * @param did The DID identifier (can be Did object or String)
     * @return The document metadata or null if not found
     */
    protected fun getDocumentMetadata(did: Any): DidDocumentMetadata? {
        val didString = when (did) {
            is Did -> did.value
            is String -> did
            else -> throw IllegalArgumentException("did must be Did or String, got ${did::class}")
        }
        return documentMetadata[didString]
    }

    /**
     * Creates resolution metadata for successful resolution.
     *
     * @return Map of resolution metadata
     */
    protected fun createSuccessResolutionMetadata(): Map<String, Any?> {
        return mapOf(
            "method" to method,
            "driver" to this.javaClass.simpleName
        )
    }

    /**
     * Creates resolution metadata for error cases.
     *
     * @param error Error code
     * @param message Error message
     * @return Map of resolution metadata
     */
    protected fun createErrorResolutionMetadata(error: String, message: String? = null): Map<String, Any?> {
        return buildMap {
            put("error", error)
            if (message != null) {
                put("errorMessage", message)
            }
            put("method", method)
        }
    }
}

