---
title: Verify Credentials
nav_order: 3
parent: How-To Guides
keywords:
  - verify
  - credential
  - verification
  - validation
  - proof
  - revocation
---

# Verify Credentials

This guide shows you how to verify verifiable credentials with TrustWeave. You'll learn how to check proof validity, issuer resolution, expiration, and revocation status.

## Quick Example

Here's a complete example that verifies a credential:

```kotlin
import com.trustweave.trust.TrustLayer
import com.trustweave.core.TrustWeaveError
import kotlinx.coroutines.runBlocking

fun main() = runBlocking {
    try {
        // Create TrustLayer instance
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

        // Assume you have a credential from issuance
        val credential = // ... credential from issuer ...

        // Verify credential
        val verification = trustLayer.verify {
            credential(credential)
        }

        if (verification.valid) {
            println("✅ Credential is valid")
            println("   Proof valid: ${verification.proofValid}")
            println("   Issuer valid: ${verification.issuerValid}")
            println("   Not expired: ${verification.notExpired}")
            println("   Not revoked: ${verification.notRevoked}")
            
            if (verification.warnings.isNotEmpty()) {
                println("   Warnings: ${verification.warnings.joinToString()}")
            }
        } else {
            println("❌ Credential invalid")
            println("   Errors: ${verification.errors.joinToString()}")
        }
    } catch (error: TrustWeaveError) {
        println("❌ Verification error: ${error.message}")
    }
}
```

**Expected Output:**
```
✅ Credential is valid
   Proof valid: true
   Issuer valid: true
   Not expired: true
   Not revoked: true
```

## Step-by-Step Guide

### Step 1: Get the Credential

Obtain the credential to verify (from holder, storage, or network):

```kotlin
val credential: VerifiableCredential = // ... get credential ...
```

### Step 2: Verify the Credential

Use the `verify` DSL to verify the credential:

```kotlin
val verification = trustLayer.verify {
    credential(credential)
}
```

### Step 3: Check Verification Result

Examine the verification result:

```kotlin
if (verification.valid) {
    // Credential passed all checks
    println("Credential is valid")
} else {
    // Credential failed one or more checks
    println("Credential invalid: ${verification.errors}")
}
```

### Step 4: Handle Warnings

Check for warnings even if verification passed:

```kotlin
if (verification.valid && verification.warnings.isNotEmpty()) {
    verification.warnings.forEach { warning ->
        println("Warning: $warning")
    }
}
```

## Verification Checks Explained

TrustWeave performs multiple checks during verification:

### 1. Proof Validation

Checks that the cryptographic proof (signature) is valid:

```kotlin
if (verification.proofValid) {
    println("Proof signature is valid")
} else {
    println("Proof signature is invalid")
}
```

**What it checks:**
- Signature matches the credential content
- Signature was created with a key from the issuer's DID document
- Proof type is supported

### 2. Issuer Validation

Checks that the issuer DID can be resolved:

```kotlin
if (verification.issuerValid) {
    println("Issuer DID resolved successfully")
} else {
    println("Issuer DID resolution failed")
}
```

**What it checks:**
- Issuer DID format is valid
- DID method is registered
- DID document can be retrieved
- Issuer DID document contains the verification method used in proof

### 3. Expiration Check

Checks if the credential has expired:

```kotlin
if (verification.notExpired) {
    println("Credential has not expired")
} else {
    println("Credential has expired")
}
```

**What it checks:**
- `expirationDate` field exists
- Current time is before expiration date

### 4. Revocation Check

Checks if the credential has been revoked:

```kotlin
if (verification.notRevoked) {
    println("Credential is not revoked")
} else {
    println("Credential has been revoked")
}
```

**What it checks:**
- `credentialStatus` field exists
- Status list is accessible
- Credential index in status list is not set (not revoked)

## Verification Policies

Configure verification behavior using verification options:

### Basic Verification

Default verification checks all aspects:

```kotlin
val verification = trustLayer.verify {
    credential(credential)
    // All checks enabled by default
}
```

### Custom Verification Configuration

Control which checks are performed:

```kotlin
val verification = trustLayer.verify {
    credential(credential)
    checkExpiration(true)      // Check expiration (default: true)
    checkRevocation(true)      // Check revocation (default: true)
    checkTrust(false)          // Check trust registry (default: false)
    expectedAudience(null)     // Expected audience DID (default: null)
}
```

### Skip Expiration Check

For credentials without expiration or when expiration doesn't matter:

```kotlin
val verification = trustLayer.verify {
    credential(credential)
    checkExpiration(false)  // Skip expiration check
}
```

### Skip Revocation Check

For credentials without revocation status or when revocation doesn't matter:

```kotlin
val verification = trustLayer.verify {
    credential(credential)
    checkRevocation(false)  // Skip revocation check
}
```

### Trust Registry Verification

Verify that the issuer is in the trust registry:

```kotlin
val verification = trustLayer.verify {
    credential(credential)
    checkTrust(true)  // Verify issuer is trusted
}
```

**Note:** Requires trust registry to be configured in TrustLayer.

## Common Patterns

### Pattern 1: Verify with Detailed Results

Get detailed information about each check:

```kotlin
val verification = trustLayer.verify {
    credential(credential)
}

println("Overall valid: ${verification.valid}")
println("Proof valid: ${verification.proofValid}")
println("Issuer valid: ${verification.issuerValid}")
println("Not expired: ${verification.notExpired}")
println("Not revoked: ${verification.notRevoked}")

if (verification.errors.isNotEmpty()) {
    println("Errors:")
    verification.errors.forEach { error ->
        println("  - $error")
    }
}

if (verification.warnings.isNotEmpty()) {
    println("Warnings:")
    verification.warnings.forEach { warning ->
        println("  - $warning")
    }
}
```

### Pattern 2: Verify Multiple Credentials

Verify a batch of credentials:

```kotlin
val credentials: List<VerifiableCredential> = // ... get credentials ...

val results = credentials.map { cred ->
    val verification = trustLayer.verify {
        credential(cred)
    }
    cred.id to verification
}

val valid = results.filter { (_, verification) -> verification.valid }
val invalid = results.filter { (_, verification) -> !verification.valid }

println("Valid: ${valid.size}/${results.size}")
println("Invalid: ${invalid.size}/${results.size}")

invalid.forEach { (credId, verification) ->
    println("Credential $credId failed: ${verification.errors.joinToString()}")
}
```

### Pattern 3: Verify with Error Handling

Handle verification errors gracefully:

```kotlin
val verification = try {
    trustLayer.verify {
        credential(credential)
    }
} catch (error: TrustWeaveError) {
    when (error) {
        is TrustWeaveError.CredentialInvalid -> {
            println("Credential structure invalid: ${error.reason}")
            return@runBlocking
        }
        is TrustWeaveError.DidMethodNotRegistered -> {
            println("Issuer DID method not registered: ${error.method}")
            return@runBlocking
        }
        else -> {
            println("Verification error: ${error.message}")
            return@runBlocking
        }
    }
}
```

### Pattern 4: Conditional Verification

Verify with different policies based on context:

```kotlin
fun verifyCredential(
    credential: VerifiableCredential,
    strict: Boolean = false
): CredentialVerificationResult {
    return trustLayer.verify {
        credential(credential)
        checkExpiration(strict)      // Only check expiration if strict
        checkRevocation(strict)      // Only check revocation if strict
        checkTrust(strict)            // Only check trust if strict
    }
}

// For production: strict verification
val productionResult = verifyCredential(credential, strict = true)

// For testing: lenient verification
val testResult = verifyCredential(credential, strict = false)
```

## Error Handling

Verification can fail in several ways:

### Verification Result Errors

The verification result contains errors if checks fail:

```kotlin
val verification = trustLayer.verify {
    credential(credential)
}

if (!verification.valid) {
    verification.errors.forEach { error ->
        when {
            error.contains("proof") -> {
                println("Proof validation failed")
            }
            error.contains("issuer") -> {
                println("Issuer validation failed")
            }
            error.contains("expired") -> {
                println("Credential expired")
            }
            error.contains("revoked") -> {
                println("Credential revoked")
            }
            else -> {
                println("Verification error: $error")
            }
        }
    }
}
```

### Exception Handling

Verification can throw exceptions for structural issues:

```kotlin
try {
    val verification = trustLayer.verify {
        credential(credential)
    }
    // Use verification result
} catch (error: TrustWeaveError) {
    when (error) {
        is TrustWeaveError.CredentialInvalid -> {
            println("Credential structure invalid: ${error.reason}")
            if (error.field != null) {
                println("Field: ${error.field}")
            }
        }
        is TrustWeaveError.DidMethodNotRegistered -> {
            println("Issuer DID method not registered: ${error.method}")
        }
        is TrustWeaveError.DidNotFound -> {
            println("Issuer DID not found: ${error.did}")
        }
        else -> {
            println("Error: ${error.message}")
        }
    }
}
```

## Verification Result Structure

The `CredentialVerificationResult` contains:

```kotlin
data class CredentialVerificationResult(
    val valid: Boolean,              // Overall validity (all checks passed)
    val proofValid: Boolean,          // Proof signature is valid
    val issuerValid: Boolean,         // Issuer DID resolved successfully
    val notExpired: Boolean,          // Credential has not expired
    val notRevoked: Boolean,          // Credential is not revoked
    val errors: List<String>,        // List of error messages
    val warnings: List<String>       // List of warnings
)
```

## API Reference

For complete API documentation, see:
- **[Core API - verify()](../api-reference/core-api.md#verify)** - Complete parameter reference
- **[Verification Policies](../advanced/verification-policies.md)** - Advanced verification configuration

## Related Concepts

- **[Verifiable Credentials](../core-concepts/verifiable-credentials.md)** - Understanding credentials
- **[Blockchain-Anchored Revocation](../core-concepts/blockchain-anchored-revocation.md)** - Understanding revocation
- **[Trust Registry](../core-concepts/trust-registry.md)** - Understanding trust relationships

## Related How-To Guides

- **[Issue Credentials](issue-credentials.md)** - Issue credentials to verify
- **[Handle Errors](../advanced/error-handling.md)** - Error handling patterns

## Next Steps

**Ready to issue credentials?**
- [Issue Credentials](issue-credentials.md) - Issue credentials that can be verified

**Want to configure verification?**
- [Verification Policies](../advanced/verification-policies.md) - Advanced verification configuration

**Want to learn more?**
- [Verifiable Credentials Concept](../core-concepts/verifiable-credentials.md) - Deep dive into credentials
- [Credential Issuance Tutorial](../tutorials/credential-issuance-tutorial.md) - Comprehensive tutorial

