package com.trustweave.did.services

/**
 * Factory interface for creating DID method instances.
 *
 * Eliminates the need for reflection when instantiating DID method implementations.
 */
interface DidMethodFactory {
    /**
     * Creates a DID method instance.
     *
     * @param methodName The DID method name (e.g., "key", "web")
     * @param config The DID method configuration (as Any to avoid dependency)
     * @param kms The key management service (as Any to avoid dependency)
     * @return The DID method instance (as Any to avoid dependency), or null if method not found
     */
    suspend fun create(
        methodName: String,
        config: Any, // DidMethodConfig - using Any to avoid dependency
        kms: Any // KeyManagementService - using Any to avoid dependency
    ): Any? // DidMethod? - using Any to avoid dependency
}

