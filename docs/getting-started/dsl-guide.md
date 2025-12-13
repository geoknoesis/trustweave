---
title: TrustWeave DSL Guide
nav_order: 10
parent: Getting Started
---

# TrustWeave DSL Guide

## Overview

The TrustWeave DSL (Domain-Specific Language) provides a fluent, type-safe API for working with verifiable credentials, making it easier to configure trust layers, issue credentials, verify credentials, create presentations, and manage wallets.

```kotlin
dependencies {
    implementation("com.trustweave:trustweave-all:1.0.0-SNAPSHOT")
    // Or use individual modules:
    // implementation("com.trustweave:trust:1.0.0-SNAPSHOT")
    // implementation("com.trustweave:testkit:1.0.0-SNAPSHOT")
}
```

**Result:** You get access to the DSL builders plus in-memory services that make the examples below executable out of the box.

## Key Benefits

- **Reduced Boilerplate**: ~60-70% less code for common operations
- **Better Readability**: Intent is clearer with fluent API
- **Type Safety**: Compile-time checks for credential structure
- **Centralized Configuration**: Single place to configure entire trust layer
- **Easier Onboarding**: More intuitive for new developers

## Trust Layer Configuration

The trust layer configuration is the foundation of the DSL. It centralizes the setup of cryptographic keys (KMS), Decentralized Identifiers (DIDs), and blockchain anchoring.

### Basic Configuration

```kotlin
import com.trustweave.trust.TrustWeave
import com.trustweave.trust.dsl.*
import kotlinx.coroutines.runBlocking

fun main() = runBlocking {
    val trustWeave = TrustWeave.build {
        keys {
            provider("inMemory")  // or "waltid", "hardware", etc.
            algorithm("Ed25519")
        }

        did {
            method("key") {
                algorithm("Ed25519")
            }
        }

        anchor {
            chain("algorand:testnet") {
                inMemory()  // For testing
            }
        }

        credentials {
            defaultProofType(ProofType.Ed25519Signature2020)
            autoAnchor(false)
        }

        trust {
            provider("inMemory")  // Trust registry provider
        }
    }
}
```
**Outcome:** Produces a fully configured `TrustWeave` instance with in-memory KMS, DID method, anchoring, and trust registry—ideal for local experiments or tests.

### Multiple Trust Layers

You can create multiple trust layer configurations for different environments:

```kotlin
// Production TrustWeave instance
val productionTrustWeave = TrustWeave.build {
    keys { provider("hardware") }
    did { method("web") { domain("company.com") } }
    anchor { chain("algorand:mainnet") { provider("algorand") } }
}

// Test TrustWeave instance
val testTrustWeave = TrustWeave.build {
    keys { provider("inMemory") }
    did { method("key") }
    anchor { chain("algorand:testnet") { inMemory() } }
}
```

**Outcome:** Maintains separate configurations for production and test contexts while sharing the same DSL surface.

## Credential Creation DSL

Create verifiable credentials using a fluent builder:

```kotlin
import com.trustweave.trust.dsl.credential.credential
import kotlinx.datetime.Clock
import kotlin.time.Duration.Companion.years

val credential = credential {
    id("https://example.edu/credentials/123")
    type("DegreeCredential", "BachelorDegreeCredential")
    issuer("did:key:university")
    subject {
        id("did:key:student")
        "degree" {
            "type" to "BachelorDegree"
            "name" to "Bachelor of Science"
            "university" to "Example University"
        }
    }
    issued(Clock.System.now())
    expires(10.years)  // Use Duration extension, e.g., 10.years
    schema("https://example.edu/schemas/degree.json")
}
```

**Outcome:** Returns a `VerifiableCredential` data structure ready to sign, store, or anchor.

### Credential Builder Methods

