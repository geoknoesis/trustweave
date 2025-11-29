---
title: Migrating to Phase 4 API (Service Layer Removal)
nav_order: 2
parent: Migration Guides
keywords:
  - migration
  - api changes
  - phase 4
  - service layer
  - direct methods
---

# Migrating to Phase 4 API: Service Layer Removal

This guide helps you migrate from the service layer API (`dids`, `credentials`, `wallets`) to the new direct methods API introduced in Phase 4.

## Overview

Phase 4 simplifies the TrustWeave API by removing the service layer indirection. Common operations are now available as direct methods on `TrustWeave`, making the API more intuitive and discoverable.

### What Changed

**Removed from Public API:**
- ❌ `trustweave.dids` property
- ❌ `trustweave.credentials` property  
- ❌ `trustweave.wallets` property

**New Direct Methods:**
- ✅ `trustweave.createDid()` - Direct DID creation
- ✅ `trustweave.resolveDid()` - Direct DID resolution
- ✅ `trustweave.issueCredential()` - Direct credential issuance
- ✅ `trustweave.verifyCredential()` - Direct credential verification
- ✅ `trustweave.createWallet()` - Direct wallet creation

**Still Available (Complex Services):**
- ✅ `trustweave.blockchains` - Complex blockchain operations
- ✅ `trustweave.contracts` - Smart contract operations

## Migration Steps

### Step 1: Update DID Operations

#### Before (Service Layer)
```kotlin
val trustweave = TrustWeave.create()

// Create DID
val did = trustweave.dids.create()

// Resolve DID
val result = trustweave.dids.resolve("did:key:...")
```

#### After (Direct Methods)
```kotlin
val trustweave = TrustWeave.create()

// Create DID - Direct method
val did = trustweave.createDid()

// Resolve DID - Direct method with sealed result
when (val result = trustweave.resolveDid("did:key:...")) {
    is DidResolutionResult.Success -> {
        println("Resolved: ${result.document.id}")
    }
    is DidResolutionResult.Failure.NotFound -> {
        println("DID not found: ${result.did}")
    }
    is DidResolutionResult.Failure.MethodNotRegistered -> {
        println("Method not registered: ${result.method}")
    }
    // ... handle other cases
}
```

### Step 2: Update Credential Operations

#### Before (Service Layer)
```kotlin
// Issue credential
val credential = trustweave.credentials.issue(
    issuer = issuerDid.id,
    subject = buildJsonObject {
        put("id", holderDid.id)
        put("name", "Alice")
    },
    config = IssuanceConfig(
        proofType = ProofType.Ed25519Signature2020,
        keyId = issuerKeyId,
        issuerDid = issuerDid.id
    ),
    types = listOf("UniversityDegreeCredential")
)

// Verify credential
val verification = trustweave.credentials.verify(credential)
if (verification.valid) {
    println("Valid!")
}
```

#### After (Direct Methods)
```kotlin
// Issue credential - Simple overload
val credential = trustweave.issueCredential(
    issuer = issuerDid.id,
    keyId = issuerKeyId,
    subject = mapOf(
        "id" to holderDid.id,
        "name" to "Alice"
    ),
    credentialType = "UniversityDegreeCredential"
)

// Or use advanced overload with JsonElement
val credential = trustweave.issueCredential(
    issuer = issuerDid.id,
    subject = buildJsonObject {
        put("id", holderDid.id)
        put("name", "Alice")
    },
    config = IssuanceConfig(
        proofType = ProofType.Ed25519Signature2020,
        keyId = issuerKeyId,
        issuerDid = issuerDid.id
    ),
    types = listOf("UniversityDegreeCredential")
)

// Verify credential - Returns sealed class
val verification = trustweave.verifyCredential(credential)
when (verification) {
    is CredentialVerificationResult.Valid -> {
        println("Valid! ${verification.credential.id}")
    }
    is CredentialVerificationResult.Invalid.Expired -> {
        println("Expired at ${verification.expiredAt}")
    }
    is CredentialVerificationResult.Invalid.Revoked -> {
        println("Revoked")
    }
    // ... handle other cases
}
```

### Step 3: Update Wallet Operations

