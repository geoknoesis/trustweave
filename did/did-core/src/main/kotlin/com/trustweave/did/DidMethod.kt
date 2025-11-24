package com.trustweave.did

import com.trustweave.did.resolver.DidResolutionResult
import com.trustweave.did.validation.DidValidator

/**
 * Interface for DID method implementations.
 * Each DID method (e.g., "web", "key", "ion") should implement this interface.
 */
interface DidMethod {

    /**
     * The DID method name (e.g., "web", "key", "ion").
     */
    val method: String

    /**
     * Creates a new DID and returns its initial DID Document.
     *
     * @param options Method-specific options for DID creation
     * @return The initial DID Document
     */
    suspend fun createDid(options: DidCreationOptions = DidCreationOptions()): DidDocument

    /**
     * Resolves a DID to its DID Document.
     * 
     * **Implementation Note:** Implementations should validate the DID format
     * before processing. The DID must start with "did:{method}:" where {method}
     * matches this method's name. Use [DidValidator.validateFormat] for validation.
     *
     * @param did The DID string to resolve (should be validated by implementation)
     * @return A DidResolutionResult containing the document and metadata
     */
    suspend fun resolveDid(did: String): DidResolutionResult

    /**
     * Updates a DID Document.
     *
     * @param did The DID to update
     * @param updater Function that transforms the current document to the new document
     * @return The updated DID Document
     */
    suspend fun updateDid(
        did: String,
        updater: (DidDocument) -> DidDocument
    ): DidDocument

    /**
     * Deactivates a DID.
     *
     * @param did The DID to deactivate
     * @return true if the DID was successfully deactivated
     */
    suspend fun deactivateDid(did: String): Boolean
}

