# API Patterns and Best Practices

This guide explains the correct API patterns to use with TrustWeave and clarifies common misconceptions.

## Primary API: TrustLayer

**TrustLayer is the main entry point** for all TrustWeave operations. Always use `TrustLayer` for your application code.

### Creating a TrustLayer Instance

```kotlin
import com.trustweave.trust.TrustLayer
import kotlinx.coroutines.runBlocking

fun main() = runBlocking {
    val trustLayer = TrustLayer.build {
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
    
    // Use trustLayer for all operations
    val did = trustLayer.createDid { method("key") }
}
```

## Correct API Patterns

### DID Operations

✅ **Correct:**
```kotlin
val trustLayer = TrustLayer.build { ... }

// Create DID
val did = trustLayer.createDid {
    method("key")
    algorithm("Ed25519")
}

// Update DID
val updated = trustLayer.updateDid {
    did("did:key:example")
    addService { ... }
}

// Delegate
val delegation = trustLayer.delegate {
    from("did:key:issuer")
    to("did:key:delegate")
}

// Rotate key
val rotated = trustLayer.rotateKey {
    did("did:key:example")
    oldKeyId("did:key:example#key-1")
    newKeyId("did:key:example#key-2")
}
```

❌ **Incorrect (Old API - Do Not Use):**
```kotlin
// These patterns are from older documentation and should not be used
val did = TrustWeave.dids.create()
val resolution = TrustWeave.dids.resolve(did)
```

### Credential Operations

✅ **Correct:**
```kotlin
val trustLayer = TrustLayer.build { ... }

// Issue credential
val credential = trustLayer.issue {
    credential {
        type("VerifiableCredential", "PersonCredential")
        issuer(issuerDid)
        subject {
            id(holderDid)
            claim("name", "Alice")
        }
    }
    by(issuerDid = issuerDid, keyId = "$issuerDid#key-1")
}

// Verify credential
val verification = trustLayer.verify {
    credential(credential)
    checkExpiration(true)
    checkRevocation(true)
    checkTrust(true)
}
```

❌ **Incorrect (Old API - Do Not Use):**
```kotlin
// These patterns are from older documentation
val credential = TrustWeave.credentials.issue(...)
val verification = TrustWeave.credentials.verify(...)
```

### Wallet Operations

✅ **Correct:**
```kotlin
val trustLayer = TrustLayer.build { ... }

// Create wallet
val wallet = trustLayer.wallet {
    holder(holderDid)
    enableOrganization()
    enablePresentation()
}

// Use wallet
val credentialId = wallet.store(credential)
val retrieved = wallet.get(credentialId)
val allCredentials = wallet.list()
```

### Trust Operations

✅ **Correct:**
```kotlin
val trustLayer = TrustLayer.build {
    trust { provider("inMemory") }
}

// Using DSL
trustLayer.trust {
    addAnchor("did:key:university") {
        credentialTypes("EducationCredential")
        description("Trusted university")
    }
    
    val isTrusted = isTrusted("did:key:university", "EducationCredential")
}

// Using direct methods
trustLayer.addTrustAnchor("did:key:university") {
    credentialTypes("EducationCredential")
}
val isTrusted = trustLayer.isTrustedIssuer("did:key:university", "EducationCredential")
```

## Error Handling Patterns

### Exception-Based (TrustLayer Methods)

All `TrustLayer` methods throw `TrustWeaveError` exceptions. Always use try-catch:

```kotlin
import com.trustweave.core.TrustWeaveError

try {
    val did = trustLayer.createDid { method("key") }
    val credential = trustLayer.issue { ... }
} catch (error: TrustWeaveError) {
    when (error) {
        is TrustWeaveError.DidMethodNotRegistered -> {
            println("Method not registered: ${error.method}")
            println("Available methods: ${error.availableMethods}")
        }
        is TrustWeaveError.CredentialInvalid -> {
            println("Credential invalid: ${error.reason}")
        }
        else -> {
            println("Error: ${error.message}")
        }
    }
}
```

### Result-Based (Lower-Level APIs)

Some lower-level service APIs return `Result<T>`. Use `fold()`:

```kotlin
val result = someService.operation()
result.fold(
    onSuccess = { value ->
        // Handle success
        println("Success: $value")
    },
    onFailure = { error ->
        // Handle error
        when (error) {
            is TrustWeaveError.ValidationFailed -> {
                println("Validation failed: ${error.reason}")
            }
            else -> {
                println("Error: ${error.message}")
            }
        }
    }
)
```

