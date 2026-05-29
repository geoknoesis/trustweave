---
title: DID Operations Tutorial
nav_exclude: true
nav_order: 40
---

# DID Operations Tutorial

This tutorial provides a comprehensive guide to performing DID operations with TrustWeave. You'll learn how to create, resolve, update, and deactivate DIDs using various DID methods.

```kotlin
dependencies {
    implementation("org.trustweave:did-did-core:0.6.0")
    implementation("org.trustweave:kms-kms-core:0.6.0")
    implementation("org.trustweave:common:0.6.0")
    implementation("org.trustweave:testkit:0.6.0")
}
```

**Result:** Gives you the DID registry, DID method interfaces, KMS abstractions, and in-memory implementations used throughout this tutorial.

> Tip: The runnable quick-start sample (`./gradlew :TrustWeave-examples:runQuickStartSample`) mirrors the core flows below. Clone it as a starting point before wiring more advanced DID logic.

## Prerequisites

- Basic understanding of Kotlin
- Familiarity with coroutines
- Understanding of [DIDs](../core-concepts/dids.md)

## Table of Contents

1. [Understanding DID Methods](#understanding-did-methods)
2. [Creating DIDs](#creating-dids)
3. [Resolving DIDs](#resolving-dids)
4. [Updating DIDs](#updating-dids)
5. [Deactivating DIDs](#deactivating-dids)
6. [Working with Multiple DID Methods](#working-with-multiple-did-methods)
7. [Advanced DID Operations](#advanced-did-operations)

## Understanding DID Methods

A DID method is an implementation of the `DidMethod` interface that supports a specific DID method (e.g., `did:key`, `did:web`, `did:ion`). Each DID method has its own creation, resolution, update, and deactivation logic.

```kotlin
import org.trustweave.did.DidMethod
import org.trustweave.did.DidCreationOptions
import org.trustweave.did.identifiers.Did
import org.trustweave.did.model.DidDocument
import org.trustweave.did.resolver.DidResolutionResult

// Sketch of the SPI (see did/did-core/src/main/kotlin/org/trustweave/did/DidMethod.kt)
interface DidMethodSketch {
    val method: String
    suspend fun createDid(options: DidCreationOptions): DidDocument
    suspend fun resolveDid(did: Did): DidResolutionResult
    suspend fun updateDid(did: Did, updater: (DidDocument) -> DidDocument): DidDocument
    suspend fun deactivateDid(did: Did): Boolean
}
```

**What this does:** Defines the contract for DID operations that all DID method implementations must fulfill.

**Outcome:** Enables TrustWeave to support multiple DID methods through a unified interface.

## Creating DIDs

### Using TrustWeave DSL (Recommended)

```kotlin
import org.trustweave.trust.types.DidCreationResult

// Kotlin stdlib
import kotlinx.coroutines.runBlocking

// TrustWeave core
import org.trustweave.trust.TrustWeave
import org.trustweave.trust.dsl.credential.DidMethods
import org.trustweave.trust.dsl.credential.KeyAlgorithms
import org.trustweave.trust.dsl.credential.KmsProviders

fun main() = runBlocking {
    // Build TrustWeave instance - KMS and DID methods auto-discovered via SPI
    val trustWeave = TrustWeave.build {
        keys {
            provider(KmsProviders.IN_MEMORY)  // Auto-discovered via SPI
            algorithm(KeyAlgorithms.ED25519)
        }
        did {
            method(DidMethods.KEY) {  // Auto-discovered via SPI
                algorithm(KeyAlgorithms.ED25519)
            }
        }
    }

    // Create DID using did:key method (returns sealed result)
    val didResult = trustWeave.createDid {
        method(DidMethods.KEY)
        algorithm(KeyAlgorithms.ED25519)
    }
    
    when (didResult) {
        is DidCreationResult.Success -> {
            println("Created DID: ${didResult.did.value}")
        }
        is DidCreationResult.Failure -> {
            val msg = when (didResult) {
                is DidCreationResult.Failure.MethodNotRegistered ->
                    "method ${didResult.method} not registered; available: ${didResult.availableMethods.joinToString()}"
                is DidCreationResult.Failure.KeyGenerationFailed -> didResult.reason
                is DidCreationResult.Failure.DocumentCreationFailed -> didResult.reason
                is DidCreationResult.Failure.InvalidConfiguration -> didResult.reason
                is DidCreationResult.Failure.Other -> didResult.reason
            }
            println("DID creation failed: $msg")
        }
    }
}
```

**Outcome:** Creates a DID using the configured DID method. Returns a type-safe `Did` object - access the string value using `.value`.

### Creating DIDs with Specific Methods

```kotlin
import org.trustweave.trust.types.getOrThrowDid

// Kotlin stdlib
import kotlinx.coroutines.runBlocking

// TrustWeave core
import org.trustweave.trust.TrustWeave
import org.trustweave.trust.dsl.credential.DidMethods
import org.trustweave.trust.dsl.credential.KeyAlgorithms
import org.trustweave.trust.dsl.credential.KmsProviders

fun main() = runBlocking {
    // Per-method options (algorithm, domain, ...) are set on the build-time DID config.
    // The did:web plugin must be on the classpath (SPI auto-discovery).
    val trustWeave = TrustWeave.build {
        keys { provider(KmsProviders.IN_MEMORY); algorithm(KeyAlgorithms.ED25519) }
        did {
            method(DidMethods.KEY) { algorithm(KeyAlgorithms.ED25519) }
            method(DidMethods.WEB) { domain("example.com") }
        }
    }

    // Create DID with did:key method
    val keyDid = trustWeave.createDid {
        method(DidMethods.KEY)
        algorithm(KeyAlgorithms.ED25519)
    }.getOrThrowDid()

    // Create DID with did:web method (uses the domain configured at build time)
    val webDid = trustWeave.createDid {
        method(DidMethods.WEB)
    }.getOrThrowDid()

    println("Key DID: ${keyDid.value}")
    println("Web DID: ${webDid.value}")
}
```

**Outcome:** Creates DIDs using specific DID methods with custom configuration options.

### Using DID Method Registry

```kotlin
// Kotlin stdlib
import kotlinx.coroutines.runBlocking

// TrustWeave core
import org.trustweave.did.DidMethod
import org.trustweave.did.KeyAlgorithm
import org.trustweave.did.didCreationOptions
import org.trustweave.did.registry.DidMethodRegistry
import org.trustweave.testkit.kms.InMemoryKeyManagementService

fun main() = runBlocking {
    // Create KMS
    val kms = InMemoryKeyManagementService()

    // Create DID method registry
    val registry = DidMethodRegistry()

    // Obtain a DidMethod implementation. Plugins (e.g. did:key) expose `DidMethod` instances
    // via SPI; for direct registration use the plugin's factory or auto-registration.
    val keyMethod: DidMethod = TODO("provide a did:key DidMethod implementation")
    registry.register(keyMethod)  // method name is read from keyMethod.method

    // Create DID using registry
    val options = didCreationOptions {
        algorithm = KeyAlgorithm.SECP256K1
    }

    val method = registry.get("key")
    val didDoc = method?.createDid(options)

    println("Created DID: ${didDoc?.id}")
}
```

**Outcome:** Creates a DID using a manually configured DID method registry.

## Resolving DIDs

### Resolving DIDs with TrustWeave DSL

```kotlin
import org.trustweave.trust.TrustWeave
import org.trustweave.trust.dsl.credential.DidMethods
import org.trustweave.trust.dsl.credential.KeyAlgorithms
import org.trustweave.trust.dsl.credential.KmsProviders
import org.trustweave.did.identifiers.Did
import org.trustweave.did.resolver.DidResolutionResult
import org.trustweave.did.resolver.errorMessage
import kotlinx.coroutines.runBlocking

fun main() = runBlocking {
    val trustWeave = TrustWeave.build {
        keys { provider(KmsProviders.IN_MEMORY); algorithm(KeyAlgorithms.ED25519) }
        did { method(DidMethods.KEY) { algorithm(KeyAlgorithms.ED25519) } }
    }

    val did = Did("did:key:z6MkhaXgBZDvotDkL5257faiztiGiC2QtKLGpbnnEGta2doK")
    val document = when (val res = trustWeave.resolveDid(did)) {
        is DidResolutionResult.Success -> res.document
        else -> throw IllegalStateException(res.errorMessage ?: "Failed to resolve DID")
    }

    println("Resolved DID: ${document.id}")
    println("Document: $document")
    println("Verification methods: ${document.verificationMethod.size}")
}
```

**Outcome:** Resolves a DID using the appropriate DID method automatically. Returns a sealed `DidResolutionResult` for type-safe error handling.

### Resolving DIDs with Method Registry

```kotlin
// Kotlin stdlib
import kotlinx.coroutines.runBlocking

// TrustWeave core
import org.trustweave.did.*
import org.trustweave.did.resolver.DidResolutionResult

fun main() = runBlocking {
    val registry = DidMethodRegistry()
    // ... register methods ...

    val didString = "did:key:z6MkhaXgBZDvotDkL5257faiztiGiC2QtKLGpbnnEGta2doK"
    val resolutionResult = registry.resolve(didString) // String overload returns sealed DidResolutionResult

    when (resolutionResult) {
        is DidResolutionResult.Success ->
            println("Resolved: ${resolutionResult.document.id}")
        is DidResolutionResult.Failure ->
            println("Resolution failed: ${resolutionResult::class.simpleName}")
    }
}
```

**Outcome:** Resolves a DID using a manually configured method registry.

## Updating DIDs

### Updating DID Documents

```kotlin
// Kotlin stdlib
import kotlinx.coroutines.runBlocking

// TrustWeave core
import org.trustweave.trust.TrustWeave
import org.trustweave.trust.dsl.credential.DidMethods
import org.trustweave.trust.dsl.credential.KeyAlgorithms
import org.trustweave.trust.dsl.credential.KmsProviders
import org.trustweave.trust.types.DidResult
import org.trustweave.did.identifiers.Did

fun main() = runBlocking {
    val trustWeave = TrustWeave.build {
        keys { provider(KmsProviders.IN_MEMORY); algorithm(KeyAlgorithms.ED25519) }
        did { method(DidMethods.KEY) { algorithm(KeyAlgorithms.ED25519) } }
    }

    val did = Did("did:key:z6MkhaXgBZDvotDkL5257faiztiGiC2QtKLGpbnnEGta2doK")

    // updateDid returns a sealed DidResult
    when (val updateResult = trustWeave.updateDid {
        did(did.value)  // DSL builder accepts string for convenience
        method("key")
        // Add a new service endpoint
        addService {
            id("${did.value}#service-1")
            type("LinkedDomains")
            endpoint("https://example.com/service")
        }
    }) {
        is DidResult.Success -> {
            println("Updated DID: ${updateResult.did.value}")
            println("Services: ${updateResult.document.service.size}")
        }
        is DidResult.Failure -> {
            println("Update failed: $updateResult")
        }
    }
}
```

**Outcome:** Updates a DID document with new verification methods or services.

## Deactivating DIDs

### Deactivating DIDs

`TrustWeave` does **not** expose a top-level `deactivateDid(did)` facade method.
Deactivation is method-specific: resolve the `DidMethod` plugin from the registry
and call its `deactivateDid(did: Did)` directly. `did:key` is stateless and cannot
be deactivated (the call returns `false`).

```kotlin
import kotlinx.coroutines.runBlocking
import org.trustweave.did.DidMethod
import org.trustweave.did.identifiers.Did

fun main() = runBlocking {
    val did = Did("did:web:example.com:users:alice")

    val method = trustWeave.getDidRegistry().get("web") as? DidMethod
        ?: error("did:web is not registered")

    val deactivated: Boolean = method.deactivateDid(did)
    println(if (deactivated) "Deactivated $did" else "Could not deactivate $did")
}
```

For remote (universal-registrar) deactivation with options, use
`org.trustweave.did.DidRegistrar.deactivateDid(did, DeactivateDidOptions)` from the
`did/registrar` module instead — the plain `DidMethod` SPI does not take options.

## Working with Multiple DID Methods

### Managing Multiple DID Methods

```kotlin
// Kotlin stdlib
import kotlinx.coroutines.runBlocking

// TrustWeave core
import org.trustweave.did.DidMethod
import org.trustweave.did.KeyAlgorithm
import org.trustweave.did.didCreationOptions
import org.trustweave.did.registry.DidMethodRegistry
import org.trustweave.testkit.kms.InMemoryKeyManagementService

fun main() = runBlocking {
    val kms = InMemoryKeyManagementService()
    val registry = DidMethodRegistry()

    // Register multiple DID methods (each DidMethod knows its own method name)
    val keyMethod: DidMethod = TODO("did:key DidMethod")
    val webMethod: DidMethod = TODO("did:web DidMethod")
    val ionMethod: DidMethod = TODO("did:ion DidMethod")
    registry.registerAll(keyMethod, webMethod, ionMethod)

    // Create DIDs using different methods
    val keyDid = registry.get("key")?.createDid(didCreationOptions {
        algorithm = KeyAlgorithm.ED25519
    })

    val webDid = registry.get("web")?.createDid(didCreationOptions {
        property("domain", "example.com")
        property("path", "/did/user/alice")
    })

    val ionDid = registry.get("ion")?.createDid(didCreationOptions {
        // ION-specific options via property(key, value)
    })

    println("Key DID: ${keyDid?.id}")
    println("Web DID: ${webDid?.id}")
    println("ION DID: ${ionDid?.id}")
}
```

**Outcome:** Demonstrates how to work with multiple DID methods in the same application.

## Advanced DID Operations

### Working with DID Documents

```kotlin
// Kotlin stdlib
import kotlinx.coroutines.runBlocking

// TrustWeave core
import org.trustweave.trust.TrustWeave
import org.trustweave.trust.dsl.credential.DidMethods
import org.trustweave.trust.dsl.credential.KeyAlgorithms
import org.trustweave.trust.dsl.credential.KmsProviders
import org.trustweave.trust.types.getOrThrowDid
import org.trustweave.did.resolver.DidResolutionResult

fun main() = runBlocking {
    val trustWeave = TrustWeave.build {
        keys { provider(KmsProviders.IN_MEMORY); algorithm(KeyAlgorithms.ED25519) }
        did { method(DidMethods.KEY) { algorithm(KeyAlgorithms.ED25519) } }
    }

    try {
        val did = trustWeave.createDid {
            method(DidMethods.KEY)
            algorithm(KeyAlgorithms.ED25519)
        }.getOrThrowDid()

        // Resolve DID to get document
        val resolution = trustWeave.resolveDid(did)
        val document = when (resolution) {
            is DidResolutionResult.Success -> resolution.document
            else -> throw IllegalStateException("Failed to resolve DID")
        }

        // Access verification methods
        val verificationMethods = document.verificationMethod
        println("Verification methods: ${verificationMethods.size}")

        // Access services
        val services = document.service
        println("Services: ${services.size}")

        // Access authentication methods
        val authentication = document.authentication
        println("Authentication methods: ${authentication.size}")
    } catch (error: Exception) {
        println("Error: ${error.message}")
    }
}
```

**Outcome:** Shows how to access and work with DID document components.

### Error Handling

```kotlin
// Kotlin stdlib
import kotlinx.coroutines.runBlocking

// TrustWeave core
import org.trustweave.trust.TrustWeave
import org.trustweave.trust.dsl.credential.DidMethods
import org.trustweave.trust.dsl.credential.KeyAlgorithms
import org.trustweave.trust.dsl.credential.KmsProviders
import org.trustweave.trust.types.DidCreationResult

fun main() = runBlocking {
    val trustWeave = TrustWeave.build {
        keys { provider(KmsProviders.IN_MEMORY); algorithm(KeyAlgorithms.ED25519) }
        did { method(DidMethods.KEY) { algorithm(KeyAlgorithms.ED25519) } }
    }

    // Prefer exhaustive sealed-result handling over try/catch for DID creation.
    when (val result = trustWeave.createDid {
        method(DidMethods.KEY)
        algorithm(KeyAlgorithms.ED25519)
    }) {
        is DidCreationResult.Success -> println("Created: ${result.did.value}")
        is DidCreationResult.Failure.MethodNotRegistered ->
            println("DID method '${result.method}' not registered. Available: ${result.availableMethods.joinToString()}")
        is DidCreationResult.Failure.KeyGenerationFailed -> println("Key generation failed: ${result.reason}")
        is DidCreationResult.Failure.DocumentCreationFailed -> println("Document creation failed: ${result.reason}")
        is DidCreationResult.Failure.InvalidConfiguration -> println("Invalid configuration: ${result.reason}")
        is DidCreationResult.Failure.Other -> println("Error: ${result.reason}")
    }
}
```

**Outcome:** Demonstrates structured error handling for DID operations.

## Next Steps

- Review [DID Concepts](../core-concepts/dids.md) for deeper understanding
- Explore [DID Integration Guides](../how-to/integrations/README.md) for specific method implementations
- See [Wallet API Tutorial](wallet-api-tutorial.md) for credential workflows
- Check [Creating Plugins](../contributing/creating-plugins.md) to implement custom DID methods

## References

- [W3C DID Core Specification](https://www.w3.org/TR/did-core/)
- [TrustWeave DID Module](../api-reference/modules/trustweave-did.md)
- [TrustWeave Core API](../api-reference/core-api.md)

