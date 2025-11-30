package com.trustweave.trust.services

import com.trustweave.trust.TrustRegistry

/**
 * Factory interface for creating TrustRegistry instances.
 *
 * Eliminates the need for reflection when instantiating TrustRegistry implementations.
 */
interface TrustRegistryFactory {
    /**
     * Creates a trust registry instance from a provider name.
     *
     * @param providerName The provider name (e.g., "inMemory")
     * @return The trust registry instance
     * @throws IllegalStateException if the provider is not found or cannot be instantiated
     */
    suspend fun create(providerName: String): TrustRegistry
}

