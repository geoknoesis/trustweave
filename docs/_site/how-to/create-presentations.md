# How to Create Verifiable Presentations

## Purpose

This guide shows you how to create Verifiable Presentations (VPs) from credentials stored in wallets. You'll learn how to select credentials, create presentations, enable selective disclosure, and sign presentations with holder proofs.

**What you'll accomplish:**
- Create verifiable presentations from wallet credentials
- Select specific credentials to include
- Enable selective disclosure to reveal only necessary information
- Sign presentations with holder proofs
- Present credentials to verifiers

**Why this matters:**
Verifiable Presentations allow credential holders to share credentials selectively while maintaining privacy. They enable zero-knowledge proofs, selective disclosure, and privacy-preserving credential exchange—essential for user-controlled identity systems.

---

## Prerequisites

- **Kotlin**: 2.2.0 or higher
- **Java**: 21 or higher
- **TrustWeave SDK**: Latest version
- **Dependencies**: `distribution-all` and `testkit`

**Required imports:**
```kotlin
import com.trustweave.trust.TrustWeave
import com.trustweave.credential.models.VerifiablePresentation
import com.trustweave.trust.types.ProofType
import kotlinx.coroutines.runBlocking
```

**Configuration needed:**
- Wallet with presentation capabilities enabled
- Holder DID for signing presentations

---

## Before You Begin

A Verifiable Presentation is a wrapper around one or more Verifiable Credentials, signed by the holder. It allows selective disclosure and enables privacy-preserving credential sharing.

**When to use this:**
- Sharing credentials with verifiers (job applications, age verification, etc.)
- Implementing selective disclosure (reveal only necessary information)
- Creating privacy-preserving credential exchanges
- Building user-controlled identity systems

**How it fits in a workflow:**
```kotlin
// 1. Store credentials in wallet
val wallet = trustWeave.wallet {
    holder(holderDid.value)
    enablePresentation()
}

val credentialId = wallet.store(credential)

// 2. Create presentation
val presentation = wallet.presentation {
    fromWallet(credentialId)
    holder(holderDid.value)
    challenge("job-application-123")
}

// 3. Present to verifier
verifier.verifyPresentation(presentation)
```

---

## Step-by-Step Guide

### Step 1: Configure Wallet with Presentation Support

Create a wallet with presentation capabilities enabled:

```kotlin
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
}

// Create holder DID
val holderDid = trustWeave.createDid { method(KEY) }

// Create wallet with presentation support
val wallet = trustWeave.wallet {
    holder(holderDid.value)
    enablePresentation()  // Enable presentation creation
}
```

**What this does:**
- Creates a wallet for the credential holder
- Enables presentation creation capabilities
- Sets up the holder's DID for signing presentations

---

### Step 2: Store Credentials in Wallet

Store credentials that will be presented:

```kotlin
// Issue or receive credentials
val credential = trustWeave.issue { ... }

// Store in wallet
val credentialId = wallet.store(credential)
println("Credential stored: $credentialId")
```

**What this does:**
- Stores the credential in the wallet
- Returns a credential ID for later reference
- Makes the credential available for presentation creation

---

### Step 3: Create a Basic Presentation

Create a presentation from stored credentials:

```kotlin
val presentation = wallet.presentation {
    fromWallet(credentialId)
    holder(holderDid.value)
    challenge("job-application-${System.currentTimeMillis()}")
}

println("✅ Presentation created: ${presentation.id}")
println("   Credentials: ${presentation.verifiableCredential.size}")
println("   Holder: ${presentation.holder}")
```

**What this does:**
- Creates a Verifiable Presentation containing the specified credentials
- Sets the holder DID (who is presenting)
- Includes a challenge (prevents replay attacks)
- Returns a presentation ready to share with verifiers

**Key concepts:**
- **Holder**: The entity presenting the credentials (usually the credential subject)
- **Challenge**: A nonce or identifier that prevents replay attacks
- **Proof**: Cryptographic signature proving the holder controls the credentials

---

### Step 4: Create Presentation with Proof

Sign the presentation with the holder's key:

```kotlin
// Get holder's key ID
val resolutionResult = trustWeave.resolveDid(holderDid)
val holderDocument = when (resolutionResult) {
    is DidResolutionResult.Success -> resolutionResult.document
    else -> throw IllegalStateException("Failed to resolve holder DID")
}
val holderKeyId = holderDocument.verificationMethod.firstOrNull()?.id?.substringAfter("#")
    ?: throw IllegalStateException("No verification method found")

val presentation = wallet.presentation {
    fromWallet(credentialId)
    holder(holderDid.value)
    challenge("job-application-123")
    keyId(holderKeyId)  // Sign with holder's key
    proofType(ProofType.Ed25519Signature2020.value)
}

println("✅ Presentation created with proof")
println("   Has proof: ${presentation.proof != null}")
```

**What this does:**
- Signs the presentation with the holder's private key
- Creates a cryptographic proof that the holder controls the credentials
- Enables verifiers to verify the presentation signature

---

### Step 5: Verify the Presentation

Verify the presentation (as a verifier would):

```kotlin
// As verifier
val isValid = presentation.proof != null && 
              presentation.holder == holderDid.value &&
              presentation.verifiableCredential.isNotEmpty()

if (isValid) {
    println("✅ Presentation structure is valid")
    
    // Verify each credential in the presentation
    presentation.verifiableCredential.forEach { cred ->
        val result = trustWeave.verifyCredential(cred)
        when (result) {
            is VerificationResult.Valid -> println("   ✅ Credential valid: ${cred.id}")
            else -> println("   ❌ Credential invalid: ${cred.id}")
        }
    }
} else {
    println("❌ Presentation structure is invalid")
}
```

**What this does:**
- Checks presentation structure (proof, holder, credentials)
- Verifies each credential in the presentation
- Validates the presentation is properly formed

---

## Complete Example

Here's a complete, runnable example:

```kotlin
import com.trustweave.trust.TrustWeave
import com.trustweave.trust.types.ProofType
import com.trustweave.trust.types.VerificationResult
import com.trustweave.did.resolver.DidResolutionResult
import kotlinx.coroutines.runBlocking
import java.time.Instant
import java.time.temporal.ChronoUnit

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
    }
    
    // Step 2: Create DIDs
    val issuerDid = trustWeave.createDid { method(KEY) }
    val holderDid = trustWeave.createDid { method(KEY) }
    
    // Step 3: Get issuer key ID
    val issuerResolution = trustWeave.resolveDid(issuerDid)
    val issuerDocument = when (issuerResolution) {
        is DidResolutionResult.Success -> issuerResolution.document
        else -> throw IllegalStateException("Failed to resolve issuer DID")
    }
    val issuerKeyId = issuerDocument.verificationMethod.firstOrNull()?.id?.substringAfter("#")
        ?: throw IllegalStateException("No verification method found")
    
    // Step 4: Issue credential
    val credential = trustWeave.issue {
        credential {
            id("https://example.edu/credentials/degree-123")
            type("DegreeCredential")
            issuer(issuerDid.value)
            subject {
                id(holderDid.value)
                "degree" {
                    "name" to "Bachelor of Science"
                    "university" to "Example University"
                }
            }
            issued(Instant.now())
        }
        signedBy(issuerDid = issuerDid.value, keyId = issuerKeyId)
    }
    
    println("✅ Credential issued: ${credential.id}")
    
    // Step 5: Create wallet with presentation support
    val wallet = trustWeave.wallet {
        holder(holderDid.value)
        enablePresentation()
    }
    
    // Step 6: Store credential
    val credentialId = wallet.store(credential)
    println("✅ Credential stored: $credentialId")
    
    // Step 7: Get holder key ID
    val holderResolution = trustWeave.resolveDid(holderDid)
    val holderDocument = when (holderResolution) {
        is DidResolutionResult.Success -> holderResolution.document
        else -> throw IllegalStateException("Failed to resolve holder DID")
    }
    val holderKeyId = holderDocument.verificationMethod.firstOrNull()?.id?.substringAfter("#")
        ?: throw IllegalStateException("No verification method found")
    
    // Step 8: Create presentation
    val presentation = wallet.presentation {
        fromWallet(credentialId)
        holder(holderDid.value)
        challenge("job-application-${System.currentTimeMillis()}")
        keyId(holderKeyId)
        proofType(ProofType.Ed25519Signature2020.value)
    }
    
    println("✅ Presentation created:")
    println("   ID: ${presentation.id}")
    println("   Holder: ${presentation.holder}")
    println("   Credentials: ${presentation.verifiableCredential.size}")
    println("   Has proof: ${presentation.proof != null}")
    println("   Challenge: ${presentation.challenge}")
    
    // Step 9: Verify presentation
    val allValid = presentation.verifiableCredential.all { cred ->
        val result = trustWeave.verifyCredential(cred)
        result is VerificationResult.Valid
    }
    
    if (allValid && presentation.proof != null) {
        println("✅ Presentation is valid and ready to share")
    } else {
        println("❌ Presentation verification failed")
    }
}
```

