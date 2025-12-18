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
- ✅ Configure KMS with production-grade providers
- ✅ Use secure key storage
- ✅ Validate inputs before passing to TrustWeave
- ✅ Implement proper error handling

```kotlin
// ✅ Good: Secure configuration
val trustweave = TrustWeave.create {
    kms = AwsKeyManagementService(
        region = "us-east-1",
        credentials = secureCredentials
    )
    did {
        method(WEB) {
            domain("yourdomain.com")
        }
    }
}

// ❌ Bad: Insecure configuration
val trustweave = TrustWeave.create {
    kms = InMemoryKeyManagementService()  // Only for testing!
}
```

### 2. TrustWeave SDK → External Services

**Trust Level:** Variable (Depends on service)

**External Services:**
- **DID Resolution Services** (e.g., Universal Resolver)
- **Blockchain Networks** (e.g., Ethereum, Algorand)
- **Key Management Services** (e.g., AWS KMS, Azure Key Vault)
- **Trust Registries** (e.g., External trust anchor registries)

**Security Responsibilities:**
- ✅ Verify TLS certificates
- ✅ Validate service responses
- ✅ Use authenticated connections
- ✅ Implement timeouts and retry logic
- ✅ Handle service failures gracefully

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

// ❌ Bad: Blindly trusting external service
val document = trustweave.resolveDid(did)
// No validation - could be tampered with!
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
val verification = trustweave.verifyCredential(credential)

when (verification) {
    is CredentialVerificationResult.Valid -> {
        // Additional trust check
        val issuerTrusted = trustweave.trust.isTrustedIssuer(
            issuer = IssuerIdentity(credential.issuer),
            credentialType = CredentialType(credential.type.first())
        )
        
        if (!issuerTrusted) {
            throw SecurityException("Issuer not trusted")
        }
        
        // Now safe to use credential
    }
    is CredentialVerificationResult.Invalid -> {
        throw SecurityException("Credential invalid: ${verification.errors}")
    }
}

// ❌ Bad: Accepting credentials without trust checks
val verification = trustweave.verifyCredential(credential)
if (verification.valid) {
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
// ✅ Good: Secure wallet configuration
val wallet = trustweave.createWallet(
    holderDid = holderDid.id,
    options = walletOptions {
        encryptionKey = secureKey  // From secure key management
        storagePath = "/secure/storage"
        property("encryptionAlgorithm", "AES-256-GCM")
    }
)

// ❌ Bad: Insecure wallet storage
val wallet = trustweave.createWallet(
    holderDid = holderDid.id
    // No encryption, stored in plain text!
)
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
val trustweave = TrustWeave.build {
    trust {
        provider(IN_MEMORY)
    }
}

// Add trust anchors
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
val verification = trustweave.verifyCredential(credential)
when (verification) {
    is CredentialVerificationResult.Valid -> {
        // Verify issuer is in trust registry
        val isTrusted = trustweave.trust.isTrustedIssuer(
            issuer = IssuerIdentity(credential.issuer),
            credentialType = CredentialType("EducationCredential")
        )
        
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
// Verify credential
val credentialValid = verifyCredential(credential)

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

- [ ] **Cryptographic Verification**
  - [ ] Credential proof is valid
  - [ ] DID document signature is valid
  - [ ] Blockchain anchor is verified

- [ ] **Trust Registry**
  - [ ] Issuer is in trust registry
  - [ ] Credential type is allowed
  - [ ] Trust relationship is current

- [ ] **Status Checks**
  - [ ] Credential is not revoked
  - [ ] Credential is not expired
  - [ ] DID is not deactivated

- [ ] **Data Integrity**
  - [ ] Data matches expected format
  - [ ] No tampering detected
  - [ ] Timestamps are valid

## Best Practices

### 1. Principle of Least Trust

Only trust what you must, and verify everything:

```kotlin
// ✅ Good: Verify everything
fun acceptCredential(credential: VerifiableCredential): Boolean {
    // 1. Verify proof
    val verification = trustweave.verifyCredential(credential)
    if (verification !is CredentialVerificationResult.Valid) {
        return false
    }
    
    // 2. Check issuer trust
    val issuerTrusted = trustweave.trust.isTrustedIssuer(...)
    if (!issuerTrusted) {
        return false
    }
    
    // 3. Check revocation
    if (!verification.notRevoked) {
        return false
    }
    
    // 4. Check expiration
    if (!verification.notExpired) {
        return false
    }
    
    return true
}
```

### 2. Defense in Depth

Multiple layers of verification:

```kotlin
// Layer 1: Cryptographic verification
val verification = trustweave.verifyCredential(credential)

// Layer 2: Trust registry check
val issuerTrusted = trustweave.trust.isTrustedIssuer(...)

// Layer 3: Business logic validation
val businessValid = validateBusinessRules(credential)

// All layers must pass
return verification.valid && issuerTrusted && businessValid
```

### 3. Fail Securely

When in doubt, reject:

```kotlin
// ✅ Good: Fail securely
when (val verification = trustweave.verifyCredential(credential)) {
    is CredentialVerificationResult.Valid -> {
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
fun acceptCredential(credential: VerifiableCredential): Boolean {
    val verification = trustweave.verifyCredential(credential)
    val issuerTrusted = trustweave.trust.isTrustedIssuer(...)
    
    val accepted = verification.valid && issuerTrusted
    
    // Audit log
    auditLogger.info(
        "Credential trust decision",
        mapOf(
            "credentialId" to credential.id,
            "issuer" to credential.issuer,
            "accepted" to accepted,
            "verificationValid" to (verification is CredentialVerificationResult.Valid),
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
   // ❌ Bad: No trust registry check
   if (verification.valid) {
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

