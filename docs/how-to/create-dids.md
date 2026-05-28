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
import org.trustweave.trust.TrustWeave
import org.trustweave.trust.types.getOrThrow
import org.trustweave.trust.dsl.credential.DidMethods.KEY
import org.trustweave.trust.dsl.credential.KeyAlgorithms.ED25519
import org.trustweave.trust.dsl.credential.KmsProviders.IN_MEMORY
import org.trustweave.did.identifiers.extractKeyId
import kotlinx.coroutines.runBlocking

fun main() = runBlocking {
    val trustWeave = TrustWeave.build {
        keys {
            provider(IN_MEMORY)  // Auto-discovered via SPI
            algorithm(ED25519)
        }
        did {
            method(KEY) {  // Auto-discovered via SPI
                algorithm(ED25519)
            }
        }
        // KMS, DID methods, and CredentialService all auto-created!
    }
    
    // Create a DID (returns DID and document directly)
    val (issuerDid, issuerDoc) = trustWeave.createDid().getOrThrow()
    
    // Extract key ID from document using type-safe extension function
    val issuerKeyId = issuerDoc.verificationMethod.firstOrNull()?.extractKeyId()
        ?: throw IllegalStateException("No verification method found")

    println("Created DID: ${issuerDid.value}")
    println("Key ID: $issuerKeyId")
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
import org.trustweave.trust.TrustWeave
import org.trustweave.trust.types.getOrThrow
import org.trustweave.trust.dsl.credential.DidMethods.KEY
import org.trustweave.trust.dsl.credential.DidMethods.WEB
import org.trustweave.trust.dsl.credential.KeyAlgorithms.ED25519
import org.trustweave.trust.dsl.credential.KmsProviders.IN_MEMORY

val trustWeave = TrustWeave.build {
    keys {
        provider(IN_MEMORY)  // Auto-discovered via SPI (for testing; use production KMS in production)
        algorithm(ED25519)
    }
    did {
        method(KEY) {  // Auto-discovered via SPI
            algorithm(ED25519)
        }
        // Add more methods as needed
        method(WEB) {  // Auto-discovered via SPI
            domain("example.com")
        }
    }
    // KMS, DID methods, and CredentialService all auto-created!
}
```

### Step 2: Create a DID

Create a DID using the default method (did:key) or specify a method:

```kotlin
import org.trustweave.trust.dsl.credential.DidMethods.KEY
import org.trustweave.trust.dsl.credential.DidMethods.WEB
import org.trustweave.trust.dsl.credential.KeyAlgorithms.ED25519
// Simple: Use defaults (did:key, ED25519)
val did = trustWeave.createDid {
    method(KEY)
    algorithm(ED25519)
}

// Or even simpler with default method
val didSimple = trustWeave.createDid()  // Uses "key" method by default

// With custom method — pass method-specific options via option(key, value)
val webDid = trustWeave.createDid {
    method(WEB)
    option("domain", "example.com")
}
```

### Step 3: Extract Key ID

Extract the key ID from the DID for signing operations:

```kotlin
import org.trustweave.did.identifiers.extractKeyId
import org.trustweave.trust.dsl.credential.DidMethods.KEY
import org.trustweave.trust.dsl.credential.KeyAlgorithms.ED25519
import org.trustweave.did.resolver.DidResolutionResult
import org.trustweave.did.resolver.errorMessage
import org.trustweave.trust.types.getOrThrowDid

val did = trustWeave.createDid { method(KEY); algorithm(ED25519) }.getOrThrowDid()

// Resolve DID to get verification method

val document = when (val res = trustWeave.resolveDid(did)) {
    is DidResolutionResult.Success -> res.document
    else -> throw IllegalStateException(res.errorMessage ?: "Failed to resolve DID")
}

// Extract key ID from verification method using type-safe extension function
val keyId = document.verificationMethod.firstOrNull()?.extractKeyId()
    ?: throw IllegalStateException("No verification method found")  // e.g., "key-1"
```

### Step 4: Use the DID

Use the DID and key ID in credential operations:

```kotlin
val credential = trustWeave.issue {
    credential {
        issuer(did)
        // ... credential configuration
    }
    signedBy(did)  // keyId is automatically resolved
}
```

## Common Patterns

### Pattern 1: Create Multiple DIDs

Create DIDs for different roles (issuer, holder, verifier):

```kotlin
import org.trustweave.trust.dsl.credential.DidMethods.KEY
import org.trustweave.trust.types.getOrThrowDid
val issuerDid = trustWeave.createDid { method(KEY) }.getOrThrowDid()
val holderDid = trustWeave.createDid { method(KEY) }.getOrThrowDid()
val verifierDid = trustWeave.createDid { method(KEY) }.getOrThrowDid()

println("Issuer: ${issuerDid.value}")
println("Holder: ${holderDid.value}")
println("Verifier: ${verifierDid.value}")
```

### Pattern 2: Create DID with Error Handling

Handle errors gracefully:

```kotlin
import org.trustweave.trust.dsl.credential.DidMethods.KEY
import org.trustweave.trust.dsl.credential.KeyAlgorithms.ED25519
import org.trustweave.core.exception.TrustWeaveException
import org.trustweave.core.exception.PluginException

// createDid returns DidCreationResult (no exception is thrown for normal failure cases);
// only configuration errors propagate as TrustWeaveException.
val didResult = try {
    trustWeave.createDid {
        method(KEY)
        algorithm(ED25519)
    }
} catch (error: PluginException.NotFound) {
    println("Plugin not registered: ${error.pluginId} (type=${error.pluginType ?: "n/a"})")
    return@runBlocking
} catch (error: TrustWeaveException) {
    println("Unexpected TrustWeave error: ${error.message}")
    return@runBlocking
}
```

### Pattern 3: Resolve a DID

Resolve a DID to get its document:

```kotlin
import org.trustweave.did.identifiers.Did

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
import org.trustweave.did.identifiers.Did

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

There is no `trustWeave.deactivateDid(...)` facade method. Deactivation is
method-specific: resolve the `DidMethod` plugin from the registry and call its
`deactivateDid(did: Did): Boolean` directly. `did:key` is stateless and cannot be
deactivated (the underlying `DidMethod.deactivateDid` returns `false`).

```kotlin
import org.trustweave.did.DidMethod
import org.trustweave.did.identifiers.Did

val did    = Did("did:web:example.com:users:alice")
val method = trustWeave.getDidRegistry().get("web") as? DidMethod
    ?: error("did:web method not registered")

val deactivated: Boolean = method.deactivateDid(did)
println(if (deactivated) "Deactivated $did" else "Could not deactivate $did")
```

> The `DidMethod` SPI takes `Did` only — there is no `DeactivateDidOptions` parameter
> on `DidMethod.deactivateDid`. Remote-registrar deactivation with options goes through
> `org.trustweave.did.DidRegistrar.deactivateDid(...)` (see `did/registrar`) instead.

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
import org.trustweave.trust.types.DidCreationResult
import org.trustweave.trust.dsl.credential.DidMethods.KEY

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

**For tests and examples**, you can use `getOrThrowDid()`:

```kotlin
import org.trustweave.trust.types.getOrThrowDid
import org.trustweave.trust.dsl.credential.DidMethods.KEY

val did = trustWeave.createDid { method(KEY) }.getOrThrowDid()
// Throws IllegalStateException on failure (suitable for tests/examples only)
```

**`createDidWithKey`** returns [`DidCreationWithKeyResult`](../../trust/src/main/kotlin/org/trustweave/trust/types/DidResult.kt) (not `kotlin.Result`). For a quick `(Did, keyId)` pair in examples/tests, use `import org.trustweave.trust.types.getOrThrow` and `.getOrThrow()`.

**Note:** All I/O operations (`createDid`, `issue`, `updateDid`, `rotateKey`, `wallet`, `revoke`) now return sealed result types for exhaustive error handling. `resolveDid()` also returns a sealed result type.

## API Reference

For complete API documentation, see:
- **[Core API - createDid()](../api-reference/core-api.md#create)** - Complete parameter reference
- **[Core API - createDidWithKey()](../api-reference/core-api.md#createdidwithkey)** - DID + first key id
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
- Issue Credentials](issue-credentials.md) - Use your DID to issue credentials

**Want to learn more?**
- DIDs Concept](../core-concepts/dids.md) - Deep dive into DIDs
- DID Operations Tutorial](../tutorials/did-operations-tutorial.md) - Comprehensive tutorial

