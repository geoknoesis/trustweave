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
import com.trustweave.core.exception.TrustWeaveException
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

fun main() = runBlocking {
    try {
        // Create TrustWeave instance
        val trustWeave = TrustWeave.build {
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
        val issuerDid = trustLayer.createDid {
            method("key")
            algorithm("Ed25519")
        }
        val issuerKeyId = "$issuerDid#key-1"

        // Issue credential
        val credential = trustLayer.issue {
            credential {
                type("VerifiableCredential", "PersonCredential")
                issuer(issuerDid)
                subject {
                    id("did:key:holder-placeholder")
                    claim("name", "Alice Example")
                    claim("role", "Site Reliability Engineer")
                }
            }
            by(issuerDid = issuerDid, keyId = issuerKeyId)
        }

        println("✅ Issued credential: ${credential.id}")
        println("   Issuer: ${credential.issuer}")
        println("   Subject: ${credential.credentialSubject}")
    } catch (error: TrustWeaveError) {
        when (error) {
            is TrustWeaveError.CredentialInvalid -> {
                println("❌ Credential invalid: ${error.reason}")
            }
            is TrustWeaveError.InvalidDidFormat -> {
                println("❌ Invalid issuer DID: ${error.reason}")
            }
            else -> {
                println("❌ Error: ${error.message}")
            }
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
val trustLayer = TrustLayer.build {
    keys { provider("inMemory"); algorithm("Ed25519") }
    did { method("key") { algorithm("Ed25519") } }
}

val issuerDid = trustLayer.createDid {
    method("key")
    algorithm("Ed25519")
}
val issuerKeyId = "$issuerDid#key-1"
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
val credential = trustLayer.issue {
    credential {
        type("VerifiableCredential", "PersonCredential")
        issuer(issuerDid)
        subject {
            id("did:key:holder")
            claim("name", "Alice")
            claim("email", "alice@example.com")
        }
    }
    by(issuerDid = issuerDid, keyId = issuerKeyId)
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
    claim("name", "Alice")
    claim("email", "alice@example.com")
    claim("age", 30)
    claim("role", "Engineer")
}
```

## Advanced Configuration

### Expiration Dates

Set an expiration date for time-sensitive credentials:

```kotlin
import java.time.Instant
import java.time.temporal.ChronoUnit

val credential = trustLayer.issue {
    credential {
        type("VerifiableCredential", "TemporaryCredential")
        issuer(issuerDid)
        subject { id("did:key:holder"); claim("access", "temporary") }
        expires(Instant.now().plus(30, ChronoUnit.DAYS))
    }
    by(issuerDid = issuerDid, keyId = issuerKeyId)
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
    subject { id("did:key:holder"); claim("name", "Alice") }
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
                    if (key != "id") claim(key, value)
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
            issuer(issuerDid)
            subject {
                id("did:key:holder")
                claim("name", "Alice")
            }
        }
        signedBy(issuerIdentity)
    }
} catch (error: TrustWeaveError) {
    when (error) {
        is TrustWeaveError.CredentialInvalid -> {
            println("Credential validation failed: ${error.reason}")
            if (error.field != null) {
                println("Field: ${error.field}")
            }
            return@runBlocking
        }
        is TrustWeaveError.InvalidDidFormat -> {
            println("Invalid issuer DID: ${error.reason}")
            return@runBlocking
        }
        else -> {
            println("Issuance failed: ${error.message}")
            return@runBlocking
        }
    }
}
```

### Pattern 3: Issue Credential with Status List

Issue credential with revocation status list:

```kotlin
// First create a status list (see blockchain-anchored-revocation guide)
val statusList = // ... create status list ...

val credential = trustLayer.issue {
    credential {
        type("VerifiableCredential", "RevocableCredential")
        issuer(issuerDid)
        subject { id("did:key:holder"); claim("name", "Alice") }
        status {
            id(statusList.id)
            type("StatusList2021")
            statusPurpose("revocation")
            statusListIndex(0)
        }
    }
    by(issuerDid = issuerDid, keyId = issuerKeyId)
}
```

## Error Handling

All credential issuance operations throw `TrustWeaveError` exceptions on failure:

```kotlin
try {
    val credential = trustLayer.issue { ... }
    // Use credential
} catch (error: TrustWeaveError) {
    when (error) {
        is TrustWeaveError.CredentialInvalid -> {
            // Credential validation failed
            println("Invalid: ${error.reason}")
            if (error.field != null) {
                println("Field: ${error.field}")
            }
        }
        is TrustWeaveError.InvalidDidFormat -> {
            // Invalid issuer DID format
            println("Invalid DID: ${error.reason}")
        }
        is TrustWeaveError.DidMethodNotRegistered -> {
            // Issuer DID method not registered
            println("Method not registered: ${error.method}")
        }
        is TrustWeaveError.DidNotFound -> {
            // Issuer DID cannot be resolved
            println("DID not found: ${error.did}")
        }
        else -> {
            println("Error: ${error.message}")
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

