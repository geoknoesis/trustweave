---
title: Credential Issuance Tutorial
nav_exclude: true
---

# Credential Issuance Tutorial

This tutorial provides a comprehensive guide to issuing verifiable credentials with TrustWeave. You'll learn how to issue credentials, verify them, and handle the complete credential lifecycle.

```kotlin
dependencies {
    implementation("org.trustweave:distribution-all:1.0.0-SNAPSHOT")
    // Or use individual modules:
    // implementation("org.trustweave:trust:1.0.0-SNAPSHOT")
    // implementation("org.trustweave:testkit:1.0.0-SNAPSHOT")
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
import kotlinx.serialization.json.*
import java.time.Instant

// TrustWeave core
import org.trustweave.trust.TrustWeave
import org.trustweave.trust.dsl.credential.DidMethods
import org.trustweave.trust.dsl.credential.KeyAlgorithms
import org.trustweave.trust.dsl.credential.ProofTypes
import org.trustweave.trust.dsl.credential.CredentialTypes
import org.trustweave.trust.types.ProofType
import org.trustweave.trust.types.CredentialType
import org.trustweave.testkit.services.*

fun main() = runBlocking {
    val trustWeave = TrustWeave.build {
        factories(
            kmsFactory = TestkitKmsFactory(),  // Test-only factory for tutorials
            didMethodFactory = TestkitDidMethodFactory()  // Test-only factory for tutorials
        )
        keys { provider(IN_MEMORY); algorithm(KeyAlgorithms.ED25519) }
        did { method(DidMethods.KEY) { algorithm(KeyAlgorithms.ED25519) } }
        credentials { defaultProofType(ProofType.Ed25519Signature2020) }  // Using ProofType enum
    }

    // Create issuer DID (returns sealed result)
import org.trustweave.trust.types.DidCreationResult
import org.trustweave.credential.results.IssuanceResult
import org.trustweave.trust.types.VerificationResult
    
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
    
    // Resolve DID to get key ID
    val resolution = trustWeave.resolveDid(issuerDid)
    val issuerDoc = when (resolution) {
        is DidResolutionResult.Success -> resolution.document
        else -> throw IllegalStateException("Failed to resolve issuer DID")
    }
    val issuerKeyId = issuerDoc.verificationMethod.firstOrNull()?.id?.substringAfter("#")
        ?: throw IllegalStateException("No verification method found")

    // Issue credential using DSL
    val issuanceResult = trustWeave.issue {
        credential {
            type(CredentialTypes.PERSON)
            issuer(issuerDid.value)  // Using typed Did object's value
            subject {
                id("did:key:subject")
                "type" to "Person"
                "name" to "Alice"
                "email" to "alice@example.com"
            }
            issued(Instant.now())
        }
        signedBy(issuerDid = issuerDid.value, keyId = issuerKeyId)  // Using typed Did object's value
    }

    val credential = when (issuanceResult) {
        is IssuanceResult.Success -> {
            println("Issued credential: ${issuanceResult.credential.id}")
            println("Issuer: ${issuanceResult.credential.issuer}")
            println("Subject: ${issuanceResult.credential.credentialSubject}")
            println("Proof: ${issuanceResult.credential.proof}")
            issuanceResult.credential
        }
        else -> {
            println("Issuance failed: ${issuanceResult.allErrors.joinToString("; ")}")
            return@runBlocking
        }
    }
}
```

**Outcome:** Issues a verifiable credential with the issuer's DID and cryptographic signature.

### Issuing Credentials with Custom Options

