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
    implementation("org.trustweave:did-plugins-polygon:0.6.0")
    implementation("org.trustweave:did-did-core:0.6.0")
    implementation("org.trustweave:did-plugins-base:0.6.0")
    implementation("org.trustweave:anchors-anchor-core:0.6.0")
    implementation("org.trustweave:common:0.6.0")

    // Web3j for Polygon blockchain
    implementation("org.web3j:core:4.10.0")

    // Polygon anchor client
    implementation("org.trustweave:anchors-plugins-polygon:0.6.0")
}
```

## Configuration

### Basic Configuration

```kotlin
import org.trustweave.polygondid.*
import org.trustweave.anchor.*
import org.trustweave.anchor.polygon.PolygonBlockchainAnchorClient
import org.trustweave.kms.inmemory.InMemoryKeyManagementService

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
import org.trustweave.did.*
import org.trustweave.did.spi.DidMethodProvider
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
import org.trustweave.did.identifiers.Did
import org.trustweave.did.resolver.DidResolutionResult

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
import org.trustweave.did.identifiers.Did
import org.trustweave.did.model.DidService

val did = Did("did:polygon:0x1234...")
val document = method.updateDid(did) { currentDoc ->
    currentDoc.copy(
        service = currentDoc.service + DidService(
            id = "${currentDoc.id}#didcomm",
            type = listOf("DIDCommMessaging"),
            serviceEndpoint = "https://example.com/didcomm"
        )
    )
}
```

### Deactivating a did:polygon

```kotlin
import org.trustweave.did.identifiers.Did

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
import org.trustweave.trust.TrustWeave
import org.trustweave.trust.types.getOrThrowDid
import org.trustweave.did.KeyAlgorithm
import org.trustweave.did.resolver.DidResolutionResult
import org.trustweave.kms.inmemory.InMemoryKeyManagementService
import org.trustweave.polygondid.*

val config = PolygonDidConfig.mumbai("https://rpc-mumbai.maticvigil.com")
val kms = InMemoryKeyManagementService()

val trustWeave = TrustWeave.build {
    customKms(kms)
    anchor {
        chain(config.chainId) {
            provider("polygon")
            // OptionsBuilder uses an infix String.to(value); list keys explicitly so the
            // call resolves to the builder rather than kotlin.Pair.
            options {
                "rpcUrl" to config.rpcUrl
                "chainId" to config.chainId
                config.privateKey?.let { "privateKey" to it }
                config.network?.let { "network" to it }
            }
        }
    }
    did {
        method("polygon") { algorithm("Secp256k1") }
    }
}

val did = trustWeave.createDid {
    method("polygon")
    algorithm(KeyAlgorithm.SECP256K1)
}.getOrThrowDid()

when (val resolved = trustWeave.resolveDid(did)) {
    is DidResolutionResult.Success -> println("Resolved: ${resolved.document.id}")
    else -> println("Resolve failed: $resolved")
}
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
import org.trustweave.testkit.anchor.InMemoryBlockchainAnchorClient

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

- See [Creating Plugins Guide](../../contributing/creating-plugins.md) for custom implementations
- Review [Blockchain Anchoring](../../core-concepts/blockchain-anchoring.md) for anchoring details
- Check [Ethereum DID Guide](ethr-did.md) for similar patterns

## References

- Polygon Documentation](https://docs.polygon.technology/)
- Polygon Network](https://polygon.technology/)
- Ethereum DID Specification](https://github.com/decentralized-identity/ethr-did-resolver) (similar pattern)

