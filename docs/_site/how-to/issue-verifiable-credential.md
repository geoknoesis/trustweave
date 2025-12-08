# How to Issue a Verifiable Credential

## Purpose

This guide shows you how to issue a Verifiable Credential (VC) using TrustWeave. You'll create a cryptographically signed credential that can be verified by anyone without contacting the issuer. By the end, you'll have issued a credential with a digital proof that demonstrates authenticity and integrity.

**What you'll accomplish:**
- Configure TrustWeave with key management and DID methods
- Create an issuer identity (DID)
- Build and issue a credential with a cryptographic proof
- Verify the credential to confirm it was issued correctly

**Why this matters:**
Verifiable Credentials enable trust without intermediaries. They're tamper-proof, privacy-preserving, and instantly verifiable—essential for identity systems, academic credentials, professional certifications, and more.

---

## Prerequisites

- **Kotlin**: 2.2.0 or higher
- **Java**: 21 or higher
- **TrustWeave SDK**: Latest version
- **Dependencies**: TrustWeave-core and TrustWeave-testkit (for in-memory KMS)

**Required imports:**
```kotlin
import com.trustweave.trust.TrustWeave
import com.trustweave.trust.types.ProofType
import com.trustweave.trust.types.VerificationResult
import com.trustweave.trust.types.DidCreationResult
import com.trustweave.trust.types.IssuanceResult
import com.trustweave.did.resolver.DidResolutionResult
import kotlinx.coroutines.runBlocking
import java.time.Instant
import java.time.temporal.ChronoUnit
```

**Configuration needed:**
- Key Management Service (KMS) provider
- DID method (e.g., `did:key`)
- Signing key for the issuer

---

## Before You Begin

A Verifiable Credential is a tamper-proof document that proves something about a subject. TrustWeave handles the cryptographic complexity—you focus on the credential content.

**When to use this:**
- Issuing academic degrees, professional certifications, or identity documents
- Creating attestations that need to be independently verifiable
- Building systems where trust must be established without a central authority

**How it fits in a workflow:**
```kotlin
// 1. Configure TrustWeave
val trustWeave = TrustWeave.build { ... }

// 2. Create issuer DID
val issuerDid = trustWeave.createDid { ... }

// 3. Issue credential
val credential = trustWeave.issue { ... }

// 4. Verify credential
val result = trustWeave.verify { ... }
```

---

## Step-by-Step Guide

### Step 1: Configure TrustWeave

Set up TrustWeave with a Key Management Service and DID method. For development, use the in-memory KMS.

```kotlin
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
    
    credentials {
        defaultProofType(ProofType.Ed25519Signature2020)
    }
}
```

**What this does:**
- `keys { }` configures the Key Management Service for signing
- `did { }` registers the DID method (e.g., `did:key`) for creating issuer identities
- `credentials { }` sets the default proof type for cryptographic signatures

> **Note:** For production, replace `"inMemory"` with a secure KMS provider (AWS KMS, CyberArk, etc.). See the [KMS plugins documentation](../plugins/kms-plugins.md) for options.

---

### Step 2: Create an Issuer Identity

Create a DID (Decentralized Identifier) for the credential issuer. This identity will sign the credential.

```kotlin
import com.trustweave.trust.types.DidCreationResult

val didResult = trustWeave.createDid {
    method("key")
    algorithm("Ed25519")
}

val issuerDid = when (didResult) {
    is DidCreationResult.Success -> {
        println("Issuer DID: ${didResult.did.value}")
        didResult.did
    }
    else -> {
        throw IllegalStateException("Failed to create DID: ${didResult.reason}")
    }
}
```

**What this does:**
- Creates a new DID using the `did:key` method
- Generates an Ed25519 key pair for signing
- Returns a `Did` object containing the issuer's identifier

The DID document includes verification methods that prove ownership of the signing key.

---

### Step 3: Get the Signing Key

Retrieve the key ID from the issuer's DID document. This key will be used to sign the credential.

```kotlin
// Resolve the DID to get the verification method
val resolutionResult = trustWeave.resolveDid(issuerDid)
val issuerDocument = when (resolutionResult) {
    is DidResolutionResult.Success -> resolutionResult.document
    else -> throw IllegalStateException("Failed to resolve issuer DID: ${resolutionResult}")
}

// Get the first verification method key ID
val verificationMethod = issuerDocument.verificationMethod.firstOrNull()
    ?: throw IllegalStateException("No verification method found")

// Extract key ID from verification method (e.g., "did:key:xxx#key-1" -> "key-1")
val keyId = verificationMethod.id.substringAfter("#")

println("Signing key ID: $keyId")
```

