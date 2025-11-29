package com.trustweave.anchor.spi

import com.trustweave.anchor.BlockchainAnchorClient

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

    /**
     * Returns the list of environment variables required for this blockchain anchor provider.
     *
     * **Example:**
     * ```kotlin
     * override val requiredEnvironmentVariables: List<String> = listOf(
     *     "ALGORAND_ALGOD_URL",
     *     "?ALGORAND_ALGOD_TOKEN"
     * )
     * ```
     *
     * **Note:** Optional env vars should be prefixed with "?" (e.g., "?ALGORAND_ALGOD_TOKEN")
     *
     * @return List of required environment variable names (empty by default)
     */
    val requiredEnvironmentVariables: List<String>
        get() = emptyList()

    /**
     * Checks if all required environment variables are available for this provider.
     *
     * @return true if all required env vars are set, false otherwise
     */
    fun hasRequiredEnvironmentVariables(): Boolean {
        return requiredEnvironmentVariables.all { envVar ->
            val isOptional = envVar.startsWith("?")
            val actualVar = if (isOptional) envVar.substring(1) else envVar
            if (isOptional) true else System.getenv(actualVar) != null
        }
    }
}

