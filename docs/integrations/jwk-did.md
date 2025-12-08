---
title: JWK DID Integration
---

# JWK DID Integration

> This guide covers the did:jwk method integration for TrustWeave. The did:jwk plugin provides W3C-standard DID resolution using JSON Web Keys directly.

## Overview

The `did/plugins/jwk` module provides an implementation of TrustWeave's `DidMethod` interface using the W3C did:jwk specification. This integration enables you to:

- Create and resolve DIDs from JSON Web Keys (JWK) directly
- Use standard JWK format without multicodec prefixes
- Support all JWK key types (OKP, EC, RSA)
- Zero external dependencies - documents are derived from JWK

## Why did:jwk?

did:jwk offers a standardized approach to DIDs:
- **W3C Standard**: Official W3C specification
- **JWK Native**: Uses JSON Web Key format directly
- **Simple**: No multicodec encoding required
- **Standardized**: Works with any JWK-compatible system

## Installation

Add the did:jwk module to your dependencies:

```kotlin
dependencies {
    implementation("com.trustweave.did:jwk:1.0.0-SNAPSHOT")
    implementation("com.trustweave:trustweave-did:1.0.0-SNAPSHOT")
    implementation("com.trustweave.did:base:1.0.0-SNAPSHOT")
    implementation("com.trustweave:trustweave-kms:1.0.0-SNAPSHOT")
    implementation("com.trustweave:trustweave-common:1.0.0-SNAPSHOT")
    implementation("com.trustweave:trustweave-json:1.0.0-SNAPSHOT")

    // JSON processing (included automatically)
    implementation("org.jose4j:jose4j:0.9.5")
}
```

## Configuration

### Basic Configuration

The did:jwk provider can be configured via options or automatically discovered via SPI:

```kotlin
import com.trustweave.did.*
import com.trustweave.jwkdid.*
import com.trustweave.kms.*

// Manual creation
val kms = InMemoryKeyManagementService()
val method = JwkDidMethod(kms)
```

### SPI Auto-Discovery

When the module is on the classpath, did:jwk is automatically available:

```kotlin
import com.trustweave.did.*
import java.util.ServiceLoader

// Discover did:jwk provider
val providers = ServiceLoader.load(DidMethodProvider::class.java)
val jwkProvider = providers.find { it.supportedMethods.contains("jwk") }

// Create method
val options = didCreationOptions {
    // KMS will be discovered automatically if not provided
}
val method = jwkProvider?.create("jwk", options)
```

## Usage Examples

### Creating a did:jwk

```kotlin
val kms = InMemoryKeyManagementService()
val method = JwkDidMethod(kms)

// Create DID with Ed25519 key
val options = didCreationOptions {
    algorithm = KeyAlgorithm.ED25519
    purpose(KeyPurpose.AUTHENTICATION)
    purpose(KeyPurpose.ASSERTION)
}

val document = method.createDid(options)
println("Created: ${document.id}") // did:jwk:eyJ...
```

### Resolving a did:jwk

```kotlin
import com.trustweave.did.identifiers.Did
import com.trustweave.did.resolver.DidResolutionResult

// Resolve DID (derived from JWK)
val did = Did("did:jwk:eyJkIjoieCIsImNydiI6IkVkMjU1MTkiLCJrdHkiOiJPS1AifQ")
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

### Using Different Key Types

```kotlin
// Ed25519 (OKP type)
val ed25519Options = didCreationOptions {
    algorithm = KeyAlgorithm.ED25519
}
val ed25519Did = method.createDid(ed25519Options)

// secp256k1 (EC type)
val secp256k1Options = didCreationOptions {
    algorithm = KeyAlgorithm.SECP256K1
}
val secp256k1Did = method.createDid(secp256k1Options)

// P-256 (EC type)
val p256Options = didCreationOptions {
    algorithm = KeyAlgorithm.P256
}
val p256Did = method.createDid(p256Options)
```

### Integration with TrustWeave

```kotlin
import com.trustweave.TrustWeave
import com.trustweave.jwkdid.*
import com.trustweave.kms.InMemoryKeyManagementService

val kms = InMemoryKeyManagementService()

val TrustWeave = TrustWeave.create {
    this.kms = kms

    didMethods {
        + JwkDidMethod(kms)
    }
}

