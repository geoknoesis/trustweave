---
title: Key DID Integration
parent: Integration Modules
---

# Key DID Integration

> This guide covers the native did:key method integration for TrustWeave. The did:key plugin provides the most widely-used DID method with zero external dependencies.

## Overview

The `did/plugins/key` module provides a native implementation of TrustWeave's `DidMethod` interface using the W3C did:key specification. This integration enables you to:

- Create and resolve DIDs from public keys without external registries
- Use multibase-encoded public keys for portable DIDs
- Support all major cryptographic algorithms (Ed25519, secp256k1, P-256, P-384, P-521)
- Zero external dependencies - documents are derived from public keys

## Why did:key?

did:key is the **most widely-used DID method** because:
- **Simple**: No external registry or blockchain required
- **Portable**: DIDs are derived directly from public keys
- **Fast**: Resolution is instantaneous (no network calls)
- **Universal**: Works with any public key type

## Installation

Add the did:key module to your dependencies:

```kotlin
dependencies {
    implementation("org.trustweave.did:key:1.0.0-SNAPSHOT")
    implementation("org.trustweave:trustweave-did:1.0.0-SNAPSHOT")
    implementation("org.trustweave.did:base:1.0.0-SNAPSHOT")
    implementation("org.trustweave:trustweave-kms:1.0.0-SNAPSHOT")
    implementation("org.trustweave:trustweave-common:1.0.0-SNAPSHOT")

    // Multibase encoding (included automatically)
    implementation("org.multiformats:multibase:1.1.2")
}
```

## Configuration

### Basic Configuration

The did:key provider can be configured via options or automatically discovered via SPI:

```kotlin
import org.trustweave.did.*
import org.trustweave.keydid.*
import org.trustweave.kms.*

// Manual creation
val kms = InMemoryKeyManagementService()
val method = KeyDidMethod(kms)
```

### SPI Auto-Discovery

When the module is on the classpath, did:key is automatically available:

```kotlin
import org.trustweave.did.*
import java.util.ServiceLoader

// Discover did:key provider
val providers = ServiceLoader.load(DidMethodProvider::class.java)
val keyProvider = providers.find { it.supportedMethods.contains("key") }

// Create method
val options = didCreationOptions {
    // KMS will be discovered automatically if not provided
}
val method = keyProvider?.create("key", options)
```

## Usage Examples

### Creating a did:key

```kotlin
val kms = InMemoryKeyManagementService()
val method = KeyDidMethod(kms)

// Create DID with Ed25519 key
val options = didCreationOptions {
    algorithm = KeyAlgorithm.ED25519
    purpose(KeyPurpose.AUTHENTICATION)
    purpose(KeyPurpose.ASSERTION)
}

val document = method.createDid(options)
println("Created: ${document.id}") // did:key:z6Mk...
```

### Resolving a did:key

```kotlin
import org.trustweave.did.identifiers.Did

// Resolve DID (derived from public key)
val did = Did("did:key:z6MkhaXgBZDvotDkL5257faiztiGiC2QtKLGpbnnEGta2doK")
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

### Using Different Algorithms

```kotlin
// Ed25519 (most common)
val ed25519Options = didCreationOptions {
    algorithm = KeyAlgorithm.ED25519
}
val ed25519Did = method.createDid(ed25519Options)

// secp256k1 (Ethereum-compatible)
val secp256k1Options = didCreationOptions {
    algorithm = KeyAlgorithm.SECP256K1
}
val secp256k1Did = method.createDid(secp256k1Options)

// P-256 (NIST)
val p256Options = didCreationOptions {
    algorithm = KeyAlgorithm.P256
}
val p256Did = method.createDid(p256Options)
```

### Integration with TrustWeave

```kotlin
import org.trustweave.TrustWeave
import org.trustweave.keydid.*
import org.trustweave.kms.InMemoryKeyManagementService

val kms = InMemoryKeyManagementService()

