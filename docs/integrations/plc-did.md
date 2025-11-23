# PLC DID (did:plc) Integration

> This guide covers the did:plc method integration for TrustWeave. The did:plc plugin provides Personal Linked Container (PLC) DID resolution for AT Protocol.

## Overview

The `did/plugins/plc` module provides an implementation of TrustWeave's `DidMethod` interface using the Personal Linked Container (PLC) DID method for AT Protocol. This integration enables you to:

- Create and resolve DIDs for AT Protocol applications
- Store DID documents in PLC registry
- Support distributed DID resolution via HTTP endpoints
- Integrate with AT Protocol's identity system

## Installation

Add the did:plc module to your dependencies:

```kotlin
dependencies {
    implementation("com.trustweave.did:plc:1.0.0-SNAPSHOT")
    implementation("com.trustweave:TrustWeave-did:1.0.0-SNAPSHOT")
    implementation("com.trustweave.did:base:1.0.0-SNAPSHOT")
    implementation("com.trustweave:trustweave-common:1.0.0-SNAPSHOT")
    
    // HTTP client for AT Protocol integration
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
}
```

## Configuration

### Basic Configuration

```kotlin
import com.trustweave.plcdid.*
import com.trustweave.kms.*

// Create configuration
val config = PlcDidConfig.builder()
    .plcRegistryUrl("https://plc.directory") // PLC registry URL
    .timeoutSeconds(30)                      // HTTP timeout
    .build()

// Create KMS
val kms = InMemoryKeyManagementService()

// Create did:plc method
val method = PlcDidMethod(kms, config)
```

### Default Configuration

```kotlin
// Use default PLC registry
val config = PlcDidConfig.default()
val method = PlcDidMethod(kms, config)
```

### SPI Auto-Discovery

When the module is on the classpath, did:plc is automatically available:

```kotlin
import com.trustweave.did.*
import java.util.ServiceLoader

// Discover did:plc provider
val providers = ServiceLoader.load(DidMethodProvider::class.java)
val plcProvider = providers.find { it.supportedMethods.contains("plc") }

// Create method (uses defaults if not specified)
val options = didCreationOptions {
    property("plcRegistryUrl", "https://plc.directory") // Optional
    property("timeoutSeconds", 30)                      // Optional
}

val method = plcProvider?.create("plc", options)
```

## Usage Examples

### Creating a did:plc

```kotlin
val config = PlcDidConfig.default()
val kms = InMemoryKeyManagementService()
val method = PlcDidMethod(kms, config)

// Create DID
val options = didCreationOptions {
    algorithm = KeyAlgorithm.ED25519
    purpose(KeyPurpose.AUTHENTICATION)
    purpose(KeyPurpose.ASSERTION)
}

val document = method.createDid(options)
println("Created: ${document.id}") // did:plc:...
```

### Resolving a did:plc

```kotlin
val result = method.resolveDid("did:plc:...")

result.document?.let { doc ->
    println("Resolved: ${doc.id}")
    println("Verification methods: ${doc.verificationMethod.size}")
} ?: println("Not found")
```

### Updating a did:plc

```kotlin
val document = method.updateDid("did:plc:...") { currentDoc ->
    currentDoc.copy(
        service = currentDoc.service + Service(
            id = "${currentDoc.id}#didcomm",
            type = "DIDCommMessaging",
            serviceEndpoint = "https://example.com/didcomm"
        )
    )
}
```

### Deactivating a did:plc

```kotlin
val deactivated = method.deactivateDid("did:plc:...")
println("Deactivated: $deactivated")
```

## DID Format

### PLC DID Format

```
did:plc:abc123def456...
```

PLC DIDs use a base32-encoded identifier. The format is:
- Method: `plc`
- Identifier: Base32-encoded hash of the initial document

## PLC Registry

### Default Registry

The default PLC registry is hosted at `https://plc.directory`, which is the AT Protocol's official PLC registry.

### Registry Endpoints

- **Create DID**: `POST /did`
- **Resolve DID**: `GET /did/{did}`
- **Update DID**: `PUT /did/{did}`

## Configuration Options

### PlcDidConfig

```kotlin
val config = PlcDidConfig.builder()
    .plcRegistryUrl("https://plc.directory") // Optional: PLC registry URL
    .timeoutSeconds(30)                      // Optional: HTTP timeout (default: 30)
    .build()
```

