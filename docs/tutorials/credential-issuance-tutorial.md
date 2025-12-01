---
title: Credential Issuance Tutorial
nav_exclude: true
---

# Credential Issuance Tutorial

This tutorial provides a comprehensive guide to issuing verifiable credentials with TrustWeave. You'll learn how to issue credentials, verify them, and handle the complete credential lifecycle.

```kotlin
dependencies {
    implementation("com.trustweave:trustweave-common:1.0.0-SNAPSHOT")
    implementation("com.trustweave:trustweave-did:1.0.0-SNAPSHOT")
    implementation("com.trustweave:trustweave-kms:1.0.0-SNAPSHOT")
    implementation("com.trustweave:trustweave-testkit:1.0.0-SNAPSHOT")
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

**Outcome:** Enables secure, verifiable credential issuance with cryptographic proof.

## Issuing Credentials

### Using TrustWeave Service API (Recommended)

```kotlin
// Kotlin stdlib
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import java.time.Instant

// TrustWeave core
import com.trustweave.trust.TrustWeave
import com.trustweave.trust.dsl.credential.DidMethods
import com.trustweave.trust.dsl.credential.KeyAlgorithms
import com.trustweave.trust.dsl.credential.ProofTypes
import com.trustweave.trust.types.ProofType
import com.trustweave.testkit.services.*

fun main() = runBlocking {
    val trustWeave = TrustWeave.build {
        factories(
            kmsFactory = TestkitKmsFactory(),  // Test-only factory for tutorials
            didMethodFactory = TestkitDidMethodFactory()  // Test-only factory for tutorials
        )
        keys { provider("inMemory"); algorithm(KeyAlgorithms.ED25519) }
        did { method(DidMethods.KEY) { algorithm(KeyAlgorithms.ED25519) } }
        credentials { defaultProofType(ProofType.Ed25519Signature2020) }  // Using ProofType enum
    }

    // Create issuer DID (returns type-safe Did object)
    val issuerDid = trustWeave.createDid {
        method(DidMethods.KEY)
        algorithm(KeyAlgorithms.ED25519)
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
    try {
        val credential = trustWeave.issue {
            credential {
                type(CredentialTypes.PERSON)
                issuer(issuerDid.value)  // Using typed Did object's value
                subject {
                    id("did:key:subject")
                    claim("type", "Person")
                    claim("name", "Alice")
                    claim("email", "alice@example.com")
                }
                issued(Instant.now())
            }
            signedBy(issuerDid = issuerDid.value, keyId = issuerKeyId)  // Using typed Did object's value
        }

        println("Issued credential: ${credential.id}")
        println("Issuer: ${credential.issuer}")
        println("Subject: ${credential.credentialSubject}")
        println("Proof: ${credential.proof}")
    } catch (error: TrustWeaveError) {
        println("Issuance failed: ${error.message}")
    }
}
```

**Outcome:** Issues a verifiable credential with the issuer's DID and cryptographic signature.

### Issuing Credentials with Custom Options

```kotlin
import com.trustweave.trust.TrustWeave
import com.trustweave.trust.dsl.credential.DidMethods
import com.trustweave.trust.dsl.credential.KeyAlgorithms
import com.trustweave.credential.*
import com.trustweave.testkit.services.*
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
        keys { provider("inMemory"); algorithm(KeyAlgorithms.ED25519) }
        did { method(DidMethods.KEY) { algorithm(KeyAlgorithms.ED25519) } }
    }
    val issuerDid = trustWeave.createDid { method(DidMethods.KEY) }
    val issuerResolution = trustWeave.resolveDid(issuerDid)
    val issuerDoc = when (issuerResolution) {
        is DidResolutionResult.Success -> issuerResolution.document
        else -> throw IllegalStateException("Failed to resolve issuer DID")
    }
    val issuerKeyId = issuerDoc.verificationMethod.firstOrNull()?.id?.substringAfter("#")
        ?: throw IllegalStateException("No verification method found")

    // Create credential subject
    val credentialSubject = buildJsonObject {
        put("id", "did:key:subject")
        put("type", "Person")
        put("name", "Alice")
    }

    // Issue credential with custom expiration
    try {
        val credential = trustWeave.issue {
            credential {
                type(CredentialTypes.PERSON)
                issuer(issuerDid.value)
                subject {
                    id("did:key:subject")
                    claim("type", "Person")
                    claim("name", "Alice")
                    claim("email", "alice@example.com")
                }
                issued(Instant.now())
                expires(Instant.now().plus(365, ChronoUnit.DAYS))
            }
            signedBy(issuerDid = issuerDid.value, keyId = issuerKeyId)
        }

        println("Issued credential with expiration: ${credential.expirationDate}")
        if (credential.credentialStatus != null) {
            println("Status: ${credential.credentialStatus}")
        }
    } catch (error: TrustWeaveError) {
        println("Error: ${error.message}")
    }
}
```

**Outcome:** Issues a credential with custom expiration and status information.

## Verifying Credentials

### Verifying Credentials

```kotlin
import com.trustweave.trust.TrustWeave
import com.trustweave.trust.dsl.credential.DidMethods
import com.trustweave.trust.dsl.credential.KeyAlgorithms
import com.trustweave.credential.VerificationConfig
import com.trustweave.testkit.services.*
import kotlinx.coroutines.runBlocking

