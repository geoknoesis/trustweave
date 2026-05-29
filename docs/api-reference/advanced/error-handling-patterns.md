---
title: "Error Handling Patterns: Exceptions vs Sealed Results"
nav_order: 7
parent: Advanced Topics
keywords:
  - error handling
  - exceptions
  - sealed results
  - patterns
  - best practices
redirect_from:
  - /advanced/error-handling-patterns/

---

# Error Handling Patterns: Exceptions vs Sealed Results

TrustWeave uses a hybrid error handling approach: **exceptions for programming errors** and **sealed results for expected failures**. This guide explains when to use each pattern and why.

## Core Principle

**The Rule:**
- **Exceptions** → Programming errors, invalid configuration, unexpected failures
- **Sealed Results** → Expected failures that are part of normal operation

## Exception-Based Operations

Operations that throw exceptions are those where failure indicates a **programming error** or **invalid configuration**:

### DID Creation (`createDid()`)

**Why Exceptions?** Creating a DID should always succeed if configuration is correct. Failure means:
- Invalid configuration (programming error)
- Missing required components (KMS not configured)
- Method not registered (should be registered at startup)

```kotlin
try {
    val did = trustweave.createDid()
    // Success - DID created
} catch (error: DidException.DidMethodNotRegistered) {
    // Programming error: Method should be registered
    println("Method not registered: ${error.method}")
    println("Available: ${error.availableMethods}")
} catch (error: DidException) {
    // Configuration error
    println("Failed to create DID: ${error.message}")
}
```

### Credential Issuance (`trustWeave.issue { }`)

**Why a sealed result?** Issuance can fail for resolver, KMS, schema, or adapter reasons—handle them with **`when`** (or **`getOrThrow()`** only in tests).

```kotlin
import org.trustweave.credential.results.IssuanceResult

// issuerDid: Did, issuerKeyId: String (e.g. from verificationMethod.extractKeyId())
when (val issued = trustWeave.issue {
    credential {
        type("PersonCredential")
        issuer(issuerDid)
        subject { "name" to "Alice" }
    }
    signedBy(issuerDid = issuerDid, keyId = issuerKeyId)
}) {
    is IssuanceResult.Success -> {
        val credential = issued.credential
        // …
    }
    is IssuanceResult.Failure.UnsupportedFormat -> println(issued.allErrors.joinToString())
    is IssuanceResult.Failure.AdapterNotReady -> println(issued.allErrors.joinToString())
    is IssuanceResult.Failure.InvalidRequest -> println("Invalid field '${issued.field}': ${issued.reason}")
    is IssuanceResult.Failure.AdapterError -> println("Adapter error: ${issued.reason}")
    is IssuanceResult.Failure.MultipleFailures -> println(issued.allErrors.joinToString())
}
```

### Wallet Creation (`trustWeave.wallet { }`)

**Why a sealed result?** Misconfigured factories, invalid holder DIDs, or storage errors surface as **`WalletCreationResult.Failure`**—handle with **`when`** or **`getOrThrow()`** in tests.

```kotlin
import org.trustweave.trust.types.getOrThrow

val wallet = trustWeave.wallet {
    holder(holderDid)
    provider("inMemory")
}.getOrThrow()
```

## Sealed Result-Based Operations

Operations that return sealed results are those where failure is **expected and normal**:

### DID Resolution (`resolveDid()`)

**Why Sealed Result?** DID resolution can fail for expected reasons:
- DID doesn't exist (normal - DIDs are created by others)
- Network unavailable (expected transient failure)
- Method not available (expected - methods are optional)

```kotlin
when (val result = trustWeave.resolveDid("did:key:z6Mk...")) {
    is DidResolutionResult.Success -> {
        println("Resolved: ${result.document.id}")
    }
    is DidResolutionResult.Failure.NotFound -> {
        println("DID not found - may be created later")
    }
    is DidResolutionResult.Failure.InvalidFormat -> {
        println("Invalid DID: ${result.reason}")
    }
    is DidResolutionResult.Failure.MethodNotRegistered -> {
        println("Method not available: ${result.method}")
    }
    is DidResolutionResult.Failure.ResolutionError -> {
        println("Resolution error - retry later")
    }
}
```