#### Before (Service Layer)
```kotlin
// Create wallet
val wallet = trustweave.wallets.create(
    holderDid = holderDid.id,
    walletId = "my-wallet-id",
    type = WalletType.InMemory
)

// Store credential
wallet.store(credential)
```

#### After (Direct Methods)
```kotlin
// Create wallet - Direct method
val wallet = trustweave.createWallet(
    holderDid = holderDid.id,
    walletId = "my-wallet-id",
    type = WalletType.InMemory
)

// Store credential - Same as before
wallet.store(credential)
```

## Complete Migration Example

### Before (Service Layer API)
```kotlin
val trustweave = TrustWeave.create()

// DIDs
val issuerDid = trustweave.dids.create()
val holderDid = trustweave.dids.create()
val issuerKeyId = issuerDid.verificationMethod.first().id

// Credentials
val credential = trustweave.credentials.issue(
    issuer = issuerDid.id,
    subject = buildJsonObject {
        put("id", holderDid.id)
        put("name", "Alice")
    },
    config = IssuanceConfig(
        proofType = ProofType.Ed25519Signature2020,
        keyId = issuerKeyId,
        issuerDid = issuerDid.id
    ),
    types = listOf("PersonCredential")
)

val verification = trustweave.credentials.verify(credential)
if (!verification.valid) {
    throw Exception("Invalid credential")
}

// Wallets
val wallet = trustweave.wallets.create(holderDid = holderDid.id)
wallet.store(credential)
```

### After (Direct Methods API)
```kotlin
val trustweave = TrustWeave.create()

// DIDs - Direct methods
val issuerDid = trustweave.createDid()
val holderDid = trustweave.createDid()
val issuerKeyId = issuerDid.verificationMethod.first().id

// Credentials - Direct methods
val credential = trustweave.issueCredential(
    issuer = issuerDid.id,
    keyId = issuerKeyId,
    subject = mapOf(
        "id" to holderDid.id,
        "name" to "Alice"
    ),
    credentialType = "PersonCredential"
)

// Verification - Sealed result handling
val verification = trustweave.verifyCredential(credential)
when (verification) {
    is CredentialVerificationResult.Valid -> {
        // Credential is valid
    }
    is CredentialVerificationResult.Invalid -> {
        throw Exception("Invalid credential: ${verification.errors}")
    }
}

// Wallets - Direct methods
val wallet = trustweave.createWallet(holderDid = holderDid.id)
wallet.store(credential)
```

## Error Handling Changes

### DID Resolution

#### Before
```kotlin
val result = trustweave.dids.resolve("did:key:...")
if (result.document != null) {
    // Success
} else {
    // Failure - check result.metadata.error
}
```

#### After
```kotlin
when (val result = trustweave.resolveDid("did:key:...")) {
    is DidResolutionResult.Success -> {
        // Access result.document directly
        println("Resolved: ${result.document.id}")
    }
    is DidResolutionResult.Failure.NotFound -> {
        println("DID not found: ${result.did}")
    }
    is DidResolutionResult.Failure.MethodNotRegistered -> {
        println("Method not registered: ${result.method}")
        println("Available: ${result.availableMethods}")
    }
    // ... exhaustive handling
}
```

### Credential Verification

#### Before
```kotlin
val verification = trustweave.credentials.verify(credential)
if (verification.valid) {
    // Success
} else {
    // Check verification.errors, verification.proofValid, etc.
}
```

#### After
```kotlin
val verification = trustweave.verifyCredential(credential)
when (verification) {
    is CredentialVerificationResult.Valid -> {
        // Credential is valid
        println("Valid: ${verification.credential.id}")
        verification.warnings.forEach { println("Warning: $it") }
    }
    is CredentialVerificationResult.Invalid.Expired -> {
        println("Expired at ${verification.expiredAt}")
    }
    is CredentialVerificationResult.Invalid.Revoked -> {
        println("Revoked at ${verification.revokedAt}")
    }
    is CredentialVerificationResult.Invalid.InvalidProof -> {
        println("Invalid proof: ${verification.reason}")
    }
    // ... handle all cases
}
```

## Benefits of Migration

1. **Simpler API**: Direct methods are more intuitive
   ```kotlin
   // Before: trustweave.dids.create()
   // After: trustweave.createDid()
   ```

