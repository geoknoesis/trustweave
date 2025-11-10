package io.geoknoesis.vericore.anchor.services

import io.geoknoesis.vericore.anchor.BlockchainAnchorClient
import io.geoknoesis.vericore.anchor.BlockchainRegistry

/**
 * Adapter that wraps BlockchainRegistry to implement blockchain registry service.
 * 
 * This adapter allows vericore-core to access BlockchainRegistry functionality
 * without direct dependency or reflection.
 */
class BlockchainRegistryServiceAdapter {
    fun register(chainId: String, client: Any) {
        BlockchainRegistry.register(chainId, client as BlockchainAnchorClient)
    }
    
    fun get(chainId: String): Any? {
        return BlockchainRegistry.get(chainId) as? Any
    }
}

