package org.trustweave.anchor.services

import org.trustweave.anchor.BlockchainAnchorClient

/**
 * Factory interface for creating BlockchainAnchorClient instances.
 *
 * Eliminates the need for reflection when instantiating blockchain anchor client implementations.
 */
interface BlockchainAnchorClientFactory {
    /**
     * Creates a blockchain anchor client instance.
     *
     * @param chainId The chain identifier (e.g., "algorand:testnet")
     * @param providerName The provider name (e.g., "algorand", "inMemory")
     * @param config The anchor client configuration (typically a Map<String, Any?>)
     * @return The blockchain anchor client instance
     * @throws IllegalStateException if the provider is not found or cannot be instantiated
     */
    suspend fun create(
        chainId: String,
        providerName: String,
        config: Map<String, Any?>
    ): BlockchainAnchorClient
}

