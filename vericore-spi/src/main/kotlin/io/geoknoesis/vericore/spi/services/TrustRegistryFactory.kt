package io.geoknoesis.vericore.spi.services

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
     * @return The trust registry instance (as Any to avoid dependency)
     * @throws IllegalStateException if the provider is not found or cannot be instantiated
     */
    suspend fun create(providerName: String): Any // TrustRegistry - using Any to avoid dependency
}


