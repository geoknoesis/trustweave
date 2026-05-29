---
title: API Patterns and Best Practices
nav_order: 80
parent: Getting Started
redirect_from:
  - /getting-started/api-patterns/
  - /tutorials/getting-started/api-patterns/
---

# API Patterns and Best Practices

This guide explains the correct API patterns to use with TrustWeave and clarifies common misconceptions.

## API contract: results vs exceptions

Use **sealed results** for credential flows so production code can handle failures without catching generic exceptions.

| Operation | Return type | Notes |
|-----------|-------------|--------|
| `createDid` / `createDidWithKey` | `DidCreationResult` / `DidCreationWithKeyResult` | Use `when` or `getOrThrowDid()` / `DidCreationWithKeyResult.getOrThrow()` |
| `issue` | `IssuanceResult` | `AdapterNotReady` if `CredentialService` is not configured |
| `verify` / `verify(credential)` | `VerificationResult` | `Invalid.AdapterNotReady` if not configured; **`verify { }` without service** carries an internal placeholder VC in that result—**do not** treat it as real holder data (handle `AdapterNotReady` first) |
| `presentationResult` / `presentationFromWalletResult` | `PresentationResult` | `Failure.AdapterNotReady` if not configured; `Failure.InvalidRequest` for missing holder / no credentials |
| `issueBatch` / `verifyBatch` | `Flow` of `IssuanceResult` / `VerificationResult` | Each element is independent; misconfiguration yields `AdapterNotReady` per item |

**Unwrapping:** Most `getOrThrow()` extensions throw `IllegalStateException` with context; **`PresentationResult.getOrThrow()`** throws **`TrustWeaveException.InvalidState`**. Prefer `when` on sealed results in user-facing code.

See [Result types guide](../../api-reference/result-types-guide.md) and [Production integration checklist](production-integration-checklist.md).

### `getOrThrow()` imports

- **`org.trustweave.credential.results.getOrThrow`** unwraps **`IssuanceResult`** after **`issue { }`**.
- **`org.trustweave.trust.types.getOrThrow`** unwraps **`VerificationResult`**, **`WalletCreationResult`**, and other **trust-layer** results (richer error messages for verification).

You may import **both**; Kotlin resolves the correct extension by **receiver type**.

### Presentations and scenario samples

Product code should use **`PresentationRequest`** (`org.trustweave.credential.requests`) with **`CredentialService.createPresentation`** or the **`trustWeave.presentationResult { }`** DSL. Wallets that implement **`CredentialPresentation`** take **`ProofOptions`**. Some **scenario** snippets use a plain **`mapOf(...)`** only as a narrative stand-in for those options.

## Primary API: TrustWeave

**TrustWeave is the main entry point** for all TrustWeave operations. Always use `TrustWeave` for your application code.

### Creating a TrustWeave Instance

```kotlin
import org.trustweave.trust.TrustWeave
import org.trustweave.trust.dsl.credential.DidMethods.KEY
import org.trustweave.trust.dsl.credential.DidMethods.WEB
import org.trustweave.trust.dsl.credential.KeyAlgorithms.ED25519
import org.trustweave.trust.dsl.credential.KmsProviders.IN_MEMORY
import org.trustweave.trust.dsl.credential.KmsProviders.AWS
import org.trustweave.trust.dsl.credential.AnchorProviders.ALGORAND
import kotlinx.coroutines.runBlocking

fun main() = runBlocking {
    val trustWeave = TrustWeave.build {
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
import org.trustweave.trust.types.getOrThrowDid
import org.trustweave.did.resolver.DidResolutionResult
import org.trustweave.trust.dsl.credential.DidMethods.KEY
import org.trustweave.trust.dsl.credential.DidMethods.WEB
import org.trustweave.trust.dsl.credential.KeyAlgorithms.ED25519
import org.trustweave.trust.dsl.credential.KmsProviders.IN_MEMORY
import org.trustweave.trust.dsl.credential.KmsProviders.AWS
import org.trustweave.trust.dsl.credential.AnchorProviders.ALGORAND

val trustWeave = TrustWeave.build { ... }

// Create DID → DidCreationResult
val did = trustWeave.createDid {
    method(KEY)
    algorithm(ED25519)
}.getOrThrowDid()

// Update DID → DidResult (sealed)
val updated = trustWeave.updateDid {
    did("did:key:example")
    addService { /* ... */ }
}

// Rotate key → DidResult (sealed)
val rotated = trustWeave.rotateKey {
    did("did:key:example")
}

// Resolve → DidResolutionResult (sealed)
when (val resolution = trustWeave.resolveDid(did)) {
    is DidResolutionResult.Success -> { /* use resolution.document */ }
    is DidResolutionResult.Failure -> { /* handle */ }
}
```

