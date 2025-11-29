package com.trustweave.examples.blockchain

import com.trustweave.TrustWeave
import com.trustweave.anchor.*
import com.trustweave.chain.ethereum.EthereumBlockchainAnchorClient
import com.trustweave.chain.base.BaseBlockchainAnchorClient
import com.trustweave.chain.arbitrum.ArbitrumBlockchainAnchorClient
import com.trustweave.testkit.anchor.InMemoryBlockchainAnchorClient
import com.trustweave.core.util.DigestUtils
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
 * Run: `./gradlew :TrustWeave-examples:runBlockchainAnchoring`
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

    println("Step 1: Setting up TrustWeave with blockchain anchoring...")
    val trustweave = TrustWeave.create {
        blockchains {
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

    val ethereumResult = trustweave.blockchains.anchor(
        data = ethereumPayload,
        serializer = JsonObject.serializer(),
        chainId = EthereumBlockchainAnchorClient.MAINNET
    )
    println("Anchored to Ethereum: ${ethereumResult.ref.txHash}")
    println("Chain ID: ${ethereumResult.ref.chainId}")

    // Step 3: Anchor to Base
    println("\nStep 3: Anchoring to Base (Coinbase L2)...")
    val basePayload = buildJsonObject {
        put("digest", digest)
        put("chain", "base")
        put("timestamp", System.currentTimeMillis())
    }

    val baseResult = trustweave.blockchains.anchor(
        data = basePayload,
        serializer = JsonObject.serializer(),
        chainId = BaseBlockchainAnchorClient.MAINNET
    )
    println("Anchored to Base: ${baseResult.ref.txHash}")
    println("Chain ID: ${baseResult.ref.chainId}")

    // Step 4: Anchor to Arbitrum
    println("\nStep 4: Anchoring to Arbitrum (Largest L2 by TVL)...")
    val arbitrumPayload = buildJsonObject {
        put("digest", digest)
        put("chain", "arbitrum")
        put("timestamp", System.currentTimeMillis())
    }

    val arbitrumResult = trustweave.blockchains.anchor(
        data = arbitrumPayload,
        serializer = JsonObject.serializer(),
        chainId = ArbitrumBlockchainAnchorClient.MAINNET
    )
    println("Anchored to Arbitrum: ${arbitrumResult.ref.txHash}")
    println("Chain ID: ${arbitrumResult.ref.chainId}")

    // Step 5: Read back anchored data
    println("\nStep 5: Reading back anchored data...")

    val ethereumRead = trustweave.blockchains.read(
        ref = ethereumResult.ref,
        serializer = JsonObject.serializer()
    )
    println("Ethereum read: ${ethereumRead}")

    val baseRead = trustweave.blockchains.read(
        ref = baseResult.ref,
        serializer = JsonObject.serializer()
    )
    println("Base read: ${baseRead}")

    val arbitrumRead = trustweave.blockchains.read(
        ref = arbitrumResult.ref,
        serializer = JsonObject.serializer()
    )
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

