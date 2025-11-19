# Ethereum DID (did:ethr) Integration

> This guide covers the did:ethr method integration for VeriCore. The did:ethr plugin provides Ethereum DID resolution with blockchain anchoring support.

## Overview

The `vericore-did-ethr` module provides an implementation of VeriCore's `DidMethod` interface using the Ethereum DID method. This integration enables you to:

- Create and resolve DIDs on Ethereum blockchain
- Store DID documents via blockchain anchoring
- Support Ethereum mainnet and testnets (Sepolia, etc.)
- Use secp256k1 keys compatible with Ethereum addresses
- Integrate with ERC1056 registry contracts (via blockchain anchoring)

## Installation

Add the did:ethr module to your dependencies:

```kotlin
dependencies {
    implementation("com.geoknoesis.vericore:vericore-did-ethr:1.0.0-SNAPSHOT")
    implementation("com.geoknoesis.vericore:vericore-did:1.0.0-SNAPSHOT")
    implementation("com.geoknoesis.vericore:vericore-did-base:1.0.0-SNAPSHOT")
    implementation("com.geoknoesis.vericore:vericore-anchor:1.0.0-SNAPSHOT")
    implementation("com.geoknoesis.vericore:vericore-core:1.0.0-SNAPSHOT")
    
    // Web3j for Ethereum blockchain
    implementation("org.web3j:core:4.10.0")
    
    // Optional: Polygon client for EVM-compatible chains
    implementation("com.geoknoesis.vericore:vericore-polygon:1.0.0-SNAPSHOT")
}
```

## Configuration

### Basic Configuration

```kotlin
import com.geoknoesis.vericore.ethrdid.*
import com.geoknoesis.vericore.anchor.*
import com.geoknoesis.vericore.polygon.PolygonBlockchainAnchorClient
import com.geoknoesis.vericore.kms.*

// Create configuration
val config = EthrDidConfig.builder()
    .rpcUrl("https://eth-sepolia.g.alchemy.com/v2/YOUR_KEY")
    .chainId("eip155:11155111") // Sepolia testnet
    .privateKey("0x...") // Optional: for signing transactions
    .build()

// Create blockchain anchor client
val anchorClient = PolygonBlockchainAnchorClient(config.chainId, config.toMap())

// Create KMS
val kms = InMemoryKeyManagementService()

// Create did:ethr method
val method = EthrDidMethod(kms, anchorClient, config)
```

### Pre-configured Networks

```kotlin
// Ethereum mainnet
val mainnetConfig = EthrDidConfig.mainnet(
    rpcUrl = "https://eth-mainnet.g.alchemy.com/v2/YOUR_KEY",
    privateKey = "0x..." // Optional
)

// Sepolia testnet
val sepoliaConfig = EthrDidConfig.sepolia(
    rpcUrl = "https://eth-sepolia.g.alchemy.com/v2/YOUR_KEY",
    privateKey = "0x..." // Optional
)
```

### SPI Auto-Discovery

When the module is on the classpath, did:ethr is automatically available:

```kotlin
import com.geoknoesis.vericore.did.*
import com.geoknoesis.vericore.anchor.*
import java.util.ServiceLoader

// Discover did:ethr provider
val providers = ServiceLoader.load(DidMethodProvider::class.java)
val ethrProvider = providers.find { it.supportedMethods.contains("ethr") }

// Create method with required options
val options = didCreationOptions {
    property("rpcUrl", "https://eth-sepolia.g.alchemy.com/v2/YOUR_KEY")
    property("chainId", "eip155:11155111")
    property("anchorClient", anchorClient) // Required: provide anchor client
}

val method = ethrProvider?.create("ethr", options)
```

## Usage Examples

### Creating a did:ethr

```kotlin
val config = EthrDidConfig.sepolia("https://eth-sepolia.g.alchemy.com/v2/YOUR_KEY")
val anchorClient = PolygonBlockchainAnchorClient(config.chainId, config.toMap())
val kms = InMemoryKeyManagementService()
val method = EthrDidMethod(kms, anchorClient, config)

// Create DID (uses secp256k1 for Ethereum compatibility)
val options = didCreationOptions {
    algorithm = KeyAlgorithm.SECP256K1
    purpose(KeyPurpose.AUTHENTICATION)
    purpose(KeyPurpose.ASSERTION)
}

val document = method.createDid(options)
println("Created: ${document.id}") // did:ethr:sepolia:0x... or did:ethr:0x...
```

### Resolving a did:ethr

```kotlin
val result = method.resolveDid("did:ethr:0x1234...")

result.document?.let { doc ->
    println("Resolved: ${doc.id}")
    println("Verification methods: ${doc.verificationMethod.size}")
} ?: println("Not found")
```

### Updating a did:ethr

```kotlin
val document = method.updateDid("did:ethr:0x1234...") { currentDoc ->
    currentDoc.copy(
        service = currentDoc.service + Service(
            id = "${currentDoc.id}#didcomm",
            type = "DIDCommMessaging",
            serviceEndpoint = "https://example.com/didcomm"
        )
    )
}
```

### Deactivating a did:ethr