❌ **Incorrect (treating results as success values):**
```kotlin
import org.trustweave.trust.dsl.credential.DidMethods.KEY
import org.trustweave.trust.dsl.credential.DidMethods.WEB
import org.trustweave.trust.dsl.credential.KeyAlgorithms.ED25519
import org.trustweave.trust.dsl.credential.KmsProviders.IN_MEMORY
import org.trustweave.trust.dsl.credential.KmsProviders.AWS
import org.trustweave.trust.dsl.credential.AnchorProviders.ALGORAND
// createDid returns DidCreationResult, not Did
val did = trustWeave.createDid { method(KEY) } // missing when / getOrThrowDid()
```

### Credential Operations

✅ **Correct:**
```kotlin
import org.trustweave.credential.results.IssuanceResult
import org.trustweave.credential.results.VerificationResult
import org.trustweave.credential.results.getOrThrow
import org.trustweave.trust.dsl.credential.presentationResult
import org.trustweave.trust.types.PresentationResult
import org.trustweave.trust.types.getOrThrowDid
import org.trustweave.did.identifiers.Did

val trustWeave = TrustWeave.build { ... }

// Issue credential (using compact pattern)
val issuerDid = trustWeave.createDid { }.getOrThrowDid()
val holderDid = Did("did:key:holder")

val credential = trustWeave.issue {
    credential {
        type(CredentialType.VerifiableCredential, CredentialType.Person)
        issuer(issuerDid)
        subject {
            id(holderDid)
            "name" to "Alice"
        }
    }
    signedBy(issuerDid = issuerDid, keyId = "${issuerDid.value}#key-1")
}.getOrThrow()

// Verify credential (sealed VerificationResult); optional issuer trust via TrustRegistry
val trustRegistry = requireNotNull(trustWeave.configuration.trustRegistry) {
    "Configure trust { provider(...) } before requireTrust"
}
val verification = trustWeave.verify {
    credential(credential)
    checkExpiration()
    checkRevocation()
    requireTrust(trustRegistry)
}

// Presentations (prefer result API)

val pres = when (val pr = trustWeave.presentationResult {
    credentials(credential)
    holder(holderDid)
}) {
    is PresentationResult.Success -> pr.presentation
    is PresentationResult.Failure -> error(pr.allErrors.joinToString("; "))
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
import org.trustweave.trust.types.getOrThrow

val trustWeave = TrustWeave.build { ... }

// Create wallet (using compact pattern)

val wallet = trustWeave.wallet {
    holder(holderDid)
    enableOrganization()
    enablePresentation()
}.getOrThrow()

// Use wallet
val credentialId = wallet.store(credential)
val retrieved = wallet.get(credentialId)
val allCredentials = wallet.list()
```

### Trust Operations

✅ **Correct:**
```kotlin
import org.trustweave.trust.dsl.credential.DidMethods.KEY
import org.trustweave.trust.dsl.credential.DidMethods.WEB
import org.trustweave.trust.dsl.credential.KeyAlgorithms.ED25519
import org.trustweave.trust.dsl.credential.KmsProviders.IN_MEMORY
import org.trustweave.trust.dsl.credential.KmsProviders.AWS
import org.trustweave.trust.dsl.credential.AnchorProviders.ALGORAND
val trustWeave = TrustWeave.build {
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

// To check via TrustRegistry directly (when configured)
val isTrusted = trustWeave.configuration.trustRegistry
    ?.isTrustedIssuer("did:key:university", "EducationCredential")
    ?: false
```

## Error Handling Patterns

### Mixed model: sealed results + optional throws

