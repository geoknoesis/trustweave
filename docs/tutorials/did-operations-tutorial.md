# DID Operations Tutorial

This tutorial provides a comprehensive guide to performing DID operations with TrustWeave. You'll learn how to create, resolve, update, and deactivate DIDs using various DID methods.

```kotlin
dependencies {
    implementation("com.trustweave:trustweave-did:1.0.0-SNAPSHOT")
    implementation("com.trustweave:trustweave-kms:1.0.0-SNAPSHOT")
    implementation("com.trustweave:trustweave-common:1.0.0-SNAPSHOT")
    implementation("com.trustweave:trustweave-testkit:1.0.0-SNAPSHOT")
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
import com.trustweave.did.*

interface DidMethod {
    val method: String
    suspend fun createDid(options: DidCreationOptions): DidDocument
    suspend fun resolveDid(did: String): DidResolutionResult
    suspend fun updateDid(did: String, updater: (DidDocument) -> DidDocument): DidDocument
    suspend fun deactivateDid(did: String): Boolean
}
```

**What this does:** Defines the contract for DID operations that all DID method implementations must fulfill.

**Outcome:** Enables TrustWeave to support multiple DID methods through a unified interface.

## Creating DIDs

### Using TrustWeave Facade (Recommended)

```kotlin
import com.trustweave.TrustWeave
import kotlinx.coroutines.runBlocking

fun main() = runBlocking {
    val trustweave = TrustWeave.create()
    
    // Create DID using did:key method
    try {
        val did = trustweave.dids.create()
        println("Created DID: ${did.id}")
        println("DID Document ID: ${did.id}")
    } catch (error: TrustWeaveError) {
        println("DID creation failed: ${error.message}")
    }
}
```

**Outcome:** Creates a DID using the default DID method (typically `did:key`) with sensible defaults.

### Creating DIDs with Specific Methods

```kotlin
import com.trustweave.TrustWeave
import com.trustweave.did.*
import kotlinx.coroutines.runBlocking

fun main() = runBlocking {
    val trustweave = TrustWeave.create()
    
    // Create DID with did:key method
    val keyDid = trustweave.dids.create("key") {
        algorithm = KeyAlgorithm.Ed25519
    }
    
    // Create DID with did:web method (requires did:web method registration)
    val webDid = trustweave.dids.create("web") {
        algorithm = KeyAlgorithm.Ed25519
        // Note: did:web specific options would be in additionalProperties
    }
    
    println("Key DID: ${keyDid.id}")
    println("Web DID: ${webDid.id}")
}
```

**Outcome:** Creates DIDs using specific DID methods with custom configuration options.

### Using DID Method Registry

```kotlin
import com.trustweave.did.*
import com.trustweave.kms.*
import com.trustweave.testkit.kms.InMemoryKeyManagementService
import kotlinx.coroutines.runBlocking

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

### Resolving DIDs with TrustWeave Facade

```kotlin
import com.trustweave.TrustWeave
import kotlinx.coroutines.runBlocking

fun main() = runBlocking {
    val trustweave = TrustWeave.create()
    
    val did = "did:key:z6MkhaXgBZDvotDkL5257faiztiGiC2QtKLGpbnnEGta2doK"
    try {
        val resolution = trustweave.dids.resolve(did)
        if (resolution.document != null) {
            println("Resolved DID: ${resolution.document.id}")
            println("Document: ${resolution.document}")
            println("Metadata method: ${resolution.metadata.method}")
        } else {
            println("DID not found: ${resolution.metadata.error}")
        }
    } catch (error: TrustWeaveError) {
        println("Resolution failed: ${error.message}")
    }
}
```

**Outcome:** Resolves a DID using the appropriate DID method automatically.

### Resolving DIDs with Method Registry

```kotlin
import com.trustweave.did.*
import kotlinx.coroutines.runBlocking

fun main() = runBlocking {
    val registry = DidMethodRegistry()
    // ... register methods ...
    
    val did = "did:key:z6MkhaXgBZDvotDkL5257faiztiGiC2QtKLGpbnnEGta2doK"
    val resolutionResult = registry.resolve(did)
    
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
import com.trustweave.TrustWeave
import com.trustweave.did.*
import kotlinx.coroutines.runBlocking

fun main() = runBlocking {
    val trustweave = TrustWeave.create()
    
    val did = "did:key:z6MkhaXgBZDvotDkL5257faiztiGiC2QtKLGpbnnEGta2doK"
    
    // Update DID document
    try {
        val updatedDoc = trustweave.dids.update(did) { document ->
            // Add a new service endpoint
            document.copy(
                service = document.service + Service(
                    id = "${document.id}#service-1",
                    type = "LinkedDomains",
                    serviceEndpoint = "https://example.com/service"
                )
            )
        }
        
        println("Updated DID: ${updatedDoc.id}")
        println("Services: ${updatedDoc.service.size}")
    } catch (error: TrustWeaveError) {
        println("Update failed: ${error.message}")
    }
}
```

**Outcome:** Updates a DID document with new verification methods or services.

## Deactivating DIDs

### Deactivating DIDs

```kotlin
import com.trustweave.TrustWeave
import kotlinx.coroutines.runBlocking

fun main() = runBlocking {
    val trustweave = TrustWeave.create()
    
    val did = "did:key:z6MkhaXgBZDvotDkL5257faiztiGiC2QtKLGpbnnEGta2doK"
    
    // Deactivate DID
    try {
        val success = trustweave.dids.deactivate(did)
        if (success) {
            println("DID deactivated successfully")
        } else {
            println("DID deactivation failed")
        }
    } catch (error: TrustWeaveError) {
        println("Deactivation error: ${error.message}")
    }
}
```

**Outcome:** Deactivates a DID, marking it as no longer active.

## Working with Multiple DID Methods

### Managing Multiple DID Methods

```kotlin
import com.trustweave.did.*
import com.trustweave.kms.*
import kotlinx.coroutines.runBlocking

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
import com.trustweave.did.*
import kotlinx.coroutines.runBlocking

fun main() = runBlocking {
    val trustweave = TrustWeave.create()
    
    try {
        val did = trustweave.dids.create()
        val document = did
        
        // Access verification methods
        val verificationMethods = document.verificationMethod
        println("Verification methods: ${verificationMethods.size}")
        
        // Access services
        val services = document.service
        println("Services: ${services.size}")
        
        // Access authentication methods
        val authentication = document.authentication
        println("Authentication methods: ${authentication.size}")
    } catch (error: TrustWeaveError) {
        println("Error: ${error.message}")
    }
}
```

**Outcome:** Shows how to access and work with DID document components.

### Error Handling

```kotlin
import com.trustweave.TrustWeave
import com.trustweave.core.TrustWeaveError
import kotlinx.coroutines.runBlocking

fun main() = runBlocking {
    val trustweave = TrustWeave.create()
    
    try {
        val did = trustweave.dids.create()
        println("Created: ${did.id}")
    } catch (error: TrustWeaveError) {
        when (error) {
            is TrustWeaveError.DidMethodNotRegistered -> {
                println("Method not registered: ${error.method}")
                println("Available methods: ${error.availableMethods}")
            }
            is TrustWeaveError.InvalidDidFormat -> {
                println("Invalid DID format: ${error.reason}")
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

