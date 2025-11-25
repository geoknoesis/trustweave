---
title: Cheqd DID (did:cheqd) Integration
---

# Cheqd DID (did:cheqd) Integration

> This guide covers the did:cheqd method integration for TrustWeave. The did:cheqd plugin provides Cheqd network DID resolution with payment-enabled features.

## Overview

The `did/plugins/cheqd` module provides an implementation of TrustWeave's `DidMethod` interface using the Cheqd network. This integration enables you to:

- Create and resolve DIDs on Cheqd blockchain
- Store DID documents via blockchain anchoring
- Support payment-enabled DID operations
- Integrate with Cheqd network's Cosmos-based infrastructure

## Installation

Add the did:cheqd module to your dependencies:

```kotlin
dependencies {
    implementation("com.trustweave.did:cheqd:1.0.0-SNAPSHOT")
    implementation("com.trustweave:trustweave-did:1.0.0-SNAPSHOT")
    implementation("com.trustweave.did:base:1.0.0-SNAPSHOT")
    implementation("com.trustweave:trustweave-anchor:1.0.0-SNAPSHOT")
    implementation("com.trustweave:trustweave-core:1.0.0-SNAPSHOT")
    
    // HTTP client for Cheqd network integration
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
}
```

## Configuration

### Basic Configuration

```kotlin
import com.trustweave.cheqddid.*
import com.trustweave.anchor.*
import com.trustweave.testkit.anchor.InMemoryBlockchainAnchorClient
import com.trustweave.kms.*

// Create configuration
val config = CheqdDidConfig.builder()
    .cheqdApiUrl("https://api.cheqd.net")        // Cheqd API URL
    .network("mainnet")                          // Network (mainnet/testnet)
    .accountAddress("cheqd1...")                  // Optional: account address
    .privateKey("...")                           // Optional: private key
    .timeoutSeconds(30)                          // HTTP timeout
    .build()

// Create blockchain anchor client
val anchorClient = InMemoryBlockchainAnchorClient("cheqd:mainnet")

// Create KMS
val kms = InMemoryKeyManagementService()

// Create did:cheqd method
val method = CheqdDidMethod(kms, anchorClient, config)
```

### Pre-configured Networks

```kotlin
// Cheqd mainnet
val mainnetConfig = CheqdDidConfig.mainnet(
    cheqdApiUrl = "https://api.cheqd.net"
)

// Cheqd testnet
val testnetConfig = CheqdDidConfig.testnet(
    cheqdApiUrl = "https://testnet-api.cheqd.net"
)
```

### SPI Auto-Discovery

When the module is on the classpath, did:cheqd is automatically available:

```kotlin
import com.trustweave.did.*
import com.trustweave.anchor.*
import java.util.ServiceLoader

// Discover did:cheqd provider
val providers = ServiceLoader.load(DidMethodProvider::class.java)
val cheqdProvider = providers.find { it.supportedMethods.contains("cheqd") }

// Create method with required options
val options = didCreationOptions {
    property("cheqdApiUrl", "https://api.cheqd.net")
    property("network", "mainnet")
    property("anchorClient", anchorClient) // Required: provide anchor client
}

val method = cheqdProvider?.create("cheqd", options)
```

## Usage Examples

### Creating a did:cheqd

```kotlin
val config = CheqdDidConfig.mainnet("https://api.cheqd.net")
val anchorClient = InMemoryBlockchainAnchorClient("cheqd:mainnet")
val kms = InMemoryKeyManagementService()
val method = CheqdDidMethod(kms, anchorClient, config)

// Create DID (uses Ed25519 by default)
val options = didCreationOptions {
    algorithm = KeyAlgorithm.ED25519
    purpose(KeyPurpose.AUTHENTICATION)
    purpose(KeyPurpose.ASSERTION)
}

val document = method.createDid(options)
println("Created: ${document.id}") // did:cheqd:mainnet:...
```

### Resolving a did:cheqd

```kotlin
val result = method.resolveDid("did:cheqd:mainnet:...")

result.document?.let { doc ->
    println("Resolved: ${doc.id}")
    println("Verification methods: ${doc.verificationMethod.size}")
} ?: println("Not found")
```

### Updating a did:cheqd

```kotlin
val document = method.updateDid("did:cheqd:mainnet:...") { currentDoc ->
    currentDoc.copy(
        service = currentDoc.service + Service(
            id = "${currentDoc.id}#didcomm",
            type = "DIDCommMessaging",
            serviceEndpoint = "https://example.com/didcomm"
        )
    )
}
```

### Deactivating a did:cheqd

```kotlin
val deactivated = method.deactivateDid("did:cheqd:mainnet:...")
println("Deactivated: $deactivated")
```

## DID Format

### Network-based DID

```
did:cheqd:mainnet:abc123def456...
did:cheqd:testnet:abc123def456...
```

Cheqd DIDs use a network-prefixed format:
- Method: `cheqd`
- Network: `mainnet` or `testnet`
- Identifier: Base32-encoded hash of the key

## Cheqd Network

### Network Overview

Cheqd is a Cosmos-based blockchain network designed for identity and credentials:

- **Cosmos-based**: Built on Cosmos SDK
- **Payment-enabled**: Supports payment for DID operations
- **Credential-focused**: Designed for verifiable credentials ecosystem

### Network Endpoints

