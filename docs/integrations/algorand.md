---
title: Algorand Integration
---

# Algorand Integration

> This guide covers the Algorand blockchain integration for TrustWeave. The Algorand plugin provides blockchain anchoring support for Algorand mainnet, testnet, and betanet chains.

## Overview

The `chains/plugins/algorand` module provides a complete implementation of TrustWeave's `BlockchainAnchorClient` interface using Algorand blockchain. This integration enables you to:

- Anchor data to Algorand mainnet, testnet, or betanet
- Use Algorand transaction note fields to store payload data
- Leverage Algorand's fast transaction confirmation times
- Support type-safe configuration with `AlgorandOptions`
- Auto-discover Algorand adapter via SPI

## Installation

Add the Algorand module to your dependencies:

```kotlin
dependencies {
    implementation("com.trustweave.chains:algorand:1.0.0-SNAPSHOT")
    implementation("com.trustweave:trustweave-anchor:1.0.0-SNAPSHOT")
    implementation("com.trustweave:trustweave-common:1.0.0-SNAPSHOT")
    implementation("com.trustweave:trustweave-json:1.0.0-SNAPSHOT")

    // Algorand SDK
    implementation("com.algorand:algosdk:2.7.0")
}
```

## Configuration

### Basic Configuration

The Algorand adapter supports type-safe configuration using `AlgorandOptions`:

```kotlin
import com.trustweave.algorand.*
import com.trustweave.anchor.*
import com.trustweave.anchor.options.AlgorandOptions

// Create type-safe options
val options = AlgorandOptions(
    algodUrl = "https://testnet-api.algonode.cloud",
    indexerUrl = "https://testnet-idx.algonode.cloud",  // Optional
    privateKey = "base64-encoded-private-key"
)

// Create anchor client
val chainId = "algorand:testnet"
val client = AlgorandBlockchainAnchorClient(chainId, options)
```

### Pre-configured Networks

```kotlin
// Algorand testnet (recommended for development)
val testnetOptions = AlgorandOptions(
    algodUrl = "https://testnet-api.algonode.cloud",
    privateKey = "..."
)
val testnetClient = AlgorandBlockchainAnchorClient("algorand:testnet", testnetOptions)

// Algorand mainnet
val mainnetOptions = AlgorandOptions(
    algodUrl = "https://mainnet-api.algonode.cloud",
    privateKey = "..."
)
val mainnetClient = AlgorandBlockchainAnchorClient("algorand:mainnet", mainnetOptions)
```

### SPI Auto-Discovery

When the `chains/plugins/algorand` module is on the classpath, Algorand adapter is automatically discoverable:

```kotlin
import com.trustweave.anchor.*
import com.trustweave.anchor.options.AlgorandOptions
import java.util.ServiceLoader

// Discover Algorand provider
val providers = ServiceLoader.load(BlockchainAnchorClientProvider::class.java)
val algorandProvider = providers.find { it.supportsChain("algorand:testnet") }

// Create client with type-safe options
val options = AlgorandOptions(
    algodUrl = "https://testnet-api.algonode.cloud",
    privateKey = "..."
)

val client = algorandProvider?.create("algorand:testnet", options)
```

## Usage Examples

### Anchoring Data

```kotlin
import com.trustweave.algorand.*
import com.trustweave.anchor.*
import com.trustweave.anchor.options.AlgorandOptions
import kotlinx.coroutines.runBlocking

runBlocking {
    // Create client
    val options = AlgorandOptions(
        algodUrl = "https://testnet-api.algonode.cloud",
        privateKey = "..."
    )
    val client = AlgorandBlockchainAnchorClient("algorand:testnet", options)

    // Anchor data
    val payload = "Hello, Algorand!".toByteArray()
    val result = client.writePayload(payload)

    result.fold(
        onSuccess = { anchorResult ->
            println("Anchored to: ${anchorResult.anchorRef.chainId}")
            println("Transaction hash: ${anchorResult.anchorRef.transactionHash}")
            println("Block height: ${anchorResult.anchorRef.metadata?.get("blockHeight")}")
        },
        onFailure = { error ->
            println("Anchoring failed: ${error.message}")
        }
    )
}
```

### Reading Anchored Data

