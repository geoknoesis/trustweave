package com.trustweave.revocation.services

import com.trustweave.credential.revocation.StatusListManager

/**
 * Factory interface for creating StatusListRegistry instances.
 *
 * Eliminates the need for reflection when instantiating StatusListRegistry implementations.
 * Uses domain-precise naming: "Registry" instead of generic "Manager".
 */
interface StatusListRegistryFactory {
    /**
     * Creates a status list registry instance from a provider name.
     *
     * @param providerName The provider name (e.g., "inMemory")
     * @return The status list registry instance
     * @throws IllegalStateException if the provider is not found or cannot be instantiated
     */
    suspend fun create(providerName: String): StatusListManager
}

