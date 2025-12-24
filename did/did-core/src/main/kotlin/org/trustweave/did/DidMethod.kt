package org.trustweave.did

import org.trustweave.did.identifiers.Did
import org.trustweave.did.model.DidDocument
import org.trustweave.did.resolver.DidResolutionResult
import org.trustweave.did.validation.DidValidator

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
     * @param did Type-safe DID identifier
     * @return A DidResolutionResult containing the document and metadata
     */
    suspend fun resolveDid(did: Did): DidResolutionResult

    /**
     * Updates a DID Document.
     *
     * @param did Type-safe DID identifier to update
     * @param updater Function that transforms the current document to the new document
     * @return The updated DID Document
     */
    suspend fun updateDid(
        did: Did,
        updater: (DidDocument) -> DidDocument
    ): DidDocument

    /**
     * Deactivates a DID.
     *
     * @param did Type-safe DID identifier to deactivate
     * @return true if the DID was successfully deactivated
     */
    suspend fun deactivateDid(did: Did): Boolean
}

