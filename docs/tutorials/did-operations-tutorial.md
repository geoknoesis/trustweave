# DID Operations Tutorial

This tutorial provides a comprehensive guide to performing DID operations with VeriCore. You'll learn how to create, resolve, update, and deactivate DIDs using various DID methods.

```kotlin
dependencies {
    implementation("com.geoknoesis.vericore:vericore-did:1.0.0-SNAPSHOT")
    implementation("com.geoknoesis.vericore:vericore-kms:1.0.0-SNAPSHOT")
    implementation("com.geoknoesis.vericore:vericore-core:1.0.0-SNAPSHOT")
    implementation("com.geoknoesis.vericore:vericore-testkit:1.0.0-SNAPSHOT")
}
```

**Result:** Gives you the DID registry, DID method interfaces, KMS abstractions, and in-memory implementations used throughout this tutorial.

> Tip: The runnable quick-start sample (`./gradlew :vericore-examples:runQuickStartSample`) mirrors the core flows below. Clone it as a starting point before wiring more advanced DID logic.

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
import com.geoknoesis.vericore.did.*

interface DidMethod {
    val method: String
    suspend fun createDid(options: DidCreationOptions): DidDocument
    suspend fun resolveDid(did: String): DidResolutionResult
    suspend fun updateDid(did: String, updater: (DidDocument) -> DidDocument): DidDocument
    suspend fun deactivateDid(did: String): Boolean
}
```

**What this does:** Defines the contract for DID operations that all DID method implementations must fulfill.

**Outcome:** Enables VeriCore to support multiple DID methods through a unified interface.

## Creating DIDs

### Using VeriCore Facade (Recommended)

```kotlin
import com.geoknoesis.vericore.VeriCore
import kotlinx.coroutines.runBlocking

fun main() = runBlocking {
    val vericore = VeriCore.create()
    
    // Create DID using did:key method
    val did = vericore.dids.create()
    val didResult = Result.success(did)
    
    didResult.fold(
        onSuccess = { did ->
            println("Created DID: ${did.id}")
            println("DID Document: ${did.document}")
        },
        onFailure = { error ->
            println("DID creation failed: ${error.message}")
        }
    )
}
```

**Outcome:** Creates a DID using the default DID method (typically `did:key`) with sensible defaults.

### Creating DIDs with Specific Methods

```kotlin
import com.geoknoesis.vericore.VeriCore
import com.geoknoesis.vericore.did.*
import kotlinx.coroutines.runBlocking

fun main() = runBlocking {
    val vericore = VeriCore.create()
    
    // Create DID with did:key method
    val keyDid = vericore.dids.create("key") {
        algorithm = KeyAlgorithm.Ed25519
    }
    
    // Create DID with did:web method
    val webDid = vericore.dids.create("web") {
        domain = "example.com"
        path = "/did/user/alice"
    }
    
    println("Key DID: ${keyDid.id}")
    println("Web DID: ${webDid.id}")
}
```

**Outcome:** Creates DIDs using specific DID methods with custom configuration options.

### Using DID Method Registry

```kotlin
import com.geoknoesis.vericore.did.*
import com.geoknoesis.vericore.kms.*
import com.geoknoesis.vericore.testkit.kms.InMemoryKeyManagementService
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

### Resolving DIDs with VeriCore Facade

```kotlin
import com.geoknoesis.vericore.VeriCore
import kotlinx.coroutines.runBlocking

fun main() = runBlocking {
    val vericore = VeriCore.create()
    
    val did = "did:key:z6MkhaXgBZDvotDkL5257faiztiGiC2QtKLGpbnnEGta2doK"
    val resolution = vericore.dids.resolve(did)
    val resolutionResult = Result.success(resolution)
    
    resolutionResult.fold(
        onSuccess = { result ->
            println("Resolved DID: ${result.didDocument?.id}")
            println("Document: ${result.didDocument}")
            println("Metadata: ${result.metadata}")
        },
        onFailure = { error ->
            println("Resolution failed: ${error.message}")
        }
    )
}
```