```kotlin
val deactivated = method.deactivateDid("did:ethr:0x1234...")
println("Deactivated: $deactivated")
```

## DID Format

### Network-based DID

```
did:ethr:mainnet:0x1234567890123456789012345678901234567890
did:ethr:sepolia:0x1234567890123456789012345678901234567890
```

### Network-agnostic DID

```
did:ethr:0x1234567890123456789012345678901234567890
```

## Blockchain Anchoring

did:ethr stores DID documents on the Ethereum blockchain using the `BlockchainAnchorClient` infrastructure. Documents are anchored to the blockchain via transactions, and the transaction hash is used for resolution.

For production use, consider integrating with ERC1056 registry contracts for standard DID resolution.

## Algorithm Support

did:ethr supports:
- **secp256k1** (recommended, Ethereum-native)
- **Ed25519** (alternative)

## Configuration Options

### EthrDidConfig

```kotlin
val config = EthrDidConfig.builder()
    .rpcUrl("https://eth-mainnet.g.alchemy.com/v2/YOUR_KEY") // Required
    .chainId("eip155:1")                                      // Required: mainnet
    .registryAddress("0x...")                                 // Optional: ERC1056 registry
    .privateKey("0x...")                                      // Optional: for transactions
    .network("mainnet")                                       // Optional: network name
    .build()
```

### Chain IDs

| Network | Chain ID |
|---------|----------|
| Ethereum Mainnet | `eip155:1` |
| Sepolia Testnet | `eip155:11155111` |
| Goerli Testnet | `eip155:5` |

## Integration with VeriCore

```kotlin
import com.geoknoesis.vericore.VeriCore
import com.geoknoesis.vericore.ethrdid.*
import com.geoknoesis.vericore.anchor.*
import com.geoknoesis.vericore.polygon.PolygonBlockchainAnchorClient

val config = EthrDidConfig.sepolia("https://eth-sepolia.g.alchemy.com/v2/YOUR_KEY")
val anchorClient = PolygonBlockchainAnchorClient(config.chainId, config.toMap())

val vericore = VeriCore.create {
    kms = InMemoryKeyManagementService()
    
    blockchain {
        register(config.chainId, anchorClient)
    }
    
    didMethods {
        + EthrDidMethod(kms!!, anchorClient, config)
    }
}

// Use did:ethr
val did = vericore.createDid("ethr") {
    algorithm = KeyAlgorithm.SECP256K1
}.getOrThrow()

val resolved = vericore.resolveDid(did.id).getOrThrow()
```

## ERC1056 Compatibility

This implementation uses blockchain anchoring for document storage. For full ERC1056 compatibility, integrate with an ERC1056 registry contract that provides standard DID resolution methods.

Future enhancements:
- Direct ERC1056 registry contract integration
- Standard `identity()` and `changed()` event handlers
- Registry contract deployment support

## Error Handling

Common errors and solutions:

| Error | Cause | Solution |
|-------|-------|----------|
| `rpcUrl is required` | Missing RPC endpoint | Provide Ethereum RPC URL |
| `chainId is required` | Missing chain ID | Specify chain ID (eip155:1, etc.) |
| `BlockchainAnchorClient is required` | Missing anchor client | Provide anchor client in options |
| `Failed to anchor document` | Transaction failed | Check private key, gas, network connectivity |
| `DID document not found` | Not anchored yet | Anchor document first or check blockchain |

## Testing

For testing without actual blockchain:

```kotlin
import com.geoknoesis.vericore.testkit.anchor.InMemoryBlockchainAnchorClient

val config = EthrDidConfig.sepolia("https://eth-sepolia.g.alchemy.com/v2/YOUR_KEY")
val anchorClient = InMemoryBlockchainAnchorClient(config.chainId)
val method = EthrDidMethod(kms, anchorClient, config)

// Create and resolve (stored in memory)
val document = method.createDid(options)
val result = method.resolveDid(document.id)
```

## Best Practices

1. **Use testnets for development**: Sepolia testnet for testing
2. **Private key security**: Never hardcode private keys, use environment variables or secure storage
3. **Gas management**: Monitor gas prices for transaction costs
4. **Chain ID validation**: Always validate chain ID matches network
5. **Error handling**: Implement proper error handling for blockchain operations

## Troubleshooting

### Transaction Failures

- Check private key is valid and has sufficient funds
- Verify RPC endpoint is accessible
- Check gas prices and adjust if needed
- Ensure chain ID matches the network

### Resolution Failures

- Verify document was anchored successfully
- Check transaction hash is valid
- Ensure blockchain client can access the network

## Next Steps

- See [Creating Plugins Guide](../contributing/creating-plugins.md) for custom implementations
- Review [Blockchain Anchoring](../core-concepts/blockchain-anchoring.md) for anchoring details
- Check [Integration Modules](../integrations/README.md) for other DID methods

## References

- [Ethereum DID Method Specification](https://github.com/decentralized-identity/ethr-did-resolver)
- [ERC1056 Registry Contract](https://github.com/uport-project/ethr-did-registry)
- [Ethereum DID Resolver](https://github.com/decentralized-identity/ethr-did-resolver)