---

## Visual Flow Diagram

```
┌─────────────────────────────────────────────────────────────┐
│              Verifiable Presentation Creation                │
└─────────────────────────────────────────────────────────────┘

┌──────────────┐
│   Step 1     │  Configure Wallet
│  Create      │  • Holder DID
│  Wallet      │  • enablePresentation()
└──────┬───────┘
       │
       ▼
┌──────────────┐
│   Step 2     │  Store Credentials
│   Store      │  • wallet.store(credential)
└──────┬───────┘  • Returns credential ID
       │
       ▼
┌──────────────┐
│   Step 3     │  Create Presentation
│  Create VP  │  • fromWallet(credentialId)
└──────┬───────┘  • holder(holderDid)
       │          • challenge("nonce")
       │          • keyId(holderKeyId)
       ▼
┌──────────────┐
│   Step 4     │  Sign Presentation
│   Sign VP    │  • Generate proof
└──────┬───────┘  • Sign with holder key
       │          • Attach proof
       ▼
┌──────────────┐
│   Step 5     │  Present to Verifier
│   Present    │  • Send presentation
└──────┬───────┘  • Verifier verifies
       │          • Verifier checks credentials
       ▼
    ✅ Accepted
    or
    ❌ Rejected
```

---

## Verification Step

After creating a presentation, verify it's ready to share:

```kotlin
// Quick verification
val isValid = presentation.proof != null &&
              presentation.holder == holderDid.value &&
              presentation.verifiableCredential.isNotEmpty() &&
              presentation.challenge != null

println("Presentation is ${if (isValid) "valid" else "invalid"}")
```

**Expected output:**
```
✅ Credential issued: https://example.edu/credentials/degree-123
✅ Credential stored: urn:uuid:...
✅ Presentation created:
   ID: urn:uuid:...
   Holder: did:key:z6Mk...
   Credentials: 1
   Has proof: true
   Challenge: job-application-1234567890
✅ Presentation is valid and ready to share
```

**What to check:**
- ✅ Presentation has a `proof` property
- ✅ Holder DID matches the credential subject
- ✅ Challenge is present (prevents replay)
- ✅ All credentials in presentation are valid
- ✅ Presentation structure is correct

---

## Common Errors & Troubleshooting

### Error: "Wallet does not support presentation"

**Problem:** Presentation capability wasn't enabled when creating the wallet.

**Solution:**
```kotlin
// ❌ Missing enablePresentation()
val wallet = trustWeave.wallet {
    holder(holderDid.value)
}

// ✅ Correct
val wallet = trustWeave.wallet {
    holder(holderDid.value)
    enablePresentation()  // Required for presentations
}
```

---

### Error: "Holder DID is required"

**Problem:** The `holder()` method wasn't called in the presentation builder.

**Solution:**
```kotlin
// ❌ Missing holder
wallet.presentation {
    fromWallet(credentialId)
    // Missing: holder(holderDid.value)
}

// ✅ Correct
wallet.presentation {
    fromWallet(credentialId)
    holder(holderDid.value)  // Required
}
```

---

### Error: "At least one credential is required"

**Problem:** No credentials were added to the presentation.

**Solution:**
```kotlin
// ✅ Ensure credentials are stored and referenced
val credentialId = wallet.store(credential)

val presentation = wallet.presentation {
    fromWallet(credentialId)  // Must reference stored credential
    holder(holderDid.value)
}
```

---

### Error: Credential not found in wallet

**Problem:** The credential ID doesn't exist in the wallet.

**Solution:**
```kotlin
// ✅ Verify credential exists before creating presentation
val storedCredential = wallet.get(credentialId)
if (storedCredential == null) {
    println("Credential not found: $credentialId")
    // Store credential first
    val newId = wallet.store(credential)
    credentialId = newId
}
```

