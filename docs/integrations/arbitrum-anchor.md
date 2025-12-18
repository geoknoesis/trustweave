---
title: Arbitrum Blockchain Anchor Integration
---

# Arbitrum Blockchain Anchor Integration

> This guide covers the Arbitrum blockchain anchor client integration for TrustWeave. The Arbitrum adapter provides the largest L2 by TVL with EVM compatibility.

## Overview

The `chains/plugins/arbitrum` module provides a complete implementation of TrustWeave's `BlockchainAnchorClient` interface using Arbitrum One, the largest Ethereum L2 by TVL. This integration enables you to:

- Anchor credential digests on Arbitrum with lower fees than Ethereum
- Benefit from the largest L2 ecosystem by TVL
- Use EVM-compatible transaction data storage
- Leverage Ethereum security with L2 scalability

## Installation

Add the Arbitrum adapter module to your dependencies:

```kotlin
dependencies {
    implementation("com.trustweave.chains:arbitrum:1.0.0-SNAPSHOT")
    implementation("com.trustweave:trustweave-anchor:1.0.0-SNAPSHOT")
    implementation("com.trustweave:trustweave-json:1.0.0-SNAPSHOT")
    implementation("com.trustweave:distribution-all:1.0.0-SNAPSHOT")

    // Web3j for Arbitrum blockchain (EVM-compatible)
    implementation("org.web3j:core:5.0.1")
}
```

## Configuration

### Basic Configuration

```kotlin
import com.trustweave.anchor.*
import com.trustweave.arbitrum.*

// Create Arbitrum anchor client for mainnet
val options = mapOf(
    "rpcUrl" to "https://arb1.arbitrum.io/rpc",
    "privateKey" to "0x..." // Optional: for signing transactions
)

val client = ArbitrumBlockchainAnchorClient(
    ArbitrumBlockchainAnchorClient.MAINNET,
    options
)
```

### Pre-configured Networks

```kotlin
// Arbitrum One mainnet
val mainnetClient = ArbitrumBlockchainAnchorClient(
    ArbitrumBlockchainAnchorClient.MAINNET,
    mapOf(
        "rpcUrl" to "https://arb1.arbitrum.io/rpc",
        "privateKey" to "0x..."
    )
)

// Arbitrum Sepolia testnet
val sepoliaClient = ArbitrumBlockchainAnchorClient(
    ArbitrumBlockchainAnchorClient.ARBITRUM_SEPOLIA,
    mapOf(
        "rpcUrl" to "https://sepolia-rollup.arbitrum.io/rpc",
        "privateKey" to "0x..."
    )
)
```

### SPI Auto-Discovery

When the module is on the classpath, Arbitrum adapter is automatically available:

```kotlin
import com.trustweave.anchor.*
import com.trustweave.anchor.spi.*
import java.util.ServiceLoader

// Discover Arbitrum provider
val providers = ServiceLoader.load(BlockchainAnchorClientProvider::class.java)
val arbitrumProvider = providers.find { it.name == "arbitrum" }

// Create client
val client = arbitrumProvider?.create(
    ArbitrumBlockchainAnchorClient.MAINNET,
    mapOf("rpcUrl" to "https://arb1.arbitrum.io/rpc")
)
```

## Usage Examples

### Anchoring Data

```kotlin
import com.trustweave.anchor.*
import com.trustweave.arbitrum.*
import kotlinx.serialization.json.*

val client = ArbitrumBlockchainAnchorClient(
    ArbitrumBlockchainAnchorClient.ARBITRUM_SEPOLIA,
    mapOf(
        "rpcUrl" to "https://sepolia-rollup.arbitrum.io/rpc",
        "privateKey" to "0x..."
    )
)

// Anchor a JSON payload
val payload = buildJsonObject {
    put("digest", "uABC123...")
    put("timestamp", System.currentTimeMillis())
}

val result = client.writePayload(payload, "application/json")
println("Anchored to Arbitrum: ${result.ref.txHash}")
```

### Chain IDs

| Network | Chain ID | RPC URL |
|---------|----------|---------|
| Arbitrum One Mainnet | `eip155:42161` | `https://arb1.arbitrum.io/rpc` |
| Arbitrum Sepolia Testnet | `eip155:421614` | `https://sepolia-rollup.arbitrum.io/rpc` |

## Advantages

- **Largest L2**: Highest TVL among Ethereum L2s
- **Lower fees**: Significantly cheaper than Ethereum mainnet
- **Fast confirmations**: Optimistic rollup with fast finality
- **EVM compatible**: Same tools and patterns as Ethereum
- **Ethereum security**: Secured by Ethereum mainnet

## Integration with TrustWeave

```kotlin
import com.trustweave.TrustWeave
import com.trustweave.arbitrum.*

val TrustWeave = TrustWeave.create {
    blockchain {
        register(
            ArbitrumBlockchainAnchorClient.MAINNET,
            ArbitrumBlockchainAnchorClient(
                ArbitrumBlockchainAnchorClient.MAINNET,
                mapOf("rpcUrl" to "https://arb1.arbitrum.io/rpc")
            )
        )
    }
}
```

## Best Practices

1. **Use Arbitrum Sepolia for testing**: Always test on Arbitrum Sepolia before using mainnet
2. **Largest ecosystem**: Leverage Arbitrum's large DeFi and dApp ecosystem
3. **Lower fees**: Take advantage of Arbitrum's cost savings
4. **Bridge assets**: Use Arbitrum's official bridge for ETH transfers

## Next Steps

- See [Ethereum Blockchain Anchor Guide](ethereum-anchor.md) for mainnet option
- Review [Base Blockchain Anchor Guide](base-anchor.md) for Coinbase L2 option
- Check [Arbitrum Documentation](https://docs.arbitrum.io/) for Arbitrum-specific features

## References

- [Arbitrum Documentation](https://docs.arbitrum.io/)
- [Arbitrum One](https://arbitrum.io/)
- [Arbitrum Sepolia](https://sepolia-rollup-explorer.arbitrum.io/)