```kotlin
import org.trustweave.trust.TrustWeave
import org.trustweave.trust.dsl.credential.DidMethods
import org.trustweave.trust.dsl.credential.KeyAlgorithms
import org.trustweave.trust.dsl.credential.CredentialTypes
import org.trustweave.trust.types.CredentialType
import org.trustweave.credential.*
import org.trustweave.testkit.services.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import java.time.Instant
import java.time.temporal.ChronoUnit

fun main() = runBlocking {
    val trustWeave = TrustWeave.build {
        factories(
            kmsFactory = TestkitKmsFactory(),  // Test-only factory for tutorials
            didMethodFactory = TestkitDidMethodFactory()  // Test-only factory for tutorials
        )
        keys { provider(IN_MEMORY); algorithm(KeyAlgorithms.ED25519) }
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
    
    val issuerResolution = trustWeave.resolveDid(issuerDid)
    val issuerDoc = when (issuerResolution) {
        is DidResolutionResult.Success -> issuerResolution.document
        else -> throw IllegalStateException("Failed to resolve issuer DID")
    }
    val issuerKeyId = issuerDoc.verificationMethod.firstOrNull()?.id?.substringAfter("#")
        ?: throw IllegalStateException("No verification method found")

    // Create credential subject using DSL (preferred)
    // Note: When using the DSL, you build the subject directly in the credential block
    subject {
        id("did:key:subject")
        "type" to "Person"
        "name" to "Alice"
    }
    
    // For standalone subject creation (outside DSL), use buildJsonObject:
    // val credentialSubject = buildJsonObject {
    //     put("id", "did:key:subject")
    //     put("type", "Person")
    //     put("name", "Alice")
    // }

    // Issue credential with custom expiration
    val issuanceResult = trustWeave.issue {
        credential {
            type(CredentialTypes.PERSON)
            issuer(issuerDid.value)
            subject {
                id("did:key:subject")
                "type" to "Person"
                "name" to "Alice"
                "email" to "alice@example.com"
            }
            issued(Instant.now())
            expires(Instant.now().plus(365, ChronoUnit.DAYS))
        }
        signedBy(issuerDid = issuerDid.value, keyId = issuerKeyId)
    }

    val credential = when (issuanceResult) {
        is IssuanceResult.Success -> {
            println("Issued credential with expiration: ${issuanceResult.credential.expirationDate}")
            if (issuanceResult.credential.credentialStatus != null) {
                println("Status: ${issuanceResult.credential.credentialStatus}")
            }
            issuanceResult.credential
        }
        else -> {
            println("Error: ${issuanceResult.allErrors.joinToString("; ")}")
            return@runBlocking
        }
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
import org.trustweave.credential.VerificationConfig
import org.trustweave.testkit.services.*
import kotlinx.coroutines.runBlocking

fun main() = runBlocking {
    val trustWeave = TrustWeave.build {
        factories(
            kmsFactory = TestkitKmsFactory(),  // Test-only factory for tutorials
            didMethodFactory = TestkitDidMethodFactory()  // Test-only factory for tutorials
        )
        keys { provider(IN_MEMORY); algorithm(KeyAlgorithms.ED25519) }
        did { method(DidMethods.KEY) { algorithm(KeyAlgorithms.ED25519) } }
    }

    val credential = /* previously issued credential */

    // Verify credential (verify returns VerificationResult, not a sealed result)
    val result = trustWeave.verify {
        credential(credential)
    }

    if (result.valid) {
        println("Credential is valid")
        println("Proof valid: ${result.proofValid}")
        println("Issuer valid: ${result.issuerValid}")
        println("Not expired: ${result.notExpired}")
        println("Not revoked: ${result.notRevoked}")
    } else {
        println("Credential is invalid")
        println("Errors: ${result.errors.joinToString()}")
    }
}
```

**Outcome:** Verifies a credential's signature and validity.

### Verifying with Custom Configuration

```kotlin
import org.trustweave.trust.TrustWeave
import org.trustweave.trust.dsl.credential.DidMethods
import org.trustweave.trust.dsl.credential.KeyAlgorithms
import org.trustweave.credential.VerificationConfig
import org.trustweave.testkit.services.*
import kotlinx.coroutines.runBlocking

fun main() = runBlocking {
    val trustWeave = TrustWeave.build {
        factories(
            kmsFactory = TestkitKmsFactory(),  // Test-only factory for tutorials
            didMethodFactory = TestkitDidMethodFactory()  // Test-only factory for tutorials
        )
        keys { provider(IN_MEMORY); algorithm(KeyAlgorithms.ED25519) }
        did { method(DidMethods.KEY) { algorithm(KeyAlgorithms.ED25519) } }
    }

    val credential = /* previously issued credential */

    // Verify credential with custom configuration
    val result = trustWeave.verify {
        credential(credential)
        checkExpiration()
        checkRevocation()
    }

    if (result.valid) {
        println("Credential verified successfully")
        println("All checks passed: proof=${result.proofValid}, issuer=${result.issuerValid}")
    } else {
        println("Verification failed: ${result.errors.joinToString()}")
    }
}
```

**Outcome:** Verifies a credential with custom verification configuration.

## Credential Lifecycle

### Credential Lifecycle Management

