---
title: Trust Boundaries
nav_order: 2
parent: Security
keywords:
  - security
  - trust boundaries
  - trust model
  - security model
---

# Trust Boundaries

Understanding trust boundaries is crucial for secure TrustWeave deployments. This document explains what you should trust, what you shouldn't trust, and how to establish secure trust relationships.

## Overview

A **trust boundary** is a conceptual line where trust assumptions change. Crossing a trust boundary means moving from a trusted domain to an untrusted domain (or vice versa).

## TrustWeave Trust Boundaries

### 1. Application Layer → TrustWeave SDK

**Trust Level:** High (You control this code)

**What You Control:**
- TrustWeave configuration
- KMS selection and configuration
- DID method selection
- Trust registry configuration

**Security Responsibilities:**
- Configure KMS with production-grade providers
- Use secure key storage
- Validate inputs before passing to TrustWeave
- Implement proper error handling

```kotlin
// Good: secure configuration (call from a suspend entry point or runBlocking)
val trustWeave = TrustWeave.build {
    customKms(
        AwsKeyManagementService(
            region = "us-east-1",
            credentials = secureCredentials
        )
    )
    did {
        method("web") {
            domain("yourdomain.com")
        }
    }
}

// Bad: insecure configuration (testing only)
val testOnly = TrustWeave.quickStart() // or explicit inMemory KMS via build { keys { provider("inMemory") } }
```

### 2. TrustWeave SDK → External Services

**Trust Level:** Variable (Depends on service)

**External Services:**
- **DID Resolution Services** (e.g., Universal Resolver)
- **Blockchain Networks** (e.g., Ethereum, Algorand)
- **Key Management Services** (e.g., AWS KMS, Azure Key Vault)
- **Trust Registries** (e.g., External trust anchor registries)

**Security Responsibilities:**
- Verify TLS certificates
- Validate service responses
- Use authenticated connections
- Implement timeouts and retry logic
- Handle service failures gracefully

```kotlin
// ✅ Good: Verify DID resolution results
when (val resolution = trustweave.resolveDid(did)) {
    is DidResolutionResult.Success -> {
        // Verify the document is valid
        val document = resolution.document
        validateDidDocument(document)  // Your validation
        // Use document
    }
    is DidResolutionResult.Failure -> {
        // Handle failure - don't trust unverified data
    }
}

// ❌ Bad: Ignoring sealed DidResolutionResult (failure vs success, error details)
val resolution = trustweave.resolveDid(did)
// No when / no check — cannot tell Success from Failure or validate the document
```

### 3. TrustWeave SDK → Blockchain Networks

**Trust Level:** Network-dependent

**What to Trust:**
- Blockchain consensus (if network is trusted)
- Immutable transaction history
- Smart contract execution (if verified)

**What NOT to Trust:**
- Network availability
- Transaction finality timing
- Smart contract code (unless verified)
- Unverified blockchain data

```kotlin
// ✅ Good: Verify blockchain anchor
val anchor = trustweave.blockchains.anchor(
    data = credentialJson,
    serializer = JsonElement.serializer(),
    chainId = chainId
)

// Later, verify the anchor
val readData = trustweave.blockchains.read(
    ref = anchor.ref,
    serializer = JsonElement.serializer()
)

// Verify the data matches
if (readData != credentialJson) {
    throw SecurityException("Anchored data mismatch")
}

// ❌ Bad: Trusting blockchain data without verification
val readData = trustweave.blockchains.read(...)
// No verification - could be different data!
```

### 4. Credential Issuer → Verifier

**Trust Level:** Policy-based (Requires trust registry)

**What Verifiers Should Trust:**
- Credentials from trusted issuers only
- Valid cryptographic proofs
- Non-revoked credentials
- Non-expired credentials

**What Verifiers Should NOT Trust:**
- Credentials from unknown issuers
- Credentials without valid proofs
- Revoked or expired credentials
- Unverified credential claims

```kotlin
// ✅ Good: Verify issuer trust before accepting credential
val verification = trustWeave.verify(credential)

when (verification) {
    is VerificationResult.Valid -> {
        // Additional trust check via the trust DSL (suspending)
        var issuerTrusted = false
        trustWeave.trust {
            issuerTrusted = isTrusted(
                issuerDid = credential.issuer.id.value,
                credentialType = credential.type.firstOrNull()?.value
            )
        }

        if (!issuerTrusted) {
            throw SecurityException("Issuer not trusted")
        }

        // Now safe to use credential
    }
    is VerificationResult.Invalid -> {
        throw SecurityException("Credential invalid: ${verification.allErrors.joinToString()}")
    }
}

// ❌ Bad: Accepting credentials without trust checks
val verification = trustWeave.verify(credential)
if (verification is VerificationResult.Valid) {
    // No trust registry check - accepting untrusted credentials!
}
```

