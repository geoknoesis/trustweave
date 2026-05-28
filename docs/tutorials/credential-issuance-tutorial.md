---
title: Credential Issuance Tutorial
nav_exclude: true
---

# Credential Issuance Tutorial

This tutorial provides a comprehensive guide to issuing verifiable credentials with TrustWeave. You'll learn how to issue credentials, verify them, and handle the complete credential lifecycle.

```kotlin
dependencies {
    implementation("org.trustweave:distribution-all:0.6.0")
    // Or use individual modules:
    // implementation("org.trustweave:trust:0.6.0")
    // implementation("org.trustweave:testkit:0.6.0")
}
```

**Result:** Gives you the credential issuance APIs, DID methods, KMS abstractions, and in-memory implementations used throughout this tutorial.

> Tip: The runnable quick-start sample (`./gradlew :TrustWeave-examples:runQuickStartSample`) mirrors the core flows below. Clone it as a starting point before wiring more advanced credential logic.

## Prerequisites

- Basic understanding of Kotlin
- Familiarity with coroutines
- Understanding of [DIDs](../core-concepts/dids.md) and [Verifiable Credentials](../core-concepts/verifiable-credentials.md)
- Completion of [DID Operations Tutorial](did-operations-tutorial.md) recommended

## Table of Contents

