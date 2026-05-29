---
parent: How-To Guides
nav_order: 50
---
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

- **Kotlin**: 2.2.21+ or higher
- **Java**: 21 or higher
- **TrustWeave SDK**: Latest version
- **Dependencies**: `distribution-all` and `testkit` (for in-memory KMS)

**Required imports:**
```kotlin
import org.trustweave.credential.model.ProofType
import org.trustweave.credential.results.IssuanceResult
import org.trustweave.credential.results.VerificationResult
import org.trustweave.did.resolver.DidResolutionResult
import org.trustweave.did.resolver.errorMessage
import org.trustweave.trust.TrustWeave
import org.trustweave.trust.types.DidCreationResult
import org.trustweave.trust.types.getOrThrow
import org.trustweave.credential.results.getOrThrow
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.Clock
import kotlin.time.Duration.Companion.days
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
val (issuerDid, issuerDoc) = trustWeave.createDid { ... }.getOrThrow()

// 3. Issue credential (IssuanceResult)
val credential = trustWeave.issue { ... }.getOrThrow()

// 4. Verify credential (VerificationResult)
val result = trustWeave.verify { ... }
```

---

## Step-by-Step Guide

### Step 1: Configure TrustWeave

Set up TrustWeave with a Key Management Service and DID method. For development, use the in-memory KMS.

```kotlin
import org.trustweave.credential.model.ProofType
import org.trustweave.trust.TrustWeave
import org.trustweave.trust.dsl.credential.DidMethods.KEY
import org.trustweave.trust.dsl.credential.KeyAlgorithms.ED25519
import org.trustweave.trust.dsl.credential.KmsProviders.IN_MEMORY

val trustWeave = TrustWeave.build {
    // KMS and DID methods auto-discovered via SPI
    keys {
        provider(IN_MEMORY)
        algorithm(ED25519)
    }
    
    did {
        method(KEY) {
            algorithm(ED25519)
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

> **Note:** For production, replace `"inMemory"` with a secure KMS provider (AWS KMS, CyberArk, etc.). See the [KMS plugins documentation](../api-reference/plugins.md#key-management-service-kms-plugins) for options.

---

### Step 2: Create an Issuer Identity

Create a DID (Decentralized Identifier) for the credential issuer. This identity will sign the credential.

```kotlin
// Create issuer DID (uses default method from config)
val (issuerDid, issuerDoc) = trustWeave.createDid().getOrThrow()
println("Issuer DID: ${issuerDid.value}")
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
import org.trustweave.did.identifiers.extractKeyId

// Extract key ID from DID document (already available from createDid().getOrThrow())
val keyId = issuerDoc.verificationMethod.firstOrNull()?.extractKeyId()
    ?: throw IllegalStateException("No verification method found")
println("Signing key ID: $keyId")
```

**What this does:**
- Gets the verification method (public key) that will be used for signing
- Extracts the key ID fragment using the type-safe `extractKeyId()` extension function
- Returns the key ID string (e.g., "key-1") which references the private key stored in the KMS

> **Tip:** In production, you might store the key ID separately or derive it from your key management workflow.

---

### Step 4: Build the Credential

Define the credential content using the DSL builder. Specify the subject, types, and metadata.

```kotlin
import org.trustweave.credential.results.IssuanceResult
import org.trustweave.did.identifiers.Did
import kotlinx.datetime.Clock
import kotlin.time.Duration.Companion.days

val credential = trustWeave.issue {
    credential {
        id("https://example.edu/credentials/degree-123")
        type("DegreeCredential", "BachelorDegreeCredential")
        issuer(issuerDid)
        subject {
            id(Did("did:key:student-456"))
            "degree" {
                "type" to "BachelorDegree"
                "name" to "Bachelor of Science in Computer Science"
                "university" to "Example University"
                "graduationDate" to "2023-05-15"
                "gpa" to "3.8"
            }
        }
        issued(Clock.System.now())
        expires((365 * 10).days) // Valid for 10 years
    }
    signedBy(issuerDid)
}.getOrThrow()
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
val issuedCredential = trustWeave.issue {
    credential {
        // ... credential definition from Step 4
    }
    signedBy(issuerDid)
    withProof(ProofSuiteId.VC_LD)
}.getOrThrow()
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
import org.trustweave.credential.results.VerificationResult
import org.trustweave.trust.types.issuerValid
import org.trustweave.trust.types.proofValid

val verificationResult = trustWeave.verify {
    credential(issuedCredential)
    checkExpiration()
}

