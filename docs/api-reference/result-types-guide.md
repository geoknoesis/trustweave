---
title: Result Types Guide
nav_order: 6
parent: API Reference
keywords:
  - result types
  - error handling
  - sealed classes
  - IssuanceResult
  - VerificationResult
  - DidCreationResult
---

# Result Types Guide

TrustWeave uses sealed result types for exhaustive, type-safe error handling. This guide shows how to properly handle each result type.

## IssuanceResult

**Package:** `org.trustweave.credential.results.IssuanceResult`

**Structure:**
```kotlin
sealed class IssuanceResult {
    data class Success(val credential: VerifiableCredential) : IssuanceResult()
    sealed class Failure : IssuanceResult() {
        data class UnsupportedFormat(...)
        data class AdapterNotReady(...)
        data class InvalidRequest(...)
        data class AdapterError(...)
        data class MultipleFailures(...)
    }
}
```

**Correct Usage:**
```kotlin
import org.trustweave.credential.results.IssuanceResult
import org.trustweave.credential.results.getOrThrow

val result = trustWeave.issue { ... }

when (result) {
    is IssuanceResult.Success -> {
        val credential = result.credential
        // Use credential
    }
    is IssuanceResult.Failure -> {
        // Use allErrors property to get error messages
        val errorMessage = result.allErrors.joinToString("; ")
        println("Failed: $errorMessage")
    }
}

// Or unwrap for tests/examples

val credential = result.getOrThrow() // Throws IllegalStateException on failure
```

**Incorrect Usage:**
```kotlin
// ❌ WRONG - IssuanceResult.Failure doesn't have a 'reason' property
val error = result.reason

// ✅ CORRECT - Use allErrors
val errors = result.allErrors.joinToString("; ")
```

## DidCreationResult

**Package:** `org.trustweave.trust.types.DidCreationResult`

**Structure:**
```kotlin
sealed class DidCreationResult {
    data class Success(val did: Did, val document: DidDocument) : DidCreationResult()
    sealed class Failure : DidCreationResult() {
        data class MethodNotRegistered(val method: String, val availableMethods: List<String>)
        data class KeyGenerationFailed(val reason: String)
        data class DocumentCreationFailed(val reason: String)
        data class InvalidConfiguration(val reason: String, val details: Map<String, Any?>)
        data class Other(val reason: String, val cause: Throwable?)
    }
}
```

**Correct Usage:**
```kotlin
import org.trustweave.trust.types.DidCreationResult
import org.trustweave.trust.types.getOrThrowDid

val result = trustWeave.createDid { ... }

when (result) {
    is DidCreationResult.Success -> {
        val did = result.did
        // Use DID
    }
    is DidCreationResult.Failure.MethodNotRegistered -> {
        println("Method '${result.method}' not registered")
        println("Available: ${result.availableMethods.joinToString()}")
    }
    is DidCreationResult.Failure.KeyGenerationFailed -> {
        println("Key generation failed: ${result.reason}")
    }
    is DidCreationResult.Failure.DocumentCreationFailed -> {
        println("Document creation failed: ${result.reason}")
    }
    is DidCreationResult.Failure.InvalidConfiguration -> {
        println("Invalid configuration: ${result.reason}")
    }
    is DidCreationResult.Failure.Other -> {
        println("Error: ${result.reason}")
        result.cause?.printStackTrace()
    }
}

// Or unwrap for tests/examples

val did = result.getOrThrowDid() // Throws IllegalStateException on failure
```

**Incorrect Usage:**
```kotlin
// ❌ WRONG - DidCreationResult.Failure doesn't have a direct 'reason' property
val error = result.reason

// ✅ CORRECT - Handle specific failure types
when (result) {
    is DidCreationResult.Failure.KeyGenerationFailed -> result.reason
    is DidCreationResult.Failure.DocumentCreationFailed -> result.reason
    // ... handle other types
}
```

## DidCreationWithKeyResult

**Package:** `org.trustweave.trust.types`

Returned by `trustWeave.createDidWithKey { ... }`. Aligns with `DidCreationResult` for creation failures and adds key-id extraction outcomes.

**Structure:**
```kotlin
sealed class DidCreationWithKeyResult {
    data class Success(val did: Did, val keyId: String) : DidCreationWithKeyResult()
    sealed class Failure : DidCreationWithKeyResult() {
        data class FromCreation(val failure: DidCreationResult.Failure) : Failure()
        data class KeyExtractionFailed(val did: Did, val reason: String, val cause: Throwable?) : Failure()
    }
}
```

**Correct usage:**
```kotlin
import org.trustweave.trust.types.DidCreationWithKeyResult
import org.trustweave.trust.types.getOrThrow

when (val r = trustWeave.createDidWithKey { method("key") }) {
    is DidCreationWithKeyResult.Success -> println("${r.did.value} key=${r.keyId}")
    is DidCreationWithKeyResult.Failure.FromCreation -> { /* handle DidCreationResult.Failure */ }
    is DidCreationWithKeyResult.Failure.KeyExtractionFailed -> { /* document had no usable VM */ }
}

// Or throw on failure (examples/tests)
val (did, keyId) = trustWeave.createDidWithKey { method("key") }.getOrThrow()
```

## VerificationResult

**Package:** `org.trustweave.credential.results.VerificationResult`

