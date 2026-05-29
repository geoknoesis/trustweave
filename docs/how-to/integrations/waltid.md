---
title: walt.id Integration
parent: Integration Modules
redirect_from:
  - /integrations/waltid/

---

# walt.id Integration

> This guide covers the walt.id integration for TrustWeave. The walt.id plugin provides walt.id-based KMS and DID methods with SPI-based discovery, supporting did:key and did:web methods.

## Overview

The `kms/plugins/waltid` module provides integration with walt.id libraries for key management and DID operations. This integration enables you to:

- Use walt.id KMS for secure key generation and signing
- Support did:key and did:web DID methods via walt.id
- Leverage SPI-based auto-discovery of walt.id providers
- Integrate walt.id libraries seamlessly into TrustWeave workflows

## Installation

Add the walt.id module to your dependencies:

```kotlin
dependencies {
    // Only need to add the walt.id plugin - core dependencies are included transitively
    implementation("org.trustweave:kms-plugins-waltid:0.6.0")
}
```

**Note:** The walt.id plugin automatically includes `trustweave-kms`, `trustweave-did`, and `trustweave-common` as transitive dependencies, so you don't need to declare them explicitly.

## Configuration

### Basic Configuration

The walt.id integration supports automatic discovery via SPI. `discoverAndRegister`
takes a `DidMethodRegistry` to populate:

```kotlin
import org.trustweave.waltid.WaltIdIntegration
import org.trustweave.did.registry.DidMethodRegistry
import org.trustweave.kms.*

// Auto-discover and register walt.id providers
val registry = DidMethodRegistry()
val result = WaltIdIntegration.discoverAndRegister(registry)

// Access registered components
val kms = result.kms
val registeredMethods = result.registeredDidMethods

println("Registered DID methods: $registeredMethods")
```

### Manual Setup

You can also manually configure walt.id components:

```kotlin
import org.trustweave.waltid.WaltIdKeyManagementService
import org.trustweave.waltid.did.WaltIdKeyMethod
import org.trustweave.waltid.did.WaltIdWebMethod
import org.trustweave.did.registry.DidMethodRegistry

// Create walt.id KMS
val kms = WaltIdKeyManagementService()

// Create DID methods (note: WaltIdKeyMethod / WaltIdWebMethod, no "Did" infix)
val keyMethod = WaltIdKeyMethod(kms)
val webMethod = WaltIdWebMethod(kms)

// Register methods (DidMethodRegistry.register takes a single DidMethod)
val registry = DidMethodRegistry()
registry.register(keyMethod)
registry.register(webMethod)
```

## Usage Examples

### KMS Operations

```kotlin
import org.trustweave.waltid.WaltIdKeyManagementService
import org.trustweave.kms.*
import org.trustweave.kms.results.*

val kms = WaltIdKeyManagementService()

// Generate key — generateKey returns GenerateKeyResult (sealed)
val handle = when (val result = kms.generateKey(Algorithm.Ed25519)) {
    is GenerateKeyResult.Success -> result.keyHandle
    is GenerateKeyResult.Failure -> error("Key generation failed: $result")
}
println("Generated key: ${handle.id}")

// Sign data — sign returns SignResult (sealed)
val data = "Hello, TrustWeave!".toByteArray()
val signature: ByteArray = when (val s = kms.sign(handle.id, data)) {
    is SignResult.Success -> s.signature
    is SignResult.Failure -> error("Sign failed: $s")
}

// Get public key — getPublicKey returns GetPublicKeyResult (sealed)
val publicJwk = when (val pk = kms.getPublicKey(handle.id)) {
    is GetPublicKeyResult.Success -> pk.keyHandle.publicKeyJwk
    is GetPublicKeyResult.Failure -> error("Get public key failed: $pk")
}
println("Public key JWK: $publicJwk")
```

### DID Operations