**Outcome:** Resolves a DID using the appropriate DID method automatically.

### Resolving DIDs with Method Registry

```kotlin
import com.geoknoesis.vericore.did.*
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
import com.geoknoesis.vericore.VeriCore
import com.geoknoesis.vericore.did.*
import kotlinx.coroutines.runBlocking

fun main() = runBlocking {
    val vericore = VeriCore.create()
    
    val did = "did:key:z6MkhaXgBZDvotDkL5257faiztiGiC2QtKLGpbnnEGta2doK"
    
    // Update DID document
    val updateResult = vericore.updateDid(did) { document ->
        // Add a new service endpoint
        document.copy(
            service = document.service + Service(
                id = "${document.id}#service-1",
                type = "LinkedDomains",
                serviceEndpoint = "https://example.com/service"
            )
        )
    }
    
    updateResult.fold(
        onSuccess = { updatedDoc ->
            println("Updated DID: ${updatedDoc.id}")
            println("Services: ${updatedDoc.service.size}")
        },
        onFailure = { error ->
            println("Update failed: ${error.message}")
        }
    )
}
```

**Outcome:** Updates a DID document with new verification methods or services.

## Deactivating DIDs

### Deactivating DIDs

```kotlin
import com.geoknoesis.vericore.VeriCore
import kotlinx.coroutines.runBlocking

fun main() = runBlocking {
    val vericore = VeriCore.create()
    
    val did = "did:key:z6MkhaXgBZDvotDkL5257faiztiGiC2QtKLGpbnnEGta2doK"
    
    // Deactivate DID
    val deactivateResult = vericore.deactivateDid(did)
    
    deactivateResult.fold(
        onSuccess = { success ->
            if (success) {
                println("DID deactivated successfully")
            } else {
                println("DID deactivation failed")
            }
        },
        onFailure = { error ->
            println("Deactivation error: ${error.message}")
        }
    )
}
```

**Outcome:** Deactivates a DID, marking it as no longer active.

## Working with Multiple DID Methods

### Managing Multiple DID Methods

```kotlin
import com.geoknoesis.vericore.did.*
import com.geoknoesis.vericore.kms.*
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
import com.geoknoesis.vericore.did.*
import kotlinx.coroutines.runBlocking

fun main() = runBlocking {
    val vericore = VeriCore.create()
    val did = vericore.dids.create()
    val didResult = Result.success(did)
    
    didResult.fold(
        onSuccess = { did ->
            val document = did.document
            
            // Access verification methods
            val verificationMethods = document.verificationMethod
            println("Verification methods: ${verificationMethods.size}")
            
            // Access services
            val services = document.service
            println("Services: ${services.size}")
            
            // Access authentication methods
            val authentication = document.authentication
            println("Authentication methods: ${authentication.size}")
        },
        onFailure = { error ->
            println("Error: ${error.message}")
        }
    )
}
```

**Outcome:** Shows how to access and work with DID document components.

### Error Handling

```kotlin
import com.geoknoesis.vericore.VeriCore
import com.geoknoesis.vericore.core.VeriCoreError
import kotlinx.coroutines.runBlocking

fun main() = runBlocking {
    val vericore = VeriCore.create()
    
    val did = vericore.dids.create()
    val result = Result.success(did)
    result.fold(
        onSuccess = { did -> println("Created: ${did.id}") },
        onFailure = { error ->
            when (error) {
                is VeriCoreError.DidCreationFailed -> {
                    println("Creation failed: ${error.reason}")
                }
                is VeriCoreError.DidResolutionFailed -> {
                    println("Resolution failed: ${error.reason}")
                }
                is VeriCoreError.DidUpdateFailed -> {
                    println("Update failed: ${error.reason}")
                }
                else -> println("Error: ${error.message}")
            }
        }
    )
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
- [VeriCore DID Module](../modules/vericore-did.md)
- [VeriCore Core API](../api-reference/core-api.md)

