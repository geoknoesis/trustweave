---
title: API Patterns and Best Practices
nav_order: 8
parent: Getting Started
---

# API Patterns and Best Practices

This guide explains the correct API patterns to use with TrustWeave and clarifies common misconceptions.

## Primary API: TrustWeave

**TrustWeave is the main entry point** for all TrustWeave operations. Always use `TrustWeave` for your application code.

### Creating a TrustWeave Instance

```kotlin
import org.trustweave.trust.TrustWeave
import org.trustweave.testkit.services.*
import kotlinx.coroutines.runBlocking

fun main() = runBlocking {
    val trustWeave = TrustWeave.build {
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
    }

    // Use trustWeave for all operations
    val didResult = trustWeave.createDid { method(KEY) }
}
```

## Correct API Patterns

### DID Operations

✅ **Correct:**
```kotlin
import org.trustweave.trust.types.DidCreationResult
import org.trustweave.trust.types.DidUpdateResult
import org.trustweave.trust.types.KeyRotationResult

val trustWeave = TrustWeave.build { ... }

// Create DID (returns sealed result)
val didResult = trustWeave.createDid {
    method(KEY)
    algorithm(ED25519)
}
val did = when (didResult) {
    is DidCreationResult.Success -> didResult.did
    else -> {
        println("Failed to create DID: ${didResult.reason}")
        return@runBlocking
    }
}

// Update DID (returns sealed result)
val updateResult = trustWeave.updateDid {
    did("did:key:example")
    addService { ... }
}
val updated = when (updateResult) {
    is DidUpdateResult.Success -> updateResult.document
    else -> {
        println("Failed to update DID: ${updateResult.reason}")
        return@runBlocking
    }
}

// Rotate key (returns sealed result)
val rotationResult = trustWeave.rotateKey {
    did("did:key:example")
    oldKeyId("did:key:example#key-1")
    newKeyId("did:key:example#key-2")
}
val rotated = when (rotationResult) {
    is KeyRotationResult.Success -> rotationResult.document
    else -> {
        println("Failed to rotate key: ${rotationResult.reason}")
        return@runBlocking
    }
}
```

❌ **Incorrect (Old API - Do Not Use):**
```kotlin
// These patterns are from older documentation and should not be used
val did = trustWeave.createDid { method(KEY) }
val resolution = trustWeave.resolveDid(did)
```

### Credential Operations

✅ **Correct:**
```kotlin
import org.trustweave.trust.types.IssuanceResult

val trustWeave = TrustWeave.build { ... }

// Issue credential (returns sealed result)
val issuanceResult = trustWeave.issue {
    credential {
        type(CredentialType.VerifiableCredential, CredentialType.Person)
        issuer(issuerDid)
        subject {
            id(holderDid)
            "name" to "Alice"
        }
    }
    signedBy(issuerDid = issuerDid, keyId = "$issuerDid#key-1")
}
val credential = when (issuanceResult) {
    is IssuanceResult.Success -> issuanceResult.credential
    else -> {
        println("Failed to issue credential: ${issuanceResult.reason}")
        return@runBlocking
    }
}

// Verify credential (returns VerificationResult, not sealed)
val verification = trustWeave.verify {
    credential(credential)
    checkExpiration(true)
    checkRevocation(true)
    checkTrust(true)
}
```

❌ **Incorrect (Old API - Do Not Use):**
```kotlin
// These patterns are from older documentation
val credential = trustWeave.issue { credential { ... }; signedBy(...) }
val verification = trustWeave.verify { credential(credential) }
```

### Wallet Operations

✅ **Correct:**
```kotlin
import org.trustweave.trust.types.WalletCreationResult

val trustWeave = TrustWeave.build { ... }

// Create wallet (returns sealed result)
val walletResult = trustWeave.wallet {
    holder(holderDid)
    enableOrganization()
    enablePresentation()
}
val wallet = when (walletResult) {
    is WalletCreationResult.Success -> walletResult.wallet
    else -> {
        println("Failed to create wallet: ${walletResult.reason}")
        return@runBlocking
    }
}

// Use wallet
val credentialId = wallet.store(credential)
val retrieved = wallet.get(credentialId)
val allCredentials = wallet.list()
```

### Trust Operations

✅ **Correct:**
```kotlin
val trustWeave = TrustLayer.build {
    trust { provider(IN_MEMORY) }
}

// Using DSL
trustWeave.trust {
    addAnchor("did:key:university") {
        credentialTypes("EducationCredential")
        description("Trusted university")
    }

    val isTrusted = isTrusted("did:key:university", "EducationCredential")
}

// Using direct methods
trustWeave.addTrustAnchor("did:key:university") {
    credentialTypes("EducationCredential")
}
val isTrusted = trustWeave.isTrustedIssuer("did:key:university", "EducationCredential")
```

