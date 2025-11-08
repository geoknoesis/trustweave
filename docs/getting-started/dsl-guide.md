# VeriCore DSL Guide

## Overview

The VeriCore DSL (Domain-Specific Language) provides a fluent, type-safe API for working with verifiable credentials, making it easier to configure trust layers, issue credentials, verify credentials, create presentations, and manage wallets.

## Key Benefits

- **Reduced Boilerplate**: ~60-70% less code for common operations
- **Better Readability**: Intent is clearer with fluent API
- **Type Safety**: Compile-time checks for credential structure
- **Centralized Configuration**: Single place to configure entire trust layer
- **Easier Onboarding**: More intuitive for new developers

## Trust Layer Configuration

The trust layer configuration is the foundation of the DSL. It centralizes the setup of cryptographic keys (KMS), Decentralized Identifiers (DIDs), and blockchain anchoring.

### Basic Configuration

```kotlin
import io.geoknoesis.vericore.credential.dsl.*

val trustLayer = trustLayer {
    keys {
        provider("inMemory")  // or "waltid", "hardware", etc.
        algorithm("Ed25519")
    }
    
    did {
        method("key") {
            algorithm("Ed25519")
        }
    }
    
    anchor {
        chain("algorand:testnet") {
            inMemory()  // For testing
        }
    }
    
    credentials {
        defaultProofType("Ed25519Signature2020")
        autoAnchor(false)
    }
}
```

### Multiple Trust Layers

You can create multiple trust layer configurations for different environments:

```kotlin
// Production trust layer
val productionLayer = trustLayer("production") {
    keys { provider("hardware") }
    did { method("web") { domain("company.com") } }
    anchor { chain("algorand:mainnet") { provider("algorand") } }
}

// Test trust layer
val testLayer = trustLayer("test") {
    keys { provider("inMemory") }
    did { method("key") }
    anchor { chain("algorand:testnet") { inMemory() } }
}
```

## Credential Creation DSL

Create verifiable credentials using a fluent builder:

```kotlin
val credential = credential {
    id("https://example.edu/credentials/123")
    type("DegreeCredential", "BachelorDegreeCredential")
    issuer("did:key:university")
    subject {
        id("did:key:student")
        "degree" {
            "type" to "BachelorDegree"
            "name" to "Bachelor of Science"
            "university" to "Example University"
        }
    }
    issued(Instant.now())
    expires(Instant.now().plus(10, ChronoUnit.YEARS))
    schema("https://example.edu/schemas/degree.json")
}
```

### Credential Builder Methods

- `id(String)`: Set credential ID
- `type(String...)`: Add credential types (first is primary type)
- `issuer(String)`: Set issuer DID
- `subject { }`: Build credential subject (nested JSON objects)
- `issued(Instant)`: Set issuance date
- `expires(Instant)` or `expires(Long, ChronoUnit)`: Set expiration
- `schema(String)`: Set credential schema
- `status(String)`: Set revocation status
- `evidence(JsonObject)`: Add evidence
- `termsOfUse(JsonObject)`: Add terms of use
- `refreshService(JsonObject)`: Add refresh service

## Issuance DSL

Issue credentials with automatic proof generation:

```kotlin
val issuedCredential = trustLayer.issue {
    credential {
        type("DegreeCredential")
        issuer("did:key:university")
        subject {
            id("did:key:student")
            "degree" {
                "type" to "BachelorDegree"
            }
        }
        issued(Instant.now())
    }
    by(issuerDid = "did:key:university", keyId = "key-1")
    withProof("Ed25519Signature2020")
    challenge("challenge-123")
    domain("example.com")
    anchor()  // Automatically anchor if autoAnchor is enabled
}
```

### Issuance Builder Methods

- `credential { }`: Build credential inline or use pre-built credential
- `by(issuerDid: String, keyId: String)`: Specify issuer and key
- `withProof(String)`: Set proof type (defaults to trust layer default)
- `challenge(String)`: Set proof challenge
- `domain(String)`: Set proof domain
- `anchor()`: Explicitly anchor credential

## Verification DSL

Verify credentials with configurable checks:

```kotlin
val result = trustLayer.verify {
    credential(issuedCredential)
    checkRevocation()
    checkExpiration()
    validateSchema("https://example.edu/schemas/degree.json")
    verifyAnchor()
}

if (result.valid) {
    println("Credential is valid!")
    println("Proof valid: ${result.proofValid}")
    println("Issuer valid: ${result.issuerValid}")
    println("Not expired: ${result.notExpired}")
    println("Not revoked: ${result.notRevoked}")
} else {
    result.errors.forEach { println("Error: $it") }
}
```

### Verification Builder Methods

- `credential(VerifiableCredential)`: Credential to verify (required)
- `checkRevocation()`: Enable revocation checking
- `skipRevocationCheck()`: Skip revocation checking (default)
- `checkExpiration()`: Enable expiration checking
- `skipExpirationCheck()`: Skip expiration checking (default)
- `validateSchema(String)`: Validate against schema
- `verifyAnchor()`: Verify blockchain anchor

## Wallet DSL

Create and configure wallets:

```kotlin
val wallet = trustLayer.wallet {
    id("my-wallet")
    holder("did:key:holder")
    enableOrganization()  // Enable collections, tags, metadata
    enablePresentation()  // Enable presentation creation
}
```

