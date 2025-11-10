package io.geoknoesis.vericore.spi.services

/**
 * Service interface for DID method operations.
 *
 * Provides a way to call DidMethod methods without direct dependency or reflection.
 */
interface DidMethodService {
    /**
     * Creates a new DID using the DID method.
     *
     * @param didMethod The DID method instance (as Any to avoid dependency)
     * @param options Options for DID creation
     * @return The created DID document (as Any to avoid dependency)
     */
    suspend fun createDid(
        didMethod: Any, // DidMethod - using Any to avoid dependency
        options: Map<String, Any?>
    ): Any // DidDocument - using Any to avoid dependency

    /**
     * Updates a DID document.
     *
     * @param didMethod The DID method instance (as Any to avoid dependency)
     * @param did The DID to update
     * @param updater Function to update the document
     * @return The updated DID document (as Any to avoid dependency)
     */
    suspend fun updateDid(
        didMethod: Any, // DidMethod - using Any to avoid dependency
        did: String,
        updater: (Any) -> Any // (DidDocument) -> DidDocument - using Any to avoid dependency
    ): Any // DidDocument - using Any to avoid dependency

    /**
     * Gets the ID from a DID document.
     *
     * @param document The DID document (as Any to avoid dependency)
     * @return The DID string
     */
    fun getDidId(document: Any): String
}


