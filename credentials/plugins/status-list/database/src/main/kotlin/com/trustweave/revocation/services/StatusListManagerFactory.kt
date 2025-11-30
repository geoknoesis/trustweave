package com.trustweave.revocation.services

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
     * @return The status list registry instance (as Any to avoid dependency)
     * @throws IllegalStateException if the provider is not found or cannot be instantiated
     */
    suspend fun create(providerName: String): Any // StatusListManager - using Any to avoid dependency
}