---

## Advanced Patterns

### Pattern 1: Selective Disclosure

Reveal only specific fields from credentials:

```kotlin
val presentation = wallet.presentation {
    fromWallet(credentialId)
    holder(holderDid.value)
    challenge("age-verification-123")
    
    selectiveDisclosure {
        reveal("degree", "name")  // Only reveal degree name
        // Other fields remain hidden
    }
}
```

**What this does:**
- Creates a presentation with selective disclosure
- Only reveals specified fields from credentials
- Maintains privacy for other credential claims

---

### Pattern 2: Multiple Credentials

Include multiple credentials in one presentation:

```kotlin
val credentialIds = listOf(degreeId, employmentId, certificationId)

val presentation = wallet.presentation {
    fromWallet(credentialIds)  // Multiple credentials
    holder(holderDid.value)
    challenge("job-application-123")
}
```

**What this does:**
- Combines multiple credentials into one presentation
- Allows verifiers to see all credentials at once
- Single proof covers all credentials

---

### Pattern 3: Query-Based Presentation

Create presentation from wallet query:

```kotlin
val presentation = wallet.presentation {
    fromQuery {
        type("EducationCredential")
        valid()
    }
    holder(holderDid.value)
    challenge("education-verification-123")
}
```

**What this does:**
- Queries wallet for matching credentials
- Includes all matching credentials in presentation
- Useful for dynamic credential selection

---

### Pattern 4: Presentation with Domain

Include domain for additional context:

```kotlin
val presentation = wallet.presentation {
    fromWallet(credentialId)
    holder(holderDid.value)
    challenge("job-application-123")
    domain("https://employer.example.com")  // Verifier domain
}
```

**What this does:**
- Binds presentation to a specific domain
- Prevents presentation reuse across domains
- Adds additional security context

---

## Next Steps

Now that you can create presentations, here are ways to extend your implementation:

### 1. Verify Presentations

Verify presentations as a verifier:

```kotlin
// Verify presentation structure
val isValid = presentation.proof != null &&
              presentation.holder != null &&
              presentation.verifiableCredential.isNotEmpty()

// Verify each credential
presentation.verifiableCredential.forEach { cred ->
    val result = trustWeave.verifyCredential(cred)
    // Handle result
}
```

See: [How to Verify Credentials](./verify-credentials.md)

---

### 2. Selective Disclosure

Implement zero-knowledge proofs and selective disclosure:

```kotlin
val presentation = wallet.presentation {
    fromWallet(credentialId)
    holder(holderDid.value)
    selectiveDisclosure {
        reveal("age")  // Only reveal age, hide other fields
    }
}
```

---

### 3. Presentation Exchange

Exchange presentations using protocols:

```kotlin
// Using DIDComm, OIDC4VCI, or CHAPI
// See exchange-credentials.md for protocol details
```

See: [How to Exchange Credentials](./exchange-credentials.md)

---

### 4. Presentation Analytics

Track presentation usage:

```kotlin
// Log presentation creation
fun createPresentationWithLogging(credentialId: String, challenge: String) {
    val presentation = wallet.presentation {
        fromWallet(credentialId)
        holder(holderDid.value)
        challenge(challenge)
    }
    
    // Log for analytics
    analyticsService.logPresentation(
        presentationId = presentation.id,
        holder = presentation.holder,
        credentialCount = presentation.verifiableCredential.size
    )
    
    return presentation
}
```

---

## Summary

You've learned how to create Verifiable Presentations with TrustWeave:

✅ **Configured wallet** with presentation capabilities  
✅ **Stored credentials** in wallet for presentation  
✅ **Created presentations** from stored credentials  
✅ **Signed presentations** with holder proofs  
✅ **Verified presentations** are ready to share  

**Key takeaways:**
- Presentations wrap credentials and are signed by the holder
- Selective disclosure enables privacy-preserving credential sharing
- Challenges prevent replay attacks
- Presentations can include multiple credentials

**What's next:**
- Implement selective disclosure for privacy
- Exchange presentations using protocols (DIDComm, OIDC4VCI, CHAPI)
- Verify presentations as a verifier
- Build presentation-based authentication flows

For more examples, see the [scenarios documentation](../scenarios/README.md).

