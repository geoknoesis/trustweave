package io.geoknoesis.vericore.spi.services

/**
 * Factory interface for creating StatusListManager instances.
 *
 * Eliminates the need for reflection when instantiating StatusListManager implementations.
 */
interface StatusListManagerFactory {
    /**
     * Creates a status list manager instance from a provider name.
     *
     * @param providerName The provider name (e.g., "inMemory")
     * @return The status list manager instance (as Any to avoid dependency)
     * @throws IllegalStateException if the provider is not found or cannot be instantiated
     */
    suspend fun create(providerName: String): Any // StatusListManager - using Any to avoid dependency
}


