package com.trustweave.did.base

import com.trustweave.core.TrustWeaveException
import com.trustweave.did.*
import com.trustweave.kms.KeyManagementService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant
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
 *     override suspend fun resolveDid(did: String): DidResolutionResult {
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
        did: String,
        updater: (DidDocument) -> DidDocument
    ): DidDocument = withContext(Dispatchers.IO) {
        validateDidFormat(did)
        
        val current = documents[did]
            ?: throw TrustWeaveException("DID not found: $did")
        
        val updated = updater(current)
        
        // Update document and metadata
        documents[did] = updated
        val now = Instant.now()
        documentMetadata[did] = (documentMetadata[did] ?: DidDocumentMetadata(created = now))
            .copy(updated = now)
        
        updated
    }

    /**
     * Default implementation of deactivateDid using in-memory storage.
     * 
     * Subclasses can override for methods that require external deactivation.
     */
    override suspend fun deactivateDid(did: String): Boolean = withContext(Dispatchers.IO) {
        validateDidFormat(did)
        
        val removed = documents.remove(did) != null
        documentMetadata.remove(did)
        removed
    }

    /**
     * Validates that the DID matches this method's format.
     * 
     * @param did The DID to validate
     * @throws IllegalArgumentException if the DID format is invalid
     */
    protected fun validateDidFormat(did: String) {
        if (!did.startsWith("did:$method:")) {
            throw IllegalArgumentException("Invalid DID format for method $method: expected did:$method:*, got $did")
        }
    }

    /**
     * Stores a DID document in memory.
     * 
     * Useful for methods that need to cache resolved documents.
     * 
     * @param did The DID identifier
     * @param document The DID document
     * @param created Optional creation timestamp (defaults to now)
     */
    protected fun storeDocument(did: String, document: DidDocument, created: Instant? = null) {
        documents[did] = document
        val now = created ?: Instant.now()
        documentMetadata[did] = DidDocumentMetadata(
            created = now,
            updated = now
        )
    }

    /**
     * Gets a stored DID document.
     * 
     * @param did The DID identifier
     * @return The DID document or null if not found
     */
    protected fun getStoredDocument(did: String): DidDocument? {
        return documents[did]
    }

    /**
     * Gets document metadata.
     * 
     * @param did The DID identifier
     * @return The document metadata or null if not found
     */
    protected fun getDocumentMetadata(did: String): DidDocumentMetadata? {
        return documentMetadata[did]
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