**What this does:**
- Resolves the issuer DID using `resolveDid()` which returns a sealed result type
- Extracts the DID document from the success result
- Gets the verification method (public key) that will be used for signing
- Extracts the key ID fragment (the part after `#`) which references the private key stored in the KMS

> **Tip:** In production, you might store the key ID separately or derive it from your key management workflow.

---

### Step 4: Build the Credential

Define the credential content using the DSL builder. Specify the subject, types, and metadata.

```kotlin
import com.trustweave.trust.types.IssuanceResult

val issuanceResult = trustWeave.issue {
    credential {
        id("https://example.edu/credentials/degree-123")
        type("DegreeCredential", "BachelorDegreeCredential")
        issuer(issuerDid.value)
        subject {
            id("did:key:student-456")
            "degree" {
                "type" to "BachelorDegree"
                "name" to "Bachelor of Science in Computer Science"
                "university" to "Example University"
                "graduationDate" to "2023-05-15"
                "gpa" to "3.8"
            }
        }
        issued(Instant.now())
        expires(365 * 10, ChronoUnit.DAYS) // Valid for 10 years
    }
    signedBy(issuerDid = issuerDid.value, keyId = keyId)
}

val credential = when (issuanceResult) {
    is IssuanceResult.Success -> issuanceResult.credential
    else -> throw IllegalStateException("Failed to issue credential: ${issuanceResult.reason}")
}
```

**What this does:**
- `credential { }` builds the credential structure
  - `id()` sets a unique credential identifier
  - `type()` specifies credential types (VerifiableCredential is added automatically)
  - `issuer()` sets the issuer DID
  - `subject { }` defines the credential claims about the subject
  - `issued()` and `expires()` set validity period
- `by()` specifies the issuer DID and key ID for signing

**Key concepts:**
- **Subject**: The entity the credential is about (e.g., a student)
- **Claims**: Properties asserted about the subject (e.g., degree, GPA)
- **Types**: Semantic meaning of the credential (e.g., DegreeCredential)

---

### Step 5: Issue the Credential

The `issue { }` block automatically generates a cryptographic proof and attaches it to the credential.

```kotlin
val issuanceResult = trustWeave.issue {
    credential {
        // ... credential definition from Step 4
    }
    signedBy(issuerDid = issuerDid.value, keyId = keyId)
    withProof(ProofType.Ed25519Signature2020)
}

val issuedCredential = when (issuanceResult) {
    is IssuanceResult.Success -> issuanceResult.credential
    else -> throw IllegalStateException("Failed to issue credential: ${issuanceResult.reason}")
}
```

**What happens internally:**
1. TrustWeave canonicalizes the credential JSON (JCS)
2. Computes a cryptographic digest
3. Signs the digest using the issuer's private key (via KMS)
4. Creates a proof object with the signature
5. Attaches the proof to the credential

**Result:**
The credential now contains a `proof` property that anyone can verify without contacting the issuer.

---

### Step 6: Verify the Credential

Verify the credential to confirm the proof is valid and the credential hasn't been tampered with.

```kotlin
val verificationResult = trustWeave.verify {
    credential(issuedCredential)
    checkExpiration()
}

when (verificationResult) {
    is VerificationResult.Valid -> {
        println("✅ Credential is valid!")
        println("  - Proof valid: ${verificationResult.proofValid}")
        println("  - Issuer valid: ${verificationResult.issuerValid}")
    }
    is VerificationResult.Invalid.Expired -> {
        println("❌ Credential expired at ${verificationResult.expiredAt}")
    }
    is VerificationResult.Invalid.InvalidProof -> {
        println("❌ Invalid proof: ${verificationResult.reason}")
    }
    else -> {
        println("❌ Verification failed")
        verificationResult.errors.forEach { println("  - $it") }
    }
}
```

**What this does:**
- Resolves the issuer DID to get the public key
- Verifies the cryptographic signature
- Checks expiration (if enabled)
- Returns a sealed result type for exhaustive handling

---

## Complete Example

Here's a complete, runnable example that brings all steps together:

```kotlin
import com.trustweave.trust.TrustWeave
import com.trustweave.trust.types.ProofType
import com.trustweave.trust.types.VerificationResult
import kotlinx.coroutines.runBlocking
import java.time.Instant
import java.time.temporal.ChronoUnit

fun main() = runBlocking {
    // Step 1: Configure TrustWeave
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
        
        credentials {
            defaultProofType(ProofType.Ed25519Signature2020)
        }
    }
    
    // Step 2: Create issuer DID
    val didResult = trustWeave.createDid {
        method("key")
    }
    val issuerDid = when (didResult) {
        is DidCreationResult.Success -> {
            println("Issuer DID: ${didResult.did.value}")
            didResult.did
        }
        else -> {
            println("Failed to create DID: ${didResult.reason}")
            return@runBlocking
        }
    }
    
    // Step 3: Get signing key
    val resolutionResult = trustWeave.resolveDid(issuerDid)
    val issuerDocument = when (resolutionResult) {
        is DidResolutionResult.Success -> resolutionResult.document
        else -> throw IllegalStateException("Failed to resolve issuer DID: ${resolutionResult}")
    }
    val verificationMethod = issuerDocument.verificationMethod.firstOrNull()
        ?: throw IllegalStateException("No verification method found")
    val keyId = verificationMethod.id.substringAfter("#")
    
    // Step 4 & 5: Issue credential
    val issuanceResult = trustWeave.issue {
        credential {
            id("https://example.edu/credentials/degree-123")
            type("DegreeCredential")
            issuer(issuerDid.value)
            subject {
                id("did:key:student-456")
                "degree" {
                    "name" to "Bachelor of Science"
                    "university" to "Example University"
                }
            }
            issued(Instant.now())
            expires(365 * 10, ChronoUnit.DAYS)
        }
        signedBy(issuerDid = issuerDid.value, keyId = keyId)
    }
    
    val issuedCredential = when (issuanceResult) {
        is IssuanceResult.Success -> issuanceResult.credential
        else -> {
            println("Failed to issue credential: ${issuanceResult.reason}")
            return@runBlocking
        }
    }
    
    println("Credential issued:")
    println("  - ID: ${issuedCredential.id}")
    println("  - Issuer: ${issuedCredential.issuer}")
    println("  - Has proof: ${issuedCredential.proof != null}")
    
    // Step 6: Verify credential
    val result = trustWeave.verify {
        credential(issuedCredential)
        checkExpiration()
    }
    
    when (result) {
        is VerificationResult.Valid -> println("✅ Credential verified successfully")
        else -> println("❌ Verification failed: ${result.errors}")
    }
}
```

---

## Visual Flow Diagram

```
┌─────────────────────────────────────────────────────────────┐
│                    Credential Issuance Flow                 │
└─────────────────────────────────────────────────────────────┘

┌──────────────┐
│   Step 1     │  Configure TrustWeave
│  Configure   │  • KMS Provider (inMemory/AWS/CyberArk)
└──────┬───────┘  • DID Method (key/web/ion)
       │          • Proof Type (Ed25519Signature2020)
       ▼
┌──────────────┐
│   Step 2     │  Create Issuer Identity
│ Create DID   │  • Generate key pair
└──────┬───────┘  • Create DID document
       │          • Store keys in KMS
       ▼
┌──────────────┐
│   Step 3     │  Get Signing Key
│ Extract Key  │  • Resolve DID document
└──────┬───────┘  • Get verification method
       │          • Reference private key in KMS
       ▼
┌──────────────┐
│   Step 4     │  Build Credential
│ Build VC     │  • Define subject & claims
└──────┬───────┘  • Set types & metadata
       │          • Specify issuer & validity
       ▼
┌──────────────┐
│   Step 5     │  Issue Credential
│ Sign & Issue │  • Canonicalize JSON (JCS)
└──────┬───────┘  • Compute digest
       │          • Sign with private key
       │          • Attach proof
       ▼
┌──────────────┐
│   Step 6      │  Verify Credential
│  Verify VC   │  • Resolve issuer DID
└──────┬───────┘  • Get public key
       │          • Verify signature
       │          • Check expiration
       ▼
    ✅ Valid
    or
    ❌ Invalid
```

---

## Verification Step

After issuing, verify the credential programmatically:

```kotlin
// Quick verification check
val isValid = when (trustWeave.verify {
    credential(issuedCredential)
    checkExpiration()
}) {
    is VerificationResult.Valid -> true
    else -> false
}

println("Credential is ${if (isValid) "valid" else "invalid"}")
```

**Expected output:**
```
Issuer DID: did:key:z6Mk...
Credential issued:
  - ID: https://example.edu/credentials/degree-123
  - Issuer: did:key:z6Mk...
  - Has proof: true
✅ Credential verified successfully
```

**What to check:**
- ✅ Credential has a `proof` property
- ✅ Proof type matches (e.g., `Ed25519Signature2020`)
- ✅ Verification returns `VerificationResult.Valid`
- ✅ No errors in verification result

---

## Common Errors & Troubleshooting

### Error: "Issuer identity is required"

**Problem:** The `by()` method wasn't called or the issuer DID/key ID is missing.

**Solution:**
```kotlin
// ❌ Missing issuer identity
trustWeave.issue {
    credential { ... }
    // Missing: signedBy(issuerDid = ..., keyId = ...)
}

// ✅ Correct
trustWeave.issue {
    credential { ... }
    signedBy(issuerDid = issuerDid.value, keyId = keyId)
}
```