### Credential Verification (`trustWeave.verify`)

**Why Sealed Result?** Verification can fail for expected reasons:
- Credential expired (normal lifecycle)
- Credential revoked (normal operation)
- Proof invalid (credential may be tampered with)
- Issuer not trusted (policy decision)

```kotlin
when (val result = trustWeave.verify(credential)) {
    is VerificationResult.Valid -> {
        println("✅ Valid: ${result.credential.id}")
        result.warnings.forEach { println("⚠️ Warning: $it") }
    }
    is VerificationResult.Invalid.Expired -> {
        // Expected: Credentials expire
        println("❌ Expired at ${result.expiredAt}")
    }
    is VerificationResult.Invalid.Revoked -> {
        // Expected: Credentials can be revoked
        println("❌ Revoked at ${result.revokedAt}")
    }
    is VerificationResult.Invalid.InvalidProof -> {
        // Expected: Proof may be invalid
        println("❌ Invalid proof: ${result.reason}")
    }
    is VerificationResult.Invalid.UntrustedIssuer -> {
        // Expected: Policy decision
        println("❌ Issuer not trusted: ${result.issuerDid.value}")
    }
    is VerificationResult.Invalid -> {
        println("❌ Verification failed: ${result.allErrors.joinToString()}")
    }
}
```

## Decision Matrix

Use this table to determine which pattern to use:

| Operation Type | Pattern | Reason |
|---------------|---------|--------|
| **Creation** (`createDid`, `issue { }`, `wallet { }`) | Sealed results (`DidCreationResult`, `IssuanceResult`, `WalletCreationResult`) | Handle with `when` / `getOrThrow` in tests |
| **Resolution** (resolveDid) | Sealed Result | May fail for expected reasons |
| **Verification** (`trustWeave.verify`) | Sealed Result | May fail for expected reasons |
| **Read Operations** (read anchored data) | Sealed Result | Data may not exist |
| **Configuration Errors** | Exception | Programming error |
| **Expected Failures** | Sealed Result | Part of normal operation |

## Pattern Examples

### Pattern 1: Exception for Programming Errors

```kotlin
// ❌ Bad: Using sealed result for programming error
val didResult = trustweave.createDid()
when (didResult) {
    is Success -> { ... }
    is Failure -> { ... }  // This is a programming error, not expected
}

// ✅ Good: Exception for programming error
try {
    val did = trustweave.createDid()  // Should succeed if configured
} catch (error: DidException.DidMethodNotRegistered) {
    // Fix configuration
    throw IllegalStateException("Missing DID method registration", error)
}
```

### Pattern 2: Sealed Result for Expected Failures

```kotlin
// ❌ Bad: Using exception for expected failure
try {
    val resolution = trustweave.resolveDid("did:unknown:test")
} catch (error: DidException.DidNotFound) {
    // This is expected - DID may not exist
}

// ✅ Good: Sealed result for expected failure
when (val resolution = trustweave.resolveDid("did:unknown:test")) {
    is DidResolutionResult.Success -> {
        // DID exists
    }
    is DidResolutionResult.Failure.NotFound -> {
        // Expected: DID doesn't exist
        println("DID not found - this is normal")
    }
}
```

## When to Use Each Pattern

### Use Exceptions When:

1. **Programming Error**
   - Method not registered (should be registered at startup)
   - Invalid configuration
   - Missing required components

2. **Should Never Fail in Production**
   - Creating a DID (if configured correctly)
   - Issuing a credential (if issuer is valid)
   - Creating a wallet (if storage is available)

3. **Failures Indicate Bugs**
   - Configuration issues
   - Missing dependencies
   - Invalid state

### Use Sealed Results When:

1. **Expected Failure**
   - DID doesn't exist (created by others)
   - Credential expired (normal lifecycle)
   - Credential revoked (normal operation)
   - Network unavailable (transient)

2. **Part of Business Logic**
   - Verification decisions
   - Policy decisions
   - Validation results

3. **Failures Are Recoverable**
   - Retry logic
   - Fallback strategies
   - Alternative approaches

## Error Recovery Patterns

### Exception Recovery (Fix Configuration)

