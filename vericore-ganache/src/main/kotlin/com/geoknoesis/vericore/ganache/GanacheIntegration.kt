package com.geoknoesis.vericore.ganache

import com.geoknoesis.vericore.anchor.BlockchainAnchorRegistry

/**
 * Integration helper for Ganache blockchain adapter.
 */
object GanacheIntegration {
    
    /**
     * Sets up Ganache blockchain client and registers it.
     * 
     * @param chainId The chain ID (default: "eip155:1337" for Ganache)
     * @param rpcUrl The RPC URL (default: "http://localhost:8545")
     * @param privateKey Required private key for signing transactions
     * @param contractAddress Optional contract address
     * @return The created GanacheBlockchainAnchorClient
     */
    fun setup(
        blockchainRegistry: BlockchainAnchorRegistry,
        chainId: String = GanacheBlockchainAnchorClient.LOCAL,
        rpcUrl: String = "http://localhost:8545",
        privateKey: String,
        contractAddress: String? = null
    ): GanacheBlockchainAnchorClient {
        val options = mutableMapOf<String, Any?>(
            "rpcUrl" to rpcUrl,
            "privateKey" to privateKey
        )
        contractAddress?.let { options["contractAddress"] = it }
        
        val client = GanacheBlockchainAnchorClient(chainId, options)
        blockchainRegistry.register(chainId, client)
        return client
    }
}

