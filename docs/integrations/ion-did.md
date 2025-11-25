---
title: ION DID Integration
---

# ION DID Integration

> This guide covers the did:ion method integration for TrustWeave. The did:ion plugin provides Microsoft ION (Identity Overlay Network) DID resolution using the Sidetree protocol.

## Overview

The `did/plugins/ion` module provides an implementation of TrustWeave's `DidMethod` interface using Microsoft ION and the Sidetree protocol. This integration enables you to:

- Create and resolve DIDs using ION network
- Store DID operations anchored to Bitcoin blockchain
- Support long-form and short-form DID resolution
- Integrate with ION nodes for DID operations

## Installation

Add the did:ion module to your dependencies:

```kotlin
dependencies {
    implementation("com.trustweave.did:ion:1.0.0-SNAPSHOT")
    implementation("com.trustweave:trustweave-did:1.0.0-SNAPSHOT")
    implementation("com.trustweave.did:base:1.0.0-SNAPSHOT")
    implementation("com.trustweave:trustweave-common:1.0.0-SNAPSHOT")
}
```

## Configuration

### Basic Configuration

```kotlin
import com.trustweave.iondid.*
import com.trustweave.kms.*

// Create configuration
val config = IonDidConfig.builder()
    .ionNodeUrl("https://ion-node.tbddev.org")
    .bitcoinNetwork("mainnet")
    .build()

// Create KMS
val kms = InMemoryKeyManagementService()

// Create did:ion method
val method = IonDidMethod(kms, config)
```

### Pre-configured Networks

```kotlin
// ION mainnet
val mainnetConfig = IonDidConfig.mainnet(
    ionNodeUrl = "https://ion-node.tbddev.org" // Optional: uses default if omitted
)

// ION testnet
val testnetConfig = IonDidConfig.testnet(
    ionNodeUrl = "https://ion-testnet-node.tbddev.org" // Optional
)
```

### SPI Auto-Discovery

When the module is on the classpath, did:ion is automatically available:

```kotlin
import com.trustweave.did.*
import java.util.ServiceLoader

// Discover did:ion provider
val providers = ServiceLoader.load(DidMethodProvider::class.java)
val ionProvider = providers.find { it.supportedMethods.contains("ion") }

// Create method with required options
val options = didCreationOptions {
    property("ionNodeUrl", "https://ion-node.tbddev.org")
}

val method = ionProvider?.create("ion", options)
```

## Usage Examples

### Creating a did:ion

```kotlin
val config = IonDidConfig.testnet()
val kms = InMemoryKeyManagementService()
val method = IonDidMethod(kms, config)

// Create DID
val options = didCreationOptions {
    algorithm = KeyAlgorithm.ED25519
    purpose(KeyPurpose.AUTHENTICATION)
    purpose(KeyPurpose.ASSERTION)
}

val document = method.createDid(options)
println("Created: ${document.id}") // Long-form DID initially

// After anchoring, you'll get a short-form DID
// Long-form: did:ion:EiA2...:eyJ...
// Short-form: did:ion:EiA2...
```

### Resolving a did:ion

```kotlin
// Resolve short-form DID (after anchoring)
val result = method.resolveDid("did:ion:EiA2...")

// Resolve long-form DID (for newly created DIDs)
val longFormResult = method.resolveDid("did:ion:EiA2...:eyJ...")

result.document?.let { doc ->
    println("Resolved: ${doc.id}")
    println("Verification methods: ${doc.verificationMethod.size}")
} ?: println("Not found")
```

### Updating a did:ion

```kotlin
val document = method.updateDid("did:ion:EiA2...") { currentDoc ->
    currentDoc.copy(
        service = currentDoc.service + Service(
            id = "${currentDoc.id}#didcomm",
            type = "DIDCommMessaging",
            serviceEndpoint = "https://example.com/didcomm"
        )
    )
}
```

### Deactivating a did:ion

```kotlin
val deactivated = method.deactivateDid("did:ion:EiA2...")
println("Deactivated: $deactivated")
```

## DID Format

### Short-form DID

```
did:ion:EiA2...
```

