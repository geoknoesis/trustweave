---
title: Issue Credentials
nav_order: 4
parent: How-To Guides
keywords:
  - issue
  - credential
  - verifiable credential
  - issuance
  - proof
  - signing
---

# Issue Credentials

This guide shows you how to issue verifiable credentials with TrustWeave. You'll learn how to create credentials, configure proof types, handle expiration, and manage the credential lifecycle.

## Quick Example

Here's a complete example that issues a credential:

```kotlin
import com.trustweave.trust.TrustWeave
import com.trustweave.did.resolver.DidResolutionResult
import com.trustweave.testkit.services.*
import kotlinx.coroutines.runBlocking

fun main() = runBlocking {
    // Create TrustWeave instance
    val trustWeave = TrustWeave.build {
        factories(
            kmsFactory = TestkitKmsFactory(),
            didMethodFactory = TestkitDidMethodFactory()
        )
        keys {
            provider("inMemory")
            algorithm("Ed25519")
        }
        did {
            method("key") {
                algorithm("Ed25519")
            }
        }
    }

    // Create issuer DID
    import com.trustweave.trust.types.DidCreationResult
    import com.trustweave.trust.types.IssuanceResult
    
    val didResult = trustWeave.createDid {
        method("key")
        algorithm("Ed25519")
    }
    
    val issuerDid = when (didResult) {
        is DidCreationResult.Success -> didResult.did
        else -> {
            println("❌ Failed to create DID: ${didResult.reason}")
            return@runBlocking
        }
    }
    
    // Get key ID from DID document
    val resolutionResult = trustWeave.resolveDid(issuerDid)
    val issuerDocument = when (resolutionResult) {
        is DidResolutionResult.Success -> resolutionResult.document
        else -> throw IllegalStateException("Failed to resolve issuer DID")
    }
    val verificationMethod = issuerDocument.verificationMethod.firstOrNull()
        ?: throw IllegalStateException("No verification method found")
    val issuerKeyId = verificationMethod.id.substringAfter("#")

    // Issue credential
    val issuanceResult = trustWeave.issue {
        credential {
            type("VerifiableCredential", "PersonCredential")
            issuer(issuerDid.value)
            subject {
                id("did:key:holder-placeholder")
                "name" to "Alice Example"
                "role" to "Site Reliability Engineer"
            }
        }
        signedBy(issuerDid = issuerDid.value, keyId = issuerKeyId)
    }

    val credential = when (issuanceResult) {
        is IssuanceResult.Success -> {
            println("✅ Issued credential: ${issuanceResult.credential.id}")
            println("   Issuer: ${issuanceResult.credential.issuer}")
            println("   Subject: ${issuanceResult.credential.credentialSubject}")
            issuanceResult.credential
        }
        else -> {
            println("❌ Failed to issue credential: ${issuanceResult.reason}")
            return@runBlocking
        }
    }
}
```

**Expected Output:**
```
✅ Issued credential: urn:uuid:...
   Issuer: did:key:z6Mk...
   Subject: {"id":"did:key:holder-placeholder","name":"Alice Example","role":"Site Reliability Engineer"}
```

## Step-by-Step Guide

### Step 1: Create Issuer DID and Key

First, create a DID for the issuer and extract the key ID:

```kotlin
import com.trustweave.testkit.services.*

val trustWeave = TrustWeave.build {
    factories(
        kmsFactory = TestkitKmsFactory(),
        didMethodFactory = TestkitDidMethodFactory()
    )
    keys { provider("inMemory"); algorithm("Ed25519") }
    did { method("key") { algorithm("Ed25519") } }
}

val issuerDid = trustWeave.createDid {
    method("key")
    algorithm("Ed25519")
}

// Get key ID from DID document
val resolutionResult = trustWeave.resolveDid(issuerDid)
val issuerDocument = when (resolutionResult) {
    is DidResolutionResult.Success -> resolutionResult.document
    else -> throw IllegalStateException("Failed to resolve issuer DID")
}
val verificationMethod = issuerDocument.verificationMethod.firstOrNull()
    ?: throw IllegalStateException("No verification method found")
val issuerKeyId = verificationMethod.id.substringAfter("#")
```

