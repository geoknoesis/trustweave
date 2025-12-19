---
title: Create and Manage DIDs
nav_order: 3
parent: How-To Guides
keywords:
  - dids
  - create did
  - resolve did
  - update did
  - deactivate did
  - did methods
  - identity
---

# Create and Manage DIDs

This guide shows you how to create, resolve, update, and deactivate Decentralized Identifiers (DIDs) using TrustWeave.

## Quick Example

Here's a complete example that creates a DID, extracts the key ID, and uses it:

```kotlin
import com.trustweave.trust.dsl.trustWeave
import com.trustweave.trust.dsl.credential.*
import com.trustweave.testkit.services.*
import kotlinx.coroutines.runBlocking

fun main() = runBlocking {
    trustWeave {
        factories(
            kmsFactory = TestkitKmsFactory(),
            didMethodFactory = TestkitDidMethodFactory()
        )
        keys {
            provider(IN_MEMORY)
            algorithm(ED25519)
        }
        did {
        method(KEY) {
            algorithm(ED25519)
            }
        }
    }.run {
        // Create a DID (returns DID and document directly)
        val (issuerDid, issuerDoc) = createDid().getOrThrow()
        
        // Extract key ID from document (no need to resolve separately)
        val issuerKeyId = issuerDoc.verificationMethod.first().id.substringAfter("#")

        println("Created DID: ${issuerDid.value}")
        println("Key ID: $issuerKeyId")
    }
}
```

**Expected Output:**
```
Created DID: did:key:z6MkhaXgBZDvotDkL5257faiztiGiC2QtKLGpbnnEGta2doK
Key ID: key-1
```

**Note:** `createDid()` returns a type-safe `Did` object. Access the string value using `.value` property.

## Step-by-Step Guide

### Step 1: Configure TrustWeave

First, create a `TrustWeave` instance with DID method support:

```kotlin
import com.trustweave.trust.dsl.trustWeave
import com.trustweave.trust.dsl.credential.*
import com.trustweave.testkit.services.*

val trustWeave = trustWeave {
    factories(
        kmsFactory = TestkitKmsFactory(),
        didMethodFactory = TestkitDidMethodFactory()
    )
    keys {
        provider(IN_MEMORY)  // For testing; use production KMS in production
        algorithm(ED25519)
    }
    did {
        method(KEY) {  // Register did:key method
            algorithm(ED25519)
        }
        // Add more methods as needed
        method(WEB) {
            domain("example.com")
        }
    }
}
```

### Step 2: Create a DID

Create a DID using the default method (did:key) or specify a method:

```kotlin
// Simple: Use defaults (did:key, ED25519)
val did = trustWeave.createDid {
    method(KEY)
    algorithm(ED25519)
}

// Or even simpler with default method
val didSimple = trustWeave.createDid()  // Uses "key" method by default

// With custom method
val webDid = trustWeave.createDid {
    method(WEB)
    domain("example.com")
}
```

### Step 3: Extract Key ID

Extract the key ID from the DID for signing operations:

```kotlin
val did = trustWeave.createDid { method(KEY) }

// Resolve DID to get verification method
val resolutionResult = trustWeave.resolveDid(did)
val document = when (resolutionResult) {
    is DidResolutionResult.Success -> resolutionResult.document
    else -> throw IllegalStateException("Failed to resolve DID")
}

// Extract key ID from verification method
val verificationMethod = document.verificationMethod.firstOrNull()
    ?: throw IllegalStateException("No verification method found")
val keyId = verificationMethod.id.substringAfter("#")  // e.g., "key-1"
```

### Step 4: Use the DID

Use the DID and key ID in credential operations:

```kotlin
val credential = trustWeave.issue {
    credential {
        issuer(did.value)
        // ... credential configuration
    }
    signedBy(issuerDid = did.value, keyId = keyId)  // keyId is already a String
}
```

## Common Patterns

### Pattern 1: Create Multiple DIDs

Create DIDs for different roles (issuer, holder, verifier):

```kotlin
val issuerDid = trustWeave.createDid { method(KEY) }
val holderDid = trustWeave.createDid { method(KEY) }
val verifierDid = trustWeave.createDid { method(KEY) }

println("Issuer: $issuerDid")
println("Holder: $holderDid")
println("Verifier: $verifierDid")
```

### Pattern 2: Create DID with Error Handling

Handle errors gracefully:

```kotlin
val did = try {
    trustWeave.createDid {
        method(KEY)
        algorithm(ED25519)
    }
} catch (error: TrustWeaveException) {
    when (error) {
        is TrustWeaveException.PluginNotFound -> {
            println("DID method not registered: ${error.pluginId}")
            return@runBlocking
        }
        else -> {
            println("Unexpected error: ${error.message}")
            return@runBlocking
        }
    }
}
```

### Pattern 3: Resolve a DID

Resolve a DID to get its document:

```kotlin
import com.trustweave.did.identifiers.Did

val did = Did("did:key:z6Mk...")
val result = trustWeave.resolveDid(did)

when (result) {
    is DidResolutionResult.Success -> {
        println("DID resolved: ${result.document.id}")
        println("Verification methods: ${result.document.verificationMethod.size}")
    }
    is DidResolutionResult.Failure.NotFound -> {
        println("DID not found: ${result.did.value}")
    }
    is DidResolutionResult.Failure.InvalidFormat -> {
        println("Invalid DID format: ${result.reason}")
    }
    is DidResolutionResult.Failure.MethodNotRegistered -> {
        println("DID method not registered: ${result.method}")
    }
    else -> {
        println("Resolution failed: ${result}")
    }
}
```

### Pattern 4: Update a DID Document

Update a DID document to add services or verification methods:

```kotlin
import com.trustweave.did.identifiers.Did

val did = Did("did:key:example")
val updated = trustWeave.updateDid {
    did(did.value)  // DSL builder accepts string for convenience
    addService {
        id("${did.value}#service-1")
        type("LinkedDomains")
        endpoint("https://example.com/service")
    }
}
```

### Pattern 5: Deactivate a DID

Deactivate a DID when it's no longer needed:

```kotlin
import com.trustweave.did.identifiers.Did

// Note: Deactivation depends on the DID method implementation
// For did:key, deactivation is typically not supported as it's stateless
// For other methods like did:web or did:ion, use the method-specific deactivation API

val did = Did("did:key:example")
val deactivated = trustWeave.deactivateDid(did)
if (deactivated) {
    println("DID deactivated successfully")
}
```

## DID Methods

TrustWeave supports multiple DID methods. Choose based on your needs:

| Method | Use Case | Network Required |
|--------|----------|------------------|
| `did:key` | Testing, simple use cases | No |
| `did:web` | Web-based identity | Yes (HTTPS) |
| `did:ion` | Microsoft ION network | Yes |
| `did:ethr` | Ethereum-based identity | Yes (Ethereum) |
| `did:polygon` | Polygon-based identity | Yes (Polygon) |

See [DID Method Integrations](../how-to/README.md#did-method-integrations) for complete list.

## Error Handling

DID operations now return sealed result types instead of throwing exceptions. This provides type-safe, exhaustive error handling:

```kotlin
import com.trustweave.trust.types.DidCreationResult

val didResult = trustWeave.createDid { 
    method(KEY) 
}

when (didResult) {
    is DidCreationResult.Success -> {
        println("Created DID: ${didResult.did.value}")
        // Use didResult.did and didResult.document
    }
    is DidCreationResult.Failure.MethodNotRegistered -> {
        println("DID method not found: ${didResult.method}")
        println("Available methods: ${didResult.availableMethods.joinToString()}")
    }
    is DidCreationResult.Failure.KeyGenerationFailed -> {
        println("Key generation failed: ${didResult.reason}")
        didResult.cause?.printStackTrace()
    }
    is DidCreationResult.Failure.DocumentCreationFailed -> {
        println("Document creation failed: ${didResult.reason}")
    }
    is DidCreationResult.Failure.InvalidConfiguration -> {
        println("Invalid configuration: ${didResult.reason}")
    }
    is DidCreationResult.Failure.Other -> {
        println("Error: ${didResult.reason}")
        didResult.cause?.printStackTrace()
    }
}
```

**For tests and examples**, you can use the `getOrFail()` helper:

```kotlin
import com.trustweave.testkit.getOrFail

val did = trustWeave.createDid { method(KEY) }.getOrFail()
// Throws AssertionError on failure (suitable for tests/examples only)
```

**Note:** All I/O operations (`createDid`, `issue`, `updateDid`, `rotateKey`, `wallet`, `revoke`) now return sealed result types for exhaustive error handling. `resolveDid()` also returns a sealed result type.

## API Reference

For complete API documentation, see:
- **[Core API - createDid()](../api-reference/core-api.md#create)** - Complete parameter reference
- **[Core API - resolveDid()](../api-reference/core-api.md#resolve)** - Resolution details
- **[Core API - updateDid()](../api-reference/core-api.md#update)** - Update operations
- **[Core API - deactivateDid()](../api-reference/core-api.md#deactivate)** - Deactivation

## Related Concepts

- **[DIDs](../concepts/README.md#core-concepts)** - Understanding what DIDs are
- **[Key Management](../core-concepts/key-management.md)** - How keys are managed for DIDs
- **[DID Methods](../integrations/README.md#did-method-integrations)** - Available DID method implementations

## See Also

- **[DIDs Concept](../core-concepts/dids.md)** - Understanding what DIDs are and why they exist
- **[Key Management](../core-concepts/key-management.md)** - How keys are managed for DIDs
- **[DID Operations Tutorial](../tutorials/did-operations-tutorial.md)** - Comprehensive tutorial with examples
- **[DID Method Integrations](../integrations/README.md#did-method-integrations)** - Implementation guides for all DID methods

## Related How-To Guides

- **[Issue Credentials](issue-credentials.md)** - Use DIDs to issue credentials
- **[Manage Wallets](manage-wallets.md)** - Create DIDs in wallets

## Next Steps

**Ready to issue credentials?**
- [Issue Credentials](issue-credentials.md) - Use your DID to issue credentials

**Want to learn more?**
- [DIDs Concept](../core-concepts/dids.md) - Deep dive into DIDs
- [DID Operations Tutorial](../tutorials/did-operations-tutorial.md) - Comprehensive tutorial