fun main() = runBlocking {
    val trustWeave = TrustWeave.build {
        factories(
            kmsFactory = TestkitKmsFactory(),  // Test-only factory for tutorials
            didMethodFactory = TestkitDidMethodFactory()  // Test-only factory for tutorials
        )
        keys { provider("inMemory"); algorithm(KeyAlgorithms.ED25519) }
        did { method(DidMethods.KEY) { algorithm(KeyAlgorithms.ED25519) } }
    }

    val credential = /* previously issued credential */

    // Verify credential
    try {
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
    } catch (error: TrustWeaveError) {
        println("Verification failed: ${error.message}")
    }
}
```

**Outcome:** Verifies a credential's signature and validity.

### Verifying with Custom Configuration

```kotlin
import com.trustweave.trust.TrustWeave
import com.trustweave.trust.dsl.credential.DidMethods
import com.trustweave.trust.dsl.credential.KeyAlgorithms
import com.trustweave.credential.VerificationConfig
import com.trustweave.testkit.services.*
import kotlinx.coroutines.runBlocking

fun main() = runBlocking {
    val trustWeave = TrustWeave.build {
        factories(
            kmsFactory = TestkitKmsFactory(),  // Test-only factory for tutorials
            didMethodFactory = TestkitDidMethodFactory()  // Test-only factory for tutorials
        )
        keys { provider("inMemory"); algorithm(KeyAlgorithms.ED25519) }
        did { method(DidMethods.KEY) { algorithm(KeyAlgorithms.ED25519) } }
    }

    val credential = /* previously issued credential */

    // Verify credential with custom configuration
    try {
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
    } catch (error: TrustWeaveError) {
        println("Error: ${error.message}")
    }
}
```

**Outcome:** Verifies a credential with custom verification configuration.

## Credential Lifecycle

### Credential Lifecycle Management

```kotlin
import com.trustweave.TrustWeave
import com.trustweave.credential.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*

fun main() = runBlocking {
    val trustWeave = TrustWeave.build {
        factories(
            kmsFactory = TestkitKmsFactory(),  // Test-only factory for tutorials
            didMethodFactory = TestkitDidMethodFactory()  // Test-only factory for tutorials
        )
        keys { provider("inMemory"); algorithm(KeyAlgorithms.ED25519) }
        did { method(DidMethods.KEY) { algorithm(KeyAlgorithms.ED25519) } }
        credentials { defaultProofType(ProofType.Ed25519Signature2020) }  // Using ProofType enum
    }
    
    val issuerDid = trustWeave.createDid {
        method(DidMethods.KEY)
        algorithm(KeyAlgorithms.ED25519)
    }
    
    val resolution = trustWeave.resolveDid(issuerDid)
    val issuerDoc = when (resolution) {
        is DidResolutionResult.Success -> resolution.document
        else -> throw IllegalStateException("Failed to resolve issuer DID")
    }
    val issuerKeyId = issuerDoc.verificationMethod.firstOrNull()?.id?.substringAfter("#")
        ?: throw IllegalStateException("No verification method found")

    // Issue credential
    try {
        val credential = trustWeave.issue {
            credential {
                issuer(issuerDid.value)
                subject {
                    id("did:key:subject")
                }
                issued(Instant.now())
            }
            signedBy(issuerDid = issuerDid.value, keyId = issuerKeyId)
        )

        println("Issued: ${credential.id}")

        // Verify credential
        val verificationResult = trustWeave.verify {
            credential(credential)
        }
        when (verificationResult) {
            is VerificationResult.Valid -> println("Valid: true")
            is VerificationResult.Invalid -> println("Valid: false - ${verificationResult.reason}")
        }

        // Note: Revocation is typically handled through credential status lists
        // and checked during verification. See revocation documentation for details.

    } catch (error: TrustWeaveError) {
        println("Error: ${error.message}")
    }
}
```

**Outcome:** Demonstrates the complete credential lifecycle from issuance to verification.

## Advanced Credential Operations

### Batch Credential Issuance

```kotlin
import com.trustweave.TrustWeave
import com.trustweave.credential.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*