| Network | API URL |
|---------|---------|
| Mainnet | `https://api.cheqd.net` |
| Testnet | `https://testnet-api.cheqd.net` |

## Blockchain Anchoring

did:cheqd stores DID documents on the Cheqd blockchain using the `BlockchainAnchorClient` infrastructure. Documents are anchored to the blockchain via transactions, and the transaction hash is used for resolution.

### Payment Features

Cheqd network supports payment-enabled DID operations:

- Transaction fees for DID creation
- Payment for DID updates
- Fee structure based on network parameters

## Algorithm Support

did:cheqd supports:
- **Ed25519** (recommended, Cosmos-native)
- Other algorithms as supported by Cosmos SDK

## Configuration Options

### CheqdDidConfig

```kotlin
val config = CheqdDidConfig.builder()
    .cheqdApiUrl("https://api.cheqd.net")      // Optional: API URL
    .network("mainnet")                        // Network (default: mainnet)
    .accountAddress("cheqd1...")                // Optional: account address
    .privateKey("...")                         // Optional: private key
    .timeoutSeconds(30)                        // Optional: HTTP timeout
    .build()
```

### Network Options

- `mainnet`: Production Cheqd network
- `testnet`: Test Cheqd network

## Integration with TrustWeave

```kotlin
import com.trustweave.TrustWeave
import com.trustweave.cheqddid.*
import com.trustweave.anchor.*
import com.trustweave.testkit.anchor.InMemoryBlockchainAnchorClient

val config = CheqdDidConfig.mainnet("https://api.cheqd.net")
val anchorClient = InMemoryBlockchainAnchorClient("cheqd:mainnet")

val TrustWeave = TrustWeave.create {
    kms = InMemoryKeyManagementService()
    
    blockchain {
        register("cheqd:mainnet", anchorClient)
    }
    
    didMethods {
        + CheqdDidMethod(kms!!, anchorClient, config)
    }
}

// Use did:cheqd
val did = TrustWeave.createDid("cheqd") {
    algorithm = KeyAlgorithm.ED25519
}.getOrThrow()

val resolved = TrustWeave.resolveDid(did.id).getOrThrow()
```

## Payment-Enabled Operations

Cheqd network supports payment for DID operations:

### Transaction Fees

- DID creation requires transaction fees
- DID updates require transaction fees
- Fee amount depends on network parameters

### Payment Configuration

```kotlin
val config = CheqdDidConfig.builder()
    .accountAddress("cheqd1...")  // Account with funds
    .privateKey("...")            // Private key for signing
    .build()
```

## Error Handling

Common errors and solutions:

| Error | Cause | Solution |
|-------|-------|----------|
| `cheqdApiUrl not accessible` | API endpoint unreachable | Check API URL and network connectivity |
| `Network not supported` | Invalid network name | Use `mainnet` or `testnet` |
| `BlockchainAnchorClient is required` | Missing anchor client | Provide anchor client in options |
| `Failed to anchor document` | Transaction failed | Check account balance, gas, network connectivity |
| `DID document not found` | Not anchored yet | Anchor document first or check blockchain |

## Testing

For testing without actual blockchain:

```kotlin
import com.trustweave.testkit.anchor.InMemoryBlockchainAnchorClient

val config = CheqdDidConfig.testnet("https://testnet-api.cheqd.net")
val anchorClient = InMemoryBlockchainAnchorClient("cheqd:testnet")
val method = CheqdDidMethod(kms, anchorClient, config)

// Create and resolve (stored in memory)
val document = method.createDid(options)
val result = method.resolveDid(document.id)
```

## Best Practices

1. **Use testnet for development**: Testnet for testing DID operations
2. **Account security**: Never hardcode private keys, use environment variables or secure storage
3. **Fee management**: Monitor transaction fees and account balance
4. **Network validation**: Always validate network matches API endpoint
5. **Error handling**: Implement proper error handling for blockchain operations

## Use Cases

### Payment-Enabled Identity

Cheqd enables payment-enabled identity systems:

```kotlin
// Create DID with payment
val config = CheqdDidConfig.mainnet("https://api.cheqd.net")
    .copy(accountAddress = "cheqd1...", privateKey = "...")

val method = CheqdDidMethod(kms, anchorClient, config)
val document = method.createDid(options) // Requires payment
```

### Credential Ecosystem

Cheqd is designed for verifiable credentials:

- Payment for credential issuance
- DID management for credentials
- Network-level credential support

## Troubleshooting

### Transaction Failures

- Check account has sufficient balance
- Verify private key is valid
- Check network connectivity
- Ensure API endpoint is accessible

### Resolution Failures

- Verify document was anchored successfully
- Check transaction hash is valid
- Ensure blockchain client can access the network
- Verify API endpoint is correct

## Next Steps

- See [Creating Plugins Guide](../contributing/creating-plugins.md) for custom implementations
- Review [Blockchain Anchoring](../core-concepts/blockchain-anchoring.md) for anchoring details
- Check [Integration Modules](README.md) for other DID methods
- Learn about [Cheqd Network](https://www.cheqd.io/)

## References

- [Cheqd Network Documentation](https://docs.cheqd.io/)
- [Cheqd DID Method Specification](https://docs.cheqd.io/node/architecture/adr-list/adr-005-cheqd-did-method-specification)
- [Cosmos SDK Documentation](https://docs.cosmos.network/)
- [DID Core Specification](https://www.w3.org/TR/did-core/)

