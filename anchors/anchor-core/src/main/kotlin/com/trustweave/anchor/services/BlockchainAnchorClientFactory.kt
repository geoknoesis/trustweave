package com.trustweave.anchor.services

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
     * @param config The anchor client configuration (as Any to avoid dependency)
     * @return The blockchain anchor client instance (as Any to avoid dependency)
     * @throws IllegalStateException if the provider is not found or cannot be instantiated
     */
    suspend fun create(
        chainId: String,
        providerName: String,
        config: Any // AnchorConfig - using Any to avoid dependency
    ): Any // BlockchainAnchorClient - using Any to avoid dependency
}

