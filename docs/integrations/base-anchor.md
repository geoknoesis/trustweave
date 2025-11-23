# Base Blockchain Anchor Integration

> This guide covers the Base (Coinbase L2) blockchain anchor client integration for TrustWeave. The Base adapter provides fast and low-cost anchoring with Ethereum security.

## Overview

The `chains/plugins/base` module provides a complete implementation of TrustWeave's `BlockchainAnchorClient` interface using Base, Coinbase's Ethereum L2. This integration enables you to:

- Anchor credential digests on Base with lower fees than Ethereum
- Benefit from Coinbase's backing and ecosystem
- Use EVM-compatible transaction data storage
- Leverage Ethereum security with L2 scalability

## Installation

Add the Base adapter module to your dependencies:

```kotlin
dependencies {
    implementation("com.trustweave.chains:base:1.0.0-SNAPSHOT")
    implementation("com.trustweave:TrustWeave-anchor:1.0.0-SNAPSHOT")
    implementation("com.trustweave:TrustWeave-json:1.0.0-SNAPSHOT")
    implementation("com.trustweave:TrustWeave-core:1.0.0-SNAPSHOT")
    
    // Web3j for Base blockchain (EVM-compatible)
    implementation("org.web3j:core:5.0.1")
}
```

## Configuration

### Basic Configuration

```kotlin
import com.trustweave.anchor.*
import com.trustweave.base.*

// Create Base anchor client for mainnet
val options = mapOf(
    "rpcUrl" to "https://mainnet.base.org",
    "privateKey" to "0x..." // Optional: for signing transactions
)

val client = BaseBlockchainAnchorClient(
    BaseBlockchainAnchorClient.MAINNET,
    options
)
```

### Pre-configured Networks

```kotlin
// Base mainnet
val mainnetClient = BaseBlockchainAnchorClient(
    BaseBlockchainAnchorClient.MAINNET,
    mapOf(
        "rpcUrl" to "https://mainnet.base.org",
        "privateKey" to "0x..."
    )
)

// Base Sepolia testnet
val sepoliaClient = BaseBlockchainAnchorClient(
    BaseBlockchainAnchorClient.BASE_SEPOLIA,
    mapOf(
        "rpcUrl" to "https://sepolia.base.org",
        "privateKey" to "0x..."
    )
)
```

### SPI Auto-Discovery

When the module is on the classpath, Base adapter is automatically available:

```kotlin
import com.trustweave.anchor.*
import com.trustweave.anchor.spi.*
import java.util.ServiceLoader

// Discover Base provider
val providers = ServiceLoader.load(BlockchainAnchorClientProvider::class.java)
val baseProvider = providers.find { it.name == "base" }

// Create client
val client = baseProvider?.create(
    BaseBlockchainAnchorClient.MAINNET,
    mapOf("rpcUrl" to "https://mainnet.base.org")
)
```

## Usage Examples

### Anchoring Data

```kotlin
import com.trustweave.anchor.*
import com.trustweave.base.*
import kotlinx.serialization.json.*

val client = BaseBlockchainAnchorClient(
    BaseBlockchainAnchorClient.BASE_SEPOLIA,
    mapOf(
        "rpcUrl" to "https://sepolia.base.org",
        "privateKey" to "0x..."
    )
)

// Anchor a JSON payload
val payload = buildJsonObject {
    put("digest", "uABC123...")
    put("timestamp", System.currentTimeMillis())
}

val result = client.writePayload(payload, "application/json")
println("Anchored to Base: ${result.ref.txHash}")
```

### Chain IDs

| Network | Chain ID | RPC URL |
|---------|----------|---------|
| Base Mainnet | `eip155:8453` | `https://mainnet.base.org` |
| Base Sepolia Testnet | `eip155:84532` | `https://sepolia.base.org` |

## Advantages

- **Lower fees**: Significantly cheaper than Ethereum mainnet
- **Fast confirmations**: Optimistic rollup with fast finality
- **Coinbase ecosystem**: Access to Coinbase's user base and tools
- **EVM compatible**: Same tools and patterns as Ethereum
- **Ethereum security**: Secured by Ethereum mainnet

## Integration with TrustWeave

```kotlin
import com.trustweave.TrustWeave
import com.trustweave.base.*

val TrustWeave = TrustWeave.create {
    blockchain {
        register(
            BaseBlockchainAnchorClient.MAINNET,
            BaseBlockchainAnchorClient(
                BaseBlockchainAnchorClient.MAINNET,
                mapOf("rpcUrl" to "https://mainnet.base.org")
            )
        )
    }
}
```

## Best Practices

1. **Use Base Sepolia for testing**: Always test on Base Sepolia before using mainnet
2. **Coinbase ecosystem**: Leverage Coinbase's infrastructure and tools
3. **Lower fees**: Take advantage of Base's cost savings
4. **Bridge assets**: Use Base's official bridge for ETH transfers

## Next Steps

- See [Ethereum Blockchain Anchor Guide](ethereum-anchor.md) for mainnet option
- Review [Arbitrum Blockchain Anchor Guide](arbitrum-anchor.md) for another L2 option
- Check [Base Documentation](https://docs.base.org/) for Base-specific features

## References

- [Base Documentation](https://docs.base.org/)
- [Base Network](https://base.org/)
- [Coinbase Base](https://www.coinbase.com/base)

