# Credential Issuance Tutorial

This tutorial provides a comprehensive guide to issuing verifiable credentials with VeriCore. You'll learn how to issue credentials, verify them, and handle the complete credential lifecycle.

```kotlin
dependencies {
    implementation("com.geoknoesis.vericore:vericore-core:1.0.0-SNAPSHOT")
    implementation("com.geoknoesis.vericore:vericore-did:1.0.0-SNAPSHOT")
    implementation("com.geoknoesis.vericore:vericore-kms:1.0.0-SNAPSHOT")
    implementation("com.geoknoesis.vericore:vericore-testkit:1.0.0-SNAPSHOT")
}
```

**Result:** Gives you the credential issuance APIs, DID methods, KMS abstractions, and in-memory implementations used throughout this tutorial.

> Tip: The runnable quick-start sample (`./gradlew :vericore-examples:runQuickStartSample`) mirrors the core flows below. Clone it as a starting point before wiring more advanced credential logic.

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

### Using VeriCore Facade (Recommended)

```kotlin
import com.geoknoesis.vericore.VeriCore
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*

fun main() = runBlocking {
    val vericore = VeriCore.create()
    
    // Create issuer DID
    val issuerDid = vericore.dids.create()
    val issuerDidResult = Result.success(issuerDid)
    val issuerDid = issuerDidResult.getOrThrow()
    
    // Create credential subject
    val credentialSubject = buildJsonObject {
        put("id", "did:key:subject")
        put("type", "Person")
        put("name", "Alice")
        put("email", "alice@example.com")
    }
    
    // Issue credential
    val credentialResult = vericore.issueCredential(
        issuerDid = issuerDid.id,
        issuerKeyId = issuerDid.document.verificationMethod.first().id,
        credentialSubject = credentialSubject
    )
    
    credentialResult.fold(
        onSuccess = { credential ->
            println("Issued credential: ${credential.id}")
            println("Issuer: ${credential.issuer}")
            println("Subject: ${credential.credentialSubject}")
            println("Proof: ${credential.proof}")
        },
        onFailure = { error ->
            println("Issuance failed: ${error.message}")
        }
    )
}
```

**Outcome:** Issues a verifiable credential with the issuer's DID and cryptographic signature.

### Issuing Credentials with Custom Options

```kotlin
import com.geoknoesis.vericore.VeriCore
import com.geoknoesis.vericore.credential.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import java.time.Instant
import java.time.temporal.ChronoUnit

fun main() = runBlocking {
    val vericore = VeriCore.create()
    val issuerDid = vericore.dids.create()
    
    // Create credential subject
    val credentialSubject = buildJsonObject {
        put("id", "did:key:subject")
        put("type", "Person")
        put("name", "Alice")
    }
    
    // Issue credential with custom expiration
    val credentialResult = vericore.issueCredential(
        issuerDid = issuerDid.id,
        issuerKeyId = issuerDid.document.verificationMethod.first().id,
        credentialSubject = credentialSubject
    ) {
        expirationDate = Instant.now().plus(365, ChronoUnit.DAYS)
        credentialStatus = CredentialStatus(
            id = "${issuerDid.id}#status",
            type = "RevocationList2020Status"
        )
    }
    
    credentialResult.fold(
        onSuccess = { credential ->
            println("Issued credential with expiration: ${credential.expirationDate}")
            println("Status: ${credential.credentialStatus}")
        },
        onFailure = { error ->
            println("Error: ${error.message}")
        }
    )
}
```

**Outcome:** Issues a credential with custom expiration and status information.

## Verifying Credentials

### Verifying Credentials

```kotlin
import com.geoknoesis.vericore.VeriCore
import kotlinx.coroutines.runBlocking

fun main() = runBlocking {
    val vericore = VeriCore.create()
    
    val credential = /* previously issued credential */
    
    // Verify credential
    val verificationResult = vericore.verifyCredential(credential)
    
    verificationResult.fold(
        onSuccess = { result ->
            if (result.valid) {
                println("Credential is valid")
                println("Issuer: ${result.issuerDid}")
                println("Subject: ${result.subjectDid}")
            } else {
                println("Credential is invalid: ${result.reason}")
            }
        },
        onFailure = { error ->
            println("Verification failed: ${error.message}")
        }
    )
}
```

**Outcome:** Verifies a credential's signature and validity.

### Verifying with Proof Purpose

```kotlin
import com.geoknoesis.vericore.VeriCore
import com.geoknoesis.vericore.credential.*
import kotlinx.coroutines.runBlocking

fun main() = runBlocking {
    val vericore = VeriCore.create()
    
    val credential = /* previously issued credential */
    
    // Verify credential with specific proof purpose
    val verificationResult = vericore.verifyCredential(
        credential = credential,
        proofPurpose = ProofPurpose.Authentication
    )
    
    verificationResult.fold(
        onSuccess = { result ->
            if (result.valid) {
                println("Credential verified with proof purpose: ${ProofPurpose.Authentication}")
            }
        },
        onFailure = { error ->
            println("Error: ${error.message}")
        }
    )
}
```

**Outcome:** Verifies a credential with a specific proof purpose requirement.

