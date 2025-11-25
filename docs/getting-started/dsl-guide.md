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
import com.trustweave.credential.dsl.*

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
        defaultProofType("Ed25519Signature2020")
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
    by(issuerDid = "did:key:university", keyId = "key-1")
    withProof("Ed25519Signature2020")
    challenge("challenge-123")
    domain("example.com")
    anchor()  // Automatically anchor if autoAnchor is enabled
}
```

**Outcome:** Returns a signed credential (`Result<VerifiableCredential>`) with optional anchoring and proof configuration baked in.

### Issuance Builder Methods

- `credential { }`: Build credential inline or use pre-built credential
- `by(issuerDid: String, keyId: String)`: Specify issuer and key
- `withProof(String)`: Set proof type (defaults to trust layer default)
- `challenge(String)`: Set proof challenge
- `