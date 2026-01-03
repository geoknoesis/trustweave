package org.trustweave.testkit.spi

import org.trustweave.anchor.BlockchainAnchorClient
import org.trustweave.anchor.spi.BlockchainAnchorClientProvider
import org.trustweave.testkit.anchor.InMemoryBlockchainAnchorClient

/**
 * SPI provider for testkit's InMemoryBlockchainAnchorClient.
 * 
 * This provider is automatically discovered when testkit is on the classpath.
 * It provides the "inMemory" anchor client for testing scenarios.
 * 
 * **Note:** This provider supports any chain ID for testing purposes.
 * When testkit is on the classpath, this provider will be available for auto-discovery.
 */
class TestkitBlockchainAnchorClientProvider : BlockchainAnchorClientProvider {
    override val name: String = "inMemory"
    
    override val supportedChains: List<String> = emptyList() // Supports any chain for testing
    
    override val requiredEnvironmentVariables: List<String> = emptyList()
    
    override fun hasRequiredEnvironmentVariables(): Boolean = true
    
    override fun create(chainId: String, config: Map<String, Any?>): BlockchainAnchorClient? {
        val contract = config["contract"] as? String
        return InMemoryBlockchainAnchorClient(chainId, contract)
    }
}