- `id(String)`: Set credential ID
- `type(String...)` or `type(CredentialType...)`: Add credential types (first is primary type)
- `issuer(String)` or `issuer(Did)`: Set issuer DID
- `subject { }`: Build credential subject (nested JSON objects using DSL)
- `issued(Instant)`: Set issuance date (use `Clock.System.now()`)
- `expires(Duration)`: Set expiration (use Duration extensions like `10.years`, `30.days`)
- `schema(String)`: Set credential schema
- `status { }`: Configure credential status (revocation status list)
- `evidence(Evidence)`: Add evidence
- `termsOfUse(TermsOfUse)`: Add terms of use
- `refreshService(RefreshService)`: Add refresh service

## Issuance DSL

Issue credentials with automatic proof generation:

```kotlin
import com.trustweave.trust.TrustWeave
import com.trustweave.credential.format.ProofSuiteId
import com.trustweave.credential.results.IssuanceResult
import kotlinx.datetime.Clock
import kotlinx.coroutines.runBlocking

fun main() = runBlocking {
    val trustWeave = TrustWeave.build { /* ... configuration ... */ }
    
    val issuanceResult = trustWeave.issue {
        credential {
            type("DegreeCredential")
            issuer("did:key:university")
            subject {
                id("did:key:student")
                "degree" {
                    "type" to "BachelorDegree"
                }
            }
            issued(Clock.System.now())
        }
        signedBy(issuerDid = "did:key:university", keyId = "key-1")
        withProof(ProofSuiteId.VC_LD)  // Use ProofSuiteId, not ProofType
        challenge("challenge-123")
        domain("example.com")
        // Note: anchor() is not available in IssuanceBuilder
        // Use trustWeave.blockchains.anchor() separately if needed
    }

    val issuedCredential = when (issuanceResult) {
        is IssuanceResult.Success -> issuanceResult.credential
        else -> throw IllegalStateException("Failed to issue credential: ${issuanceResult.reason}")
    }
}
```

**Outcome:** Returns a signed credential (`VerifiableCredential`) with optional anchoring and proof configuration baked in.

### Issuance Builder Methods

- `credential { }`: Build credential inline using CredentialBuilder DSL
- `credential(VerifiableCredential)`: Use a pre-built credential
- `signedBy(issuerDid: String, keyId: String)`: Specify issuer DID and key ID (required)
- `signedBy(issuerDid: Did, keyId: String)`: Specify issuer DID (Did object) and key ID
- `signedBy(issuer: IssuerIdentity)`: Specify issuer identity object
- `withProof(ProofSuiteId)`: Set proof suite (e.g., `ProofSuiteId.VC_LD`, `ProofSuiteId.VC_JWT`)
- `challenge(String)`: Set proof challenge for verification
- `domain(String)`: Set proof domain for verification
- `withRevocation()`: Enable automatic revocation support (creates status list if needed)

**Example:**
```kotlin
import com.trustweave.trust.TrustWeave
import com.trustweave.credential.format.ProofSuiteId
import com.trustweave.credential.results.IssuanceResult
import kotlinx.datetime.Clock
import kotlinx.coroutines.runBlocking

fun main() = runBlocking {
    val trustWeave = TrustWeave.build { /* ... configuration ... */ }
    
    val issuanceResult = trustWeave.issue {
        credential {
            type("DegreeCredential")
            issuer("did:key:university")
            subject {
                id("did:key:student")
                "degree" {
                    "type" to "BachelorDegree"
                    "name" to "Bachelor of Science"
                }
            }
            issued(Clock.System.now())
            // Note: withRevocation() is called in the issue block, not credential block
        }
        signedBy(issuerDid = "did:key:university", keyId = "key-1")
        withProof(ProofSuiteId.VC_LD)  // Use ProofSuiteId, not ProofType
        challenge("challenge-123")
        domain("example.com")
        withRevocation() // Auto-creates status list if needed
    }

    val issuedCredential = when (issuanceResult) {
        is IssuanceResult.Success -> issuanceResult.credential
        else -> throw IllegalStateException("Failed to issue credential: ${issuanceResult.reason}")
    }
}
```

