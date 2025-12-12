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

**Package:** `com.trustweave.credential.results.IssuanceResult`

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
import com.trustweave.credential.results.IssuanceResult

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

// Or use testkit helper for tests/examples
import com.trustweave.testkit.getOrFail

val credential = result.getOrFail() // Throws on failure
```

**Incorrect Usage:**
```kotlin
// ❌ WRONG - IssuanceResult.Failure doesn't have a 'reason' property
val error = result.reason

// ✅ CORRECT - Use allErrors
val errors = result.allErrors.joinToString("; ")
```

## DidCreationResult

**Package:** `com.trustweave.trust.types.DidCreationResult`

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
import com.trustweave.trust.types.DidCreationResult

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

// Or use testkit helper for tests/examples
import com.trustweave.testkit.getOrFail

val did = result.getOrFail() // Throws on failure
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

## VerificationResult

**Package:** `com.trustweave.trust.types.VerificationResult`

**Structure:**
```kotlin
sealed class VerificationResult {
    data class Valid(val credential: VerifiableCredential, val warnings: List<String>)
    sealed class Invalid : VerificationResult() {
        data class Expired(...)
        data class Revoked(...)
        data class InvalidProof(val reason: String, ...)
        data class IssuerResolutionFailed(val reason: String, ...)
        data class UntrustedIssuer(...)
        data class SchemaValidationFailed(...)
        // ...
    }
}
```

**Correct Usage:**
```kotlin
import com.trustweave.trust.types.VerificationResult

val result = trustWeave.verify { credential(credential) }

when (result) {
    is VerificationResult.Valid -> {
        println("✅ Valid: ${result.credential.id}")
        if (result.warnings.isNotEmpty()) {
            println("Warnings: ${result.warnings.joinToString()}")
        }
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
    is VerificationResult.Invalid.IssuerResolutionFailed -> {
        println("❌ Issuer resolution failed: ${result.reason}")
    }
    // ... handle other types
}
```

## WalletCreationResult

**Package:** `com.trustweave.trust.types.WalletCreationResult`

**Correct Usage:**
```kotlin
import com.trustweave.trust.types.WalletCreationResult

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

// Or use testkit helper
import com.trustweave.testkit.getOrFail

val wallet = result.getOrFail() // Throws on failure
```

## Helper Functions

For tests and examples, use the `getOrFail()` extension from testkit:

```kotlin
import com.trustweave.testkit.getOrFail

// Throws IllegalStateException with detailed error message on failure
val did = trustWeave.createDid { method("key") }.getOrFail()
val credential = trustWeave.issue { ... }.getOrFail()
val wallet = trustWeave.wallet { holder("did:key:holder") }.getOrFail()
```

## Best Practices

1. **Always handle all cases** - Use exhaustive `when` expressions
2. **Use `allErrors` for IssuanceResult** - Don't use non-existent `reason` property
3. **Handle specific failure types** - Each failure type has different properties
4. **Use testkit helpers in tests** - `getOrFail()` simplifies test code
5. **Provide user-friendly error messages** - Extract meaningful information from failure types