```kotlin
// Kotlin stdlib
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*

// TrustWeave core
import org.trustweave.trust.TrustWeave
import org.trustweave.trust.dsl.credential.DidMethods
import org.trustweave.trust.dsl.credential.KeyAlgorithms
import org.trustweave.credential.*
import org.trustweave.testkit.services.*

fun main() = runBlocking {
    val trustWeave = TrustWeave.build {
        factories(
            kmsFactory = TestkitKmsFactory(),  // Test-only factory for tutorials
            didMethodFactory = TestkitDidMethodFactory()  // Test-only factory for tutorials
        )
        keys { provider(IN_MEMORY); algorithm(KeyAlgorithms.ED25519) }
        did { method(DidMethods.KEY) { algorithm(KeyAlgorithms.ED25519) } }
        credentials { defaultProofType(ProofType.Ed25519Signature2020) }  // Using ProofType enum
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
    val issuerKeyId = issuerDoc.verificationMethod.firstOrNull()?.id?.substringAfter("#")
        ?: throw IllegalStateException("No verification method found")

    // Issue credential
    val issuanceResult = trustWeave.issue {
        credential {
            issuer(issuerDid.value)
            subject {
                id("did:key:subject")
            }
            issued(Instant.now())
        }
        signedBy(issuerDid = issuerDid.value, keyId = issuerKeyId)
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
        is VerificationResult.Invalid -> {
            val errorMsg = when (verificationResult) {
                is VerificationResult.Invalid.Expired -> "Expired at ${verificationResult.expiredAt}"
                is VerificationResult.Invalid.Revoked -> "Revoked"
                is VerificationResult.Invalid.InvalidProof -> "Invalid proof: ${verificationResult.reason}"
                is VerificationResult.Invalid.IssuerResolutionFailed -> "Issuer resolution failed: ${verificationResult.reason}"
                is VerificationResult.Invalid.UntrustedIssuer -> "Untrusted issuer: ${verificationResult.issuer.value}"
                is VerificationResult.Invalid.SchemaValidationFailed -> "Schema validation failed: ${verificationResult.errors.joinToString()}"
                is VerificationResult.Invalid.MultipleFailures -> "Multiple failures: ${verificationResult.errors.joinToString()}"
                is VerificationResult.Invalid.Other -> "Error: ${verificationResult.reason}"
            }
            println("Valid: false - $errorMsg")
        }
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
import kotlinx.serialization.json.*

// TrustWeave core
import org.trustweave.trust.TrustWeave
import org.trustweave.trust.dsl.credential.DidMethods
import org.trustweave.trust.dsl.credential.KeyAlgorithms
import org.trustweave.credential.*
import org.trustweave.testkit.services.*

fun main() = runBlocking {
    val trustWeave = TrustWeave.build {
        factories(
            kmsFactory = TestkitKmsFactory(),  // Test-only factory for tutorials
            didMethodFactory = TestkitDidMethodFactory()  // Test-only factory for tutorials
        )
        keys { provider(IN_MEMORY); algorithm(KeyAlgorithms.ED25519) }
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
    
    val issuerResolution = trustWeave.resolveDid(issuerDid)
    val issuerDoc = when (issuerResolution) {
        is DidResolutionResult.Success -> issuerResolution.document
        else -> throw IllegalStateException("Failed to resolve issuer DID")
    }
    val issuerKeyId = issuerDoc.verificationMethod.firstOrNull()?.id?.substringAfter("#")
        ?: throw IllegalStateException("No verification method found")

    // Issue credentials in batch using DSL
    val subjects = listOf(
        mapOf("id" to "did:key:alice", "name" to "Alice"),
        mapOf("id" to "did:key:bob", "name" to "Bob"),
        mapOf("id" to "did:key:charlie", "name" to "Charlie")
    )

    // Issue credentials in batch
    val credentials = subjects.mapNotNull { subjectData ->
        val issuanceResult = trustWeave.issue {
            credential {
                type("VerifiableCredential", "PersonCredential")
                issuer(issuerDid)
                subject {
                    id(subjectData["id"] as String)
                    subjectData.forEach { (key, value) ->
                        if (key != "id") {
                            key to value
                        }
                    }
                }
            }
            credential {
                type(CredentialTypes.PERSON)
                issuer(issuerDid.value)
                subject {
                    addClaims(subject)
                }
                issued(Instant.now())
            }
            signedBy(issuerDid = issuerDid.value, keyId = issuerKeyId)
        }
        
        when (issuanceResult) {
            is IssuanceResult.Success -> issuanceResult.credential
            else -> {
                println("Failed to issue credential for ${subject["id"]}: ${issuanceResult.reason}")
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
import org.trustweave.credential.*
import org.trustweave.testkit.services.*

fun main() = runBlocking {
    val trustWeave = TrustWeave.build {
        factories(
            kmsFactory = TestkitKmsFactory(),  // Test-only factory for tutorials
            didMethodFactory = TestkitDidMethodFactory()  // Test-only factory for tutorials
        )
        keys { provider(IN_MEMORY); algorithm(KeyAlgorithms.ED25519) }
        did { method(DidMethods.KEY) { algorithm(KeyAlgorithms.ED25519) } }
    }

    val credentials = /* list of credentials */
    val holderDidResult = trustWeave.createDid { method(DidMethods.KEY) }
    val holderDid = when (holderDidResult) {
        is DidCreationResult.Success -> holderDidResult.did
        else -> {
            println("Failed to create holder DID: ${holderDidResult.reason}")
            return@runBlocking
        }
    }
    
    val holderResolution = trustWeave.resolveDid(holderDid)
    val holderDoc = when (holderResolution) {
        is DidResolutionResult.Success -> holderResolution.document
        else -> throw IllegalStateException("Failed to resolve holder DID")
    }
    val holderKeyId = holderDoc.verificationMethod.firstOrNull()?.id?.substringAfter("#")
        ?: throw IllegalStateException("No verification method found")

    // Create presentation (presentation creation may still use exceptions or Result types)
    // Note: This depends on the actual presentation API implementation
    val presentation = trustweave.credentials.createPresentation(
        credentials = credentials,
        holderDid = holderDid.value,
        config = PresentationConfig(
            proofType = ProofType.Ed25519Signature2020,
            keyId = holderKeyId,
            holderDid = holderDid.value
        )
    )

    println("Created presentation: ${presentation.id}")
    println("Holder: ${presentation.holder}")
    println("Credentials: ${presentation.verifiableCredential.size}")

    // Verify presentation
    val verifyResult = trustweave.credentials.verifyPresentation(presentation)
    println("Presentation valid: ${verifyResult.valid}")
}
```

