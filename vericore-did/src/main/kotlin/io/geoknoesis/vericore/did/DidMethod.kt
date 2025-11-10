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
     * Creates a new DID using type-safe options.
     * 
     * This method provides compile-time type safety over the Map-based version.
     * Default implementation delegates to [createDid] with map conversion.
     * 
     * **Example:**
     * ```kotlin
     * val options = DidCreationOptions(
     *     algorithm = KeyAlgorithm.ED25519,
     *     purposes = listOf(KeyPurpose.AUTHENTICATION)
     * )
     * val document = didMethod.createDid(options)
     * ```
     * 
     * @param options Type-safe creation options
     * @return The initial DID Document
     */
    suspend fun createDid(options: DidCreationOptions): DidDocument {
        return createDid(options.toMap())
    }

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
 * Global DID registry for backward compatibility.
 *
 * The preferred API is `DidMethodRegistry`, which allows instance-based
 * isolation. This singleton is retained for legacy integrations that still rely
 * on a global registry and stores state in its own internal
 * `DidMethodRegistry` instance.
 */
object DidRegistry {
    private val defaultRegistry = DidMethodRegistry()

    /**
     * Registers a DID method implementation.
     *
     * @param method The DID method to register
     */
    fun register(method: DidMethod) {
        defaultRegistry.register(method)
    }

    /**
     * Gets a DID method by name.
     *
     * @param methodName The name of the DID method
     * @return The DidMethod implementation, or null if not found
     */
    fun get(methodName: String): DidMethod? {
        return defaultRegistry.get(methodName)
    }

    /**
     * Resolves a DID using the appropriate method.
     *
     * @param did The DID string to resolve
     * @return A DidResolutionResult
     * @throws IllegalArgumentException if the DID method is not registered
     */
    suspend fun resolve(did: String): DidResolutionResult {
        return defaultRegistry.resolve(did)
    }

    /**
     * Clears all registered methods (useful for testing).
     */
    fun clear() {
        defaultRegistry.clear()
    }
}

