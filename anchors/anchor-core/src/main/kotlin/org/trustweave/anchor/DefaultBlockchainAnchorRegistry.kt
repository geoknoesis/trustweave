package org.trustweave.anchor

/**
 * Default in-memory implementation of [BlockchainAnchorRegistry].
 */
class DefaultBlockchainAnchorRegistry : BlockchainAnchorRegistry {
    private val clients = mutableMapOf<String, BlockchainAnchorClient>()

    @Synchronized
    override fun register(chainId: String, client: BlockchainAnchorClient) {
        clients[chainId] = client
    }

    @Synchronized
    override fun registerAll(vararg entries: Pair<String, BlockchainAnchorClient>) {
        entries.forEach { (chainId, client) -> register(chainId, client) }
    }

    @Synchronized
    override fun get(chainId: String): BlockchainAnchorClient? = clients[chainId]

    @Synchronized
    override fun has(chainId: String): Boolean = clients.containsKey(chainId)

    @Synchronized
    override fun getAllChainIds(): List<String> = clients.keys.toList()

    @Synchronized
    override fun getAllClients(): Map<String, BlockchainAnchorClient> = clients.toMap()

    @Synchronized
    override fun unregister(chainId: String): Boolean = clients.remove(chainId) != null

    @Synchronized
    override fun clear() {
        clients.clear()
    }

    @Synchronized
    override fun size(): Int = clients.size

    @Synchronized
    override fun snapshot(): BlockchainAnchorRegistry {
        val copy = DefaultBlockchainAnchorRegistry()
        clients.forEach { (chainId, client) -> copy.register(chainId, client) }
        return copy
    }
}