// Use did:jwk
val did = TrustWeave.createDid("jwk") {
    algorithm = KeyAlgorithm.ED25519
}.getOrThrow()

println("Created DID: ${did.id}")

val resolved = TrustWeave.resolveDid(did.id).getOrThrow()
println("Resolved DID: ${resolved.document?.id}")
```

## DID Format

### Base64url-Encoded JWK

```
did:jwk:eyJkIjoieCIsImNydiI6IkVkMjU1MTkiLCJrdHkiOiJPS1AifQ
```

The `did:jwk` identifier consists of:
1. **Prefix**: `did:jwk:`
2. **Base64url encoding**: JWK JSON is base64url-encoded (no padding)
3. **JWK content**: Standard JSON Web Key format

### JWK Format Example

```json
{
  "kty": "OKP",
  "crv": "Ed25519",
  "x": "base64url-encoded-public-key"
}
```

## Supported Key Types

did:jwk supports all JWK key types:

| Key Type | JWK `kty` | Algorithms |
|----------|-----------|------------|
| Octet Key Pair | `OKP` | Ed25519, X25519 |
| Elliptic Curve | `EC` | secp256k1, P-256, P-384, P-521 |
| RSA | `RSA` | RSA-2048, RSA-4096 |

## Algorithm Support

did:jwk supports all TrustWeave algorithms through JWK format:

- **Ed25519**: `OKP` with `crv: "Ed25519"`
- **secp256k1**: `EC` with `crv: "secp256k1"`
- **P-256/P-384/P-521**: `EC` with respective curves
- **RSA**: `RSA` key type

## Error Handling

Common errors and solutions:

| Error | Cause | Solution |
|-------|-------|----------|
| `Invalid base64url encoding` | DID format is invalid | Validate DID format before resolution |
| `Invalid JWK JSON` | JWK format is incorrect | Ensure JWK is valid JSON |
| `JWK missing 'kty' field` | Required JWK field missing | Provide valid JWK with required fields |
| `KeyHandle must have publicKeyJwk` | KMS didn't provide JWK | Ensure KMS returns public key in JWK format |
| `No KeyManagementService available` | KMS not configured | Provide KMS in options or ensure a KMS provider is registered |

## Best Practices

1. **Use for JWK-native systems**: Perfect when you're already using JWK format
2. **Cache DID documents**: Resolution is fast but caching is still recommended
3. **Validate JWK format**: Ensure JWKs are properly formatted before creating DIDs
4. **Use for interoperability**: Great for systems that already work with JWK

## Performance

did:jwk is very fast:
- **Creation**: Instant (no network calls)
- **Resolution**: Instant (derived from DID itself)
- **No external dependencies**: Works offline

## Security Considerations

- **Public keys are visible**: The DID contains the JWK (public key only)
- **Private keys remain secure**: Only public keys are used, private keys stay in KMS
- **JWK normalization**: Private key fields are automatically removed
- **Base64url encoding**: Standard encoding prevents padding issues

## Comparison with did:key

| Feature | did:key | did:jwk |
|---------|---------|---------|
| Encoding | Multibase (base58btc) | Base64url |
| Format | Multicodec + public key | JWK JSON |
| Standard | Community spec | W3C spec |
| Key types | All major algorithms | All JWK types |
| Use case | General purpose | JWK-native systems |

## Troubleshooting

### Resolution Failures

- Verify the DID format is correct (`did:jwk:eyJ...`)
- Check that the JWK is valid JSON
- Ensure required JWK fields (`kty`, `crv` for EC/OKP) are present

### Key Generation Issues

- Verify KMS supports the requested algorithm
- Check that KMS returns public keys in JWK format
- Ensure JWK has all required fields for the key type

## Next Steps

- See [Creating Plugins Guide](../contributing/creating-plugins.md) for custom implementations
- Review [Key DID Integration Guide](key-did.md) for multibase alternative
- Check [Integration Modules](README.md) for other DID methods

## References

- [DID JWK Method Specification](https://w3c-ccg.github.io/did-method-jwk/)
- [JSON Web Key (JWK) RFC 7517](https://tools.ietf.org/html/rfc7517)
- [TrustWeave Core API](../api-reference/core-api.md)