val TrustWeave = TrustWeave.create {
    this.kms = kms

    didMethods {
        + KeyDidMethod(kms)
    }
}

// Use did:key
val did = TrustWeave.createDid("key") {
    algorithm = KeyAlgorithm.ED25519
}.getOrThrow()

println("Created DID: ${did.id}")

val resolved = TrustWeave.resolveDid(did.id).getOrThrow()
println("Resolved DID: ${resolved.document?.id}")
```

## DID Format

### Multibase-Encoded Public Key

```
did:key:z6MkhaXgBZDvotDkL5257faiztiGiC2QtKLGpbnnEGta2doK
```

The `did:key` identifier consists of:
1. **Prefix**: `did:key:`
2. **Multibase encoding**: `z` prefix indicates base58btc encoding
3. **Multicodec prefix**: Algorithm-specific prefix (e.g., 0xed01 for Ed25519)
4. **Public key**: The actual public key bytes

## Supported Algorithms

did:key supports all major cryptographic algorithms:

| Algorithm | Multicodec Prefix | Use Case |
|-----------|------------------|----------|
| Ed25519 | 0xed01 | Most common, fastest |
| secp256k1 | 0xe701 | Ethereum-compatible |
| P-256 | 0x8024 | NIST, FIPS-compliant |
| P-384 | 0x8124 | Higher security |
| P-521 | 0x8224 | Highest security |

## Algorithm Support

did:key natively supports all TrustWeave algorithms through the KMS abstraction:

- **Ed25519**: Recommended for most use cases
- **secp256k1**: For Ethereum ecosystem integration
- **P-256/P-384/P-521**: For FIPS compliance requirements
- **RSA**: Supported via JWK format

## Error Handling

Common errors and solutions:

| Error | Cause | Solution |
|-------|-------|----------|
| `Invalid multibase encoding` | DID format is invalid | Validate DID format before resolution |
| `Unsupported multicodec prefix` | Algorithm not supported | Use a supported algorithm (Ed25519, secp256k1, P-256, etc.) |
| `KeyHandle must have publicKeyMultibase or publicKeyJwk` | KMS didn't provide public key | Ensure KMS returns public key in JWK or multibase format |
| `No KeyManagementService available` | KMS not configured | Provide KMS in options or ensure a KMS provider is registered |

## Best Practices

1. **Use Ed25519 for new projects**: Fastest and most widely supported
2. **Cache DID documents**: Since resolution is instant, caching is less critical but still recommended
3. **Share public keys securely**: did:key DIDs reveal the public key, which is fine but be aware
4. **Use for portable identities**: Perfect for identities that need to work across multiple systems

## Performance

did:key is the fastest DID method:
- **Creation**: Instant (no network calls)
- **Resolution**: Instant (derived from DID itself)
- **No external dependencies**: Works offline

## Security Considerations

- **Public keys are visible**: The DID contains the public key, which is intentional
- **Private keys remain secure**: Only public keys are used, private keys stay in KMS
- **Algorithm choice matters**: Use Ed25519 or P-256 for best security/performance balance

## Troubleshooting

### Resolution Failures

- Verify the DID format is correct (`did:key:z...`)
- Check that the algorithm is supported
- Ensure the multibase encoding is valid

### Key Generation Issues

- Verify KMS supports the requested algorithm
- Check that KMS returns public keys in JWK or multibase format
- Ensure KMS is properly configured

## Next Steps

- See [Creating Plugins Guide](../contributing/creating-plugins.md) for custom implementations
- Review [JWK DID Integration Guide](jwk-did.md) for JWK-based alternative
- Check [Integration Modules](README.md) for other DID methods

## References

- [DID Key Method Specification](https://w3c-ccg.github.io/did-method-key/)
- [Multibase Encoding](https://github.com/multiformats/multibase)
- [Multicodec](https://github.com/multiformats/multicodec)
- [TrustWeave Core API](../api-reference/core-api.md)

