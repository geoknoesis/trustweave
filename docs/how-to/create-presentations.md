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

> **API entry point:** Presentations are built through **`TrustWeave`**, not the wallet object. Use **`trustWeave.presentationFromWalletResult(wallet) { ... }`** (sealed [`PresentationResult`](../api-reference/result-types-guide.md)) or **`.getOrThrow()`** in tests (throws **`TrustWeaveException.InvalidState`** with `PRESENTATION_*` codes on failure). See [API patterns — results vs exceptions](../getting-started/api-patterns.md#api-contract-results-vs-exceptions).

---

## Prerequisites

- **Kotlin**: 2.2.21+ or higher
- **Java**: 21 or higher
- **TrustWeave SDK**: Latest version
- **Dependencies**: `distribution-all` and `testkit`

**Required imports (typical):**
```kotlin
import org.trustweave.trust.TrustWeave
import org.trustweave.trust.dsl.wallet.presentationFromWalletResult
import org.trustweave.trust.types.PresentationResult
import org.trustweave.trust.types.getOrThrow
import org.trustweave.credential.results.getOrThrow
import org.trustweave.credential.results.VerificationResult
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
    holder(holderDid)
    enablePresentation()
}

val credentialId = wallet.store(credential)

// 2. Create presentation (TrustWeave facade + wallet)
val presentation = trustWeave.presentationFromWalletResult(wallet) {
    fromWallet(credentialId)
    holder(holderDidString) // must be a String starting with "did:"
    challenge("job-application-123")
}.getOrThrow()

// 3. Present to verifier
verifier.verifyPresentation(presentation)
```

## Presentation Flow

Creating and verifying presentations involves multiple parties:

```mermaid
sequenceDiagram
    participant Holder
    participant Wallet
    participant Issuer
    participant Verifier
    
    Note over Holder,Verifier: Phase 1: Credential Storage
    Issuer->>Holder: Issue Credential
    Holder->>Wallet: Store Credential
    Wallet-->>Holder: Credential Stored
    
    Note over Holder,Verifier: Phase 2: Presentation Request
    Verifier->>Holder: Request Presentation<br/>(challenge, domain)
    Holder->>Holder: Select Credentials
    
    Note over Holder,Verifier: Phase 3: Create Presentation
    Holder->>Wallet: Create Presentation<br/>(selective disclosure)
    Wallet->>Wallet: Filter Claims<br/>(if selective disclosure)
    Wallet->>Wallet: Sign Presentation<br/>(holder proof)
    Wallet-->>Holder: Verifiable Presentation
    
    Note over Holder,Verifier: Phase 4: Verify Presentation
    Holder->>Verifier: Send Presentation
    Verifier->>Verifier: Verify Holder Proof
    Verifier->>Issuer: Resolve Issuer DID<br/>(for each credential)
    Issuer-->>Verifier: Issuer DID Document
    Verifier->>Verifier: Verify Credential Proofs
    Verifier->>Verifier: Check Challenge/Domain
    Verifier-->>Holder: Verification Result
    
    style Holder fill:#2196f3,stroke:#1565c0,stroke-width:2px,color:#fff
    style Wallet fill:#4caf50,stroke:#2e7d32,stroke-width:2px,color:#fff
    style Issuer fill:#ff9800,stroke:#e65100,stroke-width:2px,color:#fff
    style Verifier fill:#9c27b0,stroke:#6a1b9a,stroke-width:2px,color:#fff
```

**Key Phases:**
1. **Storage**: Holder stores credentials issued by issuer in wallet
2. **Request**: Verifier requests presentation with challenge and domain
3. **Creation**: Wallet creates presentation with selective disclosure (optional)
4. **Verification**: Verifier verifies holder proof, credential proofs, and challenge

---

## Step-by-Step Guide

### Step 1: Configure Wallet with Presentation Support

Create a wallet with presentation capabilities enabled:

```kotlin
import org.trustweave.trust.dsl.credential.DidMethods.KEY
import org.trustweave.trust.dsl.credential.KeyAlgorithms.ED25519
import org.trustweave.trust.dsl.credential.KmsProviders.IN_MEMORY
import org.trustweave.trust.types.getOrThrow
import org.trustweave.credential.results.getOrThrow
import org.trustweave.trust.types.getOrThrowDid

val trustWeave = TrustWeave.build {
    keys { provider(IN_MEMORY); algorithm(ED25519) }
    did { method(KEY) { algorithm(ED25519) } }
}

val holderDid = trustWeave.createDid { method(KEY); algorithm(ED25519) }.getOrThrowDid()

val wallet = trustWeave.wallet {
    holder(holderDid)
    enablePresentation()
}.getOrThrow()
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
val presentation = trustWeave.presentationFromWalletResult(wallet) {
    fromWallet(credentialId)
    holder(holderDid.value) // WalletPresentationBuilder requires a did: String
    challenge("job-application-${System.currentTimeMillis()}")
}.getOrThrow()

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
import org.trustweave.did.resolver.DidResolutionResult
import org.trustweave.did.identifiers.extractKeyId

val holderDocument = when (val res = trustWeave.resolveDid(holderDid)) {
    is DidResolutionResult.Success -> res.document
    else -> error("Failed to resolve holder DID")
}
val holderKeyId = holderDocument.verificationMethod.firstOrNull()?.extractKeyId()
    ?: error("No verification method found")

val presentation = trustWeave.presentationFromWalletResult(wallet) {
    fromWallet(credentialId)
    holder(holderDid.value)
    challenge("job-application-123")
    verificationMethod("${holderDid.value}#$holderKeyId")
}.getOrThrow()

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
              presentation.holder.value == holderDid.value &&
              presentation.verifiableCredential.isNotEmpty()

if (isValid) {
    println("✅ Presentation structure is valid")
    
    // Verify each credential in the presentation
    presentation.verifiableCredential.forEach { cred ->
        val result = trustWeave.verify { credential(cred) }
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
import org.trustweave.credential.results.VerificationResult
import org.trustweave.did.identifiers.extractKeyId
import org.trustweave.did.resolver.DidResolutionResult
import org.trustweave.trust.TrustWeave
import org.trustweave.trust.dsl.credential.DidMethods.KEY
import org.trustweave.trust.dsl.credential.KeyAlgorithms.ED25519
import org.trustweave.trust.dsl.credential.KmsProviders.IN_MEMORY
import org.trustweave.trust.dsl.wallet.presentationFromWalletResult
import org.trustweave.trust.types.getOrThrow
import org.trustweave.credential.results.getOrThrow
import org.trustweave.trust.types.getOrThrowDid
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.Clock

fun main() = runBlocking {
    val trustWeave = TrustWeave.build {
        keys { provider(IN_MEMORY); algorithm(ED25519) }
        did { method(KEY) { algorithm(ED25519) } }
    }

    val issuerDid = trustWeave.createDid { method(KEY); algorithm(ED25519) }.getOrThrowDid()
    val holderDid = trustWeave.createDid { method(KEY); algorithm(ED25519) }.getOrThrowDid()

    val credential = trustWeave.issue {
        credential {
            id("https://example.edu/credentials/degree-123")
            type("DegreeCredential")
            issuer(issuerDid)
            subject {
                id(holderDid)
                "degree" {
                    "name" to "Bachelor of Science"
                    "university" to "Example University"
                }
            }
            issued(Clock.System.now())
        }
        signedBy(issuerDid)
    }.getOrThrow()

    println("✅ Credential issued: ${credential.id}")

    val wallet = trustWeave.wallet {
        holder(holderDid)
        enablePresentation()
    }.getOrThrow()

    val credentialId = wallet.store(credential)
    println("✅ Credential stored: $credentialId")

    val holderDocument = when (val res = trustWeave.resolveDid(holderDid)) {
        is DidResolutionResult.Success -> res.document
        else -> error("Failed to resolve holder DID")
    }
    val holderKeyId = holderDocument.verificationMethod.firstOrNull()?.extractKeyId()
        ?: error("No verification method found")

    val presentation = trustWeave.presentationFromWalletResult(wallet) {
        fromWallet(credentialId)
        holder(holderDid.value)
        challenge("job-application-${System.currentTimeMillis()}")
        verificationMethod("${holderDid.value}#$holderKeyId")
    }.getOrThrow()

    println("✅ Presentation created:")
    println("   ID: ${presentation.id}")
    println("   Holder: ${presentation.holder}")
    println("   Credentials: ${presentation.verifiableCredential.size}")
    println("   Has proof: ${presentation.proof != null}")
    println("   Challenge: ${presentation.challenge}")

    val allValid = presentation.verifiableCredential.all { cred ->
        trustWeave.verify(credential = cred) is VerificationResult.Valid
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
└──────┬───────┘  • holder(holderDid.value)
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
              presentation.holder.value == holderDid.value &&
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
- Presentation has a `proof` property
- Holder DID matches the credential subject
- Challenge is present (prevents replay)
- All credentials in presentation are valid
- Presentation structure is correct

---

## Common Errors & Troubleshooting

### Error: "Wallet does not support presentation"

**Problem:** Presentation capability wasn't enabled when creating the wallet.

**Solution:**
```kotlin
// ❌ Missing enablePresentation()
val wallet = trustWeave.wallet {
    holder(holderDid)
}

// ✅ Correct
val wallet = trustWeave.wallet {
    holder(holderDid)
    enablePresentation()  // Required for presentations
}
```

---

### Error: "Holder DID is required"

**Problem:** The `holder()` method wasn't called in the presentation builder.

**Solution:**
```kotlin
// ❌ Missing holder
trustWeave.presentationFromWalletResult(wallet) {
    fromWallet(credentialId)
    // Missing: holder(holderDid.value)
}

// ✅ Correct
trustWeave.presentationFromWalletResult(wallet) {
    fromWallet(credentialId)
    holder(holderDid.value)  // Required — did: String
}.getOrThrow()
```

---

### Error: "At least one credential is required"

**Problem:** No credentials were added to the presentation.

**Solution:**
```kotlin
// ✅ Ensure credentials are stored and referenced
val credentialId = wallet.store(credential)

val presentation = trustWeave.presentationFromWalletResult(wallet) {
    fromWallet(credentialId)  // Must reference stored credential
    holder(holderDid.value)
}.getOrThrow()
```

---

### Error: Credential not found in wallet

**Problem:** The credential ID doesn't exist in the wallet (or was mistyped).

**Solution:** `presentationFromWalletResult` returns **`PresentationResult.Failure.InvalidRequest`** with text like `Credential not found in wallet: <id>`—it does **not** silently skip missing IDs.

```kotlin
import org.trustweave.trust.types.PresentationResult

when (val pr = trustWeave.presentationFromWalletResult(wallet) {
    fromWallet(credentialId)
    holder(holderDid.value)
    challenge("job-application-123")
}) {
    is PresentationResult.Success -> use(pr.presentation)
    is PresentationResult.Failure.InvalidRequest -> {
        // e.g. fix credentialId or store the VC first
        println(pr.allErrors.joinToString())
    }
    is PresentationResult.Failure -> { /* AdapterNotReady / AdapterError */ }
}
```

---

## Advanced Patterns

### Pattern 1: Selective Disclosure

Reveal only specific fields from credentials:

```kotlin
val presentation = trustWeave.presentationFromWalletResult(wallet) {
    fromWallet(credentialId)
    holder(holderDid.value)
    challenge("age-verification-123")
    
    selectiveDisclosure {
        reveal("degree", "name")  // Only reveal degree name
        // Other fields remain hidden
    }
}.getOrThrow()
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

val presentation = trustWeave.presentationFromWalletResult(wallet) {
    fromWallet(credentialIds)  // Multiple credentials
    holder(holderDid.value)
    challenge("job-application-123")
}.getOrThrow()
```

**What this does:**
- Combines multiple credentials into one presentation
- Allows verifiers to see all credentials at once
- Single proof covers all credentials

---

### Pattern 3: Query-Based Presentation

Create presentation from wallet query:

```kotlin
val presentation = trustWeave.presentationFromWalletResult(wallet) {
    fromQuery {
        type("EducationCredential")
        valid()
    }
    holder(holderDid.value)
    challenge("education-verification-123")
}.getOrThrow()
```

**What this does:**
- Queries wallet for matching credentials
- Includes all matching credentials in presentation
- Useful for dynamic credential selection

---

### Pattern 4: Presentation with Domain

Include domain for additional context:

```kotlin
val presentation = trustWeave.presentationFromWalletResult(wallet) {
    fromWallet(credentialId)
    holder(holderDid.value)
    challenge("job-application-123")
    domain("https://employer.example.com")  // Verifier domain
}.getOrThrow()
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
    val result = trustWeave.verify { credential(cred) }
    // Handle result
}
```

See: [How to Verify Credentials](./verify-credentials.md)

---

### 2. Selective Disclosure

Implement zero-knowledge proofs and selective disclosure:

```kotlin
val presentation = trustWeave.presentationFromWalletResult(wallet) {
    fromWallet(credentialId)
    holder(holderDid.value)
    challenge("age-check-123")
    selectiveDisclosure {
        reveal("age")  // Only reveal age, hide other fields
    }
}.getOrThrow()
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
    val presentation = trustWeave.presentationFromWalletResult(wallet) {
        fromWallet(credentialId)
        holder(holderDid.value)
        challenge(challenge)
    }.getOrThrow()

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