### 5. Holder → Wallet Storage

**Trust Level:** Storage-dependent

**What to Trust:**
- Encrypted storage (if properly configured)
- Access control mechanisms
- Backup systems

**What NOT to Trust:**
- Plain text storage
- Unencrypted network transmission
- Unauthorized access

```kotlin
import org.trustweave.trust.types.getOrThrow

// ✅ Good: configure a persistent provider and pass secrets via typed options (exact keys depend on provider)
val wallet = trustWeave.wallet {
    holder(holderDid)
    provider("file") // or "database", etc.
    option("encryptionKey", secureKey)
    option("storagePath", "/secure/storage")
    option("encryptionAlgorithm", "AES-256-GCM")
}.getOrThrow()

// ❌ Bad: default in-memory / no encryption for regulated data
val insecure = trustWeave.wallet {
    holder(holderDid)
    provider("inMemory")
}.getOrThrow()
```

## Trust Assumptions

### What TrustWeave Assumes You Trust

1. **Your KMS Provider**
   - Keys are stored securely
   - Access controls are enforced
   - Keys are not exposed

2. **Your DID Method**
   - DID resolution is correct
   - DID documents are authentic
   - Key rotation is secure

3. **Your Trust Registry**
   - Trust anchors are correctly configured
   - Trust relationships are accurate
   - Registry data is current

### What TrustWeave Does NOT Trust

1. **External DIDs**
   - Always verify DID resolution results
   - Validate DID documents
   - Check key validity

2. **External Credentials**
   - Always verify proofs
   - Check revocation status
   - Validate issuer trust

3. **Network Data**
   - Verify blockchain anchors
   - Validate service responses
   - Check data integrity

## Establishing Trust Relationships

### 1. Trust Registry Configuration

Configure which issuers you trust for which credential types:

```kotlin
import org.trustweave.trust.dsl.credential.TrustProviders.IN_MEMORY
val trustweave = TrustWeave.build {
    trust {
        provider(IN_MEMORY)
    }
}

// Add trust anchors (call inside a suspend context)
trustweave.trust {
    addAnchor("did:key:university") {
        credentialTypes("EducationCredential", "DegreeCredential")
        description("Trusted university")
    }

    addAnchor("did:key:government") {
        credentialTypes("GovernmentID", "Passport")
        description("Government authority")
    }
}
```

### 2. Verify Before Trust

Always verify before trusting:

```kotlin
// ✅ Good: Verify then trust
val verification = trustWeave.verify(credential)
when (verification) {
    is VerificationResult.Valid -> {
        // Verify issuer is in trust registry (via trust DSL)
        var isTrusted = false
        trustWeave.trust {
            isTrusted = isTrusted(
                issuerDid = credential.issuer.id.value,
                credentialType = "EducationCredential"
            )
        }

        if (isTrusted) {
            // Safe to trust this credential
            acceptCredential(credential)
        }
    }
    else -> {
        rejectCredential("Invalid credential")
    }
}
```

### 3. Trust Verification Chain

Establish a chain of trust:

```kotlin
// Verify credential (use exhaustive when on the sealed VerificationResult)
val credentialValid = trustWeave.verify(credential) is VerificationResult.Valid

// Verify issuer trust
val issuerTrusted = verifyIssuerTrust(credential.issuer)

// Verify issuer's DID resolution
val issuerDidResolved = verifyIssuerDidResolution(credential.issuer)

// Only trust if all checks pass
if (credentialValid && issuerTrusted && issuerDidResolved) {
    acceptCredential(credential)
}
```

## Security Zones

### Zone 1: Fully Trusted (Your Control)

- Your application code
- Your KMS configuration
- Your trust registry
- Your key material

**Actions:**
- Store securely
- Encrypt at rest
- Control access strictly

### Zone 2: Verified Trust (Cryptographically Verified)

- Valid credentials with proofs
- Verified DID documents
- Confirmed blockchain anchors

**Actions:**
- Verify cryptographically
- Check revocation status
- Validate expiration