---

### Error: "KMS is not configured"

**Problem:** TrustWeave wasn't configured with a Key Management Service.

**Solution:**
```kotlin
// ✅ Ensure KMS is configured
val trustWeave = TrustWeave.build {
    keys {
        provider("inMemory")  // or your KMS provider
        algorithm("Ed25519")
    }
    // ... rest of config
}
```

---

### Error: "DID method 'key' not registered"

**Problem:** The DID method wasn't registered in the configuration.

**Solution:**
```kotlin
// ✅ Register DID method
val trustWeave = TrustWeave.build {
    did {
        method("key") {  // Register the method
            algorithm("Ed25519")
        }
    }
    // ... rest of config
}
```

---

### Error: "No verification method found"

**Problem:** The issuer DID document doesn't have a verification method, or the DID wasn't resolved correctly.

**Solution:**
```kotlin
// ✅ Ensure DID is created and resolved
val issuerDid = trustWeave.createDid {
    method("key")
    algorithm("Ed25519")
}

// Wait a moment for DID to be available, then resolve
val issuerDocument = trustWeave.getDslContext().resolveDid(issuerDid)
    ?: throw IllegalStateException("DID not found")

val keyId = issuerDocument.verificationMethod.firstOrNull()?.id
    ?: throw IllegalStateException("No verification method")
```

---

### Error: "Invalid proof" during verification

**Problem:** The credential was modified after issuance, or the issuer DID can't be resolved.

**Solution:**
```kotlin
// ✅ Ensure issuer DID is resolvable
val result = trustWeave.verify {
    credential(issuedCredential)
    checkExpiration()
}

// Check specific error
when (result) {
    is VerificationResult.Invalid.InvalidProof -> {
        println("Proof invalid: ${result.reason}")
        // Common causes:
        // - Credential was modified after issuance
        // - Issuer DID can't be resolved
        // - Public key doesn't match signing key
    }
    // ... other cases
}
```

---

### Warning: Credential expires soon

**Problem:** The credential expiration date is in the past or very near.

**Solution:**
```kotlin
// ✅ Set appropriate expiration
credential {
    // ...
    issued(Instant.now())
    expires(365 * 10, ChronoUnit.DAYS)  // 10 years
    // or
    expires(Instant.now().plus(365, ChronoUnit.DAYS))  // 1 year from now
}
```

---

## Next Steps

Now that you can issue credentials, here are ways to extend your implementation:

### 1. Add Revocation Support

Enable credential revocation using status lists:

```kotlin
val credential = trustWeave.issue {
    credential { ... }
    signedBy(issuerDid = issuerDid.value, keyId = keyId)
    withRevocation()  // Auto-creates status list
}
```

See: [How to Revoke Credentials](./revoke-credentials.md)

---

### 2. Store Credentials in a Wallet

Let credential holders store and manage their credentials:

```kotlin
val wallet = trustWeave.wallet {
    holder(holderDid.value)
    enableOrganization()
}

val stored = issuedCredential.storeIn(wallet)
```

See: [How to Manage Credentials in Wallets](./manage-wallet-credentials.md)

---

### 3. Create Verifiable Presentations

Allow holders to present credentials selectively:

```kotlin
val presentation = wallet.presentation {
    fromWallet(credentialId)
    holder(holderDid.value)
    challenge("job-application-123")
}
```

See: [How to Create Verifiable Presentations](./create-presentations.md)

---

### 4. Configure Trust Registry

Establish trust anchors for credential verification:

```kotlin
trustWeave.trust {
    addAnchor(issuerDid.value) {
        credentialTypes("DegreeCredential")
        description("Trusted university")
    }
}
```

See: [How to Configure Trust Registries](./configure-trust-registry.md)

---

## Summary

You've learned how to issue Verifiable Credentials with TrustWeave:

✅ **Configured TrustWeave** with KMS and DID methods  
✅ **Created an issuer identity** using DIDs  
✅ **Built and issued a credential** with cryptographic proof  
✅ **Verified the credential** to confirm authenticity  

**Key takeaways:**
- TrustWeave handles cryptographic complexity—you focus on credential content
- The DSL builder makes credential creation type-safe and ergonomic
- Sealed result types (`VerificationResult`) enable exhaustive error handling
- Credentials are tamper-proof and independently verifiable

**What's next:**
- Explore revocation for credential lifecycle management
- Set up wallets for credential storage and organization
- Configure trust registries for production deployments
- Integrate with blockchain anchoring for additional immutability

For more examples, see the [scenarios documentation](../scenarios/README.md).