**Structure:**
```kotlin
sealed class VerificationResult {
    data class Valid(...)
    sealed class Invalid : VerificationResult() {
        data class InvalidProof(...)
        data class Expired(...)
        data class NotYetValid(...)
        data class Revoked(...)
        data class InvalidIssuer(...)
        data class UntrustedIssuer(...)
        data class UnsupportedFormat(...)
        data class AdapterNotReady(...) // CredentialService not configured (TrustWeave.verify)
        data class SchemaValidationFailed(...)
        data class MultipleFailures(...)
    }
}
```

**Correct Usage:**
```kotlin
import org.trustweave.credential.results.VerificationResult

val result = trustWeave.verify(credential)
// or: trustWeave.verify { credential(credential); checkRevocation() }

when (result) {
    is VerificationResult.Valid -> {
        println("✅ Valid: ${result.credential.id}")
        if (result.warnings.isNotEmpty()) {
            println("Warnings: ${result.warnings.joinToString()}")
        }
    }
    is VerificationResult.Invalid.AdapterNotReady -> {
        println("❌ Credential service not configured: ${result.allErrors.joinToString()}")
    }
    is VerificationResult.Invalid.Expired -> {
        println("❌ Expired at ${result.expiredAt}")
    }
    is VerificationResult.Invalid.Revoked -> {
        println("❌ Revoked")
    }
    is VerificationResult.Invalid.InvalidProof -> {
        println("❌ Invalid proof: ${result.reason}")
    }
    is VerificationResult.Invalid.InvalidIssuer -> {
        println("❌ Issuer validation failed: ${result.reason}")
    }
    // ... handle other Invalid subtypes (see source / IDE)
}
```

## PresentationResult

**Package:** `org.trustweave.trust.types.PresentationResult`

Returned by `trustWeave.presentationResult { ... }` and `presentationFromWalletResult(wallet) { ... }`.

For **wallet** flows: every ID passed to `fromWallet(...)` must exist in the wallet. If any ID is missing, you get **`InvalidRequest`** with a clear message (no silent partial resolution).

**Structure:**
```kotlin
sealed class PresentationResult {
    data class Success(val presentation: VerifiablePresentation)
    sealed class Failure {
        data class AdapterNotReady(val errors: List<String>, ...)
        data class InvalidRequest(val errors: List<String>, ...)
        data class AdapterError(val cause: Throwable?, val errors: List<String>, ...)
    }
}
```

**Correct usage:**
```kotlin
import org.trustweave.trust.dsl.credential.presentationResult
import org.trustweave.trust.types.PresentationResult
import org.trustweave.trust.types.getOrThrow

when (val r = trustWeave.presentationResult { holder(holderDid); credentials(cred) }) {
    is PresentationResult.Success -> use(r.presentation)
    is PresentationResult.Failure.AdapterNotReady -> /* CredentialService missing */
    is PresentationResult.Failure.InvalidRequest -> /* e.g. missing holder */
    is PresentationResult.Failure.AdapterError -> /* service threw */
}

// Or throw on failure (tests / prototypes) — throws TrustWeaveException.InvalidState with codes
// PRESENTATION_ADAPTER_NOT_READY | PRESENTATION_INVALID_REQUEST | PRESENTATION_ADAPTER_ERROR
val vp = trustWeave.presentationResult { ... }.getOrThrow()
```

## WalletCreationResult

**Package:** `org.trustweave.trust.types.WalletCreationResult`

**Correct Usage:**
```kotlin
import org.trustweave.trust.types.WalletCreationResult
import org.trustweave.trust.types.getOrThrow

val result = trustWeave.wallet { holder("did:key:holder") }

when (result) {
    is WalletCreationResult.Success -> {
        val wallet = result.wallet
        // Use wallet
    }
    is WalletCreationResult.Failure.InvalidHolderDid -> {
        println("Invalid holder DID: ${result.holderDid} - ${result.reason}")
    }
    is WalletCreationResult.Failure.FactoryNotConfigured -> {
        println("Factory not configured: ${result.reason}")
    }
    is WalletCreationResult.Failure.StorageFailed -> {
        println("Storage failed: ${result.reason}")
        result.cause?.printStackTrace()
    }
    is WalletCreationResult.Failure.Other -> {
        println("Error: ${result.reason}")
        result.cause?.printStackTrace()
    }
}

// Or unwrap for tests/examples

val wallet = result.getOrThrow() // Throws IllegalStateException on failure
```

## Helper Functions

For tests and examples, use the typed unwrappers (same functions the production code recommends for “fail fast” demos):

```kotlin
import org.trustweave.trust.dsl.credential.DidMethods.KEY
import org.trustweave.trust.types.getOrThrowDid
import org.trustweave.credential.results.getOrThrow
import org.trustweave.trust.types.getOrThrow

// Throws IllegalStateException with detailed error message on failure
val did = trustWeave.createDid { method(KEY) }.getOrThrowDid()
val credential = trustWeave.issue { ... }.getOrThrow()
val wallet = trustWeave.wallet { holder("did:key:holder") }.getOrThrow()
```

## Best Practices

1. **Always handle all cases** - Use exhaustive `when` expressions
2. **Use `allErrors` for IssuanceResult** - Don't use non-existent `reason` property
3. **Handle specific failure types** - Each failure type has different properties
4. **Use `getOrThrow*` in tests/demos when appropriate** - Prefer `getOrThrowDid()`, `getOrThrow()` (issuance/wallet/presentation), and `DidCreationWithKeyResult.getOrThrow()` over ad-hoc unwrapping
5. **Provide user-friendly error messages** - Extract meaningful information from failure types

