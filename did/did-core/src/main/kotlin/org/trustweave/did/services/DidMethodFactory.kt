package org.trustweave.did.services

import org.trustweave.did.DidMethod
import org.trustweave.did.DidCreationOptions
import org.trustweave.kms.KeyManagementService

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
     * @param config Method-specific configuration (typically DidCreationOptions or Map<String, Any?>)
     * @param kms The key management service instance
     * @return The DID method instance, or null if the method is not found
     */
    suspend fun create(
        methodName: String,
        config: DidCreationOptions,
        kms: KeyManagementService
    ): DidMethod?
}