**Outcome:** Creates and verifies a credential presentation for selective disclosure.

## Error Handling

### Sealed Result Error Handling

TrustWeave uses sealed result types for all I/O operations, providing type-safe, exhaustive error handling:

```kotlin
// Kotlin stdlib
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*

// TrustWeave core
import org.trustweave.trust.TrustWeave
import org.trustweave.trust.dsl.credential.DidMethods
import org.trustweave.trust.dsl.credential.KeyAlgorithms
import org.trustweave.trust.types.DidCreationResult
import org.trustweave.credential.results.IssuanceResult
import org.trustweave.credential.*
import org.trustweave.testkit.services.*

fun main() = runBlocking {
    val trustWeave = TrustWeave.build {
        factories(
            kmsFactory = TestkitKmsFactory(),  // Test-only factory for tutorials
            didMethodFactory = TestkitDidMethodFactory()  // Test-only factory for tutorials
        )
        keys { provider(IN_MEMORY); algorithm(KeyAlgorithms.ED25519) }
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
            didResult.cause?.printStackTrace()
            return@runBlocking
        }
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
    
    val issuerResolution = trustWeave.resolveDid(issuerDid)
    val issuerDoc = when (issuerResolution) {
        is DidResolutionResult.Success -> issuerResolution.document
        else -> throw IllegalStateException("Failed to resolve issuer DID")
    }
    val issuerKeyId = issuerDoc.verificationMethod.firstOrNull()?.id?.substringAfter("#")
        ?: throw IllegalStateException("No verification method found")

    // Issue credential with error handling
    val issuanceResult = trustWeave.issue {
        credential {
            issuer(issuerDid.value)
            subject {
                id("did:key:subject")
            }
            issued(Instant.now())
        }
        signedBy(issuerDid = issuerDid.value, keyId = issuerKeyId)
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
            println("Multiple failures: ${issuanceResult.errors.joinToString("; ")}")
        }
        else -> {
            println("Error: ${issuanceResult.allErrors.joinToString("; ")}")
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

- [W3C Verifiable Credentials Specification](https://www.w3.org/TR/vc-data-model/)
- [TrustWeave Common Module](../modules/trustweave-common.md)
- [TrustWeave Core API](../api-reference/core-api.md)