```kotlin
import com.trustweave.anchor.*

val anchorRef = AnchorRef(
    chainId = "algorand:testnet",
    transactionHash = "abc123...",
    metadata = mapOf("blockHeight" to 12345L)
)

val result = client.readPayload(anchorRef)

result.fold(
    onSuccess = { data ->
        println("Read payload: ${data.toString(Charsets.UTF_8)}")
    },
    onFailure = { error ->
        println("Read failed: ${error.message}")
    }
)
```

### Using with TrustWeave Facade

```kotlin
import com.trustweave.TrustWeave
import com.trustweave.algorand.*
import com.trustweave.anchor.options.AlgorandOptions
import kotlinx.coroutines.runBlocking

runBlocking {
    // Setup Algorand client
    val options = AlgorandOptions(
        algodUrl = "https://testnet-api.algonode.cloud",
        privateKey = "..."
    )
    val client = AlgorandBlockchainAnchorClient("algorand:testnet", options)

    // Register with TrustWeave
    val TrustWeave = TrustWeave.create()
    // Register during TrustWeave.create { }
    val TrustWeave = TrustWeave.create {
        blockchains {
            "algorand:testnet" to client
        }
    }

    // Anchor credential
    val credential = /* your credential */
    val anchorResult = TrustWeave.blockchains.anchor(
        data = credential,
        serializer = VerifiableCredential.serializer(),
        chainId = "algorand:testnet"
    )

    anchorResult.fold(
        onSuccess = { result ->
            println("Anchored: ${result.anchorRef.transactionHash}")
        },
        onFailure = { error ->
            println("Error: ${error.message}")
        }
    )
}
```

## Type-Safe Configuration

The `AlgorandOptions` class provides compile-time validation:

```kotlin
val options = AlgorandOptions(
    algodUrl = "https://testnet-api.algonode.cloud",
    indexerUrl = "https://testnet-idx.algonode.cloud",  // Optional
    privateKey = "base64-encoded-private-key"  // Required
)
```

**Benefits:**
- ✅ Compile-time validation of required fields
- ✅ Type-safe configuration
- ✅ IDE autocomplete support
- ✅ Clear error messages for missing fields

## Error Handling

The Algorand adapter provides structured error handling:

```kotlin
import com.trustweave.anchor.exceptions.*

try {
    val result = client.writePayload(payload)
    // Handle success
} catch (e: BlockchainTransactionException) {
    // Rich context: chainId, txHash, payloadSize
    println("Transaction failed: ${e.txHash}")
    println("Chain: ${e.chainId}")
    println("Payload size: ${e.payloadSize}")
} catch (e: BlockchainConnectionException) {
    // Connection error: endpoint
    println("Connection failed: ${e.endpoint}")
} catch (e: BlockchainConfigurationException) {
    // Configuration error: config key
    println("Config error: ${e.configKey}")
}
```

## Supported Chains

The Algorand adapter supports:

- **algorand:mainnet** – Algorand mainnet
- **algorand:testnet** – Algorand testnet (recommended for development)
- **algorand:betanet** – Algorand betanet

## Network Endpoints

Default endpoints for each network:

- **Mainnet**: `https://mainnet-api.algonode.cloud`
- **Testnet**: `https://testnet-api.algonode.cloud`
- **Betanet**: `https://betanet-api.algonode.cloud`

You can override these by providing custom URLs in `AlgorandOptions`.

## Transaction Fees

Algorand has very low transaction fees (typically 0.001 ALGO). The adapter automatically handles fee estimation and transaction submission.

## Testing

```bash
# Run all Algorand tests
./gradlew :chains/plugins/algorand:test

# Run EO integration test
./gradlew :chains/plugins/algorand:test --tests "AlgorandEoIntegrationTest"
```

## Next Steps

- Review [Blockchain Anchoring Concepts](../core-concepts/blockchain-anchoring.md) for anchoring fundamentals
- See [Ethereum Anchor Guide](ethereum-anchor.md) for EVM-compatible alternative
- Check [Polygon Anchor Guide](base-anchor.md) for lower-cost L2 option
- Explore [Creating Plugins](../contributing/creating-plugins.md) to understand blockchain adapter implementation

## References

- [Algorand Documentation](https://developer.algorand.org/docs/)
- [Algorand SDK](https://github.com/algorand/java-algorand-sdk)
- [TrustWeave Anchor Module](../modules/trustweave-anchor.md)
- [Blockchain Anchoring Guide](../core-concepts/blockchain-anchoring.md)