## Common Mistakes to Avoid

### ❌ Mistake 1: Using Old API Patterns

**Wrong:**
```kotlin
val did = TrustWeave.dids.create()
```

**Correct:**
```kotlin
val trustLayer = TrustLayer.build { ... }
val did = trustLayer.createDid { method("key") }
```

### ❌ Mistake 2: Ignoring Errors

**Wrong:**
```kotlin
val did = trustLayer.createDid { method("key") }
// What if this throws? Application crashes!
```

**Correct:**
```kotlin
try {
    val did = trustLayer.createDid { method("key") }
    // Use did
} catch (error: TrustWeaveError) {
    // Handle error appropriately
    logger.error("Failed to create DID", error)
}
```

### ❌ Mistake 3: Not Configuring Required Components

**Wrong:**
```kotlin
val trustLayer = TrustLayer.build {
    // Missing KMS configuration!
}
val did = trustLayer.createDid { method("key") }
// Fails: No KMS provider configured
```

**Correct:**
```kotlin
val trustLayer = TrustLayer.build {
    keys {
        provider("inMemory")
        algorithm("Ed25519")
    }
    did {
        method("key") { algorithm("Ed25519") }
    }
}
```

### ❌ Mistake 4: Using Wrong Key ID Format

**Wrong:**
```kotlin
val credential = trustLayer.issue {
    credential { ... }
    by(issuerDid = issuerDid, keyId = "key-1")  // Missing DID prefix!
}
```

**Correct:**
```kotlin
val issuerKeyId = "$issuerDid#key-1"
val credential = trustLayer.issue {
    credential { ... }
    by(issuerDid = issuerDid, keyId = issuerKeyId)
}
```

## Migration from Old API

If you're using older documentation or examples that reference `TrustWeave.dids.create()`, here's how to migrate:

### Old Pattern
```kotlin
val trustweave = TrustWeave.create()
val did = trustweave.dids.create()
val credential = trustweave.credentials.issue(...)
```

### New Pattern
```kotlin
val trustLayer = TrustLayer.build {
    keys { provider("inMemory"); algorithm("Ed25519") }
    did { method("key") { algorithm("Ed25519") } }
}

val did = trustLayer.createDid { method("key") }
val credential = trustLayer.issue {
    credential { ... }
    by(issuerDid = issuerDid, keyId = "$issuerDid#key-1")
}
```

## Best Practices

### 1. Always Configure Explicitly

Don't rely on defaults in production. Explicitly configure all components:

```kotlin
val trustLayer = TrustLayer.build {
    keys {
        provider("awsKms")  // Production KMS
        algorithm("Ed25519")
    }
    did {
        method("key") { algorithm("Ed25519") }
        method("web") { domain("example.com") }
    }
    anchor {
        chain("algorand:mainnet") { provider("algorand") }
    }
    trust {
        provider("database")  // Production trust registry
    }
}
```

### 2. Handle Errors Explicitly

Always wrap `TrustLayer` operations in try-catch:

```kotlin
try {
    val result = trustLayer.operation { ... }
    // Process result
} catch (error: TrustWeaveError) {
    // Log and handle error
    logger.error("Operation failed", error)
    // Return error response or retry
}
```

### 3. Use Type-Safe Builders

Leverage the DSL for type safety:

```kotlin
// ✅ Good: Type-safe, IDE autocomplete
val credential = trustLayer.issue {
    credential {
        type("VerifiableCredential", "PersonCredential")
        issuer(issuerDid)
        subject {
            id(holderDid)
            claim("name", "Alice")
        }
    }
    by(issuerDid = issuerDid, keyId = "$issuerDid#key-1")
}
```

### 4. Reuse TrustLayer Instance

Create one `TrustLayer` instance and reuse it:

```kotlin
// ✅ Good: Create once, reuse
val trustLayer = TrustLayer.build { ... }

fun createUserDid() = trustLayer.createDid { method("key") }
fun issueCredential(...) = trustLayer.issue { ... }
fun verifyCredential(...) = trustLayer.verify { ... }
```

## Related Documentation

- [Mental Model](../introduction/mental-model.md) - Understanding TrustWeave architecture
- [Quick Start](quick-start.md) - Getting started guide
- [API Reference](../api-reference/core-api.md) - Complete API documentation
- [Error Handling](../advanced/error-handling.md) - Detailed error handling guide

