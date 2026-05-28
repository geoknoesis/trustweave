---
title: Credential Service API Reference
nav_order: 6
parent: API Reference
---

# Credential Service API Reference

This page describes the **`CredentialService`** API (`credentials/credential-api`), **`IssuanceRequest`** / **`VerificationOptions`**, and how instances are created for **`TrustWeave`**.

```kotlin
dependencies {
    implementation("org.trustweave:credentials-credential-api:0.6.0")
}
```

(Or depend on **`distribution-all`** / **`trust`** so the facade pulls credential APIs transitively.)

## `CredentialService`

Implementations issue and verify **W3C Verifiable Credentials** (VC-LD, SD-JWT-VC, etc.), create/verify presentations, and expose format capabilities.

**Core operations (see source for full KDoc):**

```kotlin
interface CredentialService {
    suspend fun issue(request: IssuanceRequest): IssuanceResult

    suspend fun verify(
        credential: VerifiableCredential,
        trustPolicy: TrustEvaluator? = null,
        options: VerificationOptions = VerificationOptions()
    ): VerificationResult

    suspend fun verify(
        credentials: List<VerifiableCredential>,
        trustPolicy: TrustEvaluator? = null,
        options: VerificationOptions = VerificationOptions()
    ): List<VerificationResult>

    suspend fun createPresentation(
        credentials: List<VerifiableCredential>,
        request: PresentationRequest
    ): VerifiablePresentation

    suspend fun verifyPresentation(
        presentation: VerifiablePresentation,
        trustPolicy: TrustEvaluator? = null,
        options: VerificationOptions = VerificationOptions()
    ): VerificationResult

    suspend fun status(credential: VerifiableCredential): CredentialStatusInfo

    fun supports(format: ProofSuiteId): Boolean
    fun supportedFormats(): List<ProofSuiteId>
    fun supportsCapability(
        format: ProofSuiteId,
        capability: ProofEngineCapabilities.() -> Boolean
    ): Boolean
}
```

| Operation | Returns | Notes |
|-----------|---------|--------|
| `issue` | `IssuanceResult` | Use `when` on success/failure; `getOrThrow()` lives in `org.trustweave.credential.results`. |
| `verify` | `VerificationResult` | Optional **`TrustEvaluator`** for issuer trust; options control revocation, expiration, schema, etc. |
| `createPresentation` / `verifyPresentation` | `VerifiablePresentation` / `VerificationResult` | Challenge/domain live in **`PresentationRequest`** / proof options. |
| `status` | `CredentialStatusInfo` | Fast revocation/validity snapshot. |

## Building a `CredentialService`

Use the factory helpers in **`org.trustweave.credential`** (not a separate “registry” class):

```kotlin
import org.trustweave.credential.credentialService
import org.trustweave.credential.CredentialServices

// Typical: resolver + optional schema/revocation
val service = credentialService(
    didResolver = didResolver,
    schemaRegistry = null,
    revocationManager = null,
)

// Or KMS-integrated (used by TrustWeave factory)
val fromKms = CredentialServices.createCredentialService(
    kms = kms,
    didResolver = didResolver
)
```

`TrustWeave.build { … }` wires a **`CredentialService`** when KMS + DID resolution are available, unless you override with **`credentialService(...)`** in the DSL.

## Request types

- **`IssuanceRequest`** — VC payload + **`ProofSuiteId`**, issuer key id, validity window, **`ProofOptions`**, schema, status, evidence.
- **`VerificationOptions`** — revocation, expiration, schema validation, issuer resolution toggles.
- **`PresentationRequest`** — credentials to include, selective disclosure, presentation **`ProofOptions`** (challenge, domain, etc.).

Issuer trust at verification time uses **`org.trustweave.credential.trust.TrustEvaluator`** (allowlist/blocklist/custom), or trust-registry-backed evaluators from the **`trust`** module via the **`verify { }`** DSL.

## Related

- **[TrustWeave facade](core-api.md)** — `issue { }`, `verify { }`, `presentationResult { }`.
- **[Result types](result-types-guide.md)** — `IssuanceResult`, `VerificationResult`, `PresentationResult`.
- **`DefaultCredentialService`** — internal implementation combining built-in proof engines.