### Step 2: Build Credential Subject

Create the credential subject with claims:

```kotlin
val subject = buildJsonObject {
    put("id", "did:key:holder")
    put("name", "Alice")
    put("email", "alice@example.com")
    put("role", "Engineer")
}
```

### Step 3: Issue the Credential

Use the `issue` DSL to create and sign the credential:

```kotlin
val credential = trustWeave.issue {
    credential {
        type("VerifiableCredential", "PersonCredential")
        issuer(issuerDid)
        subject {
            id("did:key:holder")
            "name" to "Alice"
            "email" to "alice@example.com"
        }
    }
    signedBy(issuerDid = issuerDid, keyId = issuerKeyId)
}
```

### Step 4: Use the Credential

The credential is now ready to be stored, presented, or verified:

```kotlin
println("Credential ID: ${credential.id}")
println("Issuer: ${credential.issuer}")
println("Issuance Date: ${credential.issuanceDate}")
```

## Credential Types and Structures

### Basic Credential Structure

A verifiable credential contains:

- **`@context`**: JSON-LD context (automatically added)
- **`id`**: Unique credential identifier (auto-generated if not provided)
- **`type`**: Credential types (must include "VerifiableCredential")
- **`issuer`**: Issuer DID
- **`issuanceDate`**: When the credential was issued
- **`credentialSubject`**: The claims being made
- **`proof`**: Cryptographic proof (automatically generated)

### Credential Types

Specify credential types to categorize credentials:

```kotlin
credential {
    type("VerifiableCredential", "PersonCredential")
    // Or multiple types
    type("VerifiableCredential", "EmployeeCredential", "ProfessionalCredential")
}
```

### Credential Subject

The subject contains the actual claims:

```kotlin
subject {
    id("did:key:holder")  // Required: subject identifier
    "name" to "Alice"
    "email" to "alice@example.com"
    "age" to 30
    "role" to "Engineer"
}
```

## Advanced Configuration

### Expiration Dates

Set an expiration date for time-sensitive credentials:

```kotlin
import java.time.Instant
import java.time.temporal.ChronoUnit

val credential = trustWeave.issue {
    credential {
        type("VerifiableCredential", "TemporaryCredential")
        issuer(issuerDid)
        subject { id("did:key:holder"); "access" to "temporary" }
        expires(Instant.now().plus(30, ChronoUnit.DAYS))
    }
    signedBy(issuerDid = issuerDid, keyId = issuerKeyId)
}
```

### Custom Credential ID

Provide a custom credential ID:

```kotlin
credential {
    id("https://example.com/credentials/12345")
    type("VerifiableCredential", "CustomCredential")
    issuer(issuerDid)
    subject { id("did:key:holder") }
}
```

### Credential Schema

Reference a credential schema for validation:

```kotlin
credential {
    type("VerifiableCredential", "PersonCredential")
    issuer(issuerDid)
    schema("https://schema.org/Person")
    subject { id("did:key:holder"); "name" to "Alice" }
}
```

## Proof Types

TrustWeave supports multiple proof types. The default is `Ed25519Signature2020`:

- **Ed25519Signature2020**: Ed25519 signatures (recommended, default)
- **JsonWebSignature2020**: JWT-based proofs
- **BbsBlsSignature2020**: BBS+ signatures for selective disclosure

The proof type is automatically selected based on the key algorithm. For Ed25519 keys, `Ed25519Signature2020` is used.

## Common Patterns

### Pattern 1: Issue Multiple Credentials

Issue credentials for multiple subjects:

```kotlin
import com.trustweave.trust.types.IssuerIdentity

val subjects = listOf(
    mapOf("id" to "did:key:alice", "name" to "Alice", "role" to "Engineer"),
    mapOf("id" to "did:key:bob", "name" to "Bob", "role" to "Manager")
)

val issuerIdentity = IssuerIdentity.from(issuerDid, issuerKeyId)

val credentials = subjects.map { subjectData ->
    trustWeave.issue {
        credential {
            type("VerifiableCredential", "EmployeeCredential")
            issuer(issuerDid)
            subject {
                id(subjectData["id"] as String)
                subjectData.forEach { (key, value) ->
                    if (key != "id") key to value
                }
            }
        }
        signedBy(issuerIdentity)
    }
}
```

### Pattern 2: Issue with Error Handling

Handle errors gracefully:

```kotlin
import com.trustweave.trust.types.IssuerIdentity

val issuerIdentity = IssuerIdentity.from(issuerDid, issuerKeyId)

val credential = try {
    trustWeave.issue {
        credential {
            type("VerifiableCredential", "PersonCredential")
            issuer(issuerDid.value)
            subject {
                id("did:key:holder")
                "name" to "Alice"
            }
        }
        signedBy(issuerDid = issuerDid.value, keyId = issuerKeyId)
    }
} catch (error: Exception) {
    println("Issuance failed: ${error.message}")
    error.printStackTrace()
    return@runBlocking
}
```

### Pattern 3: Issue Credential with Status List

Issue credential with revocation status list:

```kotlin
// First create a status list (see blockchain-anchored-revocation guide)
val statusList = // ... create status list ...

val credential = trustWeave.issue {
    credential {
        type("VerifiableCredential", "RevocableCredential")
        issuer(issuerDid)
        subject { id("did:key:holder"); "name" to "Alice" }
        status {
            id(statusList.id)
            type("StatusList2021")
            statusPurpose("revocation")
            statusListIndex(0)
        }
    }
    signedBy(issuerDid = issuerDid, keyId = issuerKeyId)
}
```

## Error Handling

Credential issuance operations may throw exceptions on failure. Always wrap in try-catch:

```kotlin
try {
    val credential = trustWeave.issue { ... }
    // Use credential
} catch (error: Exception) {
    when (error) {
        is IllegalStateException -> {
            // Configuration or validation error
            println("Error: ${error.message}")
        }
        is IllegalArgumentException -> {
            // Invalid parameter
            println("Invalid parameter: ${error.message}")
        }
        else -> {
            println("Unexpected error: ${error.message}")
            error.printStackTrace()
        }
    }
}
```

## What TrustWeave Does Automatically

When you issue a credential, TrustWeave automatically:

1. **Canonicalizes** the JSON payload using JSON Canonicalization Scheme (JCS)
2. **Generates** a cryptographic digest
3. **Signs** the digest using the issuer's key
4. **Creates** the proof (Ed25519Signature2020 by default)
5. **Validates** the credential structure
6. **Returns** a complete `VerifiableCredential` object

## API Reference

For complete API documentation, see:
- **[Core API - issue()](../api-reference/core-api.md#issue)** - Complete parameter reference
- **[Credential Service API](../api-reference/credential-service-api.md)** - Lower-level SPI

## Related Concepts

- **[Verifiable Credentials](../core-concepts/verifiable-credentials.md)** - Understanding what credentials are
- **[DIDs](../core-concepts/dids.md)** - Understanding issuer identity
- **[Key Management](../core-concepts/key-management.md)** - How keys are used for signing

## Related How-To Guides

- **[Verify Credentials](verify-credentials.md)** - Verify issued credentials
- **[Manage Wallets](manage-wallets.md)** - Store credentials in wallets
- **[Create DIDs](create-dids.md)** - Create issuer DIDs

## Next Steps

**Ready to verify?**
- [Verify Credentials](verify-credentials.md) - Verify your issued credentials

**Want to store credentials?**
- [Manage Wallets](manage-wallets.md) - Store credentials securely

**Want to learn more?**
- [Verifiable Credentials Concept](../core-concepts/verifiable-credentials.md) - Deep dive into credentials
- [Credential Issuance Tutorial](../tutorials/credential-issuance-tutorial.md) - Comprehensive tutorial

