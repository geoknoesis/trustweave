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
import org.trustweave.trust.TrustWeave
import org.trustweave.did.resolver.DidResolutionResult
import org.trustweave.trust.types.getOrThrowDid
import org.trustweave.trust.types.getOrThrow
import org.trustweave.did.identifiers.extractKeyId
import org.trustweave.testkit.services.*
import kotlinx.coroutines.runBlocking

// Helper extension for resolution results
fun DidResolutionResult.getOrThrow() = when (this) {
    is DidResolutionResult.Success -> this.document
    else -> throw IllegalStateException("Failed to resolve DID: ${this.errorMessage ?: "Unknown error"}")
}

fun main() = runBlocking {
    // Create TrustWeave instance
    val trustWeave = TrustWeave.build {
        keys {
            provider(IN_MEMORY)  // Auto-discovered via SPI
            algorithm(ED25519)
        }
        did {
            method(KEY) {  // Auto-discovered via SPI
                algorithm(ED25519)
            }
        }
        // KMS, DID methods, and CredentialService all auto-created!
    }

    // Create issuer DID
    val issuerDid = trustWeave.createDid {
        method(KEY)
        algorithm(ED25519)
    }.getOrThrowDid()
    
    // Get key ID from DID document
    val issuerDocument = trustWeave.resolveDid(issuerDid).getOrThrow()
    val issuerKeyId = issuerDocument.verificationMethod.firstOrNull()?.extractKeyId()
        ?: throw IllegalStateException("No verification method found")

    // Issue credential
    val credential = trustWeave.issue {
        credential {
            type("VerifiableCredential", "PersonCredential")
            issuer(issuerDid)
            subject {
                id(org.trustweave.did.identifiers.Did("did:key:holder-placeholder"))
                "name" to "Alice Example"
                "role" to "Site Reliability Engineer"
            }
        }
        signedBy(issuerDid)
    }.getOrThrow()

    println("✅ Issued credential: ${credential.id}")
    println("   Issuer: ${credential.issuer}")
    println("   Subject: ${credential.credentialSubject}")
}
```

**Expected Output:**
```
✅ Issued credential: urn:uuid:...
   Issuer: did:key:z6Mk...
   Subject: {"id":"did:key:holder-placeholder","name":"Alice Example","role":"Site Reliability Engineer"}
```

## Issuance Workflow

The credential issuance process involves multiple components working together:

```mermaid
sequenceDiagram
    participant App as Application
    participant TW as TrustWeave
    participant DID as DID Service
    participant KMS as KMS Provider
    participant DR as DID Resolver
    participant Issuer as Issuer DID
    
    Note over App,Issuer: Phase 1: Setup
    App->>TW: TrustWeave.build { ... }
    TW->>DID: Register DID methods
    TW->>KMS: Register KMS provider
    
    Note over App,Issuer: Phase 2: Create Issuer DID
    App->>TW: createDid { method(KEY) }
    TW->>DID: Create DID
    DID->>KMS: Generate key pair
    KMS-->>DID: Key pair
    DID->>DR: Publish DID document
    DR-->>DID: DID document published
    DID-->>TW: DID + Document
    TW-->>App: Issuer DID
    
    Note over App,Issuer: Phase 3: Issue Credential
    App->>TW: issue { credential { ... } signedBy(issuerDid) }
    TW->>DR: Resolve issuer DID
    DR-->>TW: DID document
    TW->>TW: Build credential structure
    TW->>TW: Canonicalize credential
    TW->>TW: Compute digest
    TW->>KMS: Sign digest (using key from DID)
    KMS-->>TW: Signature
    TW->>TW: Create proof
    TW->>TW: Attach proof to credential
    TW-->>App: VerifiableCredential
    
    style App fill:#1976d2,stroke:#0d47a1,stroke-width:2px,color:#fff
    style TW fill:#388e3c,stroke:#1b5e20,stroke-width:2px,color:#fff
    style DID fill:#2196f3,stroke:#1565c0,stroke-width:2px,color:#fff
    style KMS fill:#ff9800,stroke:#e65100,stroke-width:2px,color:#fff
    style DR fill:#9c27b0,stroke:#6a1b9a,stroke-width:2px,color:#fff
    style Issuer fill:#4caf50,stroke:#2e7d32,stroke-width:2px,color:#fff
```

**Key Phases:**
1. **Setup**: Configure TrustWeave with DID methods and KMS provider
2. **Create Issuer DID**: Generate DID and key pair, publish DID document
3. **Issue Credential**: Build credential, canonicalize, sign, and attach proof

## Step-by-Step Guide

### Step 1: Create Issuer DID and Key

First, create a DID for the issuer and extract the key ID:

```kotlin
import org.trustweave.testkit.services.*

val trustWeave = TrustWeave.build {
    factories(
    // KMS and DID methods auto-discovered via SPI
    keys { provider(IN_MEMORY); algorithm(ED25519) }
    did { method(KEY) { algorithm(ED25519) } }
}

val issuerDid = trustWeave.createDid {
    method(KEY)
    algorithm(ED25519)
}.getOrFail()

// Key ID is automatically extracted during signing - no manual extraction needed
```

### Step 2: Build Credential Subject

Create the credential subject with claims using the DSL:

```kotlin
// Note: When using the DSL, you build the subject directly in the credential block
// This example shows the DSL syntax (preferred):
subject {
    id(org.trustweave.did.identifiers.Did("did:key:holder"))
    "name" to "Alice"
    "email" to "alice@example.com"
    "role" to "Engineer"
}

// For nested objects, use:
subject {
    id(org.trustweave.did.identifiers.Did("did:key:holder"))
    "address" {
        "street" to "123 Main St"
        "city" to "New York"
    }
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
            id(org.trustweave.did.identifiers.Did("did:key:holder"))
            "name" to "Alice"
            "email" to "alice@example.com"
        }
    }
    signedBy(issuerDid)
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
import kotlinx.datetime.Clock
import kotlin.time.Duration.Companion.days

val credential = trustWeave.issue {
    credential {
        type("VerifiableCredential", "TemporaryCredential")
        issuer(issuerDid)
        subject { id("did:key:holder"); "access" to "temporary" }
        expires(Clock.System.now().plus(30.days))
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
import org.trustweave.trust.types.IssuerIdentity

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
import org.trustweave.trust.types.IssuerIdentity

val issuerIdentity = IssuerIdentity.from(issuerDid, issuerKeyId)

val credential = try {
    trustWeave.issue {
        credential {
            type("VerifiableCredential", "PersonCredential")
            issuer(issuerDid)
            subject {
                id("did:key:holder")
                "name" to "Alice"
            }
        }
        signedBy(issuerDid)
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

