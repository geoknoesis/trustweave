---
title: DID Operations Tutorial
nav_exclude: true
---

# DID Operations Tutorial

This tutorial provides a comprehensive guide to performing DID operations with TrustWeave. You'll learn how to create, resolve, update, and deactivate DIDs using various DID methods.

```kotlin
dependencies {
    implementation("org.trustweave:trustweave-did:1.0.0-SNAPSHOT")
    implementation("org.trustweave:trustweave-kms:1.0.0-SNAPSHOT")
    implementation("org.trustweave:trustweave-common:1.0.0-SNAPSHOT")
    implementation("org.trustweave:testkit:1.0.0-SNAPSHOT")
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
import org.trustweave.did.*
import org.trustweave.did.identifiers.Did

interface DidMethod {
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
// Kotlin stdlib
import kotlinx.coroutines.runBlocking

// TrustWeave core
import org.trustweave.trust.TrustWeave
import org.trustweave.trust.dsl.credential.DidMethods
import org.trustweave.trust.dsl.credential.KeyAlgorithms
fun main() = runBlocking {
    // Build TrustWeave instance - KMS and DID methods auto-discovered via SPI
    val trustWeave = TrustWeave.build {
        keys {
            provider(IN_MEMORY)  // Auto-discovered via SPI
            algorithm(KeyAlgorithms.ED25519)
        }
        did {
            method(DidMethods.KEY) {  // Auto-discovered via SPI
                algorithm(KeyAlgorithms.ED25519)
            }
        }
    }

    // Create DID using did:key method (returns sealed result)
    import org.trustweave.trust.types.DidCreationResult
    
    val didResult = trustWeave.createDid {
        method(DidMethods.KEY)
        algorithm(KeyAlgorithms.ED25519)
    }
    
    when (didResult) {
        is DidCreationResult.Success -> {
            println("Created DID: ${didResult.did.value}")
        }
        else -> {
            println("DID creation failed: ${didResult.reason}")
        }
    }
}
```

**Outcome:** Creates a DID using the configured DID method. Returns a type-safe `Did` object - access the string value using `.value`.

### Creating DIDs with Specific Methods

```kotlin
// Kotlin stdlib
import kotlinx.coroutines.runBlocking

// TrustWeave core
import org.trustweave.trust.TrustWeave
import org.trustweave.trust.dsl.credential.DidMethods
import org.trustweave.trust.dsl.credential.KeyAlgorithms
// Note: TestkitDidMethodFactory is for testing/tutorials only
// In production, use appropriate DID method factories
import org.trustweave.testkit.services.*

fun main() = runBlocking {
    // Build TrustWeave instance with testkit factories (for tutorials)
    val trustWeave = TrustWeave.build {
        factories(didMethodFactory = TestkitDidMethodFactory())  // Test-only factory
        keys { provider(IN_MEMORY); algorithm(KeyAlgorithms.ED25519) }
        did {
            method(DidMethods.KEY) { algorithm(KeyAlgorithms.ED25519) }
            method(DidMethods.WEB) { domain("example.com") }
        }
    }

    // Create DID with did:key method
    import org.trustweave.trust.types.getOrThrowDid
    
    val keyDid = trustWeave.createDid {
        method(DidMethods.KEY)
        algorithm(KeyAlgorithms.ED25519)
    }.getOrThrowDid()

    // Create DID with did:web method
    val webDid = trustWeave.createDid {
        method(DidMethods.WEB)
        domain("example.com")
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
import org.trustweave.did.*
import org.trustweave.kms.*
import org.trustweave.testkit.kms.InMemoryKeyManagementService

fun main() = runBlocking {
    // Create KMS
    val kms = InMemoryKeyManagementService()

    // Create DID method registry
    val registry = DidMethodRegistry()

    // Register did:key method
    val keyMethod = /* create or discover did:key method */
    registry.register("key", keyMethod)

    // Create DID using registry
    val options = didCreationOptions {
        algorithm = KeyAlgorithm.Secp256k1
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
import org.trustweave.trust.types.Did
import org.trustweave.did.resolver.DidResolutionResult
import org.trustweave.testkit.services.*
import kotlinx.coroutines.runBlocking

fun main() = runBlocking {
    val trustWeave = TrustWeave.build {
        factories(didMethodFactory = TestkitDidMethodFactory())
        keys { provider(IN_MEMORY); algorithm(KeyAlgorithms.ED25519) }
        did { method(DidMethods.KEY) { algorithm(KeyAlgorithms.ED25519) } }
    }

    import org.trustweave.trust.types.getOrThrow
    import org.trustweave.did.resolver.DidResolutionResult
    
    // Helper extension for resolution results
    fun DidResolutionResult.getOrThrow() = when (this) {
        is DidResolutionResult.Success -> this.document
        else -> throw IllegalStateException("Failed to resolve DID: ${this.errorMessage ?: "Unknown error"}")
    }
    
    val did = Did("did:key:z6MkhaXgBZDvotDkL5257faiztiGiC2QtKLGpbnnEGta2doK")
    val document = trustWeave.resolveDid(did).getOrThrow()
    
    println("Resolved DID: ${document.id}")
    println("Document: ${document}")
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

fun main() = runBlocking {
    val registry = DidMethodRegistry()
    // ... register methods ...

    val didString = "did:key:z6MkhaXgBZDvotDkL5257faiztiGiC2QtKLGpbnnEGta2doK"
    val resolutionResult = registry.resolve(didString)  // Registry convenience method accepts String

    resolutionResult.fold(
        onSuccess = { result ->
            println("Resolved: ${result.didDocument?.id}")
        },
        onFailure = { error ->
            println("Resolution failed: ${error.message}")
        }
    )
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
import org.trustweave.did.*
import org.trustweave.testkit.services.*

fun main() = runBlocking {
    val trustWeave = TrustWeave.build {
        factories(didMethodFactory = TestkitDidMethodFactory())
        keys { provider(IN_MEMORY); algorithm(KeyAlgorithms.ED25519) }
        did { method(DidMethods.KEY) { algorithm(KeyAlgorithms.ED25519) } }
    }

    val did = Did("did:key:z6MkhaXgBZDvotDkL5257faiztiGiC2QtKLGpbnnEGta2doK")

    // Update DID document
    try {
        val updatedDoc = trustWeave.updateDid {
            did(did.value)  // DSL builder accepts string for convenience
            // Add a new service endpoint
            service {
                id("${did.value}#service-1")
                type("LinkedDomains")
                endpoint("https://example.com/service")
            }
        }

        println("Updated DID: ${updatedDoc.id}")
        println("Services: ${updatedDoc.service.size}")
    } catch (error: Exception) {
        println("Update failed: ${error.message}")
    }
}
```