```kotlin
// Exceptions indicate configuration problems - fix them
try {
    val did = trustweave.createDid(method = "web")
} catch (error: DidException.DidMethodNotRegistered) {
    // Fix: Register the method
    trustweave.registerDidMethod(DidWebMethod(...))
    // Retry
    val did = trustweave.createDid(method = "web")
}
```

### Sealed Result Recovery (Handle Expected Failure)

```kotlin
// Sealed results indicate expected failures - handle them gracefully
when (val resolution = trustweave.resolveDid(did)) {
    is DidResolutionResult.Success -> {
        // Use DID document
    }
    is DidResolutionResult.Failure.NotFound -> {
        // Expected: Try alternative
        val alternativeDid = deriveAlternativeDid(did)
        trustweave.resolveDid(alternativeDid)
    }
    is DidResolutionResult.Failure.ResolutionError -> {
        delay(1000)
        trustweave.resolveDid(did) // Retry
    }
}
```

## Best Practices

### 1. Always Handle Both Patterns Appropriately

```kotlin
// ✅ Good: Handle exceptions for configuration errors
try {
    val did = trustweave.createDid()  // Exception on config error
} catch (error: DidException) {
    // Log and fix configuration
    logger.error("DID creation failed", error)
    throw IllegalStateException("Configuration error", error)
}

// ✅ Good: Handle sealed results for expected failures
when (val verification = trustWeave.verify(credential)) {
    is VerificationResult.Valid -> {
        // Proceed
    }
    is VerificationResult.Invalid -> {
        // Handle expected failure gracefully
        logger.warn("Credential invalid: ${verification.allErrors.joinToString()}")
        // Show user-friendly message
    }
}
```

### 2. Don't Mix Patterns Incorrectly

```kotlin
// ❌ Bad: Treating expected failure as exception
try {
    val resolution = trustweave.resolveDid("did:unknown:test")
    // This can fail normally - shouldn't be exception
} catch (error: Exception) {
    // Wrong pattern
}

// ✅ Good: Using sealed result for expected failure
when (val resolution = trustweave.resolveDid("did:unknown:test")) {
    is DidResolutionResult.Success -> { ... }
    is DidResolutionResult.Failure -> { ... }  // Expected
}
```

### 3. Use Exhaustive Handling for Sealed Results

```kotlin
// ✅ Good: Compiler ensures all cases handled
when (val result = trustWeave.resolveDid(did)) {
    is DidResolutionResult.Success -> { ... }
    is DidResolutionResult.Failure.NotFound -> { ... }
    is DidResolutionResult.Failure.MethodNotRegistered -> { ... }
    is DidResolutionResult.Failure.InvalidFormat -> { ... }
    is DidResolutionResult.Failure.ResolutionError -> { ... }
    // Compiler error if case missing
}
```

## Quick Reference

| Operation | Pattern | Return Type | Example |
|-----------|---------|-------------|---------|
| `createDid()` | Sealed result | `DidCreationResult` | `when (result) { ... }` / `getOrThrowDid()` |
| `resolveDid()` | Sealed result | `DidResolutionResult` | `when (result) { ... }` |
| `issue { }` | Sealed result | `IssuanceResult` | `when (result) { ... }` / `getOrThrow()` |
| `verify(...)` / `verify { }` | Sealed result | `VerificationResult` | `when (result) { ... }` |
| `wallet { }` | Sealed result | `WalletCreationResult` | `when (result) { ... }` / `getOrThrow()` |
| `blockchains.anchor()` | Success value + throws | `AnchorResult` | `try { ... } catch (e: BlockchainException) { ... }` |
| `blockchains.read()` | Throws on failure | `T` | `try { ... } catch (e: BlockchainException) { ... }` |

## Summary

- **Exceptions** = Programming errors, configuration issues (should be fixed)
- **Sealed Results** = Expected failures, business logic decisions (handle gracefully)

This hybrid approach provides:
- Clear error types for debugging (exceptions)
- Exhaustive handling for business logic (sealed results)
- Best of both worlds

## Related Documentation

- Error Handling Guide](./error-handling.md) - Complete error handling reference
- API Reference](../api-reference/core-api.md) - Method signatures and return types
- Best Practices](../getting-started/common-patterns.md) - Common patterns

