package com.geoknoesis.vericore.examples.blockchain

import com.geoknoesis.vericore.VeriCore
import com.geoknoesis.vericore.anchor.*
import com.geoknoesis.vericore.ethereum.EthereumBlockchainAnchorClient
import com.geoknoesis.vericore.base.BaseBlockchainAnchorClient
import com.geoknoesis.vericore.arbitrum.ArbitrumBlockchainAnchorClient
import com.geoknoesis.vericore.testkit.anchor.InMemoryBlockchainAnchorClient
import com.geoknoesis.vericore.json.DigestUtils
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.*

/**
 * Blockchain Anchoring Example - Ethereum, Base, and Arbitrum
 * 
 * This example demonstrates blockchain anchoring on multiple EVM-compatible chains:
 * 1. Ethereum mainnet (highest security, higher fees)
 * 2. Base (Coinbase L2, lower fees, fast confirmations)
 * 3. Arbitrum (largest L2 by TVL, lower fees)
 * 
 * Run: `./gradlew :vericore-examples:runBlockchainAnchoring`
 */
fun main() = runBlocking {
    println("=".repeat(70))
    println("Blockchain Anchoring Example - Ethereum, Base, and Arbitrum")
    println("=".repeat(70))
    println()
    
    // Example credential data
    val credentialData = buildJsonObject {
        put("id", "vc-12345")
        put("issuer", "did:key:z6Mk...")
        put("credentialSubject", buildJsonObject {
            put("id", "did:key:z6Mk...")
            put("name", "Alice")
        })
    }
    
    val digest = DigestUtils.sha256DigestMultibase(credentialData)
    println("Credential digest: $digest")
    println()
    
    // Use in-memory client for testing (replace with real clients for production)
    val testChainId = "ethereum:test"
    
    println("Step 1: Setting up VeriCore with blockchain anchoring...")
    val vericore = VeriCore.create {
        blockchain {
            // Ethereum mainnet
            put(
                EthereumBlockchainAnchorClient.MAINNET,
                InMemoryBlockchainAnchorClient(EthereumBlockchainAnchorClient.MAINNET)
            )
            
            // Base mainnet
            put(
                BaseBlockchainAnchorClient.MAINNET,
                InMemoryBlockchainAnchorClient(BaseBlockchainAnchorClient.MAINNET)
            )
            
            // Arbitrum mainnet
            put(
                ArbitrumBlockchainAnchorClient.MAINNET,
                InMemoryBlockchainAnchorClient(ArbitrumBlockchainAnchorClient.MAINNET)
            )
        }
    }
    
    // Step 2: Anchor to Ethereum mainnet
    println("\nStep 2: Anchoring to Ethereum mainnet...")
    val ethereumPayload = buildJsonObject {
        put("digest", digest)
        put("chain", "ethereum")
        put("timestamp", System.currentTimeMillis())
    }
    
    val ethereumResult = vericore.anchor(
        data = ethereumPayload,
        serializer = JsonObject.serializer(),
        chainId = EthereumBlockchainAnchorClient.MAINNET
    ).getOrThrow()
    println("Anchored to Ethereum: ${ethereumResult.ref.txHash}")
    println("Chain ID: ${ethereumResult.ref.chainId}")
    
    // Step 3: Anchor to Base
    println("\nStep 3: Anchoring to Base (Coinbase L2)...")
    val basePayload = buildJsonObject {
        put("digest", digest)
        put("chain", "base")
        put("timestamp", System.currentTimeMillis())
    }
    
    val baseResult = vericore.anchor(
        data = basePayload,
        serializer = JsonObject.serializer(),
        chainId = BaseBlockchainAnchorClient.MAINNET
    ).getOrThrow()
    println("Anchored to Base: ${baseResult.ref.txHash}")
    println("Chain ID: ${baseResult.ref.chainId}")
    
    // Step 4: Anchor to Arbitrum
    println("\nStep 4: Anchoring to Arbitrum (Largest L2 by TVL)...")
    val arbitrumPayload = buildJsonObject {
        put("digest", digest)
        put("chain", "arbitrum")
        put("timestamp", System.currentTimeMillis())
    }
    
    val arbitrumResult = vericore.anchor(
        data = arbitrumPayload,
        serializer = JsonObject.serializer(),
        chainId = ArbitrumBlockchainAnchorClient.MAINNET
    ).getOrThrow()
    println("Anchored to Arbitrum: ${arbitrumResult.ref.txHash}")
    println("Chain ID: ${arbitrumResult.ref.chainId}")
    
    // Step 5: Read back anchored data
    println("\nStep 5: Reading back anchored data...")
    
    val ethereumRead = vericore.readAnchor(
        ref = ethereumResult.ref,
        serializer = JsonObject.serializer()
    ).getOrThrow()
    println("Ethereum read: ${ethereumRead}")

    val baseRead = vericore.readAnchor(
        ref = baseResult.ref,
        serializer = JsonObject.serializer()
    ).getOrThrow()
    println("Base read: ${baseRead}")

    val arbitrumRead = vericore.readAnchor(
        ref = arbitrumResult.ref,
        serializer = JsonObject.serializer()
    ).getOrThrow()
    println("Arbitrum read: ${arbitrumRead}")
    
    println("\n" + "=".repeat(70))
    println("Blockchain Anchoring Example Complete!")
    println("=".repeat(70))
    println("\nNote: This example uses in-memory clients for testing.")
    println("For production, configure real RPC URLs and private keys:")
    println("- Ethereum: https://eth-mainnet.g.alchemy.com/v2/YOUR_KEY")
    println("- Base: https://mainnet.base.org")
    println("- Arbitrum: https://arb1.arbitrum.io/rpc")
}

