---
title: Error Handling Patterns: Exceptions vs Sealed Results
nav_order: 7
parent: Advanced Topics
keywords:
  - error handling
  - exceptions
  - sealed results
  - patterns
  - best practices
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

### Credential Issuance (`issueCredential()`)

**Why Exceptions?** Credential issuance should succeed if:
- Issuer DID is valid and resolvable
- Key exists and is accessible
- Configuration is correct

```kotlin
try {
    val credential = trustweave.issueCredential(
        issuer = issuerDid.id,
        keyId = issuerKeyId,
        subject = mapOf("name" to "Alice"),
        credentialType = "PersonCredential"
    )
    // Success
} catch (error: CredentialException.CredentialIssuanceFailed) {
    // Programming error: Configuration issue
    println("Issuance failed: ${error.reason}")
} catch (error: DidException) {
    // DID resolution failed (configuration issue)
    println("DID error: ${error.message}")
}
```

### Wallet Creation (`createWallet()`)

**Why Exceptions?** Wallet creation should succeed if:
- Holder DID is valid
- Provider is configured correctly
- Storage is available

```kotlin
try {
    val wallet = trustweave.createWallet(holderDid = holderDid.id)
    // Success
} catch (error: WalletException.WalletCreationFailed) {
    // Configuration error
    println("Wallet creation failed: ${error.reason}")
}
```

## Sealed Result-Based Operations

Operations that return sealed results are those where failure is **expected and normal**:

### DID Resolution (`resolveDid()`)

**Why Sealed Result?** DID resolution can fail for expected reasons:
- DID doesn't exist (normal - DIDs are created by others)
- Network unavailable (expected transient failure)
- Method not available (expected - methods are optional)

```kotlin
when (val result = trustweave.resolveDid("did:key:z6Mk...")) {
    is DidResolutionResult.Success -> {
        println("Resolved: ${result.document.id}")
    }
    is DidResolutionResult.Failure.NotFound -> {
        // Expected: DID doesn't exist yet
        println("DID not found - may be created later")
    }
    is DidResolutionResult.Failure.NetworkError -> {
        // Expected: Network may be temporarily unavailable
        println("Network error - retry later")
    }
    is DidResolutionResult.Failure.MethodNotRegistered -> {
        // Expected: Method may not be available
        println("Method not available: ${result.method}")
    }
    // Compiler ensures all cases handled
}
```

### Credential Verification (`verifyCredential()`)

**Why Sealed Result?** Verification can fail for expected reasons:
- Credential expired (normal lifecycle)
- Credential revoked (normal operation)
- Proof invalid (credential may be tampered with)
- Issuer not trusted (policy decision)

```kotlin
when (val result = trustweave.verifyCredential(credential)) {
    is CredentialVerificationResult.Valid -> {
        println("✅ Valid: ${result.credential.id}")
        result.warnings.forEach { println("⚠️ Warning: $it") }
    }
    is CredentialVerificationResult.Invalid.Expired -> {
        // Expected: Credentials expire
        println("❌ Expired at ${result.expiredAt}")
    }
    is CredentialVerificationResult.Invalid.Revoked -> {
        // Expected: Credentials can be revoked
        println("❌ Revoked at ${result.revokedAt}")
    }
    is CredentialVerificationResult.Invalid.InvalidProof -> {
        // Expected: Proof may be invalid
        println("❌ Invalid proof: ${result.reason}")
    }
    is CredentialVerificationResult.Invalid.UntrustedIssuer -> {
        // Expected: Policy decision
        println("❌ Issuer not trusted: ${result.issuer}")
    }
    // Compiler ensures all cases handled
}
```

## Decision Matrix

Use this table to determine which pattern to use:

| Operation Type | Pattern | Reason |
|---------------|---------|--------|
| **Creation** (createDid, issueCredential, createWallet) | Exception | Should succeed if configured correctly |
| **Resolution** (resolveDid) | Sealed Result | May fail for expected reasons |
| **Verification** (verifyCredential) | Sealed Result | May fail for expected reasons |
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
    is DidResolutionResult.Failure.NetworkError -> {
        // Expected: Retry with backoff
        delay(1000)
        trustweave.resolveDid(did)  // Retry
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
when (val verification = trustweave.verifyCredential(credential)) {
    is CredentialVerificationResult.Valid -> {
        // Proceed
    }
    is CredentialVerificationResult.Invalid -> {
        // Handle expected failure gracefully
        logger.warn("Credential invalid: ${verification.errors}")
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
when (val result = trustweave.resolveDid(did)) {
    is DidResolutionResult.Success -> { ... }
    is DidResolutionResult.Failure.NotFound -> { ... }
    is DidResolutionResult.Failure.MethodNotRegistered -> { ... }
    is DidResolutionResult.Failure.InvalidFormat -> { ... }
    is DidResolutionResult.Failure.NetworkError -> { ... }
    is DidResolutionResult.Failure.ResolutionError -> { ... }
    // Compiler error if case missing
}
```

## Quick Reference

| Operation | Pattern | Return Type | Example |
|-----------|---------|-------------|---------|
| `createDid()` | Exception | `DidDocument` | `try { ... } catch { ... }` |
| `resolveDid()` | Sealed Result | `DidResolutionResult` | `when (result) { ... }` |
| `issueCredential()` | Exception | `VerifiableCredential` | `try { ... } catch { ... }` |
| `verifyCredential()` | Sealed Result | `CredentialVerificationResult` | `when (result) { ... }` |
| `createWallet()` | Exception | `Wallet` | `try { ... } catch { ... }` |
| `blockchains.anchor()` | Exception | `AnchoredData` | `try { ... } catch { ... }` |
| `blockchains.read()` | Exception | `T` | `try { ... } catch { ... }` |

## Summary

- **Exceptions** = Programming errors, configuration issues (should be fixed)
- **Sealed Results** = Expected failures, business logic decisions (handle gracefully)

This hybrid approach provides:
- ✅ Clear error types for debugging (exceptions)
- ✅ Exhaustive handling for business logic (sealed results)
- ✅ Best of both worlds

## Related Documentation

- [Error Handling Guide](./error-handling.md) - Complete error handling reference
- [API Reference](../api-reference/core-api.md) - Method signatures and return types
- [Best Practices](../getting-started/common-patterns.md) - Common patterns

