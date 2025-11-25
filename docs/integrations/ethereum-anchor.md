---
title: Ethereum Blockchain Anchor Integration
---

# Ethereum Blockchain Anchor Integration

> This guide covers the Ethereum mainnet blockchain anchor client integration for TrustWeave. The Ethereum adapter provides production-ready anchoring for Ethereum mainnet and Sepolia testnet.

## Overview

The `chains/plugins/ethereum` module provides a complete implementation of TrustWeave's `BlockchainAnchorClient` interface using Ethereum mainnet. This integration enables you to:

- Anchor credential digests on Ethereum mainnet
- Support Sepolia testnet for development and testing
- Use EVM-compatible transaction data storage
- Leverage Ethereum's security and decentralization

## Installation

Add the Ethereum adapter module to your dependencies:

```kotlin
dependencies {
    implementation("com.trustweave.chains:ethereum:1.0.0-SNAPSHOT")
    implementation("com.trustweave:trustweave-anchor:1.0.0-SNAPSHOT")
    implementation("com.trustweave:trustweave-json:1.0.0-SNAPSHOT")
    implementation("com.trustweave:trustweave-core:1.0.0-SNAPSHOT")
    
    // Web3j for Ethereum blockchain
    implementation("org.web3j:core:5.0.1")
}
```

## Configuration

### Basic Configuration

```kotlin
import com.trustweave.anchor.*
import com.trustweave.ethereum.*

// Create Ethereum anchor client for mainnet
val options = mapOf(
    "rpcUrl" to "https://eth-mainnet.g.alchemy.com/v2/YOUR_KEY",
    "privateKey" to "0x..." // Optional: for signing transactions
)

val client = EthereumBlockchainAnchorClient(
    EthereumBlockchainAnchorClient.MAINNET,
    options
)
```

### Pre-configured Networks

```kotlin
// Ethereum mainnet
val mainnetClient = EthereumBlockchainAnchorClient(
    EthereumBlockchainAnchorClient.MAINNET,
    mapOf(
        "rpcUrl" to "https://eth-mainnet.g.alchemy.com/v2/YOUR_KEY",
        "privateKey" to "0x..."
    )
)

// Sepolia testnet
val sepoliaClient = EthereumBlockchainAnchorClient(
    EthereumBlockchainAnchorClient.SEPOLIA,
    mapOf(
        "rpcUrl" to "https://eth-sepolia.g.alchemy.com/v2/YOUR_KEY",
        "privateKey" to "0x..."
    )
)
```

### SPI Auto-Discovery

When the module is on the classpath, Ethereum adapter is automatically available:

```kotlin
import com.trustweave.anchor.*
import com.trustweave.anchor.spi.*
import java.util.ServiceLoader

// Discover Ethereum provider
val providers = ServiceLoader.load(BlockchainAnchorClientProvider::class.java)
val ethereumProvider = providers.find { it.name == "ethereum" }

// Create client
val client = ethereumProvider?.create(
    EthereumBlockchainAnchorClient.MAINNET,
    mapOf("rpcUrl" to "https://eth-mainnet.g.alchemy.com/v2/YOUR_KEY")
)
```

### Integration Helper

```kotlin
import com.trustweave.ethereum.*

// Auto-discover and register
val result = EthereumIntegration.discoverAndRegister(
    options = mapOf(
        "rpcUrl" to "https://eth-mainnet.g.alchemy.com/v2/YOUR_KEY"
    )
)

// Or manually setup specific chains
val setup = EthereumIntegration.setup(
    chainIds = listOf(EthereumBlockchainAnchorClient.SEPOLIA),
    options = mapOf(
        "rpcUrl" to "https://eth-sepolia.g.alchemy.com/v2/YOUR_KEY"
    )
)
```

## Usage Examples

### Anchoring Data

```kotlin
import com.trustweave.anchor.*
import com.trustweave.ethereum.*
import kotlinx.serialization.json.*

val client = EthereumBlockchainAnchorClient(
    EthereumBlockchainAnchorClient.SEPOLIA,
    mapOf(
        "rpcUrl" to "https://eth-sepolia.g.alchemy.com/v2/YOUR_KEY",
        "privateKey" to "0x..."
    )
)

// Anchor a JSON payload
val payload = buildJsonObject {
    put("digest", "uABC123...")
    put("timestamp", System.currentTimeMillis())
}

val result = client.writePayload(payload, "application/json")
println("Anchored to: ${result.ref.txHash}")
```

### Reading Anchored Data

```kotlin
// Read anchored data
val txHash = result.ref.txHash
val readResult = client.readPayload(txHash)
println("Retrieved: ${readResult.payload}")
```

### Chain IDs

| Network | Chain ID | RPC URL Example |
|---------|----------|----------------|
| Ethereum Mainnet | `eip155:1` | `https://eth-mainnet.g.alchemy.com/v2/YOUR_KEY` |
| Sepolia Testnet | `eip155:11155111` | `https://eth-sepolia.g.alchemy.com/v2/YOUR_KEY` |

## Integration with TrustWeave

```kotlin
import com.trustweave.TrustWeave
import com.trustweave.ethereum.*

val TrustWeave = TrustWeave.create {
    blockchain {
        register(
            EthereumBlockchainAnchorClient.MAINNET,
            EthereumBlockchainAnchorClient(
                EthereumBlockchainAnchorClient.MAINNET,
                mapOf("rpcUrl" to "https://eth-mainnet.g.alchemy.com/v2/YOUR_KEY")
            )
        )
    }
}

// Anchor a credential digest
val digest = "uABC123..."
val payload = buildJsonObject { put("digest", digest) }
val result = TrustWeave.anchor(EthereumBlockchainAnchorClient.MAINNET, payload).getOrThrow()
println("Anchored: ${result.ref.txHash}")
```

## Configuration Options

| Option | Type | Required | Description |
|--------|------|----------|-------------|
| `rpcUrl` | String | Yes | Ethereum RPC endpoint URL |
| `privateKey` | String | No | Private key for signing transactions (hex format with 0x prefix) |
| `contractAddress` | String | No | Optional contract address for custom anchoring |

## Best Practices

1. **Use Sepolia for testing**: Always test on Sepolia before using mainnet
2. **RPC endpoint reliability**: Use reliable RPC providers (Alchemy, Infura, etc.)
3. **Gas management**: Monitor gas prices, especially on mainnet
4. **Private key security**: Never hardcode private keys, use environment variables or secure vaults
5. **Error handling**: Implement proper error handling for transaction failures

## Troubleshooting

### Transaction Failures

- Verify the account has sufficient ETH for gas fees
- Check RPC endpoint is accessible and correct
- Ensure private key is correctly formatted (0x prefix)
- Verify network (mainnet vs testnet) matches chain ID

### Reading Failures

- Verify transaction hash is correct
- Check transaction has been mined (may take time)
- Ensure RPC endpoint supports transaction queries

## Next Steps

- See [Base Blockchain Anchor Guide](base-anchor.md) for Coinbase L2 option
- Review [Arbitrum Blockchain Anchor Guide](arbitrum-anchor.md) for lower fees
- Check [Blockchain Anchoring Concepts](../core-concepts/blockchain-anchoring.md) for general guidance

## References

- [Ethereum Documentation](https://ethereum.org/en/developers/docs/)
- [Sepolia Testnet](https://sepolia.dev/)
- [Web3j Documentation](https://docs.web3j.io/)

