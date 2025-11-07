package io.geoknoesis.vericore.did

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
    suspend fun createDid(options: Map<String, Any?> = emptyMap()): DidDocument

    /**
     * Resolves a DID to its DID Document.
     *
     * @param did The DID string to resolve
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

/**
 * Registry for DID method implementations.
 * Allows registration and lookup of DID methods.
 */
object DidRegistry {
    private val methods = mutableMapOf<String, DidMethod>()

    /**
     * Registers a DID method implementation.
     *
     * @param method The DID method to register
     */
    fun register(method: DidMethod) {
        methods[method.method] = method
    }

    /**
     * Gets a DID method by name.
     *
     * @param methodName The name of the DID method
     * @return The DidMethod implementation, or null if not found
     */
    fun get(methodName: String): DidMethod? {
        return methods[methodName]
    }

    /**
     * Resolves a DID using the appropriate method.
     *
     * @param did The DID string to resolve
     * @return A DidResolutionResult
     * @throws IllegalArgumentException if the DID method is not registered
     */
    suspend fun resolve(did: String): DidResolutionResult {
        val parsed = Did.parse(did)
        val method = get(parsed.method)
            ?: throw IllegalArgumentException("DID method '${parsed.method}' is not registered")
        return method.resolveDid(did)
    }

    /**
     * Clears all registered methods (useful for testing).
     */
    fun clear() {
        methods.clear()
    }
}

