---
title: Verify Credentials
nav_order: 5
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
import com.trustweave.trust.TrustWeave
import com.trustweave.trust.types.VerificationResult
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

        // Assume you have a credential from issuance
        val credential = // ... credential from issuer ...

        // Verify credential with exhaustive error handling
        val result = trustWeave.verify {
            credential(credential)
            checkRevocation()
            checkExpiration()
        }

        when (result) {
            is VerificationResult.Valid -> {
                println("✅ Credential is valid: ${result.credential.id}")
                if (result.warnings.isNotEmpty()) {
                    println("   Warnings: ${result.warnings.joinToString()}")
                }
            }
            is VerificationResult.Invalid.Expired -> {
                println("❌ Credential expired at ${result.expiredAt}")
            }
            is VerificationResult.Invalid.Revoked -> {
                println("❌ Credential revoked")
            }
            is VerificationResult.Invalid.InvalidProof -> {
                println("❌ Invalid proof: ${result.reason}")
            }
            is VerificationResult.Invalid.UntrustedIssuer -> {
                println("❌ Untrusted issuer: ${result.issuer}")
            }
            is VerificationResult.Invalid.SchemaValidationFailed -> {
                println("❌ Schema validation failed: ${result.errors.joinToString()}")
            }
            else -> {
                println("❌ Verification failed")
            }
        }
    } catch (error: Exception) {
        println("❌ Verification error: ${error.message}")
    }
}
```

**Expected Output:**
```
✅ Credential is valid: https://example.edu/credentials/123
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
val verification = trustWeave.verify {
    credential(credential)
}
```

### Step 3: Check Verification Result

Examine the verification result using sealed type for exhaustive handling:

```kotlin
when (verification) {
    is VerificationResult.Valid -> {
        println("✅ Credential is valid")
        println("  - Proof valid: ${verification.proofValid}")
        println("  - Issuer valid: ${verification.issuerValid}")
    }
    is VerificationResult.Invalid.Expired -> {
        println("❌ Credential expired at ${verification.expiredAt}")
    }
    is VerificationResult.Invalid.Revoked -> {
        println("❌ Credential revoked")
    }
    is VerificationResult.Invalid.InvalidProof -> {
        println("❌ Invalid proof: ${verification.reason}")
    }
    is VerificationResult.Invalid.UntrustedIssuer -> {
        println("❌ Untrusted issuer: ${verification.issuer}")
    }
    is VerificationResult.Invalid.SchemaValidationFailed -> {
        println("❌ Schema validation failed: ${verification.errors.joinToString()}")
    }
}
```

### Step 4: Handle Warnings

Check for warnings even if verification passed:

```kotlin
when (verification) {
    is VerificationResult.Valid -> {
        if (verification.warnings.isNotEmpty()) {
            verification.warnings.forEach { warning ->
                println("Warning: $warning")
            }
        }
    }
    else -> {
        // Handle invalid cases
    }
}
```

## Verification Checks Explained

TrustWeave performs multiple checks during verification:

### 1. Proof Validation

Checks that the cryptographic proof (signature) is valid:

```kotlin
when (verification) {
    is VerificationResult.Valid -> {
        if (verification.proofValid) {
            println("Proof signature is valid")
        }
    }
    is VerificationResult.Invalid.InvalidProof -> {
        println("Proof signature is invalid: ${verification.reason}")
    }
    else -> {
        // Other failure cases
    }
}
```

**What it checks:**
- Signature matches the credential content
- Signature was created with a key from the issuer's DID document
- Proof type is supported

### 2. Issuer Validation

Checks that the issuer DID can be resolved:

```kotlin
when (verification) {
    is VerificationResult.Valid -> {
        if (verification.issuerValid) {
            println("Issuer DID resolved successfully")
        }
    }
    is VerificationResult.Invalid.UntrustedIssuer -> {
        println("Issuer DID resolution failed or issuer not trusted: ${verification.issuer}")
    }
    else -> {
        // Other failure cases
    }
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
when (verification) {
    is VerificationResult.Valid -> {
        println("Credential has not expired")
    }
    is VerificationResult.Invalid.Expired -> {
        println("Credential has expired at ${verification.expiredAt}")
    }
    else -> {
        // Other failure cases
    }
}
```

**What it checks:**
- `expirationDate` field exists
- Current time is before expiration date

### 4. Revocation Check

Checks if the credential has been revoked:

```kotlin
when (verification) {
    is VerificationResult.Valid -> {
        println("Credential is not revoked")
    }
    is VerificationResult.Invalid.Revoked -> {
        println("Credential has been revoked")
    }
    else -> {
        // Other failure cases
    }
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
val verification = trustWeave.verify {
    credential(credential)
    // All checks enabled by default
}
```

### Custom Verification Configuration

Control which checks are performed:

```kotlin
val verification = trustWeave.verify {
    credential(credential)
    checkExpiration()          // Check expiration (default: enabled)
    checkRevocation()          // Check revocation (default: enabled)
    checkTrust(false)          // Check trust registry (default: false)
}
```

### Skip Expiration Check

For credentials without expiration or when expiration doesn't matter:

```kotlin
// Simply don't call checkExpiration() - it's optional
val verification = trustWeave.verify {
    credential(credential)
    // Expiration check skipped
}
```

### Skip Revocation Check

For credentials without revocation status or when revocation doesn't matter:

```kotlin
// Simply don't call checkRevocation() - it's optional
val verification = trustWeave.verify {
    credential(credential)
    // Revocation check skipped
}
```

### Trust Registry Verification

Verify that the issuer is in the trust registry:

```kotlin
val verification = trustWeave.verify {
    credential(credential)
    checkTrust(true)  // Verify issuer is trusted
}
```

**Note:** Requires trust registry to be configured in TrustWeave.

## Common Patterns

### Pattern 1: Verify with Detailed Results

Get detailed information about each check:

```kotlin
val verification = trustWeave.verify {
    credential(credential)
}

when (verification) {
    is VerificationResult.Valid -> {
        println("✅ Overall valid: true")
        println("  - Proof valid: ${verification.proofValid}")
        println("  - Issuer valid: ${verification.issuerValid}")
        if (verification.warnings.isNotEmpty()) {
            println("  - Warnings:")
            verification.warnings.forEach { warning ->
                println("    - $warning")
            }
        }
    }
    is VerificationResult.Invalid.Expired -> {
        println("❌ Credential expired at ${verification.expiredAt}")
        verification.errors.forEach { error ->
            println("  - $error")
        }
    }
    is VerificationResult.Invalid.Revoked -> {
        println("❌ Credential revoked")
        verification.errors.forEach { error ->
            println("  - $error")
        }
    }
    is VerificationResult.Invalid.InvalidProof -> {
        println("❌ Invalid proof: ${verification.reason}")
    }
    is VerificationResult.Invalid.UntrustedIssuer -> {
        println("❌ Untrusted issuer: ${verification.issuer}")
    }
    is VerificationResult.Invalid.SchemaValidationFailed -> {
        println("❌ Schema validation failed:")
        verification.errors.forEach { error ->
            println("  - $error")
        }
    }
}
```

### Pattern 2: Verify Multiple Credentials

Verify a batch of credentials:

```kotlin
val credentials: List<VerifiableCredential> = // ... get credentials ...

val results = credentials.map { cred ->
    val verification = trustWeave.verify {
        credential(cred)
    }
    cred.id to verification
}

val valid = results.filter { (_, verification) -> 
    verification is VerificationResult.Valid 
}
val invalid = results.filter { (_, verification) -> 
    verification !is VerificationResult.Valid 
}

println("Valid: ${valid.size}/${results.size}")
println("Invalid: ${invalid.size}/${results.size}")

invalid.forEach { (credId, verification) ->
    when (verification) {
        is VerificationResult.Invalid -> {
            println("Credential $credId failed: ${verification.errors.joinToString()}")
        }
        else -> {
            println("Credential $credId failed: Unknown error")
        }
    }
}
```

### Pattern 3: Verify with Error Handling

Handle verification errors gracefully:

```kotlin
// Verification returns a sealed result type, so no exceptions needed
val verification = trustWeave.verify {
    credential(credential)
}

when (verification) {
    is VerificationResult.Valid -> {
        println("✅ Credential is valid")
    }
    is VerificationResult.Invalid -> {
        println("❌ Credential invalid: ${verification.errors.joinToString()}")
    }
}
```

### Pattern 4: Conditional Verification

Verify with different policies based on context:

```kotlin
fun verifyCredential(
    credential: VerifiableCredential,
    strict: Boolean = false
): VerificationResult {
    return trustWeave.verify {
        credential(credential)
        if (strict) {
            checkExpiration()      // Only check expiration if strict
            checkRevocation()      // Only check revocation if strict
            checkTrust(true)       // Only check trust if strict
        }
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

The verification result is a sealed type with detailed error information:

```kotlin
val verification = trustWeave.verify {
    credential(credential)
}

when (verification) {
    is VerificationResult.Valid -> {
        println("✅ Credential is valid")
    }
    is VerificationResult.Invalid.Expired -> {
        println("❌ Credential expired at ${verification.expiredAt}")
    }
    is VerificationResult.Invalid.Revoked -> {
        println("❌ Credential revoked")
    }
    is VerificationResult.Invalid.InvalidProof -> {
        println("❌ Proof validation failed: ${verification.reason}")
    }
    is VerificationResult.Invalid.UntrustedIssuer -> {
        println("❌ Issuer validation failed: ${verification.issuer}")
    }
    is VerificationResult.Invalid.SchemaValidationFailed -> {
        println("❌ Schema validation failed")
        verification.errors.forEach { error ->
            println("  - $error")
        }
    }
}
```

### Exception Handling

Verification returns a sealed result type, so exceptions are rare. However, you should handle the result exhaustively:

```kotlin
val verification = trustWeave.verify {
    credential(credential)
}

// Exhaustive handling ensures all cases are covered
when (verification) {
    is VerificationResult.Valid -> {
        // Use valid credential
        println("Credential is valid: ${verification.credential.id}")
    }
    is VerificationResult.Invalid -> {
        // Handle all invalid cases
        println("Credential invalid: ${verification.errors.joinToString()}")
    }
}
```

## Verification Result Structure

The `VerificationResult` is a sealed class for exhaustive error handling:

```kotlin
sealed class VerificationResult {
    data class Valid(
        val credential: VerifiableCredential,
        val proofValid: Boolean,
        val issuerValid: Boolean,
        val warnings: List<String> = emptyList()
    ) : VerificationResult()
    
    sealed class Invalid : VerificationResult() {
        data class Expired(
            val expiredAt: Instant,
            val errors: List<String>
        ) : Invalid()
        
        data class Revoked(
            val revokedAt: Instant?,
            val errors: List<String>
        ) : Invalid()
        
        data class InvalidProof(
            val reason: String,
            val errors: List<String>
        ) : Invalid()
        
        data class UntrustedIssuer(
            val issuer: String,
            val errors: List<String>
        ) : Invalid()
        
        data class SchemaValidationFailed(
            val errors: List<String>
        ) : Invalid()
    }
}
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