## Credential Lifecycle

### Credential Lifecycle Management

```kotlin
import com.geoknoesis.vericore.VeriCore
import com.geoknoesis.vericore.credential.*
import kotlinx.coroutines.runBlocking

fun main() = runBlocking {
    val vericore = VeriCore.create()
    val issuerDid = vericore.dids.create()
    
    // Issue credential
    val credential = vericore.issueCredential(
        issuerDid = issuerDid.id,
        issuerKeyId = issuerDid.document.verificationMethod.first().id,
        credentialSubject = buildJsonObject { put("id", "did:key:subject") }
    ).getOrThrow()
    
    println("Issued: ${credential.id}")
    
    // Verify credential
    val verificationResult = vericore.verifyCredential(credential).getOrThrow()
    println("Valid: ${verificationResult.valid}")
    
    // Revoke credential (if revocation list is supported)
    val revokeResult = vericore.revokeCredential(
        credentialId = credential.id,
        issuerDid = issuerDid.id
    )
    
    revokeResult.fold(
        onSuccess = { success ->
            if (success) {
                println("Credential revoked")
                
                // Verify revoked credential
                val revokeVerification = vericore.verifyCredential(credential).getOrThrow()
                println("Still valid: ${revokeVerification.valid}")
            }
        },
        onFailure = { error ->
            println("Revocation failed: ${error.message}")
        }
    )
}
```

**Outcome:** Demonstrates the complete credential lifecycle from issuance to revocation.

## Advanced Credential Operations

### Batch Credential Issuance

```kotlin
import com.geoknoesis.vericore.VeriCore
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*

fun main() = runBlocking {
    val vericore = VeriCore.create()
    val issuerDid = vericore.dids.create()
    val issuerKeyId = issuerDid.document.verificationMethod.first().id
    
    // Create multiple credential subjects
    val subjects = listOf(
        buildJsonObject { put("id", "did:key:alice"); put("name", "Alice") },
        buildJsonObject { put("id", "did:key:bob"); put("name", "Bob") },
        buildJsonObject { put("id", "did:key:charlie"); put("name", "Charlie") }
    )
    
    // Issue credentials in batch
    val credentials = subjects.map { subject ->
        vericore.issueCredential(
            issuerDid = issuerDid.id,
            issuerKeyId = issuerKeyId,
            credentialSubject = subject
        ).getOrThrow()
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
import com.geoknoesis.vericore.VeriCore
import com.geoknoesis.vericore.credential.*
import kotlinx.coroutines.runBlocking

fun main() = runBlocking {
    val vericore = VeriCore.create()
    
    val credentials = /* list of credentials */
    val holderDid = vericore.dids.create()
    
    // Create presentation
    val presentationResult = vericore.createPresentation(
        credentials = credentials,
        holderDid = holderDid.id
    )
    
    presentationResult.fold(
        onSuccess = { presentation ->
            println("Created presentation: ${presentation.id}")
            println("Holder: ${presentation.holder}")
            println("Credentials: ${presentation.verifiableCredential.size}")
            
            // Verify presentation
            val verifyResult = vericore.verifyPresentation(presentation).getOrThrow()
            println("Presentation valid: ${verifyResult.valid}")
        },
        onFailure = { error ->
            println("Presentation creation failed: ${error.message}")
        }
    )
}
```

**Outcome:** Creates and verifies a credential presentation for selective disclosure.

## Error Handling

### Structured Error Handling

```kotlin
import com.geoknoesis.vericore.VeriCore
import com.geoknoesis.vericore.core.VeriCoreError
import kotlinx.coroutines.runBlocking

fun main() = runBlocking {
    val vericore = VeriCore.create()
    val issuerDid = vericore.dids.create()
    
    val result = vericore.issueCredential(
        issuerDid = issuerDid.id,
        issuerKeyId = issuerDid.document.verificationMethod.first().id,
        credentialSubject = buildJsonObject { put("id", "did:key:subject") }
    )
    
    result.fold(
        onSuccess = { credential -> println("Issued: ${credential.id}") },
        onFailure = { error ->
            when (error) {
                is VeriCoreError.CredentialInvalid -> {
                    println("Invalid credential: ${error.reason}")
                }
                is VeriCoreError.CredentialIssuanceFailed -> {
                    println("Issuance failed: ${error.reason}")
                }
                is VeriCoreError.CredentialVerificationFailed -> {
                    println("Verification failed: ${error.reason}")
                }
                else -> println("Error: ${error.message}")
            }
        }
    )
}
```

**Outcome:** Demonstrates structured error handling for credential operations.

## Next Steps

- Review [Verifiable Credentials Concepts](../core-concepts/verifiable-credentials.md) for deeper understanding
- See [Wallet API Tutorial](wallet-api-tutorial.md) for credential storage
- Explore [Verification Policies](../advanced/verification-policies.md) for advanced verification
- Check [Creating Plugins](../contributing/creating-plugins.md) to implement custom credential services

## References

- [W3C Verifiable Credentials Specification](https://www.w3.org/TR/vc-data-model/)
- [VeriCore Core Module](../modules/vericore-core.md)
- [VeriCore Core API](../api-reference/core-api.md)

