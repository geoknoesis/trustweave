# Peer DID Integration

> This guide covers the did:peer method integration for TrustWeave. The did:peer plugin provides peer-to-peer DIDs without external registries or blockchains.

## Overview

The `did/plugins/peer` module provides an implementation of TrustWeave's `DidMethod` interface using the peer DID method. This integration enables you to:

- Create and resolve peer DIDs for P2P communication
- Store DID documents locally (no external registry)
- Support numalgo 0, 1, and 2
- Embedded document resolution
- No blockchain or HTTP dependencies

## Installation

Add the did:peer module to your dependencies:

```kotlin
dependencies {
    implementation("com.trustweave.did:peer:1.0.0-SNAPSHOT")
    implementation("com.trustweave:TrustWeave-did:1.0.0-SNAPSHOT")
    implementation("com.trustweave.did:base:1.0.0-SNAPSHOT")
    implementation("com.trustweave:trustweave-common:1.0.0-SNAPSHOT")
}
```

## Configuration

### Basic Configuration

```kotlin
import com.trustweave.peerdid.*
import com.trustweave.kms.*

// Create configuration
val config = PeerDidConfig.builder()
    .numalgo(2) // Use numalgo 2 (recommended)
    .includeServices(true)
    .build()

// Create KMS
val kms = InMemoryKeyManagementService()

// Create did:peer method
val method = PeerDidMethod(kms, config)
```

### Pre-configured Numalgos

```kotlin
// Numalgo 0 (static numeric)
val config0 = PeerDidConfig.numalgo0()

// Numalgo 1 (short-form with inception key)
val config1 = PeerDidConfig.numalgo1()

// Numalgo 2 (short-form with multibase, recommended)
val config2 = PeerDidConfig.numalgo2()
```

### SPI Auto-Discovery

When the module is on the classpath, did:peer is automatically available:

```kotlin
import com.trustweave.did.*
import java.util.ServiceLoader

// Discover did:peer provider
val providers = ServiceLoader.load(DidMethodProvider::class.java)
val peerProvider = providers.find { it.supportedMethods.contains("peer") }

// Create method
val options = DidCreationOptions()
val method = peerProvider?.create("peer", options)
```

## Usage Examples

### Creating a did:peer

```kotlin
val config = PeerDidConfig.numalgo2()
val kms = InMemoryKeyManagementService()
val method = PeerDidMethod(kms, config)

// Create DID
val options = didCreationOptions {
    algorithm = KeyAlgorithm.ED25519
    purpose(KeyPurpose.AUTHENTICATION)
    purpose(KeyPurpose.ASSERTION)
    property("serviceEndpoint", "https://example.com/didcomm")
}

val document = method.createDid(options)
println("Created: ${document.id}") // did:peer:2...
```

### Resolving a did:peer

```kotlin
val result = method.resolveDid("did:peer:2...")

result.document?.let { doc ->
    println("Resolved: ${doc.id}")
    println("Verification methods: ${doc.verificationMethod.size}")
} ?: println("Not found")
```

### Updating a did:peer

```kotlin
val document = method.updateDid("did:peer:2...") { currentDoc ->
    currentDoc.copy(
        service = currentDoc.service + Service(
            id = "${currentDoc.id}#didcomm",
            type = "DIDCommMessaging",
            serviceEndpoint = "https://example.com/didcomm"
        )
    )
}
```

### Deactivating a did:peer

```kotlin
val deactivated = method.deactivateDid("did:peer:2...")
println("Deactivated: $deactivated")
```

## DID Format

### Numalgo 0 (Static Numeric)

```
did:peer:0...
```

### Numalgo 1 (Short-form with Inception Key)

```
did:peer:1...
```

### Numalgo 2 (Short-form with Multibase, Recommended)

```
did:peer:2...
```

## Numalgo Versions

- **Numalgo 0**: Static numeric algorithm (legacy)
- **Numalgo 1**: Short-form with inception key
- **Numalgo 2**: Short-form with multibase encoding (recommended)

## Local Storage

Peer DIDs don't use external registries or blockchains:

- Documents are stored locally in memory or persistent storage
- No external dependencies required
- Fast resolution from local cache
- No network calls needed

## Embedded Documents

Long-form peer DIDs can embed documents:

- Documents can be encoded in the DID itself
- Useful for offline or P2P scenarios
- No external resolution needed

## Configuration Options

### PeerDidConfig

```kotlin
val config = PeerDidConfig.builder()
    .numalgo(2)           // Numalgo version (0, 1, or 2)
    .includeServices(true) // Include service endpoints
    .build()
```

## Integration with TrustWeave

```kotlin
import com.trustweave.TrustWeave
import com.trustweave.peerdid.*

val config = PeerDidConfig.numalgo2()

val TrustWeave = TrustWeave.create {
    kms = InMemoryKeyManagementService()
    
    didMethods {
        + PeerDidMethod(kms!!, config)
    }
}

// Use did:peer
val did = TrustWeave.dids.create("peer") {
    algorithm = KeyAlgorithm.ED25519
}

val resolved = TrustWeave.dids.resolve(did.id)
```

## Error Handling

Common errors and solutions:

| Error | Cause | Solution |
|-------|-------|----------|
| `Unsupported numalgo` | Invalid numalgo version | Use 0, 1, or 2 |
| `DID document not found` | Document not stored locally | Create DID first |
| `Public key multibase required` | Missing multibase key | Ensure key has multibase format |

## Testing

Peer DIDs are ideal for testing since they don't require external services:

```kotlin
val config = PeerDidConfig.numalgo2()
val method = PeerDidMethod(kms, config)

// Create and resolve (stored locally)
val document = method.createDid(options)
val result = method.resolveDid(document.id)
```

## Best Practices

1. **Use numalgo 2**: Recommended for new implementations
2. **Local storage**: Consider persistent storage for peer DIDs
3. **Service endpoints**: Include service endpoints for P2P communication
4. **Key management**: Securely store keys for peer DIDs
5. **Document sharing**: Share documents explicitly in P2P scenarios

## Advantages

- **No external dependencies**: No blockchain or HTTP needed
- **Fast resolution**: Local storage provides instant resolution
- **P2P ready**: Designed for peer-to-peer communication
- **Privacy**: No external registry tracks DIDs
- **Offline support**: Works without network connectivity

## Use Cases

- **P2P messaging**: Direct communication between peers
- **Offline scenarios**: No external services required
- **Privacy-sensitive**: No external registry tracking
- **Testing**: Fast, local-only DIDs for testing
- **Temporary DIDs**: Short-lived identifiers

## Next Steps

- See [Creating Plugins Guide](../contributing/creating-plugins.md) for custom implementations
- Review [Peer DID Specification](https://identity.foundation/peer-did-method-spec/) for protocol details
- Check [Integration Modules](../integrations/README.md) for other DID methods

## References

- [Peer DID Method Specification](https://identity.foundation/peer-did-method-spec/)
- [DID Core Specification](https://www.w3.org/TR/did-core/)
- [TrustWeave Core API](../api-reference/core-api.md)

