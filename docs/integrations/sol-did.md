---
title: Solana DID Integration
---

# Solana DID Integration

> This guide covers the did:sol method integration for TrustWeave. The did:sol plugin provides Solana DID resolution with account-based storage on Solana blockchain.

## Overview

The `did/plugins/sol` module provides an implementation of TrustWeave's `DidMethod` interface using the Solana blockchain. This integration enables you to:

- Create and resolve DIDs on Solana blockchain
- Store DID documents in Solana program accounts
- Support Solana mainnet, devnet, and testnet
- Use Ed25519 keys (native to Solana)
- Fast, low-cost transactions on Solana

## Installation

Add the did:sol module to your dependencies:

```kotlin
dependencies {
    implementation("com.trustweave.did:sol:1.0.0-SNAPSHOT")
    implementation("com.trustweave:trustweave-did:1.0.0-SNAPSHOT")
    implementation("com.trustweave.did:base:1.0.0-SNAPSHOT")
    implementation("com.trustweave:trustweave-anchor:1.0.0-SNAPSHOT")
    implementation("com.trustweave:trustweave-common:1.0.0-SNAPSHOT")
    
    // HTTP client for Solana RPC
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
}
```

## Configuration

### Basic Configuration

```kotlin
import com.trustweave.soldid.*
import com.trustweave.anchor.*
import com.trustweave.testkit.anchor.InMemoryBlockchainAnchorClient
import com.trustweave.kms.*

// Create configuration
val config = SolDidConfig.builder()
    .rpcUrl("https://api.devnet.solana.com")
    .network("devnet")
    .privateKey("base58...") // Optional: for signing transactions
    .build()

// Create blockchain anchor client (in-memory for testing)
val anchorClient = InMemoryBlockchainAnchorClient("solana:devnet")

// Create KMS
val kms = InMemoryKeyManagementService()

// Create did:sol method
val method = SolDidMethod(kms, anchorClient, config)
```

### Pre-configured Networks

```kotlin
// Solana mainnet
val mainnetConfig = SolDidConfig.mainnet(
    rpcUrl = "https://api.mainnet-beta.solana.com", // Optional
    privateKey = "base58..." // Optional
)

// Solana devnet
val devnetConfig = SolDidConfig.devnet(
    rpcUrl = "https://api.devnet.solana.com", // Optional
    privateKey = "base58..." // Optional
)
```

### SPI Auto-Discovery

When the module is on the classpath, did:sol is automatically available:

```kotlin
import com.trustweave.did.*
import java.util.ServiceLoader

// Discover did:sol provider
val providers = ServiceLoader.load(DidMethodProvider::class.java)
val solProvider = providers.find { it.supportedMethods.contains("sol") }

// Create method with required options
val options = didCreationOptions {
    property("rpcUrl", "https://api.devnet.solana.com")
    property("network", "devnet")
}

val method = solProvider?.create("sol", options)
```

## Usage Examples

### Creating a did:sol

```kotlin
val config = SolDidConfig.devnet("https://api.devnet.solana.com")
val anchorClient = InMemoryBlockchainAnchorClient("solana:devnet")
val kms = InMemoryKeyManagementService()
val method = SolDidMethod(kms, anchorClient, config)

// Create DID (uses Ed25519 for Solana compatibility)
val options = didCreationOptions {
    algorithm = KeyAlgorithm.ED25519
    purpose(KeyPurpose.AUTHENTICATION)
    purpose(KeyPurpose.ASSERTION)
}

val document = method.createDid(options)
println("Created: ${document.id}") // did:sol:devnet:7xK... or did:sol:7xK...
```

### Resolving a did:sol

```kotlin
val result = method.resolveDid("did:sol:7xK...")

result.document?.let { doc ->
    println("Resolved: ${doc.id}")
    println("Verification methods: ${doc.verificationMethod.size}")
} ?: println("Not found")
```

### Updating a did:sol

```kotlin
val document = method.updateDid("did:sol:7xK...") { currentDoc ->
    currentDoc.copy(
        service = currentDoc.service + Service(
            id = "${currentDoc.id}#didcomm",
            type = "DIDCommMessaging",
            serviceEndpoint = "https://example.com/didcomm"
        )
    )
}
```

### Deactivating a did:sol

```kotlin
val deactivated = method.deactivateDid("did:sol:7xK...")
println("Deactivated: $deactivated")
```

