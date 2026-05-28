---
title: Proofs and Proof Engines
nav_order: 4
parent: Core Concepts
---

# Proofs and Proof Engines

## Overview

TrustWeave's proof system provides cryptographic proof generation and verification for Verifiable Credentials. The architecture is built around **Proof Engines** that handle proof suite-specific operations, and **Proof Options** that configure cross-suite proof parameters.

## Key Concepts

### Proof Suites

A **proof suite** identifies the cryptographic proof format used in a Verifiable Credential. TrustWeave supports multiple proof suites:

- **VC-LD** (`ProofSuiteId.VC_LD`) - W3C Verifiable Credentials with Linked Data Proofs
- **VC-JWT** (`ProofSuiteId.VC_JWT`) - W3C Verifiable Credentials as JWT
- **SD-JWT-VC** (`ProofSuiteId.SD_JWT_VC`) - IETF Selective Disclosure JWT Verifiable Credentials
- **mDoc/mDL** (`ProofSuiteId.MDOC`) - ISO 18013-5 mobile documents (CBOR/COSE)
- **BBS 2023** (`ProofSuiteId.BBS_2023`) - W3C Data Integrity BBS Cryptosuite (selective disclosure + ZK)

The VC-LD and SD-JWT-VC engines are **built-in** to the credential API; mDoc and BBS are
shipped as separate plugin modules. No additional registration is required when the
plugin JARs are on the classpath.

```kotlin
import org.trustweave.credential.format.ProofSuiteId

// Use enum values directly
val vcLdSuite = ProofSuiteId.VC_LD
val sdJwtVcSuite = ProofSuiteId.SD_JWT_VC
val mdocSuite = ProofSuiteId.MDOC
```

### Proof Options

`ProofOptions` configure proof generation parameters that apply across all proof suites:

- **Purpose** - Why the proof exists (assertionMethod, authentication, etc.)
- **Challenge** - Nonce for non-repudiation (prevents replay attacks)
- **Domain** - Domain binding string (prevents cross-domain attacks)
- **Verification Method** - Explicit key/verification method to use
- **Additional Options** - Proof suite-specific parameters

```kotlin
import org.trustweave.credential.proof.*

// Simple - defaults to assertionMethod
val options = proofOptions()

// With DSL builder
val options = proofOptions {
    purpose = ProofPurpose.Authentication
    challenge = generateChallenge()
    domain = "example.com"
    verificationMethod = "did:key:example#key-1"
}

// Using convenience functions
val authOptions = proofOptionsForAuthentication(
    challenge = "nonce-123",
    domain = "example.com"
)

val presentationOptions = proofOptionsForPresentation(
    challenge = verifierChallenge,
    domain = "example.com"
)
```

### Proof Engines

A **Proof Engine** is an implementation that handles proof operations for a specific proof suite. Each engine:

- Generates cryptographic proofs during credential issuance
- Verifies proofs during credential verification
- Creates presentations (if supported)
- Exposes capabilities (selective disclosure, zero-knowledge, etc.)

Proof engines are **internal** - normal users interact with them through `CredentialService`, not directly.

## Proof Purposes

Proof purposes indicate why a proof exists, following W3C Verifiable Credentials standards:

### AssertionMethod
**Default for credential issuance.** Proves that the issuer is asserting the credential claims.

```kotlin
val options = proofOptionsForIssuance()
// or
val options = proofOptions {
    purpose = ProofPurpose.AssertionMethod
}
```

### Authentication
Used when the credential holder authenticates, typically in presentation flows.

```kotlin
val options = proofOptionsForAuthentication(
    challenge = generateChallenge(),
    domain = "example.com"
)
```

### KeyAgreement
For establishing secure channels or key exchange.

```kotlin
val options = proofOptionsForKeyAgreement(
    verificationMethod = "did:key:example#key-agreement-1"
)
```

### CapabilityInvocation
For invoking capabilities or permissions.

```kotlin
val options = proofOptionsForCapabilityInvocation(
    challenge = generateChallenge()
)
```

### CapabilityDelegation
For delegating capabilities or permissions.

```kotlin
val options = proofOptionsForCapabilityDelegation(
    verificationMethod = "did:key:example#delegation-key"
)
```

## Using Proof Options

### In Issuance Requests

```kotlin
import org.trustweave.credential.requests.*
import org.trustweave.credential.proof.*

val request = IssuanceRequest(
    format = ProofSuiteId.VC_LD,
    issuer = issuer,
    credentialSubject = subject,
    type = listOf(CredentialType.VerifiableCredential),
    proofOptions = proofOptions {
        purpose = ProofPurpose.AssertionMethod
        verificationMethod = "did:key:issuer#key-1"
    }
)

// Or using extension function
val request = IssuanceRequest(...)
    .withProofOptions {
        purpose = ProofPurpose.AssertionMethod
        challenge = "nonce-123"
    }
```

### In Presentation Requests