### DidCreationOptions Properties

- `plcRegistryUrl` (optional): PLC registry URL (defaults to `https://plc.directory`)
- `timeoutSeconds` (optional): HTTP client timeout in seconds

## Integration with TrustWeave

```kotlin
import com.trustweave.TrustWeave
import com.trustweave.plcdid.*

val config = PlcDidConfig.default()

val TrustWeave = TrustWeave.create {
    kms = InMemoryKeyManagementService()
    
    didMethods {
        + PlcDidMethod(kms!!, config)
    }
}

// Use did:plc
val did = TrustWeave.createDid("plc") {
    algorithm = KeyAlgorithm.ED25519
}.getOrThrow()

val resolved = TrustWeave.resolveDid(did.id).getOrThrow()
```

## AT Protocol Integration

PLC DIDs are designed for AT Protocol applications, which use distributed identity:

### AT Protocol Features

- **Personal Linked Container**: Each user has a personal container for their identity
- **Distributed Registry**: DID documents stored in distributed registry
- **HTTP Resolution**: Resolves via HTTP endpoints
- **Update Support**: Supports document updates

### Example: AT Protocol Application

```kotlin
// Create PLC DID for AT Protocol user
val method = PlcDidMethod(kms, PlcDidConfig.default())

val options = didCreationOptions {
    algorithm = KeyAlgorithm.ED25519
    purpose(KeyPurpose.AUTHENTICATION)
    purpose(KeyPurpose.ASSERTION)
}

val document = method.createDid(options)

// Use DID in AT Protocol
val atpHandle = "alice.example.com"
// Link PLC DID to AT Protocol handle
```

## Error Handling

Common errors and solutions:

| Error | Cause | Solution |
|-------|-------|----------|
| `PLC registry not accessible` | Registry URL unreachable | Check registry URL and network connectivity |
| `DID not found in PLC registry` | DID not registered | Register DID first or verify DID identifier |
| `HTTP timeout` | Request took too long | Increase timeout or check network connectivity |
| `Failed to register with PLC registry` | Registration failed | Check registry endpoint and document format |

## Testing

For testing without actual PLC registry:

```kotlin
// Without PLC registry URL, documents are stored locally
val config = PlcDidConfig.builder()
    .plcRegistryUrl(null) // No registry URL
    .build()

val method = PlcDidMethod(kms, config)

// Create and resolve (stored in memory)
val document = method.createDid(options)
val result = method.resolveDid(document.id)
```

## Best Practices

1. **Use default registry**: Use `https://plc.directory` for production
2. **Cache resolutions**: Cache resolved documents for performance
3. **Error handling**: Implement proper error handling for registry operations
4. **Timeout configuration**: Set appropriate timeouts for network requests
5. **Document validation**: Validate document format before registration

## Use Cases

### AT Protocol Applications

PLC DIDs are designed for AT Protocol applications:

```kotlin
// Create PLC DID for AT Protocol user
val plcDid = method.createDid(options)

// Use in AT Protocol identity system
// Link to AT Protocol handle (e.g., alice.example.com)
```

### Distributed Identity

PLC DIDs support distributed identity systems:

- Personal containers for identity data
- HTTP-based resolution
- Update support for identity evolution

## Troubleshooting

### Registry Not Accessible

- Verify registry URL is correct
- Check network connectivity
- Ensure registry is online and accessible
- Verify firewall/network policies

### Registration Failures

- Check document format is valid
- Verify registry accepts registration requests
- Check for rate limiting or authentication requirements
- Review registry logs for errors

### Resolution Failures

- Verify DID identifier is correct
- Check DID was registered successfully
- Ensure registry can resolve the DID
- Check for network connectivity issues

## Next Steps

- See [Creating Plugins Guide](../contributing/creating-plugins.md) for custom implementations
- Review [DID Core Concepts](../core-concepts/dids.md) for DID fundamentals
- Check [Integration Modules](README.md) for other DID methods
- Learn about [AT Protocol](https://atproto.com/)

## References

- [AT Protocol Documentation](https://atproto.com/)
- [PLC DID Method Specification](https://atproto.com/specs/did)
- [PLC Registry](https://plc.directory)
- [DID Core Specification](https://www.w3.org/TR/did-core/)

