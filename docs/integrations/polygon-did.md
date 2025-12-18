---
title: Polygon DID Integration
parent: Integration Modules
---

# Polygon DID Integration

> This guide covers the did:polygon method integration for TrustWeave. The did:polygon plugin provides Polygon DID resolution with blockchain anchoring support, reusing the Ethereum DID pattern for EVM compatibility.

## Overview

The `did/plugins/polygon` module provides an implementation of TrustWeave's `DidMethod` interface using the Polygon blockchain. This integration enables you to:

- Create and resolve DIDs on Polygon blockchain
- Store DID documents via blockchain anchoring
- Support Polygon mainnet and Mumbai testnet
- Lower transaction costs compared to Ethereum mainnet
- Reuse Ethereum DID patterns (EVM-compatible)

## Installation

Add the did:polygon module to your dependencies:

```kotlin
dependencies {
    implementation("com.trustweave.did:polygon:1.0.0-SNAPSHOT")
    implementation("com.trustweave:trustweave-did:1.0.0-SNAPSHOT")
    implementation("com.trustweave.did:base:1.0.0-SNAPSHOT")
    implementation("com.trustweave:trustweave-anchor:1.0.0-SNAPSHOT")
    implementation("com.trustweave:trustweave-common:1.0.0-SNAPSHOT")

    // Web3j for Polygon blockchain
    implementation("org.web3j:core:4.10.0")

    // Polygon anchor client
    implementation("com.trustweave.chains:polygon:1.0.0-SNAPSHOT")
}
```

## Configuration

### Basic Configuration

```kotlin
import com.trustweave.polygondid.*
import com.trustweave.anchor.*
import com.trustweave.polygon.PolygonBlockchainAnchorClient
import com.trustweave.kms.*

// Create configuration
val config = PolygonDidConfig.builder()
    .rpcUrl("https://rpc-mumbai.maticvigil.com")
    .chainId("eip155:80001") // Mumbai testnet
    .privateKey("0x...") // Optional: for signing transactions
    .build()

// Create blockchain anchor client
val anchorClient = PolygonBlockchainAnchorClient(config.chainId, config.toMap())

// Create KMS
val kms = InMemoryKeyManagementService()

// Create did:polygon method
val method = PolygonDidMethod(kms, anchorClient, config)
```

### Pre-configured Networks

```kotlin
// Polygon mainnet
val mainnetConfig = PolygonDidConfig.mainnet(
    rpcUrl = "https://polygon-rpc.com", // Optional: uses default if omitted
    privateKey = "0x..." // Optional
)

// Mumbai testnet
val mumbaiConfig = PolygonDidConfig.mumbai(
    rpcUrl = "https://rpc-mumbai.maticvigil.com", // Optional
    privateKey = "0x..." // Optional
)
```

### SPI Auto-Discovery

When the module is on the classpath, did:polygon is automatically available:

```kotlin
import com.trustweave.did.*
import java.util.ServiceLoader

// Discover did:polygon provider
val providers = ServiceLoader.load(DidMethodProvider::class.java)
val polygonProvider = providers.find { it.supportedMethods.contains("polygon") }

// Create method with required options
val options = didCreationOptions {
    property("rpcUrl", "https://rpc-mumbai.maticvigil.com")
    property("chainId", "eip155:80001")
    property("anchorClient", anchorClient) // Required: provide anchor client
}

val method = polygonProvider?.create("polygon", options)
```

## Usage Examples

### Creating a did:polygon

```kotlin
val config = PolygonDidConfig.mumbai("https://rpc-mumbai.maticvigil.com")
val anchorClient = PolygonBlockchainAnchorClient(config.chainId, config.toMap())
val kms = InMemoryKeyManagementService()
val method = PolygonDidMethod(kms, anchorClient, config)

// Create DID (uses secp256k1 for EVM compatibility)
val options = didCreationOptions {
    algorithm = KeyAlgorithm.SECP256K1
    purpose(KeyPurpose.AUTHENTICATION)
    purpose(KeyPurpose.ASSERTION)
}

val document = method.createDid(options)
println("Created: ${document.id}") // did:polygon:mumbai:0x... or did:polygon:0x...
```

### Resolving a did:polygon

```kotlin
import com.trustweave.did.identifiers.Did
import com.trustweave.did.resolver.DidResolutionResult

val did = Did("did:polygon:0x1234...")
val result = method.resolveDid(did)

when (result) {
    is DidResolutionResult.Success -> {
        println("Resolved: ${result.document.id}")
        println("Verification methods: ${result.document.verificationMethod.size}")
    }
    is DidResolutionResult.Failure.NotFound -> {
        println("DID not found: ${result.did.value}")
    }
    else -> println("Resolution failed")
}
```

### Updating a did:polygon

```kotlin
import com.trustweave.did.identifiers.Did

val did = Did("did:polygon:0x1234...")
val document = method.updateDid(did) { currentDoc ->
    currentDoc.copy(
        service = currentDoc.service + Service(
            id = "${currentDoc.id}#didcomm",
            type = "DIDCommMessaging",
            serviceEndpoint = "https://example.com/didcomm"
        )
    )
}
```

