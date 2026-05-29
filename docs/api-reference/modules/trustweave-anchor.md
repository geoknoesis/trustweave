---
title: anchors:anchor-core
redirect_from:
  - /modules/trustweave-anchor/

---

# anchors:anchor-core

The `anchors:anchor-core` module provides blockchain anchoring abstraction with chain-agnostic interfaces for notarizing data on various blockchains.

```kotlin
dependencies {
    implementation("org.trustweave:anchors-anchor-core:0.6.0")
    implementation("org.trustweave:common:0.6.0")
}
```

**Result:** Gradle exposes the blockchain anchor client interfaces and utilities so you can anchor data to any supported blockchain.

## Overview

The `anchors:anchor-core` module provides:

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
import org.trustweave.anchor.*
import kotlinx.serialization.json.JsonElement

interface BlockchainAnchorClient {
    suspend fun writePayload(
        payload: JsonElement,
        mediaType: String = "application/json"
    ): AnchorResult

    suspend fun readPayload(ref: AnchorRef): AnchorResult
}
```

> Chain identification lives on `AnchorRef.chainId`, not on the client. Discover and select a client for a given CAIP-2 chain via `BlockchainAnchorRegistry` or a `BlockchainAnchorClientProvider`.

**What this does:** Defines the contract for anchoring operations that all blockchain adapters must fulfill.

**Outcome:** Enables TrustWeave to anchor data to multiple blockchains (Algorand, Polygon, Ethereum, etc.) through a unified interface.

### Type-Safe Options

The module provides type-safe configuration options:

```kotlin
sealed class BlockchainAnchorClientOptions {
    abstract fun toMap(): Map<String, Any?>
}

data class AlgorandOptions(
    val algodUrl: String? = null,
    val algodToken: String? = null,
    val privateKey: String? = null,
    val appId: String? = null
) : BlockchainAnchorClientOptions()

data class PolygonOptions(
    val rpcUrl: String? = null,
    val privateKey: String? = null,
    val contractAddress: String? = null
) : BlockchainAnchorClientOptions()

data class GanacheOptions(
    val rpcUrl: String? = null,
    val privateKey: String,
    val contractAddress: String? = null
) : BlockchainAnchorClientOptions()

data class IndyOptions(/* ... */) : BlockchainAnchorClientOptions()
```

> Provider `create(chainId, options)` accepts a `Map<String, Any?>`. Convert a typed options object via `options.toMap()` (or use the corresponding `fromMap` companion to build one).

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
import org.trustweave.anchor.*
import org.trustweave.anchor.options.AlgorandOptions
import org.trustweave.anchor.spi.BlockchainAnchorClientProvider
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.util.ServiceLoader

// Discover blockchain adapter via SPI
val providers = ServiceLoader.load(BlockchainAnchorClientProvider::class.java)
val algorandProvider = providers.first { "algorand:testnet" in it.supportedChains }

// Create anchor client with typed options (converted to a Map for the provider)
val options = AlgorandOptions(
    algodUrl = "https://testnet-api.algonode.cloud",
    privateKey = "..."
)
val client = algorandProvider.create("algorand:testnet", options.toMap())
    ?: error("Provider did not produce a client for algorand:testnet")

// Anchor a JSON payload
val payload = buildJsonObject { put("message", "Hello, TrustWeave!") }
val result = client.writePayload(payload)

println("Anchored to: ${result.ref.chainId}")
println("Transaction hash: ${result.ref.txHash}")

// Read anchored data
val read = client.readPayload(result.ref)
```

**What this does:** Uses SPI to discover a blockchain adapter, anchors data to the Algorand testnet, and then reads it back.

**Outcome:** Enables seamless blockchain anchoring across different networks.

## Supported Blockchains

TrustWeave provides adapters for:

- **Algorand** (`org.trustweave:anchors-plugins-algorand`) – Algorand mainnet and testnet. See [Algorand Integration Guide](../../how-to/integrations/algorand.md).
- **Polygon** (`org.trustweave:anchors-plugins-polygon`) – Polygon mainnet and Mumbai testnet. See [Polygon Anchor Integration Guide](../../how-to/integrations/README.md#blockchain-anchor-integrations).
- **Ethereum** (`org.trustweave:anchors-plugins-ethereum`) – Ethereum mainnet and Sepolia testnet. See [Ethereum Anchor Integration Guide](../../how-to/integrations/ethereum-anchor.md).
- **Base** (`org.trustweave:anchors-plugins-base`) – Base (Coinbase L2). See [Base Anchor Integration Guide](../../how-to/integrations/base-anchor.md).
- **Arbitrum** (`org.trustweave:anchors-plugins-arbitrum`) – Arbitrum One and Sepolia rollup. See [Arbitrum Anchor Integration Guide](../../how-to/integrations/arbitrum-anchor.md).
- **Ganache** (`org.trustweave:anchors-plugins-ganache`) – Local Ethereum node for testing. See [Integration Modules](../../how-to/integrations/README.md#blockchain-anchor-integrations).
- **Indy** (`org.trustweave:anchors-plugins-indy`) – Hyperledger Indy. See [Integration Modules](../../how-to/integrations/README.md#other-did--kms-integrations).

See the [Blockchain Anchor Integration Guides](../../how-to/integrations/README.md) for detailed information about each adapter.

## Exception Hierarchy

The module provides structured exception handling:

- `BlockchainException` – sealed base exception (extends `TrustWeaveException`) with chain ID and operation context
- `BlockchainException.TransactionFailed` – transaction failures (includes txHash, payloadSize, gasUsed)
- `BlockchainException.ConnectionFailed` – connection failures (includes endpoint)
- `BlockchainException.ConfigurationFailed` – configuration errors (includes config key)
- `BlockchainException.UnsupportedOperation` – unsupported operations
- `BlockchainException.ChainNotRegistered` – no client registered for the requested chain

**What this does:** Provides rich error context for debugging and error handling.

**Outcome:** Enables better error messages and easier troubleshooting.

## Dependencies

- Depends on [`common`](trustweave-common.md) for core types, exceptions, and SPI interfaces (JSON utilities are included in `common`)

## Next Steps

- Review [Blockchain Anchoring Concepts](../../core-concepts/blockchain-anchoring.md) for understanding anchoring
- Explore [Blockchain Anchor Integration Guides](../../how-to/integrations/README.md) for specific adapter setups
- Check [Creating Plugins](../../contributing/creating-plugins.md) to implement custom blockchain adapters

> **TODO:** Add a per-package README under `anchors/anchor-core/` for deeper documentation.