```kotlin
import org.trustweave.waltid.WaltIdKeyManagementService
import org.trustweave.waltid.did.WaltIdKeyMethod
import org.trustweave.did.KeyAlgorithm
import org.trustweave.did.didCreationOptions
import org.trustweave.did.resolver.DidResolutionResult

val kms = WaltIdKeyManagementService()
val keyMethod = WaltIdKeyMethod(kms)

// Create did:key
val options = didCreationOptions {
    algorithm = KeyAlgorithm.ED25519
}

val didDoc = keyMethod.createDid(options)
println("Created DID: ${didDoc.id}")

// Resolve DID
when (val resolutionResult = keyMethod.resolveDid(didDoc.id)) {
    is DidResolutionResult.Success ->
        println("Resolved DID: ${resolutionResult.document.id}")
    is DidResolutionResult.Failure ->
        println("Resolution failed")
}
```

### Using TrustWeave Facade

```kotlin
import org.trustweave.trust.TrustWeave
import org.trustweave.trust.types.DidCreationResult
import org.trustweave.waltid.WaltIdIntegration
import org.trustweave.did.registry.DidMethodRegistry
import kotlinx.coroutines.runBlocking

runBlocking {
    // discoverAndRegister requires a DidMethodRegistry to populate.
    val registry = DidMethodRegistry()
    WaltIdIntegration.discoverAndRegister(registry)

    val trustWeave = TrustWeave.quickStart()
    when (val didResult = trustWeave.createDid { }) {
        is DidCreationResult.Success ->
            println("Created: ${didResult.did}")
        is DidCreationResult.Failure ->
            println("Error: $didResult")
    }
}
```

## Supported Features

### KMS Features

- Key generation (Ed25519, secp256k1, P-256/P-384/P-521 — RSA is **not** in `WaltIdKeyManagementService.SUPPORTED_ALGORITHMS`)
- Signing operations
- Public key retrieval
- Key deletion

### DID Methods

- **did:key** — Native did:key implementation via walt.id
- **did:web** — Web DID method via walt.id

## SPI Auto-Discovery

When the `kms/plugins/waltid` module is on the classpath, walt.id providers are automatically discoverable via SPI:

```kotlin
import org.trustweave.kms.*
import org.trustweave.did.*
import java.util.ServiceLoader

// Simple factory API for KMS - no ServiceLoader needed!
val kms = KeyManagementServices.create("waltid")

// Discover DID method providers (still uses ServiceLoader for DID methods)
val didProviders = ServiceLoader.load(DidMethodProvider::class.java)
val waltIdDidProvider = didProviders.find {
    it.supportedMethods.contains("key") || it.supportedMethods.contains("web")
}
```

## Testing

See the [walt.id Testing Guide](https://github.com/geoknoesis/trustweave/blob/main/kms/plugins/waltid/TESTING.md) for detailed testing information.

### Running Tests

```bash
# Run all walt.id tests
./gradlew :kms/plugins/waltid:test

# Run specific test class
./gradlew :kms/plugins/waltid:test --tests "WaltIdEndToEndTest"
```

## Error Handling

The walt.id integration follows TrustWeave's error handling patterns:

```kotlin
import org.trustweave.kms.results.GenerateKeyResult
import org.trustweave.kms.results.fold

val result = kms.generateKey(Algorithm.Ed25519)
result.fold(
    onSuccess = { key -> println("Key: ${key.id}") },
    onFailure = { failure ->
        when (failure) {
            is GenerateKeyResult.Failure.UnsupportedAlgorithm ->
                println("Unsupported: ${failure.algorithm.name}; supported=${failure.supportedAlgorithms.joinToString { it.name }}")
            is GenerateKeyResult.Failure.InvalidOptions ->
                println("Invalid options: ${failure.reason}")
            is GenerateKeyResult.Failure.Error ->
                println("Error: ${failure.reason}")
        }
    }
)
```

## Next Steps

- Review [Key Management Concepts](../../core-concepts/key-management.md) for KMS usage patterns
- See [DID Concepts](../../core-concepts/dids.md) for DID fundamentals
- Check [Creating Plugins](../../contributing/creating-plugins.md) to understand SPI integration
- Explore [Key DID Integration](key-did.md) for native did:key alternative
- See [Web DID Integration](web-did.md) for web DID details

## References

- [walt.id Documentation](https://walt.id/)
- [walt.id GitHub](https://github.com/walt-id/waltid-ssikit)
- [TrustWeave KMS Module](../../api-reference/modules/trustweave-kms.md)
- [TrustWeave DID Module](../../api-reference/modules/trustweave-did.md)