### Deactivating a did:polygon

```kotlin
import com.trustweave.did.identifiers.Did

val did = Did("did:polygon:0x1234...")
val deactivated = method.deactivateDid(did)
println("Deactivated: $deactivated")
```

## DID Format

### Network-based DID

```
did:polygon:mainnet:0x1234567890123456789012345678901234567890
did:polygon:mumbai:0x1234567890123456789012345678901234567890
```

### Network-agnostic DID

```
did:polygon:0x1234567890123456789012345678901234567890
```

## Advantages over Ethereum

- **Lower gas costs**: Polygon transactions are significantly cheaper
- **Faster confirmation**: Polygon has faster block times
- **EVM compatibility**: Same smart contract patterns as Ethereum
- **Scalability**: Polygon handles higher transaction throughput

## Blockchain Anchoring

did:polygon stores DID documents on the Polygon blockchain using the `BlockchainAnchorClient` infrastructure. Documents are anchored via transactions, similar to did:ethr.

## Algorithm Support

did:polygon supports:
- **secp256k1** (recommended, EVM-native)
- **Ed25519** (alternative)

## Configuration Options

### PolygonDidConfig

```kotlin
val config = PolygonDidConfig.builder()
    .rpcUrl("https://polygon-rpc.com")           // Required
    .chainId("eip155:137")                       // Required: mainnet
    .registryAddress("0x...")                    // Optional: registry contract
    .privateKey("0x...")                         // Optional: for transactions
    .network("mainnet")                          // Optional: network name
    .build()
```

### Chain IDs

| Network | Chain ID |
|---------|----------|
| Polygon Mainnet | `eip155:137` |
| Mumbai Testnet | `eip155:80001` |

## Integration with TrustWeave

```kotlin
import com.trustweave.TrustWeave
import com.trustweave.polygondid.*
import com.trustweave.anchor.*
import com.trustweave.polygon.PolygonBlockchainAnchorClient

val config = PolygonDidConfig.mumbai("https://rpc-mumbai.maticvigil.com")
val anchorClient = PolygonBlockchainAnchorClient(config.chainId, config.toMap())

val TrustWeave = TrustWeave.create {
    kms = InMemoryKeyManagementService()

    blockchain {
        register(config.chainId, anchorClient)
    }

    didMethods {
        + PolygonDidMethod(kms!!, anchorClient, config)
    }
}

// Use did:polygon
val did = TrustWeave.createDid("polygon") {
    algorithm = KeyAlgorithm.SECP256K1
}.getOrThrow()

val resolved = TrustWeave.resolveDid(did.id).getOrThrow()
```

## Error Handling

Common errors and solutions:

| Error | Cause | Solution |
|-------|-------|----------|
| `rpcUrl is required` | Missing RPC endpoint | Provide Polygon RPC URL |
| `chainId is required` | Missing chain ID | Specify chain ID (eip155:137, etc.) |
| `BlockchainAnchorClient is required` | Missing anchor client | Provide anchor client in options |
| `Failed to anchor document` | Transaction failed | Check private key, gas, network connectivity |

## Testing

For testing without actual blockchain:

```kotlin
import com.trustweave.testkit.anchor.InMemoryBlockchainAnchorClient

val config = PolygonDidConfig.mumbai("https://rpc-mumbai.maticvigil.com")
val anchorClient = InMemoryBlockchainAnchorClient(config.chainId)
val method = PolygonDidMethod(kms, anchorClient, config)

// Create and resolve (stored in memory)
val document = method.createDid(options)
val result = method.resolveDid(document.id)
```

## Best Practices

1. **Use testnet for development**: Mumbai testnet for testing
2. **Lower costs**: Take advantage of Polygon's lower transaction costs
3. **Private key security**: Never hardcode private keys
4. **Gas management**: Monitor gas prices (much lower than Ethereum)
5. **Chain ID validation**: Always validate chain ID matches network

## Comparison with did:ethr

did:polygon is similar to did:ethr but optimized for Polygon:

| Feature | did:ethr | did:polygon |
|---------|----------|-------------|
| Chain | Ethereum | Polygon |
| Gas costs | Higher | Lower |
| Block time | ~15s | ~2s |
| EVM compatible | Yes | Yes |
| Contract patterns | Same | Same |

## Next Steps

- See [Creating Plugins Guide](../contributing/creating-plugins.md) for custom implementations
- Review [Blockchain Anchoring](../core-concepts/blockchain-anchoring.md) for anchoring details
- Check [Ethereum DID Guide](ethr-did.md) for similar patterns

## References

- [Polygon Documentation](https://docs.polygon.technology/)
- [Polygon Network](https://polygon.technology/)
- [Ethereum DID Specification](https://github.com/decentralized-identity/ethr-did-resolver) (similar pattern)