**Credential pipeline** (`issue`, `verify`, `presentationResult`, batch flows) returns **sealed results**—use `when`, not broad try-catch, for configuration and validation failures (`AdapterNotReady`, etc.). **DID updates** and similar APIs also return sealed result types. **Unwrapping:** `getOrThrowDid()` / many credential `getOrThrow()` helpers throw **`IllegalStateException`**; **`PresentationResult.getOrThrow()`** throws **`TrustWeaveException.InvalidState`** (`PRESENTATION_*` codes). Some **wallet** paths throw other domain exceptions. See [API contract](#api-contract-results-vs-exceptions) above.

Example: **DID + issuance** via sealed results; **wallet** may still throw `TrustWeaveException`:

```kotlin
import org.trustweave.trust.types.DidCreationResult
import org.trustweave.credential.results.IssuanceResult
import org.trustweave.core.exception.TrustWeaveException
import org.trustweave.trust.dsl.credential.DidMethods.KEY
import org.trustweave.trust.dsl.credential.DidMethods.WEB
import org.trustweave.trust.dsl.credential.KeyAlgorithms.ED25519
import org.trustweave.trust.dsl.credential.KmsProviders.IN_MEMORY
import org.trustweave.trust.dsl.credential.KmsProviders.AWS
import org.trustweave.trust.dsl.credential.AnchorProviders.ALGORAND

val did = when (val didResult = trustWeave.createDid { method(KEY) }) {
    is DidCreationResult.Success -> didResult.did
    is DidCreationResult.Failure -> {
        val msg = when (didResult) {
            is DidCreationResult.Failure.MethodNotRegistered ->
                "method ${didResult.method} not registered; available: ${didResult.availableMethods.joinToString()}"
            is DidCreationResult.Failure.KeyGenerationFailed -> didResult.reason
            is DidCreationResult.Failure.DocumentCreationFailed -> didResult.reason
            is DidCreationResult.Failure.InvalidConfiguration -> didResult.reason
            is DidCreationResult.Failure.Other -> didResult.reason
        }
        println("Failed to create DID: $msg")
        return@runBlocking
    }
}

val credential = when (val issuanceResult = trustWeave.issue { /* ... */ }) {
    is IssuanceResult.Success -> issuanceResult.credential
    is IssuanceResult.Failure.AdapterNotReady -> {
        println("Credential service not configured")
        return@runBlocking
    }
    else -> {
        println("Failed to issue: ${issuanceResult.allErrors.joinToString()}")
        return@runBlocking
    }
}

try {
    val wallet = trustWeave.wallet { holder(did) }
    wallet.store(credential)
} catch (error: TrustWeaveException) {
    println("TrustWeave error [${error.code}]: ${error.message}")
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
        println("Error: ${error.message}")
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
import org.trustweave.trust.types.getOrThrowDid
import org.trustweave.trust.dsl.credential.DidMethods.KEY
import org.trustweave.trust.dsl.credential.DidMethods.WEB
import org.trustweave.trust.dsl.credential.KeyAlgorithms.ED25519
import org.trustweave.trust.dsl.credential.KmsProviders.IN_MEMORY
import org.trustweave.trust.dsl.credential.KmsProviders.AWS
import org.trustweave.trust.dsl.credential.AnchorProviders.ALGORAND

val trustWeave = TrustWeave.build { ... }
val did = trustWeave.createDid { method(KEY) }.getOrThrowDid()
```

### ❌ Mistake 2: Ignoring Errors

**Wrong:**
```kotlin
import org.trustweave.trust.dsl.credential.DidMethods.KEY
import org.trustweave.trust.dsl.credential.DidMethods.WEB
import org.trustweave.trust.dsl.credential.KeyAlgorithms.ED25519
import org.trustweave.trust.dsl.credential.KmsProviders.IN_MEMORY
import org.trustweave.trust.dsl.credential.KmsProviders.AWS
import org.trustweave.trust.dsl.credential.AnchorProviders.ALGORAND
val did = trustWeave.createDid { method(KEY) }
// This returns DidCreationResult, not Did - need to unwrap!
```

**Correct:**
```kotlin
import org.trustweave.trust.dsl.credential.DidMethods.KEY
import org.trustweave.trust.dsl.credential.DidMethods.WEB
import org.trustweave.trust.dsl.credential.KeyAlgorithms.ED25519
import org.trustweave.trust.dsl.credential.KmsProviders.IN_MEMORY
import org.trustweave.trust.dsl.credential.KmsProviders.AWS
import org.trustweave.trust.dsl.credential.AnchorProviders.ALGORAND
when (val dr = trustWeave.createDid { method(KEY) }) {
    is DidCreationResult.Success -> {
        val did = dr.did
        // use did
    }
    is DidCreationResult.Failure -> {
        logger.error("Failed to create DID: $dr")
        return@runBlocking // or handle appropriately
    }
}
```

### ❌ Mistake 3: Not Configuring Required Components

**Wrong:**
```kotlin
import org.trustweave.trust.dsl.credential.DidMethods.KEY
import org.trustweave.trust.dsl.credential.DidMethods.WEB
import org.trustweave.trust.dsl.credential.KeyAlgorithms.ED25519
import org.trustweave.trust.dsl.credential.KmsProviders.IN_MEMORY
import org.trustweave.trust.dsl.credential.KmsProviders.AWS
import org.trustweave.trust.dsl.credential.AnchorProviders.ALGORAND
val trustWeave = TrustWeave.build {
    // Missing KMS configuration!
}
val did = trustWeave.createDid { method(KEY) }
// Fails: No KMS provider configured
```

**Correct:**
```kotlin
import org.trustweave.trust.dsl.credential.DidMethods.KEY
import org.trustweave.trust.dsl.credential.DidMethods.WEB
import org.trustweave.trust.dsl.credential.KeyAlgorithms.ED25519
import org.trustweave.trust.dsl.credential.KmsProviders.IN_MEMORY
import org.trustweave.trust.dsl.credential.KmsProviders.AWS
import org.trustweave.trust.dsl.credential.AnchorProviders.ALGORAND
val trustWeave = TrustWeave.build {
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
val issuerKeyId = "${issuerDid.value}#key-1"
val credential = trustWeave.issue {
    credential { ... }
    signedBy(issuerDid = issuerDid, keyId = issuerKeyId)
}
```

## Migration from Old API

If you're using older documentation or examples that reference `TrustWeave.dids.create()`, here's how to migrate:

### Old Pattern
```kotlin
// Legacy pseudo-code — not the current facade API
val trustWeave = TrustWeave.build { /* keys + did */ }
val did = trustWeave.createDid { }.getOrThrowDid()
val credential = trustWeave.issue { /* ... */ }.getOrThrow()
```

### New Pattern
```kotlin
import org.trustweave.trust.types.getOrThrowDid
import org.trustweave.credential.results.getOrThrow
import org.trustweave.trust.dsl.credential.DidMethods.KEY
import org.trustweave.trust.dsl.credential.DidMethods.WEB
import org.trustweave.trust.dsl.credential.KeyAlgorithms.ED25519
import org.trustweave.trust.dsl.credential.KmsProviders.IN_MEMORY
import org.trustweave.trust.dsl.credential.KmsProviders.AWS
import org.trustweave.trust.dsl.credential.AnchorProviders.ALGORAND

val trustWeave = TrustWeave.build {
    keys { provider(IN_MEMORY); algorithm(ED25519) }
    did { method(KEY) { algorithm(ED25519) } }
}

val issuerDid = trustWeave.createDid { method(KEY) }.getOrThrowDid()
val credential = trustWeave.issue {
    credential { ... }
    signedBy(issuerDid, "key-1") // fragment from the issuer DID document; or use signedBy(issuerDid) for auto key resolution
}.getOrThrow()
```

## Best Practices

### 1. Always Configure Explicitly

Don't rely on defaults in production. Explicitly configure all components:

```kotlin
import org.trustweave.trust.dsl.credential.DidMethods.KEY
import org.trustweave.trust.dsl.credential.DidMethods.WEB
import org.trustweave.trust.dsl.credential.KeyAlgorithms.ED25519
import org.trustweave.trust.dsl.credential.KmsProviders.IN_MEMORY
import org.trustweave.trust.dsl.credential.KmsProviders.AWS
import org.trustweave.trust.dsl.credential.AnchorProviders.ALGORAND
val trustWeave = TrustWeave.build {
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

### 2. Handle errors explicitly

Use **`when`** on sealed **`IssuanceResult`**, **`VerificationResult`**, **`DidCreationResult`**, **`WalletCreationResult`**, etc. Use **try/catch** for APIs that throw **`TrustWeaveException`** (and domain subclasses) or **`BlockchainException`**:

```kotlin
import org.trustweave.core.exception.TrustWeaveException

try {
    val updated = trustWeave.updateDid { did(didString); /* ... */ }
} catch (e: TrustWeaveException) {
    logger.error("Operation failed [${e.code}]: ${e.message}", e)
}
```

### 3. Use Type-Safe Builders

Leverage the DSL for type safety:

```kotlin
// ✅ Good: Type-safe, IDE autocomplete (issuerDid: Did)
val credential = trustWeave.issue {
    credential {
        type(CredentialType.VerifiableCredential, CredentialType.Person)
        issuer(issuerDid)
        subject {
            id(holderDid)
            "name" to "Alice"
        }
    }
    signedBy(issuerDid = issuerDid, keyId = "${issuerDid.value}#key-1")
}
```

### 4. Reuse TrustWeave Instance

Create one `TrustWeave` instance and reuse it:

```kotlin
import org.trustweave.trust.dsl.credential.DidMethods.KEY
import org.trustweave.trust.dsl.credential.DidMethods.WEB
import org.trustweave.trust.dsl.credential.KeyAlgorithms.ED25519
import org.trustweave.trust.dsl.credential.KmsProviders.IN_MEMORY
import org.trustweave.trust.dsl.credential.KmsProviders.AWS
import org.trustweave.trust.dsl.credential.AnchorProviders.ALGORAND
// ✅ Good: Create once, reuse
val trustWeave = TrustWeave.build { ... }

fun createUserDid() = trustWeave.createDid { method(KEY) }
fun issueWithTrustWeave(...) = trustWeave.issue { ... }
fun verifyWithTrustWeave(...) = trustWeave.verify { ... }
```

## Related Documentation

- Mental Model](../introduction/mental-model.md) - Understanding TrustWeave architecture
- Quick Start](quick-start.md) - Getting started guide
- API Reference](../api-reference/core-api.md) - Complete API documentation
- Error Handling](../advanced/error-handling.md) - Detailed error handling guide

