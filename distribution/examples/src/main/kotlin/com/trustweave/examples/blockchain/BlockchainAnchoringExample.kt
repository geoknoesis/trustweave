package com.trustweave.examples.blockchain

import com.trustweave.trust.TrustWeave
import com.trustweave.anchor.*
import com.trustweave.anchor.ethereum.EthereumBlockchainAnchorClient
import com.trustweave.anchor.base.BaseBlockchainAnchorClient
import com.trustweave.anchor.arbitrum.ArbitrumBlockchainAnchorClient
import com.trustweave.testkit.anchor.InMemoryBlockchainAnchorClient
import com.trustweave.core.util.DigestUtils
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.*
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.JsonObject

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
    val trustweave = TrustWeave.build {
        anchor {
            chain("ethereum:mainnet") {
                inMemory()
            }
            chain("base:mainnet") {
                inMemory()
            }
            chain("arbitrum:mainnet") {
                inMemory()
            }
        }
    }

    // Step 2: Anchor to Ethereum mainnet
    println("\nStep 2: Anchoring to Ethereum mainnet...")
    val ethereumPayload = buildJsonObject {
        put("digest", digest)
        put("chain", "ethereum")
        put("timestamp", System.currentTimeMillis())
    }

    // Note: This example needs to be updated to use the new TrustWeave anchoring API
    // For now, commenting out the actual anchoring calls as the API has changed
    println("Note: Anchoring API has changed. Please update this example to use the new TrustWeave anchoring API.")
    // val ethereumResult = trustweave.anchor(...)
    // Note: Anchoring API has changed - this example needs to be updated
    println("Ethereum anchoring would be performed here")

    // Step 3: Anchor to Base
    println("\nStep 3: Anchoring to Base (Coinbase L2)...")
    println("Base anchoring would be performed here")

    // Step 4: Anchor to Arbitrum
    println("\nStep 4: Anchoring to Arbitrum (Largest L2 by TVL)...")
    println("Arbitrum anchoring would be performed here")

    // Step 5: Read back anchored data
    println("\nStep 5: Reading back anchored data...")
    println("Reading anchored data would be performed here")

    println("\n" + "=".repeat(70))
    println("Blockchain Anchoring Example Complete!")
    println("=".repeat(70))
    println("\nNote: This example uses in-memory clients for testing.")
    println("For production, configure real RPC URLs and private keys:")
    println("- Ethereum: https://eth-mainnet.g.alchemy.com/v2/YOUR_KEY")
    println("- Base: https://mainnet.base.org")
    println("- Arbitrum: https://arb1.arbitrum.io/rpc")
}

