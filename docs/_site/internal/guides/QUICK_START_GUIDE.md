# TrustWeave Credential Service - Quick Start Guide

## Overview

The TrustWeave Credential Service provides a simple, type-safe API for issuing and verifying W3C Verifiable Credentials. All proof formats (VC-LD, VC-JWT, SD-JWT-VC) are **built-in and always available** - no configuration needed.

## Getting Started

### 1. Create a Service

```kotlin
import com.trustweave.credential.CredentialServices
import com.trustweave.did.resolver.DidResolver

val service = CredentialServices.default(didResolver)
```

That's it! All proof formats are automatically available.

### 2. Issue a Credential

```kotlin
import com.trustweave.credential.format.CredentialFormatId
import com.trustweave.credential.model.vc.Issuer
import com.trustweave.credential.model.vc.CredentialSubject
import com.trustweave.credential.model.CredentialType
import com.trustweave.credential.requests.IssuanceRequest
import com.trustweave.credential.results.IssuanceResult
import com.trustweave.did.identifiers.Did
import kotlinx.serialization.json.JsonPrimitive
import java.time.Instant

val result: IssuanceResult = service.issue(
    IssuanceRequest(
        format = CredentialFormatId("vc-ld"),
        issuer = Issuer.fromDid(Did("did:key:issuer")),
        credentialSubject = CredentialSubject.fromDid(
            did = Did("did:key:subject"),
            claims = mapOf(
                "name" to JsonPrimitive("Alice"),
                "email" to JsonPrimitive("alice@example.com")
            )
        ),
        type = listOf(
            CredentialType("VerifiableCredential"),
            CredentialType("PersonCredential")
        ),
        issuedAt = Instant.now()
    )
)

when (result) {
    is IssuanceResult.Success -> {
        println("✅ Issued: ${result.credential.id}")
    }
    is IssuanceResult.Failure -> {
        println("❌ Failed: ${result.allErrors.joinToString()}")
    }
}
```

### 3. Verify a Credential

```kotlin
import com.trustweave.credential.requests.VerificationOptions
import com.trustweave.credential.results.VerificationResult
import com.trustweave.credential.trust.TrustPolicy
import com.trustweave.did.identifiers.Did

// Basic verification (no trust policy)
val verification: VerificationResult = service.verify(
    credential = credential,
    options = VerificationOptions(
        checkRevocation = true,
        checkExpiration = true,
        resolveIssuerDid = true
    )
)

// Verification with trust policy
val trustPolicy = TrustPolicy.allowlist(
    trustedIssuers = setOf(Did("did:web:example.com"))
)

val verificationWithTrust: VerificationResult = service.verify(
    credential = credential,
    trustPolicy = trustPolicy,
    options = VerificationOptions()
)

when (verificationWithTrust) {
    is VerificationResult.Valid -> {
        println("✅ Valid credential from trusted issuer")
    }
    is VerificationResult.Invalid.UntrustedIssuer -> {
        println("❌ Issuer is not trusted")
    }
    is VerificationResult.Invalid -> {
        println("❌ Invalid: ${verificationWithTrust.allErrors.joinToString()}")
    }
}
```

## Simplified DID-Based Issuance

For DID-based credentials, use the extension function that handles DID resolution automatically:

```kotlin
import com.trustweave.credential.did.issueForDid

val result = service.issueForDid(
    didResolver = didResolver,
    subjectDid = Did("did:key:subject"),
    issuerDid = Did("did:key:issuer"),
    type = listOf(CredentialType("VerifiableCredential")),
    claims = mapOf("name" to JsonPrimitive("Alice")),
    format = CredentialFormatId("vc-ld")
)

// Returns IssuanceResult - no exceptions thrown!
when (result) {
    is IssuanceResult.Success -> { /* Use credential */ }
    is IssuanceResult.Failure -> { /* Handle error */ }
}
```

## Batch Operations

Verify multiple credentials in parallel:

```kotlin
val results = service.verify(
    credentials = listOf(credential1, credential2, credential3),
    options = VerificationOptions()
)

val validCount = results.count { it is VerificationResult.Valid }
```

## Check Credential Status

```kotlin
val status = service.status(credential)

when {
    status.valid -> println("✅ Valid")
    status.revoked -> println("❌ Revoked")
    status.expired -> println("❌ Expired")
}
```

## Supported Formats

All proof formats are built-in and always available:

```kotlin
val formats = service.supportedFormats()
// Returns: [vc-ld, vc-jwt, sd-jwt-vc]

val isSupported = service.supports(CredentialFormatId("vc-ld"))
// Returns: true
```

## Error Handling

All operations return sealed Result types for exhaustive, type-safe error handling:

```kotlin
when (result) {
    is IssuanceResult.Success -> { /* Success */ }
    is IssuanceResult.Failure.UnsupportedFormat -> { /* Format not available */ }
    is IssuanceResult.Failure.InvalidRequest -> { /* Invalid input */ }
    is IssuanceResult.Failure.AdapterError -> { /* Adapter error */ }
    // Compiler ensures all cases are handled
}
```

## Migration from Old API

### Before (Deprecated)

```kotlin
val registry = ProofRegistries.default()
ProofAdapters.autoRegister(registry, emptyMap())
val service = createCredentialService(registry, didResolver)
```

### After (Recommended)

```kotlin
val service = CredentialServices.default(didResolver)
```

The old API still works but is deprecated. Migrate to the new simplified API for better maintainability.

## Complete Example

```kotlin
import com.trustweave.credential.*
import com.trustweave.did.resolver.DidResolver
import kotlinx.coroutines.runBlocking

fun main() = runBlocking {
    // 1. Create service
    val service = CredentialServices.default(didResolver)
    
    // 2. Issue credential
    val issuanceResult = service.issue(
        IssuanceRequest(
            format = CredentialFormatId("vc-ld"),
            issuer = Issuer.fromDid(Did("did:key:issuer")),
            credentialSubject = CredentialSubject.fromDid(
                did = Did("did:key:subject"),
                claims = mapOf("name" to JsonPrimitive("Alice"))
            ),
            type = listOf(CredentialType("VerifiableCredential")),
            issuedAt = Instant.now()
        )
    )
    
    // 3. Handle result
    val credential = when (issuanceResult) {
        is IssuanceResult.Success -> issuanceResult.credential
        is IssuanceResult.Failure -> return@runBlocking
    }
    
    // 4. Verify
    val verification = service.verify(credential)
    
    when (verification) {
        is VerificationResult.Valid -> println("✅ Valid!")
        is VerificationResult.Invalid -> println("❌ Invalid")
    }
}
```

## Key Benefits

1. **Simple** - Single factory method, no configuration needed
2. **Type-Safe** - Sealed Result types ensure exhaustive error handling
3. **Built-in** - All proof formats always available
4. **Idiomatic** - Follows Kotlin best practices
5. **Consistent** - All operations return Result types, no exceptions

## Next Steps

- See `USAGE_EXAMPLE_NEW.kt` for more complete examples
- Check `IMPLEMENTATION_SUMMARY.md` for migration details
- Review `KOTLIN_SDK_REVIEW.md` for API design rationale

