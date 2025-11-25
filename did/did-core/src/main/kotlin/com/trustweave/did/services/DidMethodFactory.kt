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
     * @param methodName The DID method name (e.g., "key", "web", "ion")
     * @param config Method-specific configuration (typically DidCreationOptions or Map)
     * @param kms The key management service instance
     * @return The DID method instance, or null if the method is not found
     */
    suspend fun create(
        methodName: String,
        config: Any,
        kms: Any
    ): Any? // Returns DidMethod
}