### Zone 3: Untrusted (External/Unknown)

- External credentials
- External DIDs
- External services
- Network data

**Actions:**
- Always verify
- Never trust by default
- Validate all inputs
- Handle failures gracefully

## Trust Verification Checklist

Before trusting any data, verify:

- **Cryptographic Verification**
  - Credential proof is valid
  - DID document signature is valid
  - Blockchain anchor is verified

- **Trust Registry**
  - Issuer is in trust registry
  - Credential type is allowed
  - Trust relationship is current

- **Status Checks**
  - Credential is not revoked
  - Credential is not expired
  - DID is not deactivated

- **Data Integrity**
  - Data matches expected format
  - No tampering detected
  - Timestamps are valid

## Best Practices

### 1. Principle of Least Trust

Only trust what you must, and verify everything:

```kotlin
// ✅ Good: Verify everything
suspend fun acceptCredential(credential: VerifiableCredential): Boolean {
    // 1. Verify proof, expiration, and revocation (defaults on trustWeave.verify)
    if (trustWeave.verify(credential) !is VerificationResult.Valid) {
        return false
    }

    // 2. Check issuer trust (application policy) via the trust DSL
    var issuerTrusted = false
    trustWeave.trust {
        issuerTrusted = isTrusted(
            issuerDid = credential.issuer.id,
            credentialType = credential.type.firstOrNull()
        )
    }
    if (!issuerTrusted) {
        return false
    }

    return true
}
```

### 2. Defense in Depth

Multiple layers of verification:

```kotlin
// Layer 1: Cryptographic verification
val verification = trustWeave.verify(credential)

// Layer 2: Trust registry check (suspend)
var issuerTrusted = false
trustWeave.trust {
    issuerTrusted = isTrusted(credential.issuer.id, credential.type.firstOrNull())
}

// Layer 3: Business logic validation
val businessValid = validateBusinessRules(credential)

// All layers must pass
return verification is VerificationResult.Valid && issuerTrusted && businessValid
```

### 3. Fail Securely

When in doubt, reject:

```kotlin
// ✅ Good: Fail securely
when (val verification = trustWeave.verify(credential)) {
    is VerificationResult.Valid -> {
        // Additional checks
        if (!isIssuerTrusted(credential.issuer)) {
            return false  // Reject if unsure
        }
        return true
    }
    else -> {
        return false  // Reject on any uncertainty
    }
}
```

### 4. Audit Trust Decisions

Log all trust decisions for auditing:

```kotlin
suspend fun acceptCredential(credential: VerifiableCredential): Boolean {
    val verification = trustWeave.verify(credential)
    var issuerTrusted = false
    trustWeave.trust {
        issuerTrusted = isTrusted(credential.issuer.id.value, credential.type.firstOrNull()?.value)
    }

    val verificationValid = verification is VerificationResult.Valid
    val accepted = verificationValid && issuerTrusted

    // Audit log
    auditLogger.info(
        "Credential trust decision",
        mapOf(
            "credentialId" to credential.id,
            "issuer" to credential.issuer.id.value,
            "accepted" to accepted,
            "verificationValid" to verificationValid,
            "issuerTrusted" to issuerTrusted
        )
    )

    return accepted
}
```

## Trust Boundary Violations

### Common Mistakes

1. **Trusting External DIDs Without Verification**
   ```kotlin
   // ❌ Bad: Blindly trusting external DID
   val did = "did:web:example.com"  // Could be malicious
   // No verification!
   ```

2. **Accepting Credentials Without Trust Checks**
   ```kotlin
   import org.trustweave.credential.results.VerificationResult

   // ❌ Bad: No trust registry check
   if (verification is VerificationResult.Valid) {
       acceptCredential(credential)  // Could be from untrusted issuer
   }
   ```

3. **Trusting Unverified Blockchain Data**
   ```kotlin
   // ❌ Bad: No verification
   val data = trustweave.blockchains.read(...)
   // No verification that data matches!
   ```

## Summary

- **Define clear trust boundaries** in your application
- **Verify everything** that crosses trust boundaries
- **Use trust registries** to manage trusted issuers
- **Fail securely** - reject when uncertain
- **Audit trust decisions** for security monitoring

## Related Documentation

- [Security Best Practices](./README.md) - General security guidelines
- [Trust Registry](../core-concepts/trust-registry.md) - Managing trust relationships
- [Error Handling](../advanced/error-handling.md) - Handling verification failures