**Outcome:** Updates a DID document with new verification methods or services.

## Deactivating DIDs

### Deactivating DIDs

```kotlin
// Kotlin stdlib
import kotlinx.coroutines.runBlocking

// TrustWeave core
import org.trustweave.trust.TrustWeave
import org.trustweave.trust.dsl.credential.DidMethods
import org.trustweave.trust.dsl.credential.KeyAlgorithms
import org.trustweave.testkit.services.*

fun main() = runBlocking {
    val trustWeave = TrustWeave.build {
        factories(didMethodFactory = TestkitDidMethodFactory())
        keys { provider(IN_MEMORY); algorithm(KeyAlgorithms.ED25519) }
        did { method(DidMethods.KEY) { algorithm(KeyAlgorithms.ED25519) } }
    }

    val did = Did("did:key:z6MkhaXgBZDvotDkL5257faiztiGiC2QtKLGpbnnEGta2doK")

    // Deactivate DID using type-safe API
    try {
        val deactivated = trustWeave.deactivateDid(did)
        if (deactivated) {
            println("DID deactivated successfully: ${did.value}")
        } else {
            println("DID deactivation failed or not supported")
        }
    } catch (error: Exception) {
        println("Deactivation error: ${error.message}")
    }
}
```

**Outcome:** Deactivates a DID, marking it as no longer active.

## Working with Multiple DID Methods

### Managing Multiple DID Methods

```kotlin
// Kotlin stdlib
import kotlinx.coroutines.runBlocking

// TrustWeave core
import org.trustweave.did.*
import org.trustweave.kms.*

fun main() = runBlocking {
    val kms = InMemoryKeyManagementService()
    val registry = DidMethodRegistry()

    // Register multiple DID methods
    registry.register("key", /* did:key method */)
    registry.register("web", /* did:web method */)
    registry.register("ion", /* did:ion method */)

    // Create DIDs using different methods
    val keyDid = registry.get("key")?.createDid(didCreationOptions {
        algorithm = KeyAlgorithm.Ed25519
    })

    val webDid = registry.get("web")?.createDid(didCreationOptions {
        domain = "example.com"
        path = "/did/user/alice"
    })

    val ionDid = registry.get("ion")?.createDid(didCreationOptions {
        // ION-specific options
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
import org.trustweave.did.*
import org.trustweave.testkit.services.*

fun main() = runBlocking {
    // Build TrustWeave instance with testkit factories (for tutorials)
    val trustWeave = TrustWeave.build {
        factories(didMethodFactory = TestkitDidMethodFactory())  // Test-only factory
        keys { provider(IN_MEMORY); algorithm(KeyAlgorithms.ED25519) }
        did { method(DidMethods.KEY) { algorithm(KeyAlgorithms.ED25519) } }
    }

    try {
        val did = trustWeave.createDid {
            method(DidMethods.KEY)
            algorithm(KeyAlgorithms.ED25519)
        }
        
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
import org.trustweave.core.exception.TrustWeaveError
import org.trustweave.testkit.services.*

fun main() = runBlocking {
    // Build TrustWeave instance with testkit factories (for tutorials)
    val trustWeave = TrustWeave.build {
        factories(didMethodFactory = TestkitDidMethodFactory())  // Test-only factory
        keys { provider(IN_MEMORY); algorithm(KeyAlgorithms.ED25519) }
        did { method(DidMethods.KEY) { algorithm(KeyAlgorithms.ED25519) } }
    }

    try {
        val did = trustWeave.createDid {
            method(DidMethods.KEY)
            algorithm(KeyAlgorithms.ED25519)
        }
        println("Created: ${did.value}")
    } catch (error: Exception) {
        when (error) {
            is IllegalStateException -> {
                if (error.message?.contains("not configured") == true) {
                    println("DID method not configured: ${error.message}")
                } else {
                    println("Error: ${error.message}")
                }
            }
            else -> {
                println("Error: ${error.message}")
            }
            is TrustWeaveError.DidNotFound -> {
                println("DID not found: ${error.did}")
            }
            else -> println("Error: ${error.message}")
        }
    }
}
```

**Outcome:** Demonstrates structured error handling for DID operations.

## Next Steps

- Review [DID Concepts](../core-concepts/dids.md) for deeper understanding
- Explore [DID Integration Guides](../integrations/README.md) for specific method implementations
- See [Wallet API Tutorial](wallet-api-tutorial.md) for credential workflows
- Check [Creating Plugins](../contributing/creating-plugins.md) to implement custom DID methods

## References

- [W3C DID Core Specification](https://www.w3.org/TR/did-core/)
- [TrustWeave DID Module](../modules/trustweave-did.md)
- [TrustWeave Core API](../api-reference/core-api.md)