2. **Better Discoverability**: Common operations are obvious
   ```kotlin
   // Obvious what these do
   trustweave.createDid()
   trustweave.issueCredential(...)
   trustweave.createWallet(...)
   ```

3. **Type Safety**: Sealed results provide exhaustive handling
   ```kotlin
   // Compiler ensures all cases handled
   when (val result = trustweave.resolveDid(...)) {
       is Success -> ...
       is Failure -> ... // Must handle all failure types
   }
   ```

4. **Clearer Error Handling**: Sealed classes make error handling explicit
   ```kotlin
   // Before: Check boolean flags
   if (!verification.valid) { ... }
   
   // After: Handle specific error types
   when (verification) {
       is Valid -> ...
       is Invalid.Expired -> ...
       is Invalid.Revoked -> ...
   }
   ```

## Migration Checklist

- [ ] Replace `trustweave.dids.create()` with `trustweave.createDid()`
- [ ] Replace `trustweave.dids.resolve()` with `trustweave.resolveDid()` and update error handling
- [ ] Replace `trustweave.credentials.issue()` with `trustweave.issueCredential()`
- [ ] Replace `trustweave.credentials.verify()` with `trustweave.verifyCredential()` and use sealed class handling
- [ ] Replace `trustweave.wallets.create()` with `trustweave.createWallet()`
- [ ] Update error handling to use sealed result types
- [ ] Test all changes thoroughly
- [ ] Update any custom code that accesses service layer

## Complex Services (Unchanged)

Complex services remain as properties because they have many methods:

```kotlin
// Blockchain operations - Still use service
val anchor = trustweave.blockchains.anchor(
    data = credentialJson,
    serializer = JsonElement.serializer(),
    chainId = chainId
)

val readData = trustweave.blockchains.read<JsonElement>(
    ref = anchor.ref,
    serializer = JsonElement.serializer()
)

// Contract operations - Still use service
val contract = trustweave.contracts.draft(request)
val bound = trustweave.contracts.bindContract(...)
```

## Backward Compatibility

The service classes (`DidService`, `CredentialService`, `WalletService`) are now **internal** and cannot be accessed directly. If you have code that:

1. **Accesses service properties directly**: Update to use direct methods
2. **Imports service classes**: Remove imports (classes are internal)
3. **Extends service classes**: Use composition instead

## Common Issues

### Issue 1: Cannot Resolve `dids`, `credentials`, or `wallets`

**Error:**
```
Unresolved reference 'dids'
```

**Solution:**
Replace with direct methods:
```kotlin
// Before
val did = trustweave.dids.create()

// After
val did = trustweave.createDid()
```

### Issue 2: Type Mismatch in Verification Result

**Error:**
```
Unresolved reference 'valid'
```

**Solution:**
Use sealed class pattern matching:
```kotlin
// Before
if (verification.valid) { ... }

// After
when (verification) {
    is CredentialVerificationResult.Valid -> { ... }
    else -> { ... }
}
```

### Issue 3: DID Resolution Returns Different Type

**Error:**
```
Type mismatch: expected DidResolutionResult, found ...
```

**Solution:**
Update to handle sealed result:
```kotlin
// Before
val result = trustweave.dids.resolve(did)
if (result.document != null) { ... }

// After
when (val result = trustweave.resolveDid(did)) {
    is DidResolutionResult.Success -> {
        // result.document available
    }
    else -> { ... }
}
```

## Testing Migration

1. **Run Tests**: Ensure all tests pass with new API
2. **Check Error Handling**: Verify sealed result handling works
3. **Validate Functionality**: Test all DID, credential, and wallet operations
4. **Performance**: Verify no performance regressions

## Need Help?

If you encounter issues during migration:

1. Check [Error Handling Guide](../advanced/error-handling.md) for error patterns
2. Review [API Reference](../api-reference/core-api.md) for method signatures
3. Open an issue on GitHub with migration details
4. Contact support at [www.geoknoesis.com](https://www.geoknoesis.com)

## Summary

Phase 4 API simplifies TrustWeave by:
- ✅ Removing service layer indirection
- ✅ Adding direct methods for common operations
- ✅ Improving type safety with sealed results
- ✅ Enhancing discoverability

Migration is straightforward: replace service property calls with direct method calls and update error handling to use sealed result types.