Resolves through ION nodes after anchoring to Bitcoin.

### Long-form DID

```
did:ion:EiA2...:eyJ...
```

Contains operation data for newly created DIDs before anchoring.

## Sidetree Protocol

ION uses the Sidetree protocol for DID operations:

1. **Create**: Create a new DID with initial keys
2. **Update**: Update DID document (add/remove keys, services)
3. **Recover**: Recover DID with new recovery keys
4. **Deactivate**: Permanently deactivate a DID

Operations are batched and anchored to Bitcoin blockchain by ION nodes.

## ION Node Integration

This implementation communicates with ION nodes via HTTP:

- **Operations**: Submit operations to `/operations` endpoint
- **Resolution**: Resolve DIDs through `/identifiers/{did}` endpoint

ION nodes handle:
- Operation batching
- Bitcoin anchoring
- DID resolution
- State management

## Configuration Options

### IonDidConfig

```kotlin
val config = IonDidConfig.builder()
    .ionNodeUrl("https://ion-node.tbddev.org")  // Required: ION node endpoint
    .bitcoinRpcUrl("https://btc-mainnet...")     // Optional: for direct anchoring
    .bitcoinNetwork("mainnet")                   // Optional: mainnet, testnet, regtest
    .batchSize(10)                               // Optional: operation batch size
    .timeoutSeconds(60)                          // Optional: HTTP timeout
    .build()
```

## Integration with TrustWeave

```kotlin
import com.trustweave.TrustWeave
import com.trustweave.iondid.*

val config = IonDidConfig.testnet()

val TrustWeave = TrustWeave.create {
    kms = InMemoryKeyManagementService()
    
    didMethods {
        + IonDidMethod(kms!!, config)
    }
}

// Use did:ion
val did = TrustWeave.createDid("ion") {
    algorithm = KeyAlgorithm.ED25519
}.getOrThrow()

val resolved = TrustWeave.resolveDid(did.id).getOrThrow()
```

## Long-form vs Short-form DIDs

- **Long-form DID**: Contains operation data, works immediately after creation (before anchoring)
- **Short-form DID**: Compact identifier, works after anchoring to Bitcoin

When creating a DID, you receive a long-form DID. After anchoring (done by ION nodes), you can use the short-form DID.

## Error Handling

Common errors and solutions:

| Error | Cause | Solution |
|-------|-------|----------|
| `ionNodeUrl is required` | Missing ION node endpoint | Provide ION node URL |
| `DID not found` | DID not yet anchored | Use long-form DID or wait for anchoring |
| `Network error` | Cannot reach ION node | Check network connectivity and node URL |
| `Operation failed` | Invalid operation | Check operation format and keys |

## Testing

For testing without actual ION node:

```kotlin
// Use testnet ION node
val config = IonDidConfig.testnet()
val method = IonDidMethod(kms, config)

// Operations are submitted to testnet node
val document = method.createDid(options)
```

## Best Practices

1. **Use testnet for development**: ION testnet for testing
2. **Handle long-form DIDs**: Store long-form DID until short-form is available
3. **Wait for anchoring**: Operations take time to be anchored to Bitcoin
4. **Error handling**: Implement retry logic for network operations
5. **Key management**: Securely store recovery keys for DID recovery

## Troubleshooting

### DID Not Resolving

- Check if DID is anchored (use long-form if not)
- Verify ION node is accessible
- Ensure DID format is correct

### Operation Failures

- Verify keys are valid
- Check operation format matches Sidetree spec
- Ensure ION node is operational

## Next Steps

- See [Creating Plugins Guide](../contributing/creating-plugins.md) for custom implementations
- Review [Sidetree Protocol](https://identity.foundation/sidetree/spec/) for protocol details
- Check [ION Documentation](https://identity.foundation/ion/) for ION specifics

## References

- [ION Specification](https://identity.foundation/ion/)
- [Sidetree Protocol Specification](https://identity.foundation/sidetree/spec/)
- [ION GitHub Repository](https://github.com/decentralized-identity/ion)
- [ION Node Implementation](https://github.com/decentralized-identity/ion-sdk)

