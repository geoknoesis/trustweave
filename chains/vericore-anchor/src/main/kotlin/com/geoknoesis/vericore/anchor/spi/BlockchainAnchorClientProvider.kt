package com.geoknoesis.vericore.anchor.spi

import com.geoknoesis.vericore.anchor.BlockchainAnchorClient

/**
 * Service Provider Interface for BlockchainAnchorClient implementations.
 * Implementations of this interface will be discovered via Java ServiceLoader.
 */
interface BlockchainAnchorClientProvider {
    /**
     * Creates a BlockchainAnchorClient instance for the specified chain ID.
     *
     * @param chainId The chain identifier (e.g., "algorand:mainnet", "eip155:137")
     * @param options Configuration options for the client
     * @return A BlockchainAnchorClient instance, or null if this provider doesn't support the chain
     */
    fun create(chainId: String, options: Map<String, Any?> = emptyMap()): BlockchainAnchorClient?

    /**
     * The name/identifier of this provider (e.g., "algorand", "polygon").
     */
    val name: String

    /**
     * List of chain IDs supported by this provider.
     * Chain IDs should follow CAIP-2 format (e.g., "algorand:mainnet", "eip155:137").
     */
    val supportedChains: List<String>
}

