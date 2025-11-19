# walt.id Integration

> This guide covers the walt.id integration for VeriCore. The walt.id plugin provides walt.id-based KMS and DID methods with SPI-based discovery, supporting did:key and did:web methods.

## Overview

The `kms/plugins/waltid` module provides integration with walt.id libraries for key management and DID operations. This integration enables you to:

- Use walt.id KMS for secure key generation and signing
- Support did:key and did:web DID methods via walt.id
- Leverage SPI-based auto-discovery of walt.id providers
- Integrate walt.id libraries seamlessly into VeriCore workflows

## Installation

Add the walt.id module to your dependencies:

```kotlin
dependencies {
    implementation("com.geoknoesis.vericore.kms:waltid:1.0.0-SNAPSHOT")
    implementation("com.geoknoesis.vericore:vericore-kms:1.0.0-SNAPSHOT")
    implementation("com.geoknoesis.vericore:vericore-did:1.0.0-SNAPSHOT")
    implementation("com.geoknoesis.vericore:vericore-core:1.0.0-SNAPSHOT")
}
```

## Configuration

### Basic Configuration

The walt.id integration supports automatic discovery via SPI:

```kotlin
import com.geoknoesis.vericore.waltid.WaltIdIntegration
import com.geoknoesis.vericore.did.*
import com.geoknoesis.vericore.kms.*
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
import com.geoknoesis.vericore.waltid.*
import com.geoknoesis.vericore.did.*
import com.geoknoesis.vericore.kms.*

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
import com.geoknoesis.vericore.waltid.*
import com.geoknoesis.vericore.kms.*

val kms = WaltIdKeyManagementService()

// Generate key
val key = kms.generateKey(Algorithm.Ed25519)
println("Generated key: ${key.id}")

// Sign data
val data = "Hello, VeriCore!".toByteArray()
val signature = kms.sign(key.id, data)
println("Signature: ${signature.toHexString()}")

// Get public key
val publicKey = kms.getPublicKey(key.id)
println("Public key: ${publicKey.toHexString()}")
```

### DID Operations

```kotlin
import com.geoknoesis.vericore.waltid.*
import com.geoknoesis.vericore.did.*

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

### Using VeriCore Facade

```kotlin
import com.geoknoesis.vericore.VeriCore
import com.geoknoesis.vericore.waltid.WaltIdIntegration
import kotlinx.coroutines.runBlocking

runBlocking {
    // Setup walt.id integration
    WaltIdIntegration.discoverAndRegister()
    
    // Use VeriCore facade with walt.id providers
    val vericore = VeriCore.create()
    val didResult = vericore.createDid()
    
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
import com.geoknoesis.vericore.kms.*
import com.geoknoesis.vericore.did.*
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

The walt.id integration follows VeriCore's error handling patterns:

```kotlin
import com.geoknoesis.vericore.core.VeriCoreError

val result = kms.generateKey(Algorithm.Ed25519)
result.fold(
    onSuccess = { key -> println("Key: ${key.id}") },
    onFailure = { error ->
        when (error) {
            is VeriCoreError.KeyGenerationFailed -> {
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
- [VeriCore KMS Module](../modules/vericore-kms.md)
- [VeriCore DID Module](../modules/vericore-did.md)

