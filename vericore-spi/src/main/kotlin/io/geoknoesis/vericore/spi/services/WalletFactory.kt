package io.geoknoesis.vericore.spi.services

/**
 * Factory interface for creating Wallet instances.
 *
 * Eliminates the need for reflection when instantiating Wallet implementations.
 * Follows the same pattern as other factory interfaces (KmsFactory, StatusListManagerFactory, etc.).
 */
interface WalletFactory {
    /**
     * Creates a wallet instance from a provider name.
     *
     * @param providerName The provider name (e.g., "inMemory", "database", "file")
     * @param walletId The wallet ID (optional, will be generated if not provided)
     * @param walletDid The wallet DID (optional, will be generated if not provided)
     * @param holderDid The holder DID (required for most wallet types)
     * @param options Additional options for wallet creation (e.g., storage path, database connection, etc.)
     * @return The wallet instance
     * @throws IllegalStateException if the provider is not found or cannot be instantiated
     */
    suspend fun create(
        providerName: String,
        walletId: String? = null,
        walletDid: String? = null,
        holderDid: String? = null,
        options: Map<String, Any?> = emptyMap()
    ): Any

    /**
     * Creates an in-memory wallet instance (convenience method for testing).
     *
     * This is equivalent to calling `create("inMemory", walletId, walletDid, holderDid)`.
     *
     * @param walletId The wallet ID (optional, will be generated if not provided)
     * @param walletDid The wallet DID (optional, will be generated if not provided)
     * @param holderDid The holder DID (required)
     * @return The wallet instance
     */
    suspend fun createInMemory(
        walletId: String? = null,
        walletDid: String? = null,
        holderDid: String? = null
    ): Any {
        return create("inMemory", walletId, walletDid, holderDid)
    }
}