### Wallet Builder Methods

- `id(String)`: Set wallet ID (auto-generated if not provided)
- `holder(String)`: Set holder DID (required)
- `enableOrganization()`: Enable organization capabilities
- `enablePresentation()`: Enable presentation capabilities

### Wallet Operations

```kotlin
// Store credential
val credentialId = wallet.store(credential)

// Create collection
val collectionId = wallet.createCollection(
    name = "Education Credentials",
    description = "Academic degrees and certificates"
)

// Add to collection
wallet.addToCollection(credentialId, collectionId)

// Tag credential
wallet.tagCredential(credentialId, setOf("degree", "bachelor", "verified"))

// Query credentials
val degrees = wallet.query {
    byType("DegreeCredential")
    valid()
    notExpired()
}

// Get statistics
val stats = wallet.getStatistics()
println("Total credentials: ${stats.totalCredentials}")
```

## Presentation DSL

Create verifiable presentations:

```kotlin
val presentation = presentation {
    credentials(credential1, credential2, credential3)
    holder("did:key:holder")
    challenge("verification-challenge-123")
    domain("example.com")
    proofType("Ed25519Signature2020")
    selectiveDisclosure {
        reveal("degree.name", "degree.university")
        hide("degree.gpa")
    }
}
```

### Presentation Builder Methods

- `credentials(VerifiableCredential...)`: Add credentials (required)
- `credentials(List<VerifiableCredential>)`: Add credentials from list
- `holder(String)`: Set holder DID (required)
- `challenge(String)`: Set proof challenge
- `domain(String)`: Set proof domain
- `proofType(String)`: Set proof type
- `keyId(String)`: Set key ID for signing
- `selectiveDisclosure { }`: Configure selective disclosure

## Complete Example

Here's a complete example demonstrating the full DSL workflow:

```kotlin
import io.geoknoesis.vericore.credential.dsl.*
import kotlinx.coroutines.runBlocking
import java.time.Instant
import java.time.temporal.ChronoUnit

fun main() = runBlocking {
    // 1. Configure trust layer
    val trustLayer = trustLayer {
        keys {
            provider("inMemory")
            algorithm("Ed25519")
        }
        did {
            method("key") {}
        }
        anchor {
            chain("algorand:testnet") {
                inMemory()
            }
        }
        credentials {
            defaultProofType("Ed25519Signature2020")
        }
    }
    
    // 2. Create wallet
    val wallet = trustLayer.wallet {
        holder("did:key:student")
        enableOrganization()
    }
    
    // 3. Issue credential
    val issuedCredential = trustLayer.issue {
        credential {
            type("DegreeCredential")
            issuer("did:key:university")
            subject {
                id("did:key:student")
                "degree" {
                    "type" to "BachelorDegree"
                    "name" to "Bachelor of Science"
                }
            }
            issued(Instant.now())
        }
        by(issuerDid = "did:key:university", keyId = "key-1")
    }
    
    // 4. Store in wallet
    val credentialId = wallet.store(issuedCredential)
    
    // 5. Verify credential
    val result = trustLayer.verify {
        credential(issuedCredential)
        checkRevocation()
        checkExpiration()
    }
    
    if (result.valid) {
        println("Credential verified!")
    }
    
    // 6. Create presentation
    val presentation = presentation {
        credentials(issuedCredential)
        holder("did:key:student")
        challenge("job-application-123")
    }
}
```

## Migration from Manual API

### Before (Manual API)

```kotlin
val credential = VerifiableCredential(
    id = "https://example.edu/credentials/123",
    type = listOf("VerifiableCredential", "DegreeCredential"),
    issuer = "did:key:university",
    credentialSubject = buildJsonObject {
        put("id", "did:key:student")
        put("degree", buildJsonObject {
            put("type", "BachelorDegree")
            put("name", "Bachelor of Science")
        })
    },
    issuanceDate = Instant.now().toString()
)

val issuer = CredentialIssuer(proofGenerator, resolveDid)
val issuedCredential = issuer.issue(
    credential = credential,
    issuerDid = "did:key:university",
    keyId = "key-1",
    options = CredentialIssuanceOptions(proofType = "Ed25519Signature2020")
)
```

### After (DSL)

```kotlin
val issuedCredential = trustLayer.issue {
    credential {
        id("https://example.edu/credentials/123")
        type("DegreeCredential")
        issuer("did:key:university")
        subject {
            id("did:key:student")
            "degree" {
                "type" to "BachelorDegree"
                "name" to "Bachelor of Science"
            }
        }
        issued(Instant.now())
    }
    by(issuerDid = "did:key:university", keyId = "key-1")
}
```

## Best Practices

1. **Reuse Trust Layers**: Create trust layer configurations once and reuse them
2. **Named Trust Layers**: Use named trust layers for different environments
3. **Type Safety**: Leverage Kotlin's type system - the DSL provides compile-time checks
4. **Error Handling**: Always check verification results and handle errors appropriately
5. **Selective Disclosure**: Use selective disclosure to minimize data exposure in presentations

## See Also

- [Academic Credentials Example](../examples/academic/AcademicCredentialsDslExample.kt)
- [API Reference](../api-reference/wallet-api.md)
- [Core Concepts](../core-concepts/verifiable-credentials.md)