fun main() = runBlocking {
    val trustWeave = TrustWeave.build {
        factories(
            kmsFactory = TestkitKmsFactory(),  // Test-only factory for tutorials
            didMethodFactory = TestkitDidMethodFactory()  // Test-only factory for tutorials
        )
        keys { provider("inMemory"); algorithm(KeyAlgorithms.ED25519) }
        did { method(DidMethods.KEY) { algorithm(KeyAlgorithms.ED25519) } }
    }
    val issuerDid = trustWeave.createDid { method(DidMethods.KEY) }
    val issuerResolution = trustWeave.resolveDid(issuerDid)
    val issuerDoc = when (issuerResolution) {
        is DidResolutionResult.Success -> issuerResolution.document
        else -> throw IllegalStateException("Failed to resolve issuer DID")
    }
    val issuerKeyId = issuerDoc.verificationMethod.firstOrNull()?.id?.substringAfter("#")
        ?: throw IllegalStateException("No verification method found")

    // Create multiple credential subjects
    val subjects = listOf(
        buildJsonObject { put("id", "did:key:alice"); put("name", "Alice") },
        buildJsonObject { put("id", "did:key:bob"); put("name", "Bob") },
        buildJsonObject { put("id", "did:key:charlie"); put("name", "Charlie") }
    )

    // Issue credentials in batch
    val credentials = subjects.mapNotNull { subject ->
        try {
            trustWeave.issue {
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
        } catch (error: TrustWeaveError) {
            println("Failed to issue credential for ${subject["id"]}: ${error.message}")
            null
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
import com.trustweave.TrustWeave
import com.trustweave.credential.*
import kotlinx.coroutines.runBlocking

fun main() = runBlocking {
    val trustWeave = TrustWeave.build {
        factories(
            kmsFactory = TestkitKmsFactory(),  // Test-only factory for tutorials
            didMethodFactory = TestkitDidMethodFactory()  // Test-only factory for tutorials
        )
        keys { provider("inMemory"); algorithm(KeyAlgorithms.ED25519) }
        did { method(DidMethods.KEY) { algorithm(KeyAlgorithms.ED25519) } }
    }

    val credentials = /* list of credentials */
    val holderDid = trustWeave.createDid { method(DidMethods.KEY) }
    val holderResolution = trustWeave.resolveDid(holderDid)
    val holderDoc = when (holderResolution) {
        is DidResolutionResult.Success -> holderResolution.document
        else -> throw IllegalStateException("Failed to resolve holder DID")
    }
    val holderKeyId = holderDoc.verificationMethod.firstOrNull()?.id?.substringAfter("#")
        ?: throw IllegalStateException("No verification method found")

    // Create presentation
    try {
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

    } catch (error: TrustWeaveError) {
        println("Presentation creation failed: ${error.message}")
    }
}
```

**Outcome:** Creates and verifies a credential presentation for selective disclosure.

## Error Handling

### Structured Error Handling

```kotlin
import com.trustweave.TrustWeave
import com.trustweave.core.exception.TrustWeaveError
import com.trustweave.credential.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*

fun main() = runBlocking {
    val trustWeave = TrustWeave.build {
        factories(
            kmsFactory = TestkitKmsFactory(),  // Test-only factory for tutorials
            didMethodFactory = TestkitDidMethodFactory()  // Test-only factory for tutorials
        )
        keys { provider("inMemory"); algorithm(KeyAlgorithms.ED25519) }
        did { method(DidMethods.KEY) { algorithm(KeyAlgorithms.ED25519) } }
    }
    val issuerDid = trustWeave.createDid { method(DidMethods.KEY) }
    val issuerResolution = trustWeave.resolveDid(issuerDid)
    val issuerDoc = when (issuerResolution) {
        is DidResolutionResult.Success -> issuerResolution.document
        else -> throw IllegalStateException("Failed to resolve issuer DID")
    }
    val issuerKeyId = issuerDoc.verificationMethod.firstOrNull()?.id?.substringAfter("#")
        ?: throw IllegalStateException("No verification method found")

    try {
        val credential = trustWeave.issue {
            credential {
                issuer(issuerDid.value)
                subject {
                    id("did:key:subject")
                }
                issued(Instant.now())
            }
            signedBy(issuerDid = issuerDid.value, keyId = issuerKeyId)
        }

        println("Issued: ${credential.id}")

    } catch (error: TrustWeaveError) {
        when (error) {
            is TrustWeaveError.CredentialInvalid -> {
                println("Invalid credential: ${error.reason}")
                if (error.field != null) {
                    println("Field: ${error.field}")
                }
            }
            is TrustWeaveError.InvalidDidFormat -> {
                println("Invalid DID format: ${error.reason}")
            }
            is TrustWeaveError.DidMethodNotRegistered -> {
                println("DID method not registered: ${error.method}")
                println("Available methods: ${error.availableMethods}")
            }
            else -> println("Error: ${error.message}")
        }
    }
}
```

**Outcome:** Demonstrates structured error handling for credential operations using try-catch.

## Next Steps

- Review [Verifiable Credentials Concepts](../core-concepts/verifiable-credentials.md) for deeper understanding
- See [Wallet API Tutorial](wallet-api-tutorial.md) for credential storage
- Explore [Verification Policies](../advanced/verification-policies.md) for advanced verification
- Check [Creating Plugins](../contributing/creating-plugins.md) to implement custom credential services

## References

- [W3C Verifiable Credentials Specification](https://www.w3.org/TR/vc-data-model/)
- [TrustWeave Common Module](../modules/trustweave-common.md)
- [TrustWeave Core API](../api-reference/core-api.md)

