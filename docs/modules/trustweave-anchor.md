---
title: trustweave-anchor
---

# trustweave-anchor

The `trustweave-anchor` module provides blockchain anchoring abstraction with chain-agnostic interfaces for notarizing data on various blockchains.

```kotlin
dependencies {
    implementation("com.trustweave:trustweave-anchor:1.0.0-SNAPSHOT")
    implementation("com.trustweave:trustweave-common:1.0.0-SNAPSHOT")
    implementation("com.trustweave:trustweave-json:1.0.0-SNAPSHOT")
}
```

**Result:** Gradle exposes the blockchain anchor client interfaces and utilities so you can anchor data to any supported blockchain.

## Overview

The `trustweave-anchor` module provides:

- **BlockchainAnchorClient Interface** – chain-agnostic interface for anchoring operations
- **AnchorRef** – chain-agnostic reference structure (chain ID + transaction hash)
- **AnchorResult** – result structure containing anchor references
- **BlockchainAnchorRegistry** – instance-scoped registry for managing blockchain clients
- **Type-Safe Options** – sealed class hierarchy for blockchain-specific configurations
- **Type-Safe Chain IDs** – CAIP-2 compliant chain identifier types
- **SPI Support** – service provider interface for auto-discovery of blockchain adapters

## Key Components

### BlockchainAnchorClient Interface

```kotlin
import com.trustweave.anchor.*

interface BlockchainAnchorClient {
    val chainId: String  // CAIP-2 format: "eip155:1", "algorand:testnet", etc.
    
    suspend fun writePayload(payload: ByteArray): AnchorResult
    suspend fun readPayload(anchorRef: AnchorRef): ByteArray?
}
```

**What this does:** Defines the contract for anchoring operations that all blockchain adapters must fulfill.

**Outcome:** Enables TrustWeave to anchor data to multiple blockchains (Algorand, Polygon, Ethereum, etc.) through a unified interface.

### Type-Safe Options

The module provides type-safe configuration options:

```kotlin
sealed class BlockchainAnchorClientOptions {
    data class AlgorandOptions(
        val algodUrl: String,
        val indexerUrl: String? = null,
        val privateKey: String
    ) : BlockchainAnchorClientOptions()
    
    data class PolygonOptions(
        val rpcUrl: String,
        val chainId: Long,
        val privateKey: String
    ) : BlockchainAnchorClientOptions(), Closeable
    
    // ... other options
}
```

**What this does:** Provides compile-time validation for blockchain-specific configurations.

**Outcome:** Prevents runtime configuration errors and enables better IDE support.

### Type-Safe Chain IDs

```kotlin
sealed class ChainId {
    sealed class Algorand : ChainId() {
        object Mainnet : Algorand()
        object Testnet : Algorand()
    }
    
    sealed class Polygon : ChainId() {
        data class Mainnet(val id: Long = 137) : Polygon()
        data class Mumbai(val id: Long = 80001) : Polygon()
    }
    
    // ... other chain IDs
}
```

**What this does:** Provides type-safe chain identifiers that comply with CAIP-2 specification.

**Outcome:** Ensures correct chain ID usage and prevents common errors.

## Usage Example

```kotlin
import com.trustweave.anchor.*
import java.util.ServiceLoader

// Discover blockchain adapter via SPI
val providers = ServiceLoader.load(BlockchainAnchorClientProvider::class.java)
val algorandProvider = providers.find { it.supportsChain("algorand:testnet") }

// Create anchor client with type-safe options
val options = AlgorandOptions(
    algodUrl = "https://testnet-api.algonode.cloud",
    privateKey = "..."
)

val client = algorandProvider?.create("algorand:testnet", options)

// Anchor data
val payload = "Hello, TrustWeave!".toByteArray()
val result = client?.writePayload(payload)

println("Anchored to: ${result?.anchorRef?.chainId}")
println("Transaction hash: ${result?.anchorRef?.transactionHash}")

// Read anchored data
val readData = result?.anchorRef?.let { client.readPayload(it) }
```

**What this does:** Uses SPI to discover a blockchain adapter, anchors data to the Algorand testnet, and then reads it back.

**Outcome:** Enables seamless blockchain anchoring across different networks.

## Supported Blockchains

TrustWeave provides adapters for:

- **Algorand** (`com.trustweave.chains:algorand`) – Algorand mainnet and testnet. See [Algorand Integration Guide](../integrations/algorand.md).
- **Polygon** (`com.trustweave.chains:polygon`) – Polygon mainnet and Mumbai testnet. See [Polygon Anchor Integration Guide](../integrations/README.md#blockchain-anchor-integrations).
- **Ethereum** (`com.trustweave.chains:ethereum`) – Ethereum mainnet and Sepolia testnet. See [Ethereum Anchor Integration Guide](../integrations/ethereum-anchor.md).
- **Base** (`com.trustweave.chains:base`) – Base (Coinbase L2). See [Base Anchor Integration Guide](../integrations/base-anchor.md).
- **Arbitrum** (`com.trustweave.chains:arbitrum`) – Arbitrum One and Sepolia rollup. See [Arbitrum Anchor Integration Guide](../integrations/arbitrum-anchor.md).
- **Ganache** (`com.trustweave.chains:ganache`) – Local Ethereum node for testing. See [Integration Modules](../integrations/README.md#blockchain-anchor-integrations).
- **Indy** (`com.trustweave.chains:indy`) – Hyperledger Indy. See [Integration Modules](../integrations/README.md#other-did--kms-integrations).

See the [Blockchain Anchor Integration Guides](../integrations/README.md) for detailed information about each adapter.

## Exception Hierarchy

The module provides structured exception handling:

- `BlockchainException` – base exception with chain ID and operation context
- `BlockchainTransactionException` – transaction failures (includes txHash, payloadSize, gasUsed)
- `BlockchainConnectionException` – connection failures (includes endpoint)
- `BlockchainConfigurationException` – configuration errors (includes config key)
- `BlockchainUnsupportedOperationException` – unsupported operations

**What this does:** Provides rich error context for debugging and error handling.

**Outcome:** Enables better error messages and easier troubleshooting.

## Dependencies

- Depends on [`trustweave-common`](trustweave-common.md) for core types, exceptions, and SPI interfaces (JSON utilities are included in `trustweave-common`)

## Next Steps

- Review [Blockchain Anchoring Concepts](../core-concepts/blockchain-anchoring.md) for understanding anchoring
- Explore [Blockchain Anchor Integration Guides](../integrations/README.md) for specific adapter setups
- See [Anchor Package README](../../chains/TrustWeave-anchor/src/main/kotlin/com/geoknoesis/TrustWeave/anchor/README.md) for detailed package documentation
- Check [Creating Plugins](../contributing/creating-plugins.md) to implement custom blockchain adapters