```kotlin
val presentationRequest = PresentationRequest(
    disclosedClaims = setOf("name", "email"),
    proofOptions = proofOptionsForPresentation(
        challenge = verifierChallenge,
        domain = "example.com"
    )
)

// Or using extension function
val request = PresentationRequest(...)
    .withProofOptions {
        challenge = verifierChallenge
        domain = "example.com"
    }
```

### Proof Suite-Specific Options

Use `additionalOptions` for proof suite-specific parameters:

```kotlin
val options = proofOptions {
    purpose = ProofPurpose.AssertionMethod
    option("proofType", "Ed25519Signature2020")
    option("canonicalizationAlgorithm", "urdna2015")
}

// Example: pass plugin-specific options via `option(...)` entries
val customOptions = proofOptions {
    purpose = ProofPurpose.AssertionMethod
    option("hashAlgorithm", "sha-256")
}
```

## Proof Engine Architecture

### Built-in Engines

TrustWeave includes the following proof engines:

- **VcLdProofEngine** (built-in) - Handles VC-LD proofs with JSON-LD canonicalization
- **SdJwtProofEngine** (built-in) - Handles SD-JWT-VC proofs with selective disclosure
- **MdocProofEngine** (`credentials/plugins/mdl`) - ISO 18013-5 mDoc / mDL (CBOR/COSE)
- **Bbs2023ProofEngine** (`credentials/plugins/bbs`) - BBS Cryptosuite with selective disclosure and ZK

VC-LD and SD-JWT-VC are automatically available when you create a `CredentialService`; the
mDoc and BBS engines are picked up via SPI when their plugin modules are on the classpath:

```kotlin
import org.trustweave.credential.*

val service = credentialService(didResolver)
// All proof engines are automatically configured
```

### Engine Capabilities

Each proof engine exposes capabilities:

```kotlin
val capabilities = engine.capabilities

if (capabilities.selectiveDisclosure) {
    // Engine supports selective disclosure
}

if (capabilities.zeroKnowledge) {
    // Engine supports zero-knowledge proofs
}

if (capabilities.presentation) {
    // Engine supports presentations
}

if (capabilities.predicates) {
    // Engine supports predicate proofs
}
```

### Checking Support

Check if a proof suite is supported:

```kotlin
if (service.supports(ProofSuiteId.VC_LD)) {
    // VC-LD is supported
}

// Check capabilities
val supportsSelectiveDisclosure = service.supportsCapability(ProofSuiteId.SD_JWT_VC) {
    selectiveDisclosure
}
```

## Common Patterns

### Issuing with Default Proof Options

```kotlin
val request = IssuanceRequest(
    format = ProofSuiteId.VC_LD,
    issuer = issuer,
    credentialSubject = subject,
    type = listOf(CredentialType.VerifiableCredential)
    // proofOptions defaults to assertionMethod
)

val result = service.issue(request)
```

### Issuing with Custom Proof Options

```kotlin
val request = IssuanceRequest(
    format = ProofSuiteId.VC_LD,
    issuer = issuer,
    credentialSubject = subject,
    type = listOf(CredentialType.VerifiableCredential),
    proofOptions = proofOptions {
        purpose = ProofPurpose.AssertionMethod
        verificationMethod = "did:key:issuer#key-1"
        option("proofType", "Ed25519Signature2020")
    }
)

val result = service.issue(request)
```

### Creating Presentations with Challenge

```kotlin
val challenge = generateChallenge()

val presentationRequest = PresentationRequest(
    disclosedClaims = setOf("name", "email"),
    proofOptions = proofOptionsForPresentation(
        challenge = challenge,
        domain = "example.com"
    )
)

val presentation = service.createPresentation(
    credentials = listOf(credential),
    request = presentationRequest
)
```

### Modifying Proof Options

```kotlin
val baseOptions = proofOptionsForIssuance()

// Add challenge
val withChallenge = baseOptions.withChallenge("nonce-123")

// Change purpose
val forAuth = baseOptions.withPurpose(ProofPurpose.Authentication)

// Add domain
val withDomain = baseOptions.withDomain("example.com")
```

## Best Practices

1. **Use appropriate proof purposes**
   - `AssertionMethod` for credential issuance
   - `Authentication` for presentations

2. **Always include challenges in presentations**
   - Prevents replay attacks
   - Use `generateChallenge()` for random nonces

3. **Use domain binding for web flows**
   - Prevents cross-domain attacks
   - Common in OID4VP flows

4. **Specify verification method when needed**
   - Helps when issuer has multiple keys
   - Improves performance by avoiding key resolution

5. **Use proof suite-specific options sparingly**
   - Prefer standard `ProofOptions` when possible
   - Use `additionalOptions` only for suite-specific needs

## Related Documentation

- Proof Purpose Validation](./proof-purpose-validation.md) - Detailed explanation of proof purposes
- Verifiable Credentials](./verifiable-credentials.md) - W3C VC data model
- Key Management](./key-management.md) - Key management for signing
- Credential Service API](../api-reference/credential-service-api.md) - Complete API reference

