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
    implementation("com.trustweave.kms:waltid:1.0.0-SNAPSHOT")
    implementation("com.trustweave:trustweave-kms:1.0.0-SNAPSHOT")
    implementation("com.trustweave:trustweave-did:1.0.0-SNAPSHOT")
    implementation("com.trustweave:trustweave-common:1.0.0-SNAPSHOT")
}
```

## Configuration

### Basic Configuration

The walt.id integration supports automatic discovery via SPI:

```kotlin
import com.trustweave.waltid.WaltIdIntegration
import com.trustweave.did.*
import com.trustweave.kms.*
import kotlinx.coroutines.runBlocking

runBlocking {
    // Auto-discover and register walt.id providers
    val result = WaltIdIntegration.discoverAndRegister()
    
    // Access registered components
    val kms = result.kms
    val registry = result.registry
    val registeredMethods = result.registeredDidMethods
    
    println("Registered DID methods: $registeredMethods")
}
```

### Manual Setup

You can also manually configure walt.id components:

```kotlin
import com.trustweave.waltid.*
import com.trustweave.did.*
import com.trustweave.kms.*

// Create walt.id KMS
val kms = WaltIdKeyManagementService()

// Create DID methods
val keyMethod = WaltIdDidKeyMethod(kms)
val webMethod = WaltIdDidWebMethod(kms)

// Register methods
val registry = DidMethodRegistry()
registry.register("key", keyMethod)
registry.register("web", webMethod)
```

## Usage Examples

### KMS Operations

```kotlin
import com.trustweave.waltid.*
import com.trustweave.kms.*

val kms = WaltIdKeyManagementService()

// Generate key
val key = kms.generateKey(Algorithm.Ed25519)
println("Generated key: ${key.id}")

// Sign data
val data = "Hello, TrustWeave!".toByteArray()
val signature = kms.sign(key.id, data)
println("Signature: ${signature.toHexString()}")

// Get public key
val publicKey = kms.getPublicKey(key.id)
println("Public key: ${publicKey.toHexString()}")
```

### DID Operations

```kotlin
import com.trustweave.waltid.*
import com.trustweave.did.*

val kms = WaltIdKeyManagementService()
val keyMethod = WaltIdDidKeyMethod(kms)

// Create did:key
val options = didCreationOptions {
    algorithm = KeyAlgorithm.Ed25519
}

val didDoc = keyMethod.createDid(options)
println("Created DID: ${didDoc.id}")

// Resolve DID
val resolutionResult = keyMethod.resolveDid(didDoc.id)
println("Resolved DID: ${resolutionResult.didDocument?.id}")
```

### Using TrustWeave Facade

```kotlin
import com.trustweave.TrustWeave
import com.trustweave.waltid.WaltIdIntegration
import kotlinx.coroutines.runBlocking

runBlocking {
    // Setup walt.id integration
    WaltIdIntegration.discoverAndRegister()
    
    // Use TrustWeave facade with walt.id providers
    val TrustWeave = TrustWeave.create()
    val did = TrustWeave.dids.create()
    
    didResult.fold(
        onSuccess = { did -> println("Created: ${did.id}") },
        onFailure = { error -> println("Error: ${error.message}") }
    )
}
```

## Supported Features

### KMS Features

- ✅ Key generation (Ed25519, secp256k1, P-256/P-384/P-521, RSA)
- ✅ Signing operations
- ✅ Public key retrieval
- ✅ Key deletion

### DID Methods

- ✅ **did:key** – Native did:key implementation via walt.id
- ✅ **did:web** – Web DID method via walt.id

## SPI Auto-Discovery

When the `kms/plugins/waltid` module is on the classpath, walt.id providers are automatically discoverable via SPI:

```kotlin
import com.trustweave.kms.*
import com.trustweave.did.*
import java.util.ServiceLoader

// Discover KMS provider
val kmsProviders = ServiceLoader.load(KeyManagementServiceProvider::class.java)
val waltIdKmsProvider = kmsProviders.find { it.name == "waltid" }

// Discover DID method providers
val didProviders = ServiceLoader.load(DidMethodProvider::class.java)
val waltIdDidProvider = didProviders.find { 
    it.supportedMethods.contains("key") || it.supportedMethods.contains("web")
}
```

## Testing

See the [walt.id Testing Guide](../../kms/plugins/waltid/TESTING.md) for detailed testing information.

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
import com.trustweave.core.TrustWeaveError

val result = kms.generateKey(Algorithm.Ed25519)
result.fold(
    onSuccess = { key -> println("Key: ${key.id}") },
    onFailure = { error ->
        when (error) {
            is TrustWeaveError.KeyGenerationFailed -> {
                println("Key generation failed: ${error.reason}")
            }
            else -> println("Error: ${error.message}")
        }
    }
)
```

## Next Steps

- Review [Key Management Concepts](../core-concepts/key-management.md) for KMS usage patterns
- See [DID Concepts](../core-concepts/dids.md) for DID fundamentals
- Check [Creating Plugins](../contributing/creating-plugins.md) to understand SPI integration
- Explore [Key DID Integration](key-did.md) for native did:key alternative
- See [Web DID Integration](web-did.md) for web DID details

## References

- [walt.id Documentation](https://walt.id/)
- [walt.id GitHub](https://github.com/walt-id/waltid-ssikit)
- [TrustWeave KMS Module](../modules/trustweave-kms.md)
- [TrustWeave DID Module](../modules/trustweave-did.md)

