package io.geoknoesis.vericore.anchor

/**
 * Instance-based registry for blockchain anchor clients.
 * 
 * This class provides a thread-safe, testable alternative to the global
 * `BlockchainRegistry` singleton. Multiple registries can coexist, enabling
 * isolation between different contexts.
 * 
 * **Example Usage:**
 * ```kotlin
 * // Create a registry
 * val registry = BlockchainAnchorRegistry()
 * 
 * // Register clients
 * registry.register("algorand:testnet", algorandClient)
 * registry.register("ethereum:mainnet", ethereumClient)
 * 
 * // Get a client
 * val client = registry.get("algorand:testnet")
 * val result = client?.writePayload(payload)
 * ```
 * 
 * **Benefits over global singleton:**
 * - Thread-safe without global state
 * - Testable in isolation
 * - Multiple contexts can coexist
 * - No hidden dependencies
 * 
 * @see BlockchainRegistry for backward-compatible global registry
 */
class BlockchainAnchorRegistry {
    private val clients = mutableMapOf<String, BlockchainAnchorClient>()
    
    /**
     * Registers a blockchain anchor client for a specific chain.
     * 
     * If a client for the same chain ID is already registered, it will be replaced.
     * 
     * **Example:**
     * ```kotlin
     * val registry = BlockchainAnchorRegistry()
     * registry.register("algorand:testnet", algorandClient)
     * ```
     * 
     * @param chainId The chain identifier (CAIP-2 format, e.g., "algorand:mainnet")
     * @param client The blockchain anchor client implementation
     */
    @Synchronized
    fun register(chainId: String, client: BlockchainAnchorClient) {
        clients[chainId] = client
    }
    
    /**
     * Registers multiple blockchain clients at once.
     * 
     * **Example:**
     * ```kotlin
     * registry.registerAll(
     *     "algorand:testnet" to algorandClient,
     *     "ethereum:mainnet" to ethereumClient
     * )
     * ```
     * 
     * @param pairs Pairs of chain ID to client
     */
    @Synchronized
    fun registerAll(vararg pairs: Pair<String, BlockchainAnchorClient>) {
        pairs.forEach { (chainId, client) ->
            register(chainId, client)
        }
    }
    
    /**
     * Gets a blockchain anchor client for a specific chain.
     * 
     * **Example:**
     * ```kotlin
     * val client = registry.get("algorand:testnet")
     * if (client != null) {
     *     val result = client.writePayload(payload)
     * }
     * ```
     * 
     * @param chainId The chain identifier
     * @return The BlockchainAnchorClient, or null if not registered
     */
    @Synchronized
    fun get(chainId: String): BlockchainAnchorClient? {
        return clients[chainId]
    }
    
    /**
     * Gets a blockchain anchor client, throwing if not found.
     * 
     * **Example:**
     * ```kotlin
     * try {
     *     val client = registry.getOrThrow("algorand:testnet")
     *     val result = client.writePayload(payload)
     * } catch (e: IllegalArgumentException) {
     *     println("Chain not configured: ${e.message}")
     * }
     * ```
     * 
     * @param chainId The chain identifier
     * @return The BlockchainAnchorClient
     * @throws IllegalArgumentException if no client is registered for the chain
     */
    @Synchronized
    fun getOrThrow(chainId: String): BlockchainAnchorClient {
        return get(chainId) ?: throw IllegalArgumentException(
            "No blockchain client registered for chain: $chainId. " +
            "Available chains: ${getAllChainIds()}"
        )
    }
    
    /**
     * Checks if a blockchain client is registered for a chain.
     * 
     * @param chainId The chain identifier
     * @return true if a client is registered, false otherwise
     */
    @Synchronized
    fun has(chainId: String): Boolean {
        return clients.containsKey(chainId)
    }
    
    /**
     * Gets all registered chain IDs.
     * 
     * **Example:**
     * ```kotlin
     * val availableChains = registry.getAllChainIds()
     * println("Available chains: $availableChains")
     * ```
     * 
     * @return List of registered chain IDs
     */
    @Synchronized
    fun getAllChainIds(): List<String> {
        return clients.keys.toList()
    }
    
    /**
     * Gets all registered clients.
     * 
     * @return Map of chain ID to client instance
     */
    @Synchronized
    fun getAllClients(): Map<String, BlockchainAnchorClient> {
        return clients.toMap()
    }
    
    /**
     * Unregisters a blockchain client.
     * 
     * @param chainId The chain identifier
     * @return true if the client was removed, false if it wasn't registered
     */
    @Synchronized
    fun unregister(chainId: String): Boolean {
        return clients.remove(chainId) != null
    }
    
    /**
     * Clears all registered clients.
     * 
     * Useful for testing or resetting the registry state.
     */
    @Synchronized
    fun clear() {
        clients.clear()
    }
    
    /**
     * Gets the number of registered blockchain clients.
     * 
     * @return Count of registered clients
     */
    @Synchronized
    fun size(): Int = clients.size

    /**
     * Creates a shallow copy of this registry.
     */
    @Synchronized
    fun snapshot(): BlockchainAnchorRegistry {
        val copy = BlockchainAnchorRegistry()
        clients.forEach { (chainId, client) -> copy.register(chainId, client) }
        return copy
    }
    
    companion object {
        /**
         * Creates an empty registry.
         * 
         * @return New empty registry
         */
        fun empty(): BlockchainAnchorRegistry = BlockchainAnchorRegistry()
    }
}