## Error Handling Patterns

### Exception-Based (TrustWeave Methods)

All `TrustWeave` methods throw exceptions. Always use try-catch for error handling:

```kotlin
import org.trustweave.core.TrustWeaveError

try {
    val didResult = trustWeave.createDid { method(KEY) }
    val did = when (didResult) {
        is DidCreationResult.Success -> didResult.did
        is DidCreationResult.Failure.MethodNotRegistered -> {
            println("Method not registered: ${didResult.method}")
            println("Available methods: ${didResult.availableMethods.joinToString()}")
            return@runBlocking
        }
        else -> {
            println("Failed to create DID: ${didResult.reason}")
            return@runBlocking
        }
    }
    
    val issuanceResult = trustWeave.issue { ... }
    val credential = when (issuanceResult) {
        is IssuanceResult.Success -> issuanceResult.credential
        else -> {
            println("Failed to issue credential: ${issuanceResult.reason}")
            return@runBlocking
        }
    }
} catch (error: TrustWeaveException) {
    when (error) {
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
val trustWeave = TrustWeave.build { ... }
val did = trustWeave.createDid { method(KEY) }
```

### ❌ Mistake 2: Ignoring Errors

**Wrong:**
```kotlin
val did = trustWeave.createDid { method(KEY) }
// This returns DidCreationResult, not Did - need to unwrap!
```

**Correct:**
```kotlin
val didResult = trustWeave.createDid { method(KEY) }
val did = when (didResult) {
    is DidCreationResult.Success -> didResult.did
    else -> {
        logger.error("Failed to create DID: ${didResult.reason}")
        return@runBlocking // or handle appropriately
    }
}
// Use did
```

### ❌ Mistake 3: Not Configuring Required Components

**Wrong:**
```kotlin
val trustWeave = TrustLayer.build {
    // Missing KMS configuration!
}
val did = trustWeave.createDid { method(KEY) }
// Fails: No KMS provider configured
```

**Correct:**
```kotlin
val trustWeave = TrustLayer.build {
    keys {
        provider(IN_MEMORY)
        algorithm(ED25519)
    }
    did {
        method(KEY) { algorithm(ED25519) }
    }
}
```

### ❌ Mistake 4: Using Wrong Key ID Format

**Wrong:**
```kotlin
val credential = trustWeave.issue {
    credential { ... }
    signedBy(issuerDid = issuerDid, keyId = "key-1")  // Missing DID prefix!
}
```

**Correct:**
```kotlin
val issuerKeyId = "$issuerDid#key-1"
val credential = trustWeave.issue {
    credential { ... }
    signedBy(issuerDid = issuerDid, keyId = issuerKeyId)
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
val trustWeave = TrustLayer.build {
    keys { provider(IN_MEMORY); algorithm(ED25519) }
    did { method(KEY) { algorithm(ED25519) } }
}

val did = trustWeave.createDid { method(KEY) }
val credential = trustWeave.issue {
    credential { ... }
    signedBy(issuerDid = issuerDid, keyId = "$issuerDid#key-1")
}
```

## Best Practices

### 1. Always Configure Explicitly

Don't rely on defaults in production. Explicitly configure all components:

```kotlin
val trustWeave = TrustLayer.build {
    keys {
        provider(AWS)  // Production KMS
        algorithm(ED25519)
    }
    did {
        method(KEY) { algorithm(ED25519) }
        method(WEB) { domain("example.com") }
    }
    anchor {
        chain("algorand:mainnet") { provider(ALGORAND) }
    }
    trust {
        provider("database")  // Production trust registry
    }
}
```

### 2. Handle Errors Explicitly

Always wrap `TrustWeave` operations in try-catch:

```kotlin
try {
    val result = trustWeave.operation { ... }
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
val credential = trustWeave.issue {
    credential {
        type(CredentialType.VerifiableCredential, CredentialType.Person)
        issuer(issuerDid)
        subject {
            id(holderDid)
            "name" to "Alice"
        }
    }
    signedBy(issuerDid = issuerDid, keyId = "$issuerDid#key-1")
}
```

### 4. Reuse TrustWeave Instance

Create one `TrustWeave` instance and reuse it:

```kotlin
// ✅ Good: Create once, reuse
val trustWeave = TrustWeave.build { ... }

fun createUserDid() = trustWeave.createDid { method(KEY) }
fun issueCredential(...) = trustWeave.issue { ... }
fun verifyCredential(...) = trustWeave.verify { ... }
```

## Related Documentation

- [Mental Model](../introduction/mental-model.md) - Understanding TrustWeave architecture
- [Quick Start](quick-start.md) - Getting started guide
- [API Reference](../api-reference/core-api.md) - Complete API documentation
- [Error Handling](../advanced/error-handling.md) - Detailed error handling guide

