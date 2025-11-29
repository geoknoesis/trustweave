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
import com.trustweave.trust.TrustWeave
import com.trustweave.trust.types.Did
import com.trustweave.core.TrustWeaveError
import kotlinx.coroutines.runBlocking

fun main() = runBlocking {
    try {
        // Create TrustWeave instance
        val trustWeave = TrustWeave.build {
            keys {
                provider("inMemory")
                algorithm("Ed25519")
            }
            did {
                method("key") {
                    algorithm("Ed25519")
                }
            }
        }

        // Create a DID (returns type-safe Did)
        val issuerDid: Did = trustWeave.createDid {
            method("key")
            algorithm("Ed25519")
        }

        // Extract key ID for signing
        // Note: When using keyHandle.id, it's already a KeyId value class
        // For string-based key IDs, construct using KeyId("...")
        val issuerKeyId = KeyId("${issuerDid.value}#key-1")

        println("Created DID: ${issuerDid.value}")
        println("Key ID: $issuerKeyId")
    } catch (error: TrustWeaveError) {
        when (error) {
            is TrustWeaveError.DidMethodNotRegistered -> {
                println("❌ DID method not registered: ${error.method}")
                println("Available methods: ${error.availableMethods}")
            }
            else -> {
                println("❌ Error: ${error.message}")
            }
        }
    }
}
```

**Expected Output:**
```
Created DID: did:key:z6MkhaXgBZDvotDkL5257faiztiGiC2QtKLGpbnnEGta2doK
Key ID: did:key:z6MkhaXgBZDvotDkL5257faiztiGiC2QtKLGpbnnEGta2doK#key-1
```

**Note:** `createDid()` returns a type-safe `Did` object. Access the string value using `.value` property.

## Step-by-Step Guide

### Step 1: Configure TrustWeave

First, create a `TrustWeave` instance with DID method support:

```kotlin
val trustWeave = TrustWeave.build {
    keys {
        provider("inMemory")  // For testing; use production KMS in production
        algorithm("Ed25519")
    }
    did {
        method("key") {  // Register did:key method
            algorithm("Ed25519")
        }
        // Add more methods as needed
        method("web") {
            domain("example.com")
        }
    }
}
```

### Step 2: Create a DID

Create a DID using the default method (did:key) or specify a method:

```kotlin
// Simple: Use defaults (did:key, ED25519)
val did = trustLayer.createDid {
    method("key")
    algorithm("Ed25519")
}

// With custom method
val webDid = trustLayer.createDid {
    method("web")
    domain("example.com")
}
```

### Step 3: Extract Key ID

Extract the key ID from the DID for signing operations:

```kotlin
val did = trustLayer.createDid { method("key") }
val keyId = KeyId("${did.value}#key-1")  // Standard key ID format using KeyId value class
```

### Step 4: Use the DID

Use the DID and key ID in credential operations:

```kotlin
val credential = trustLayer.issue {
    credential {
        issuer(did.value)
        // ... credential configuration
    }
    by(issuerDid = did.value, keyId = keyId.value)
}
```

## Common Patterns

### Pattern 1: Create Multiple DIDs

Create DIDs for different roles (issuer, holder, verifier):

```kotlin
val issuerDid = trustLayer.createDid { method("key") }
val holderDid = trustLayer.createDid { method("key") }
val verifierDid = trustLayer.createDid { method("key") }

println("Issuer: $issuerDid")
println("Holder: $holderDid")
println("Verifier: $verifierDid")
```

### Pattern 2: Create DID with Error Handling

Handle errors gracefully:

```kotlin
val did = try {
        trustWeave.createDid {
        method("key")
        algorithm("Ed25519")
    }
} catch (error: TrustWeaveError) {
    when (error) {
        is TrustWeaveError.DidMethodNotRegistered -> {
            println("Method not registered: ${error.method}")
            println("Available: ${error.availableMethods}")
            return@runBlocking
        }
        is TrustWeaveError.ValidationFailed -> {
            println("Validation failed: ${error.reason}")
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
val context = trustLayer.getDslContext()
val resolver = context.getDidResolver()
val result = resolver?.resolve("did:key:z6Mk...")

if (result?.document != null) {
    println("DID resolved: ${result.document.id}")
    println("Verification methods: ${result.document.verificationMethod.size}")
} else {
    println("DID not found: ${result?.metadata?.error}")
}
```

### Pattern 4: Update a DID Document

Update a DID document to add services or verification methods:

```kotlin
val updated = trustLayer.updateDid {
    did("did:key:example")
    addService {
        id("${did}#service-1")
        type("LinkedDomains")
        endpoint("https://example.com/service")
    }
}
```

### Pattern 5: Deactivate a DID

Deactivate a DID when it's no longer needed:

```kotlin
val context = trustLayer.getDslContext()
val deactivated = context.deactivateDid("did:key:example")

if (deactivated) {
    println("DID deactivated successfully")
} else {
    println("DID deactivation failed or DID already deactivated")
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

All DID operations throw `TrustWeaveError` exceptions on failure. Always wrap in try-catch:

```kotlin
try {
    val did = trustLayer.createDid { method("key") }
    // Use DID
} catch (error: TrustWeaveError) {
    when (error) {
        is TrustWeaveError.DidMethodNotRegistered -> {
            // Method not available
        }
        is TrustWeaveError.ValidationFailed -> {
            // Invalid configuration
        }
        is TrustWeaveError.InvalidOperation -> {
            // KMS or other operation failed
        }
        else -> {
            // Other error
        }
    }
}
```

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