## Verification DSL

Verify credentials with exhaustive error handling using sealed result types:

```kotlin
import com.trustweave.trust.types.VerificationResult

val result = trustWeave.verify {
    credential(credential)
    checkRevocation() // Check revocation status
    checkExpiration() // Check expiration
}

// Exhaustive error handling with sealed result type
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
    // ... other error cases handled exhaustively
}
```

### Verification Builder Methods

- `credential(VerifiableCredential)`: Set credential to verify (required)
- `checkRevocation()`: Enable revocation checking
- `skipRevocation()`: Disable revocation checking
- `checkExpiration()`: Enable expiration checking
- `skipExpiration()`: Disable expiration checking
- `validateSchema(String)`: Enable schema validation
- `skipSchema()`: Disable schema validation
- `validateProofPurpose()`: Enable proof purpose validation

## Wallet DSL

Create and manage wallets:

```kotlin
import com.trustweave.trust.types.WalletCreationResult

val walletResult = trustWeave.wallet {
    holder("did:key:holder")
    // Additional wallet configuration
}

val wallet = when (walletResult) {
    is WalletCreationResult.Success -> walletResult.wallet
    else -> throw IllegalStateException("Failed to create wallet: ${walletResult.reason}")
}

// Store credentials
val credentialId = wallet.store(credential)

// Query credentials
val credentials = wallet.query {
    byType("EducationCredential")
    valid(true)
}
```

## Trust Registry DSL

Manage trust anchors:

```kotlin
trustWeave.trust {
    addAnchor("did:key:university") {
        credentialTypes("EducationCredential")
        description("Trusted university")
    }

    val isTrusted = isTrusted("did:key:university", "EducationCredential")
    val path = getTrustPath("did:key:verifier", "did:key:issuer")
}
```

## Common Patterns

Here are some common patterns you'll use frequently:

### Pattern 1: Quick Credential Issuance

```kotlin
val result = trustWeave.issue {
    credential {
        type("TestCredential")
        issuer(issuerDid)
        subject {
            id(subjectDid)
            "name" to "Alice"
        }
    }
    signedBy(issuerDid, "key-1")
}
```

### Pattern 2: Credential with Expiration

```kotlin
val result = trustWeave.issue {
    credential {
        type("DegreeCredential")
        issuer(universityDid)
        subject {
            id(studentDid)
            "degree" to "Bachelor of Science"
        }
        issued(Clock.System.now())
        expiresIn(365.days)  // Expires in 1 year
    }
    signedBy(universityDid, "key-1")
}
```

### Pattern 3: Verification with Trust

```kotlin
val result = trustWeave.verify {
    credential(credential)
    requireTrust(trustRegistry)
}
```

### Pattern 4: Quick Verification (Skip Checks)

```kotlin
val result = trustWeave.verify {
    credential(credential)
    skipRevocation()   // Skip revocation check
    skipExpiration()   // Skip expiration check
}
```

### Pattern 5: DID Creation with Configuration

```kotlin
val result = trustWeave.createDid(method = "key") {
    algorithm("Ed25519")
}
```

### Pattern 6: Batch Issuance

```kotlin
trustWeave.issueBatch {
    requests = listOf(
        { credential { type("Cred1"); issuer(did1); subject { id(did1) } }; signedBy(did1, "key-1") },
        { credential { type("Cred2"); issuer(did2); subject { id(did2) } }; signedBy(did2, "key-2") }
    )
    maxConcurrency = 5
}.collect { result ->
    when (result) {
        is IssuanceResult.Success -> println("Issued: ${result.credential.id}")
        is IssuanceResult.Failure -> println("Failed: ${result.allErrors}")
    }
}
```

## Next Steps

- [Quick Start](quick-start.md) - Get started with a complete example
- [API Reference](../api-reference/core-api.md) - Complete API documentation
- [Core Concepts](../core-concepts/README.md) - Deep dives into DIDs, VCs, etc.