when (verificationResult) {
    is VerificationResult.Valid -> {
        println("✅ Credential is valid!")
        println("  - Proof valid: ${verificationResult.proofValid}")
        println("  - Issuer resolved: ${verificationResult.issuerValid}")
    }
    is VerificationResult.Invalid -> {
        println("❌ Verification failed: ${verificationResult.allErrors.joinToString("; ")}")
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
import org.trustweave.trust.TrustWeave
import org.trustweave.credential.model.ProofType
import org.trustweave.credential.results.VerificationResult
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.Clock
import kotlin.time.Duration.Companion.days
import org.trustweave.trust.types.getOrThrowDid
import org.trustweave.did.resolver.DidResolutionResult
import org.trustweave.did.identifiers.extractKeyId
import org.trustweave.trust.dsl.credential.DidMethods.KEY
import org.trustweave.trust.dsl.credential.KeyAlgorithms.ED25519
import org.trustweave.trust.dsl.credential.KmsProviders.IN_MEMORY
import org.trustweave.credential.results.getOrThrow

fun main() = runBlocking {
    // Step 1: Configure TrustWeave
    val trustWeave = TrustWeave.build {
        keys {
            provider(IN_MEMORY)
            algorithm(ED25519)
        }
        
        did {
            method(KEY) {
                algorithm(ED25519)
            }
        }
        
        credentials {
            defaultProofType(ProofType.Ed25519Signature2020)
        }
    }
    
    // Step 2: Create issuer DID
    
    val issuerDid = trustWeave.createDid {
        method(KEY)
    }.getOrThrowDid()
    println("Issuer DID: ${issuerDid.value}")
    
    // Step 3: Get signing key
    val issuerDocument = when (val res = trustWeave.resolveDid(issuerDid)) {
    is DidResolutionResult.Success -> res.document
    else -> throw IllegalStateException(res.errorMessage ?: "Failed to resolve DID")
}
    val verificationMethod = issuerDocument.verificationMethod.firstOrNull()
        ?: throw IllegalStateException("No verification method found")
    val keyId = verificationMethod.extractKeyId()
        ?: throw IllegalStateException("Failed to extract key ID")
    
    // Step 4 & 5: Issue credential
    
    val issuedCredential = trustWeave.issue {
        credential {
            id("https://example.edu/credentials/degree-123")
            type("DegreeCredential")
            issuer(issuerDid)
            subject {
                id("did:key:student-456")
                "degree" {
                    "name" to "Bachelor of Science"
                    "university" to "Example University"
                }
            }
            issued(Clock.System.now())
            expires((365 * 10).days)
        }
        signedBy(issuerDid = issuerDid, keyId = keyId)
    }.getOrThrow()
    
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
        else -> println("[FAIL] Verification failed: ${result.allErrors.joinToString()}")
    }
}
```

---

## Visual Flow Diagram

```mermaid
flowchart TD
    A[Step 1: Configure TrustWeave<br/>- KMS Provider<br/>- DID Method<br/>- Proof Type] --> B[Step 2: Create Issuer Identity<br/>- Generate key pair<br/>- Create DID document<br/>- Store keys in KMS]
    B --> C[Step 3: Get Signing Key<br/>- Resolve DID document<br/>- Get verification method<br/>- Reference private key]
    C --> D[Step 4: Build Credential<br/>- Define subject & claims<br/>- Set types & metadata<br/>- Specify issuer & validity]
    D --> E[Step 5: Sign & Issue<br/>- Canonicalize JSON (JCS)<br/>- Compute digest<br/>- Sign with private key<br/>- Attach proof]
    E --> F[Step 6: Verify Credential<br/>- Resolve issuer DID<br/>- Get public key<br/>- Verify signature<br/>- Check expiration]
    F --> G{Valid?}
    G -->|Yes| H["[OK] Valid"]
    G -->|No| I["[FAIL] Invalid"]

    style A fill:#1976d2,stroke:#0d47a1,stroke-width:2px,color:#fff
    style B fill:#388e3c,stroke:#1b5e20,stroke-width:2px,color:#fff
    style C fill:#f57c00,stroke:#e65100,stroke-width:2px,color:#fff
    style D fill:#7b1fa2,stroke:#4a148c,stroke-width:2px,color:#fff
    style E fill:#c2185b,stroke:#880e4f,stroke-width:2px,color:#fff
    style F fill:#00796b,stroke:#004d40,stroke-width:2px,color:#fff
    style H fill:#4caf50,stroke:#2e7d32,stroke-width:2px,color:#fff
    style I fill:#f44336,stroke:#c62828,stroke-width:2px,color:#fff
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
- Credential has a `proof` property
- Proof type matches (e.g., `Ed25519Signature2020`)
- Verification returns `VerificationResult.Valid`
- No errors in verification result

---

## Common Errors & Troubleshooting

### Error: "Issuer identity is required"

**Problem:** The `by()` method wasn't called or the issuer DID/key ID is missing.

**Solution:**
```kotlin
// âŒ Missing issuer identity
trustWeave.issue {
    credential { ... }
    // Missing: signedBy(issuerDid = ..., keyId = ...)
}

// ✅ Correct
trustWeave.issue {
    credential { ... }
    signedBy(issuerDid = issuerDid, keyId = keyId)
}
```

---

### Error: "KMS is not configured"

**Problem:** TrustWeave wasn't configured with a Key Management Service.

**Solution:**
```kotlin
import org.trustweave.trust.dsl.credential.KmsProviders.IN_MEMORY
import org.trustweave.trust.dsl.credential.KeyAlgorithms.ED25519
// ✅ Ensure KMS is configured
val trustWeave = TrustWeave.build {
    // KMS and DID methods auto-discovered via SPI
    keys {
        provider(IN_MEMORY)  // or your KMS provider
        algorithm(ED25519)
    }
    // ... rest of config
}
```

---

### Error: "DID method 'key' not registered"

**Problem:** The DID method wasn't registered in the configuration.

**Solution:**
```kotlin
import org.trustweave.trust.dsl.credential.DidMethods.KEY
import org.trustweave.trust.dsl.credential.KeyAlgorithms.ED25519
import org.trustweave.trust.dsl.credential.KmsProviders.IN_MEMORY
// ✅ Register DID method
val trustWeave = TrustWeave.build {
    keys { provider(IN_MEMORY); algorithm(ED25519) }
    did {
        method(KEY) {
            algorithm(ED25519)
        }
    }
}
```

---

### Error: "No verification method found"

**Problem:** The issuer DID document doesn't have a verification method, or the DID wasn't resolved correctly.

**Solution:**
```kotlin
import org.trustweave.did.resolver.DidResolutionResult

// ✅ Prefer createDid: you get the document immediately (no extra resolve)
import org.trustweave.did.identifiers.extractKeyId
import org.trustweave.trust.types.getOrThrow
import org.trustweave.credential.results.getOrThrow

val (issuerDid, issuerDoc) = trustWeave.createDid { /* method/algorithm if needed */ }.getOrThrow()
val keyId = issuerDoc.verificationMethod.firstOrNull()?.extractKeyId()
    ?: throw IllegalStateException("No verification method found")

// If you must resolve later, use the sealed result (not a nullable document)

val issuerDocument = when (val res = trustWeave.resolveDid(issuerDid)) {
    is DidResolutionResult.Success -> res.document
    else -> throw IllegalStateException("DID not found or not resolvable")
}
val keyIdFromResolve = issuerDocument.verificationMethod.firstOrNull()?.extractKeyId()
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
    issued(Clock.System.now())
    expires((365 * 10).days)  // 10 years
    // or
    expires(Clock.System.now().plus(365.days))  // 1 year from now
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
    signedBy(issuerDid = issuerDid, keyId = keyId)
    withRevocation()  // Auto-creates status list
}
```

See: [How to Revoke Credentials](revoke-credentials.md)

---

### 2. Store Credentials in a Wallet

Let credential holders store and manage their credentials:

```kotlin
import org.trustweave.trust.types.getOrThrow

val wallet = trustWeave.wallet {
    holder(holderDid.value)
    enableOrganization()
}.getOrThrow()

val stored = issuedCredential.storeIn(wallet)
```

See: [How to Manage Credentials in Wallets](manage-wallets.md)

---

### 3. Create Verifiable Presentations

Allow holders to present credentials selectively:

```kotlin
import org.trustweave.trust.dsl.wallet.presentationFromWalletResult
import org.trustweave.trust.types.getOrThrow
import org.trustweave.credential.results.getOrThrow

val presentation = trustWeave.presentationFromWalletResult(wallet) {
    fromWallet(credentialId)
    holder(holderDid.value)
    challenge("job-application-123")
}.getOrThrow()
```

See: [How to Create Verifiable Presentations](create-presentations.md)

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

See: [How to Configure Trust Registries](configure-trust-registry.md)

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