## DID Format

### Network-based DID

```
did:sol:mainnet-beta:7xKXtg2CW87d97TXJSDpbD5jBkheTqA83TZRuJosgAsU
did:sol:devnet:7xKXtg2CW87d97TXJSDpbD5jBkheTqA83TZRuJosgAsU
```

### Network-agnostic DID

```
did:sol:7xKXtg2CW87d97TXJSDpbD5jBkheTqA83TZRuJosgAsU
```

## Account-based Storage

did:sol stores DID documents in Solana program accounts:

- Each DID has a corresponding Solana account
- Account data contains the DID document
- Account owner is the DID registry program
- Updates require transactions to modify account data

## Algorithm Support

did:sol supports:
- **Ed25519** (required, Solana-native)

Solana uses Ed25519 signatures, so Ed25519 keys are required for did:sol.

## Configuration Options

### SolDidConfig

```kotlin
val config = SolDidConfig.builder()
    .rpcUrl("https://api.mainnet-beta.solana.com") // Required
    .network("mainnet-beta")                       // Optional: defaults to mainnet-beta
    .programId("YourProgramId")                    // Optional: DID registry program
    .privateKey("base58...")                       // Optional: for transactions
    .commitment("finalized")                       // Optional: finalized, confirmed, processed
    .build()
```

### Networks

| Network | RPC URL |
|---------|---------|
| Mainnet | `https://api.mainnet-beta.solana.com` |
| Devnet | `https://api.devnet.solana.com` |
| Testnet | `https://api.testnet.solana.com` |

## Integration with TrustWeave

```kotlin
import com.trustweave.TrustWeave
import com.trustweave.soldid.*
import com.trustweave.anchor.*
import com.trustweave.testkit.anchor.InMemoryBlockchainAnchorClient

val config = SolDidConfig.devnet("https://api.devnet.solana.com")
val anchorClient = InMemoryBlockchainAnchorClient("solana:devnet")

val TrustWeave = TrustWeave.create {
    kms = InMemoryKeyManagementService()
    
    blockchains {
        register("solana:devnet", anchorClient)
    }
    
    didMethods {
        + SolDidMethod(kms!!, anchorClient, config)
    }
}

// Use did:sol
val did = TrustWeave.createDid("sol") {
    algorithm = KeyAlgorithm.ED25519
}.getOrThrow()

val resolved = TrustWeave.resolveDid(did.id).getOrThrow()
```

## Error Handling

Common errors and solutions:

| Error | Cause | Solution |
|-------|-------|----------|
| `rpcUrl is required` | Missing RPC endpoint | Provide Solana RPC URL |
| `did:sol requires Ed25519` | Wrong algorithm | Use Ed25519 algorithm |
| `DID document not found` | Account not found | Create DID first or check address |
| `Failed to get account data` | RPC error | Check network connectivity and RPC URL |

## Testing

For testing without actual Solana:

```kotlin
import com.trustweave.testkit.anchor.InMemoryBlockchainAnchorClient

val config = SolDidConfig.devnet("https://api.devnet.solana.com")
val anchorClient = InMemoryBlockchainAnchorClient("solana:devnet")
val method = SolDidMethod(kms, anchorClient, config)

// Create and resolve (stored in memory)
val document = method.createDid(options)
val result = method.resolveDid(document.id)
```

## Best Practices

1. **Use devnet for development**: Solana devnet for testing
2. **Ed25519 keys**: Always use Ed25519 for Solana compatibility
3. **Account management**: Understand Solana account model
4. **Transaction costs**: Solana has very low transaction fees
5. **Fast confirmations**: Solana has fast block times (~400ms)

## Solana RPC

This implementation uses Solana JSON-RPC API:

- **getAccountInfo**: Retrieve account data containing DID document
- **sendTransaction**: Submit transactions to update DID documents
- **getTransaction**: Verify transaction status

## Next Steps

- See [Creating Plugins Guide](../contributing/creating-plugins.md) for custom implementations
- Review [Blockchain Anchoring](../core-concepts/blockchain-anchoring.md) for anchoring details
- Check [Solana Documentation](https://docs.solana.com/) for Solana specifics

## References

- [Solana Documentation](https://docs.solana.com/)
- [Solana JSON-RPC API](https://docs.solana.com/api/http)
- [Solana Web3.js](https://solana-labs.github.io/solana-web3.js/)