1. [Understanding Credential Issuance](#understanding-credential-issuance)
2. [Issuing Credentials](#issuing-credentials)
3. [Verifying Credentials](#verifying-credentials)
4. [Credential Lifecycle](#credential-lifecycle)
5. [Advanced Credential Operations](#advanced-credential-operations)
6. [Error Handling](#error-handling)

## Understanding Credential Issuance

A verifiable credential is a tamper-evident credential that has authorship that can be cryptographically verified. The issuance process involves:

1. **Issuer** creates a DID and keys
2. **Issuer** creates credential with subject information
3. **Issuer** signs credential with issuer key
4. **Holder** receives credential
5. **Verifier** verifies credential signature

**What this does:** Defines the credential issuance workflow.

### Type-Safe Credential Types

TrustWeave uses strongly-typed `CredentialType` instances for credential types. You can use:
- **Built-in types**: `CredentialType.Education`, `CredentialType.Person`, `CredentialType.Degree`, etc.
- **Convenience constants**: `CredentialTypes.EDUCATION`, `CredentialTypes.PERSON` (returns `CredentialType` instances)
- **Custom types**: `CredentialType.Custom("MyCustomType")` or `CredentialType.fromString("MyCustomType")`

**Example:**
```kotlin
credential {
    type(CredentialType.Person)  // Type-safe
    // OR
    type(CredentialTypes.PERSON)  // Convenience constant
    // OR
    type(CredentialType.Custom("MyCustomCredential"))  // Custom type
}
```

**Outcome:** Enables secure, verifiable credential issuance with cryptographic proof.

## Issuing Credentials

### Using TrustWeave Service API (Recommended)

```kotlin
// Kotlin stdlib
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.Clock

// TrustWeave core
import org.trustweave.trust.TrustWeave
import org.trustweave.trust.dsl.credential.DidMethods
import org.trustweave.trust.dsl.credential.KeyAlgorithms
import org.trustweave.trust.dsl.credential.KmsProviders
import org.trustweave.credential.model.ProofType
import org.trustweave.credential.model.CredentialTypes
import org.trustweave.trust.types.getOrThrowDid
import org.trustweave.credential.results.getOrThrow
import org.trustweave.did.resolver.DidResolutionResult
import org.trustweave.did.resolver.errorMessage
import org.trustweave.did.identifiers.extractKeyId
import org.trustweave.did.identifiers.Did

fun main() = runBlocking {
    val trustWeave = TrustWeave.build {
        keys { provider(KmsProviders.IN_MEMORY); algorithm(KeyAlgorithms.ED25519) }
        did { method(DidMethods.KEY) { algorithm(KeyAlgorithms.ED25519) } }
        credentials { defaultProofType(ProofType.Ed25519Signature2020) }  // Using ProofType sealed type
    }

    // Create issuer DID
    val issuerDid = trustWeave.createDid {
        method(DidMethods.KEY)
        algorithm(KeyAlgorithms.ED25519)
    }.getOrThrowDid()

    // Resolve DID to get key ID
    val issuerDoc = when (val res = trustWeave.resolveDid(issuerDid)) {
        is DidResolutionResult.Success -> res.document
        else -> throw IllegalStateException(res.errorMessage ?: "Failed to resolve DID")
    }
    val issuerKeyId = issuerDoc.verificationMethod.firstOrNull()?.extractKeyId()
        ?: throw IllegalStateException("No verification method found")

    // Issue credential using DSL
    val credential = trustWeave.issue {
        credential {
            type(CredentialTypes.PERSON)
            issuer(issuerDid)
            subject(Did("did:key:subject")) {
                "type" to "Person"
                "name" to "Alice"
                "email" to "alice@example.com"
            }
            issued(Clock.System.now())
        }
        signedBy(issuerDid, issuerKeyId)
    }.getOrThrow()

    println("Issued credential: ${credential.id}")
    println("Issuer: ${credential.issuer}")
    println("Subject: ${credential.credentialSubject}")
    println("Proof: ${credential.proof}")
}
```

**Outcome:** Issues a verifiable credential with the issuer's DID and cryptographic signature.

### Issuing Credentials with Custom Options

```kotlin
import org.trustweave.trust.TrustWeave
import org.trustweave.trust.dsl.credential.DidMethods
import org.trustweave.trust.dsl.credential.KeyAlgorithms
import org.trustweave.trust.dsl.credential.KmsProviders
import org.trustweave.credential.model.CredentialTypes
import org.trustweave.trust.types.DidCreationResult
import org.trustweave.did.resolver.DidResolutionResult
import org.trustweave.did.resolver.errorMessage
import org.trustweave.did.identifiers.extractKeyId
import org.trustweave.credential.results.getOrThrow
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.Clock
import kotlin.time.Duration.Companion.days

fun main() = runBlocking {
    val trustWeave = TrustWeave.build {
        keys { provider(KmsProviders.IN_MEMORY); algorithm(KeyAlgorithms.ED25519) }
        did { method(DidMethods.KEY) { algorithm(KeyAlgorithms.ED25519) } }
    }
    val didResult = trustWeave.createDid { method(DidMethods.KEY) }
    val issuerDid = when (didResult) {
        is DidCreationResult.Success -> didResult.did
        else -> {
            val errorMsg = when (didResult) {
                is DidCreationResult.Failure.MethodNotRegistered -> 
                    "Method '${didResult.method}' not registered. Available: ${didResult.availableMethods.joinToString()}"
                is DidCreationResult.Failure.KeyGenerationFailed -> 
                    "Key generation failed: ${didResult.reason}"
                is DidCreationResult.Failure.DocumentCreationFailed -> 
                    "Document creation failed: ${didResult.reason}"
                is DidCreationResult.Failure.InvalidConfiguration -> 
                    "Invalid configuration: ${didResult.reason}"
                is DidCreationResult.Failure.Other -> 
                    "Error: ${didResult.reason}"
            }
            println("Failed to create DID: $errorMsg")
            return@runBlocking
        }
    }
    
    val issuerDoc = when (val res = trustWeave.resolveDid(issuerDid)) {
        is DidResolutionResult.Success -> res.document
        else -> throw IllegalStateException(res.errorMessage ?: "Failed to resolve issuer DID")
    }
    val issuerKeyId = issuerDoc.verificationMethod.firstOrNull()?.extractKeyId()
        ?: throw IllegalStateException("No verification method found")

    // Issue credential with custom expiration
    val issuanceResult = trustWeave.issue {
        credential {
            type(CredentialTypes.PERSON)
            issuer(issuerDid)
            subject("did:key:subject") {
                "type" to "Person"
                "name" to "Alice"
                "email" to "alice@example.com"
            }
            issued(Clock.System.now())
            expiresIn(365.days)
        }
        signedBy(issuerDid = issuerDid, keyId = issuerKeyId)
    }

    val credential = issuanceResult.getOrThrow()
    println("Issued credential with expiration: ${credential.expirationDate}")
    if (credential.credentialStatus != null) {
        println("Status: ${credential.credentialStatus}")
    }
}
```

**Outcome:** Issues a credential with custom expiration and status information.

## Verifying Credentials

### Verifying Credentials

```kotlin
import org.trustweave.trust.TrustWeave
import org.trustweave.trust.dsl.credential.DidMethods
import org.trustweave.trust.dsl.credential.KeyAlgorithms
import org.trustweave.trust.dsl.credential.KmsProviders
import org.trustweave.credential.model.vc.VerifiableCredential
import org.trustweave.credential.results.VerificationResult
import kotlinx.coroutines.runBlocking

fun main() = runBlocking {
    val trustWeave = TrustWeave.build {
        keys { provider(KmsProviders.IN_MEMORY); algorithm(KeyAlgorithms.ED25519) }
        did { method(DidMethods.KEY) { algorithm(KeyAlgorithms.ED25519) } }
    }

    val credential: VerifiableCredential = TODO("use a credential from the issuance step")

    val result = trustWeave.verify {
        credential(credential)
    }

    when (result) {
        is VerificationResult.Valid -> {
            println("Credential is valid: ${result.credential.id}")
            result.warnings.forEach { println("Warning: $it") }
        }
        is VerificationResult.Invalid -> {
            println("Credential is invalid: ${result.allErrors.joinToString()}")
        }
    }
}
```

**Outcome:** Verifies a credential's signature and validity.

### Verifying with Custom Configuration

```kotlin
import org.trustweave.trust.TrustWeave
import org.trustweave.trust.dsl.credential.DidMethods
import org.trustweave.trust.dsl.credential.KeyAlgorithms
import org.trustweave.trust.dsl.credential.KmsProviders
import org.trustweave.credential.model.vc.VerifiableCredential
import org.trustweave.credential.results.VerificationResult
import kotlinx.coroutines.runBlocking

fun main() = runBlocking {
    val trustWeave = TrustWeave.build {
        keys { provider(KmsProviders.IN_MEMORY); algorithm(KeyAlgorithms.ED25519) }
        did { method(DidMethods.KEY) { algorithm(KeyAlgorithms.ED25519) } }
    }

    val credential: VerifiableCredential = TODO("use a credential from the issuance step")

    val result = trustWeave.verify {
        credential(credential)
        checkExpiration()
        checkRevocation()
    }

    when (result) {
        is VerificationResult.Valid -> println("Credential verified successfully")
        is VerificationResult.Invalid -> println("Verification failed: ${result.allErrors.joinToString()}")
    }
}
```

**Outcome:** Verifies a credential with custom verification configuration.

## Credential Lifecycle

### Credential Lifecycle Management

```kotlin
// Kotlin stdlib
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.Clock

// TrustWeave core
import org.trustweave.trust.TrustWeave
import org.trustweave.trust.dsl.credential.DidMethods
import org.trustweave.trust.dsl.credential.KeyAlgorithms
import org.trustweave.trust.dsl.credential.KmsProviders
import org.trustweave.trust.types.DidCreationResult
import org.trustweave.credential.model.ProofType
import org.trustweave.credential.results.IssuanceResult
import org.trustweave.credential.results.VerificationResult
import org.trustweave.did.resolver.DidResolutionResult
import org.trustweave.did.identifiers.extractKeyId

fun main() = runBlocking {
    val trustWeave = TrustWeave.build {
        keys { provider(KmsProviders.IN_MEMORY); algorithm(KeyAlgorithms.ED25519) }
        did { method(DidMethods.KEY) { algorithm(KeyAlgorithms.ED25519) } }
        credentials { defaultProofType(ProofType.Ed25519Signature2020) }
    }

    val didResult = trustWeave.createDid {
        method(DidMethods.KEY)
        algorithm(KeyAlgorithms.ED25519)
    }

    val issuerDid = when (didResult) {
        is DidCreationResult.Success -> didResult.did
        else -> {
            val errorMsg = when (didResult) {
                is DidCreationResult.Failure.MethodNotRegistered -> 
                    "Method '${didResult.method}' not registered. Available: ${didResult.availableMethods.joinToString()}"
                is DidCreationResult.Failure.KeyGenerationFailed -> 
                    "Key generation failed: ${didResult.reason}"
                is DidCreationResult.Failure.DocumentCreationFailed -> 
                    "Document creation failed: ${didResult.reason}"
                is DidCreationResult.Failure.InvalidConfiguration -> 
                    "Invalid configuration: ${didResult.reason}"
                is DidCreationResult.Failure.Other -> 
                    "Error: ${didResult.reason}"
            }
            println("Failed to create DID: $errorMsg")
            return@runBlocking
        }
    }
    
    val resolution = trustWeave.resolveDid(issuerDid)
    val issuerDoc = when (resolution) {
        is DidResolutionResult.Success -> resolution.document
        else -> throw IllegalStateException("Failed to resolve issuer DID")
    }
    val issuerKeyId = issuerDoc.verificationMethod.firstOrNull()?.extractKeyId()
        ?: throw IllegalStateException("No verification method found")

    // Issue credential
    val issuanceResult = trustWeave.issue {
        credential {
            type("VerifiableCredential")
            issuer(issuerDid)
            subject("did:key:subject") {
                "name" to "Alice"
            }
            issued(Clock.System.now())
        }
        signedBy(issuerDid = issuerDid, keyId = issuerKeyId)
    }

    val credential = when (issuanceResult) {
        is IssuanceResult.Success -> {
            println("Issued: ${issuanceResult.credential.id}")
            issuanceResult.credential
        }
        else -> {
            println("Error: ${issuanceResult.allErrors.joinToString("; ")}")
            return@runBlocking
        }
    }

    // Verify credential
    val verificationResult = trustWeave.verify {
        credential(credential)
    }
    when (verificationResult) {
        is VerificationResult.Valid -> println("Valid: true")
        is VerificationResult.Invalid.Expired -> println("Valid: false — expired at ${verificationResult.expiredAt}")
        is VerificationResult.Invalid.NotYetValid -> println("Valid: false — not yet valid (validFrom ${verificationResult.validFrom})")
        is VerificationResult.Invalid.Revoked -> println("Valid: false — revoked")
        is VerificationResult.Invalid.InvalidProof -> println("Valid: false — invalid proof: ${verificationResult.reason}")
        is VerificationResult.Invalid.InvalidIssuer -> println("Valid: false — issuer: ${verificationResult.reason}")
        is VerificationResult.Invalid.UntrustedIssuer -> println("Valid: false — untrusted issuer: ${verificationResult.issuerDid.value}")
        is VerificationResult.Invalid.UnsupportedFormat -> println("Valid: false — unsupported format ${verificationResult.format.value}")
        is VerificationResult.Invalid.AdapterNotReady -> println("Valid: false — adapter not ready: ${verificationResult.reason}")
        is VerificationResult.Invalid.SchemaValidationFailed -> println("Valid: false — schema: ${verificationResult.allErrors.joinToString()}")
        is VerificationResult.Invalid.MultipleFailures -> println("Valid: false — ${verificationResult.allErrors.joinToString()}")
    }

    // Note: Revocation is typically handled through credential status lists
    // and checked during verification. See revocation documentation for details.
}
```

**Outcome:** Demonstrates the complete credential lifecycle from issuance to verification.

## Advanced Credential Operations

### Batch Credential Issuance

```kotlin
// Kotlin stdlib
import kotlinx.coroutines.runBlocking

// TrustWeave core
import org.trustweave.trust.TrustWeave
import org.trustweave.trust.dsl.credential.DidMethods
import org.trustweave.trust.dsl.credential.KeyAlgorithms
import org.trustweave.trust.dsl.credential.KmsProviders
import org.trustweave.trust.types.DidCreationResult
import org.trustweave.did.resolver.DidResolutionResult
import org.trustweave.did.resolver.errorMessage
import org.trustweave.did.identifiers.extractKeyId
import org.trustweave.credential.results.IssuanceResult

fun main() = runBlocking {
    val trustWeave = TrustWeave.build {
        keys { provider(KmsProviders.IN_MEMORY); algorithm(KeyAlgorithms.ED25519) }
        did { method(DidMethods.KEY) { algorithm(KeyAlgorithms.ED25519) } }
    }
    val didResult = trustWeave.createDid { method(DidMethods.KEY) }
    val issuerDid = when (didResult) {
        is DidCreationResult.Success -> didResult.did
        else -> {
            val errorMsg = when (didResult) {
                is DidCreationResult.Failure.MethodNotRegistered -> 
                    "Method '${didResult.method}' not registered. Available: ${didResult.availableMethods.joinToString()}"
                is DidCreationResult.Failure.KeyGenerationFailed -> 
                    "Key generation failed: ${didResult.reason}"
                is DidCreationResult.Failure.DocumentCreationFailed -> 
                    "Document creation failed: ${didResult.reason}"
                is DidCreationResult.Failure.InvalidConfiguration -> 
                    "Invalid configuration: ${didResult.reason}"
                is DidCreationResult.Failure.Other -> 
                    "Error: ${didResult.reason}"
            }
            println("Failed to create DID: $errorMsg")
            return@runBlocking
        }
    }
    
    val issuerDoc = when (val res = trustWeave.resolveDid(issuerDid)) {
        is DidResolutionResult.Success -> res.document
        else -> throw IllegalStateException(res.errorMessage ?: "Failed to resolve issuer DID")
    }
    val issuerKeyId = issuerDoc.verificationMethod.firstOrNull()?.extractKeyId()
        ?: throw IllegalStateException("No verification method found")

    // Issue credentials in batch using DSL
    val subjects = listOf(
        mapOf("id" to "did:key:alice", "name" to "Alice"),
        mapOf("id" to "did:key:bob", "name" to "Bob"),
        mapOf("id" to "did:key:charlie", "name" to "Charlie")
    )

    // Issue credentials in batch (one credential per subject)
    val credentials = subjects.mapNotNull { subjectData ->
        val issuanceResult = trustWeave.issue {
            credential {
                type("PersonCredential")
                issuer(issuerDid)
                subject(subjectData["id"] as String) {
                    "name" to (subjectData["name"] as String)
                }
            }
            signedBy(issuerDid = issuerDid, keyId = issuerKeyId)
        }

        when (issuanceResult) {
            is IssuanceResult.Success -> issuanceResult.credential
            is IssuanceResult.Failure -> {
                println(
                    "Failed to issue credential for ${subjectData["id"]}: ${issuanceResult.allErrors.joinToString()}"
                )
                null
            }
        }
    }

    println("Issued ${credentials.size} credentials")
    credentials.forEach { credential ->
        println("Credential: ${credential.id} for ${credential.credentialSubject}")
    }
}
```

**Outcome:** Demonstrates batch credential issuance for multiple subjects.

### Credential Presentations

```kotlin
// Kotlin stdlib
import kotlinx.coroutines.runBlocking

// TrustWeave core
import org.trustweave.trust.TrustWeave
import org.trustweave.trust.dsl.credential.DidMethods
import org.trustweave.trust.dsl.credential.KeyAlgorithms
import org.trustweave.trust.dsl.credential.KmsProviders
import org.trustweave.trust.dsl.credential.presentationResult
import org.trustweave.trust.types.DidCreationResult
import org.trustweave.trust.types.PresentationResult
import org.trustweave.credential.requests.VerificationOptions
import org.trustweave.credential.results.VerificationResult
import org.trustweave.credential.model.vc.VerifiableCredential

fun main() = runBlocking {
    val trustWeave = TrustWeave.build {
        keys { provider(KmsProviders.IN_MEMORY); algorithm(KeyAlgorithms.ED25519) }
        did { method(DidMethods.KEY) { algorithm(KeyAlgorithms.ED25519) } }
    }

    val credentials: List<VerifiableCredential> = TODO("credentials from earlier issuance steps")
    val holderDidResult = trustWeave.createDid { method(DidMethods.KEY) }
    val holderDid = when (holderDidResult) {
        is DidCreationResult.Success -> holderDidResult.did
        is DidCreationResult.Failure.MethodNotRegistered -> {
            println("Holder DID: method not registered: ${holderDidResult.method}")
            return@runBlocking
        }
        is DidCreationResult.Failure.KeyGenerationFailed -> {
            println("Holder DID: key generation failed: ${holderDidResult.reason}")
            return@runBlocking
        }
        is DidCreationResult.Failure.DocumentCreationFailed -> {
            println("Holder DID: document creation failed: ${holderDidResult.reason}")
            return@runBlocking
        }
        is DidCreationResult.Failure.InvalidConfiguration -> {
            println("Holder DID: invalid configuration: ${holderDidResult.reason}")
            return@runBlocking
        }
        is DidCreationResult.Failure.Other -> {
            println("Holder DID: ${holderDidResult.reason}")
            return@runBlocking
        }
    }

    // Build presentation via TrustWeave DSL → CredentialService.createPresentation
    val presentationOutcome = trustWeave.presentationResult {
        credentials(credentials)
        holder(holderDid)
        challenge("tutorial-challenge-" + System.currentTimeMillis())
    }

    val presentation = when (presentationOutcome) {
        is PresentationResult.Success -> presentationOutcome.presentation
        is PresentationResult.Failure -> {
            println("Presentation failed: ${presentationOutcome.allErrors.joinToString()}")
            return@runBlocking
        }
    }

    println("Created presentation: ${presentation.id}")
    println("Holder: ${presentation.holder}")
    println("Credentials: ${presentation.verifiableCredential.size}")

    val credentialService = trustWeave.configuration.credentialService
    if (credentialService == null) {
        println("CredentialService not configured on TrustWeave")
        return@runBlocking
    }

    when (
        val verifyResult = credentialService.verifyPresentation(
            presentation = presentation,
            options = VerificationOptions(),
        )
    ) {
        is VerificationResult.Valid -> println("Presentation verification: valid")
        is VerificationResult.Invalid -> {
            println("Presentation verification: invalid — ${verifyResult.allErrors.joinToString()}")
        }
    }
}
```

**Outcome:** Creates a presentation with **`trustWeave.presentationResult { }`** and verifies it with **`CredentialService.verifyPresentation`** from **`trustWeave.configuration.credentialService`**.

## Error Handling

### Sealed Result Error Handling

TrustWeave uses sealed result types for all I/O operations, providing type-safe, exhaustive error handling:

```kotlin
// Kotlin stdlib
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.Clock

// TrustWeave core
import org.trustweave.trust.TrustWeave
import org.trustweave.trust.dsl.credential.DidMethods
import org.trustweave.trust.dsl.credential.KeyAlgorithms
import org.trustweave.trust.dsl.credential.KmsProviders
import org.trustweave.trust.types.DidCreationResult
import org.trustweave.credential.results.IssuanceResult
import org.trustweave.did.resolver.DidResolutionResult
import org.trustweave.did.resolver.errorMessage
import org.trustweave.did.identifiers.extractKeyId

fun main() = runBlocking {
    val trustWeave = TrustWeave.build {
        keys { provider(KmsProviders.IN_MEMORY); algorithm(KeyAlgorithms.ED25519) }
        did { method(DidMethods.KEY) { algorithm(KeyAlgorithms.ED25519) } }
    }
    
    // Create DID with error handling
    val didResult = trustWeave.createDid { method(DidMethods.KEY) }
    val issuerDid = when (didResult) {
        is DidCreationResult.Success -> didResult.did
        is DidCreationResult.Failure.MethodNotRegistered -> {
            println("DID method not registered: ${didResult.method}")
            println("Available methods: ${didResult.availableMethods.joinToString()}")
            return@runBlocking
        }
        is DidCreationResult.Failure.KeyGenerationFailed -> {
            println("Key generation failed: ${didResult.reason}")
            return@runBlocking
        }
        is DidCreationResult.Failure.DocumentCreationFailed -> {
            println("Document creation failed: ${didResult.reason}")
            return@runBlocking
        }
        is DidCreationResult.Failure.InvalidConfiguration -> {
            println("Invalid configuration: ${didResult.reason}")
            return@runBlocking
        }
        is DidCreationResult.Failure.Other -> {
            println("Error: ${didResult.reason}")
            didResult.cause?.printStackTrace()
            return@runBlocking
        }
    }
    
    val issuerDoc = when (val res = trustWeave.resolveDid(issuerDid)) {
        is DidResolutionResult.Success -> res.document
        else -> throw IllegalStateException(res.errorMessage ?: "Failed to resolve issuer DID")
    }
    val issuerKeyId = issuerDoc.verificationMethod.firstOrNull()?.extractKeyId()
        ?: throw IllegalStateException("No verification method found")

    // Issue credential with error handling
    val issuanceResult = trustWeave.issue {
        credential {
            type("VerifiableCredential")
            issuer(issuerDid)
            subject("did:key:subject") {
                "name" to "Alice"
            }
            issued(Clock.System.now())
        }
        signedBy(issuerDid = issuerDid, keyId = issuerKeyId)
    }

    when (issuanceResult) {
        is IssuanceResult.Success -> {
            println("Issued: ${issuanceResult.credential.id}")
        }
        is IssuanceResult.Failure.UnsupportedFormat -> {
            println("Unsupported format: ${issuanceResult.format.value}")
            println("Supported formats: ${issuanceResult.supportedFormats.joinToString { it.value }}")
        }
        is IssuanceResult.Failure.AdapterNotReady -> {
            println("Adapter not ready: ${issuanceResult.reason ?: "Unknown reason"}")
        }
        is IssuanceResult.Failure.InvalidRequest -> {
            println("Invalid request: field '${issuanceResult.field}' - ${issuanceResult.reason}")
        }
        is IssuanceResult.Failure.AdapterError -> {
            println("Adapter error: ${issuanceResult.reason}")
            issuanceResult.cause?.printStackTrace()
        }
        is IssuanceResult.Failure.MultipleFailures -> {
            println("Multiple failures: ${issuanceResult.allErrors.joinToString("; ")}")
        }
    }
}
```

**Outcome:** Demonstrates exhaustive error handling using sealed result types with `when` expressions.

## Next Steps

- Review [Verifiable Credentials Concepts](../core-concepts/verifiable-credentials.md) for deeper understanding
- See [Wallet API Tutorial](wallet-api-tutorial.md) for credential storage
- Explore [Verification Policies](../advanced/verification-policies.md) for advanced verification
- Check [Creating Plugins](../contributing/creating-plugins.md) to implement custom credential services

## References

- W3C Verifiable Credentials Specification](https://www.w3.org/TR/vc-data-model/)
- TrustWeave Common Module](../modules/trustweave-common.md)
- TrustWeave Core API](../api-reference/core-api.md)

