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
    implementation("com.trustweave:trustweave-core:1.0.0-SNAPSHOT")
    implementation("com.trustweave:trustweave-trust:1.0.0-SNAPSHOT")
    implementation("com.trustweave:trustweave-testkit:1.0.0-SNAPSHOT")
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
import com.trustweave.trust.dsl.*

val trustLayer = trustLayer {
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
```
**Outcome:** Produces a fully configured `trustLayer` with in-memory KMS, DID method, anchoring, and trust registry—ideal for local experiments or tests.

### Multiple Trust Layers

You can create multiple trust layer configurations for different environments:

```kotlin
// Production trust layer
val productionLayer = trustLayer("production") {
    keys { provider("hardware") }
    did { method("web") { domain("company.com") } }
    anchor { chain("algorand:mainnet") { provider("algorand") } }
}

// Test trust layer
val testLayer = trustLayer("test") {
    keys { provider("inMemory") }
    did { method("key") }
    anchor { chain("algorand:testnet") { inMemory() } }
}
```

**Outcome:** Maintains separate configurations for production and test contexts while sharing the same DSL surface.

## Credential Creation DSL

Create verifiable credentials using a fluent builder:

```kotlin
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
    issued(Instant.now())
    expires(Instant.now().plus(10, ChronoUnit.YEARS))
    schema("https://example.edu/schemas/degree.json")
}
```

**Outcome:** Returns a `VerifiableCredential` data structure ready to sign, store, or anchor.

### Credential Builder Methods

- `id(String)`: Set credential ID
- `type(String...)`: Add credential types (first is primary type)
- `issuer(String)`: Set issuer DID
- `subject { }`: Build credential subject (nested JSON objects)
- `issued(Instant)`: Set issuance date
- `expires(Instant)` or `expires(Long, ChronoUnit)`: Set expiration
- `schema(String)`: Set credential schema
- `status(String)`: Set revocation status
- `evidence(JsonObject)`: Add evidence
- `termsOfUse(JsonObject)`: Add terms of use
- `refreshService(JsonObject)`: Add refresh service

## Issuance DSL

Issue credentials with automatic proof generation:

```kotlin
import com.trustweave.trust.types.IssuerIdentity
import com.trustweave.trust.types.ProofType

val issuedCredential = trustLayer.issue {
    credential {
        type("DegreeCredential")
        issuer("did:key:university")
        subject {
            id("did:key:student")
            "degree" {
                "type" to "BachelorDegree"
            }
        }
        issued(Instant.now())
    }
    signedBy(IssuerIdentity.from("did:key:university", "key-1"))
    withProof(ProofType.Ed25519Signature2020)
    challenge("challenge-123")
    domain("example.com")
    anchor()  // Automatically anchor if autoAnchor is enabled
}
```

**Outcome:** Returns a signed credential (`VerifiableCredential`) with optional anchoring and proof configuration baked in.

### Issuance Builder Methods

- `credential { }`: Build credential inline using CredentialBuilder DSL
- `credential(VerifiableCredential)`: Use a pre-built credential
- `signedBy(IssuerIdentity)`: Specify issuer identity with type-safe DID and key ID (required)
- `withProof(ProofType)`: Set proof type (defaults to trust layer default)
- `challenge(String)`: Set proof challenge for verification
- `domain(String)`: Set proof domain for verification
- `withRevocation()`: Enable automatic revocation support (creates status list if needed)

**Example:**
```kotlin
import com.trustweave.trust.types.IssuerIdentity
import com.trustweave.trust.types.ProofType

val issuedCredential = trustWeave.issue {
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
        issued(Instant.now())
        withRevocation() // Auto-creates status list if needed
    }
    signedBy(IssuerIdentity.from("did:key:university", "key-1"))
    withProof(ProofType.Ed25519Signature2020)
    challenge("challenge-123")
    domain("example.com")
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
- `skipRevocationCheck()`: Disable revocation checking
- `checkExpiration()`: Enable expiration checking
- `skipExpirationCheck()`: Disable expiration checking
- `validateSchema(String)`: Enable schema validation
- `validateProofPurpose()`: Enable proof purpose validation

## Wallet DSL

Create and manage wallets:

```kotlin
val wallet = trustWeave.wallet {
    holder("did:key:holder")
    // Additional wallet configuration
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

## Next Steps

- [Quick Start](quick-start.md) - Get started with a complete example
- [API Reference](../api-reference/core-api.md) - Complete API documentation
- [Core Concepts](../core-concepts/README.md) - Deep dives into DIDs, VCs, etc.