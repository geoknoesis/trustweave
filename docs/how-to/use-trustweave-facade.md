---
title: Use TrustWeave Facade for Quick Setup
nav_order: 10
parent: How-To Guides
keywords:
  - facade
  - quick start
  - simple api
  - defaults
  - rapid prototyping
---

# Use TrustWeave Facade for Quick Setup

This guide shows you how to use TrustWeave's simple facade API for rapid prototyping and production applications. The facade provides sensible defaults and reduces setup code by 95%.

## Prerequisites

Before you begin, ensure you have:

- TrustWeave dependencies added to your project
- Basic understanding of DIDs and verifiable credentials
- Kotlin coroutines knowledge

## Expected Outcome

After completing this guide, you will have:

- Created a TrustWeave instance with one line
- Issued your first credential with minimal code
- Understood when to use the facade vs. full configuration
- Learned how to customize facade defaults

## Quick Example

Here's a complete example showing the simplicity of the facade API:

```kotlin
import org.trustweave.trust.TrustWeave
import org.trustweave.trust.quickStart
import org.trustweave.trust.types.getOrThrowDid
import org.trustweave.trust.types.getOrThrow
import org.trustweave.credential.results.getOrThrow
import kotlinx.coroutines.runBlocking

fun main() = runBlocking {
    // Same defaults as TrustWeave.inMemory(); use build { } when you need custom KMS/DID/anchors
    val trustWeave = TrustWeave.quickStart()

    val issuerDid = trustWeave.createDid().getOrThrowDid()

    val credential = trustWeave.issue {
        credential {
            type("EmployeeCredential")
            issuer(issuerDid)
            subject {
                id("did:key:holder")
                "name" to "Alice"
                "role" to "Engineer"
            }
        }
        signedBy(issuerDid)
    }.getOrThrow()

    println("✅ Created DID: ${issuerDid.value}")
    println("✅ Issued credential: ${credential.id}")
}
```

**Expected Output:**
```
✅ Created DID: did:key:z6MkhaXgBZDvotDkL5257faiztiGiC2QtKLGpbnnEGta2doK
✅ Issued credential: urn:uuid:12345678-1234-1234-1234-123456789abc
```

## Step-by-Step Guide

### Step 1: Create TrustWeave Instance

Configure TrustWeave with minimal setup:

```kotlin
import org.trustweave.trust.TrustWeave
import org.trustweave.trust.quickStart
import kotlinx.coroutines.runBlocking

val tw = runBlocking { TrustWeave.quickStart() }
```

**What this does:**
- Configures in-memory key management (Ed25519) and `did:key`
- Sets up default proof types and services (same as `TrustWeave.inMemory()`)

Use `TrustWeave.build { ... }` when you need non-default providers, anchors, or trust registry wiring.

**Expected Result:** A fully functional TrustWeave instance ready to use.

---

### Step 2: Create a DID

Create a DID with automatic defaults:

```kotlin
import org.trustweave.trust.types.getOrThrow

val (issuerDid, issuerDoc) = tw.createDid().getOrThrow()  // Uses default from config
```

**What this does:**
- Uses `did:key` method
- Generates Ed25519 key pair
- Creates verification method
- Returns `Did` + `DidDocument` (no separate resolution needed!)

**Expected Result:** A `Did` object with value like `did:key:z6Mk...` and its document.

---

### Step 3: Issue a Credential

Issue a credential using the DSL:

```kotlin
import org.trustweave.did.identifiers.extractKeyId

val issuerKeyId = issuerDoc.verificationMethod.firstOrNull()?.extractKeyId()
    ?: error("No verification method on issuer DID document")
val credential = tw.issue {
    credential {
        type("EmployeeCredential")
        issuer(issuerDid)
        subject {
            id("did:key:holder")
            "name" to "Alice"
            "role" to "Engineer"
        }
    }
    signedBy(issuerDid, issuerKeyId)
}.getOrThrow()
```

**What this does:**
- Creates credential structure via DSL
- Generates proof automatically
- Signs with issuer's key
- Returns signed credential

**Expected Result:** A verifiable credential with proof.

---

### Step 4: Verify the Credential

Verify the credential you just issued:

```kotlin
import org.trustweave.credential.results.getOrThrow

tw.verify { credential(credential) }.getOrThrow()
println("✅ Credential is valid")
```

**Expected Result:** Verification passes or throws with error details.

---

## Comparison: Minimal vs. Full Configuration

### Minimal Setup

```kotlin
import org.trustweave.trust.TrustWeave
import org.trustweave.trust.dsl.credential.DidMethods.KEY
import org.trustweave.trust.dsl.credential.KeyAlgorithms.ED25519
import org.trustweave.trust.dsl.credential.KmsProviders.IN_MEMORY
import org.trustweave.trust.types.getOrThrow
import org.trustweave.credential.results.getOrThrow
import org.trustweave.did.identifiers.extractKeyId

val trustWeave = TrustWeave.build {
    keys { provider(IN_MEMORY); algorithm(ED25519) }
    did { method(KEY) { algorithm(ED25519) } }
}

val (did, doc) = trustWeave.createDid().getOrThrow()  // Uses default
val keyId = doc.verificationMethod.firstOrNull()?.extractKeyId()
    ?: error("No verification method on DID document")
val cred = trustWeave.issue {
    credential { type("MyCredential"); issuer(did); subject { "claim" to "value" } }
    signedBy(did, keyId)
}.getOrThrow()
```

**Best for:** Prototyping, examples, learning, simple use cases.

### Full Configuration

```kotlin
import org.trustweave.trust.dsl.credential.DidMethods.KEY
import org.trustweave.trust.dsl.credential.DidMethods.WEB
import org.trustweave.trust.dsl.credential.KeyAlgorithms.ED25519
import org.trustweave.trust.dsl.credential.KmsProviders.IN_MEMORY
import org.trustweave.trust.dsl.credential.AnchorProviders.ALGORAND
import org.trustweave.did.identifiers.extractKeyId
val trustWeave = TrustWeave.build {
    keys { provider(IN_MEMORY); algorithm(ED25519) }
    did { method(KEY) { algorithm(ED25519) }; method(WEB) { domain("example.com") } }
    anchor { chain("algorand:testnet") { provider(ALGORAND) } }
}

val (did, doc) = trustWeave.createDid().getOrThrow()  // Uses default "key"
val keyId = doc.verificationMethod.firstOrNull()?.extractKeyId()
    ?: error("No verification method on DID document")
val cred = trustWeave.issue {
    credential { type("MyCredential"); issuer(did); subject { "claim" to "value" } }
    signedBy(did, keyId)
}.getOrThrow()

// Anchor to blockchain (returns AnchorResult; throws if chain not registered)
val anchored = trustWeave.blockchains.anchor(cred, VerifiableCredential.serializer(), "algorand:testnet")
println("Anchored at ${anchored.ref.txHash}")
```

**Best for:** Production, multiple DID methods, blockchain anchoring, advanced features.

---

## Customizing Defaults

### Adding DID Methods and Blockchain Anchoring

```kotlin
import org.trustweave.trust.dsl.credential.DidMethods.KEY
import org.trustweave.trust.dsl.credential.DidMethods.WEB
import org.trustweave.trust.dsl.credential.KeyAlgorithms.ED25519
import org.trustweave.trust.dsl.credential.KmsProviders.IN_MEMORY
import org.trustweave.trust.dsl.credential.AnchorProviders.ALGORAND
val trustWeave = TrustWeave.build {
    keys { provider(IN_MEMORY); algorithm(ED25519) }
    did {
        method(KEY) { algorithm(ED25519) }
        method(WEB) { domain("example.com") }
    }
    anchor { chain("algorand:testnet") { provider(ALGORAND) } }
}
```

**What this does:**
- Registers multiple DID methods (`did:key`, `did:web`)
- Configures blockchain anchoring
- All with minimal, readable DSL syntax

---

## Common Patterns

### Pattern 1: Quick Prototype

For rapid prototyping and testing:

```kotlin
import org.trustweave.trust.TrustWeave
import org.trustweave.trust.dsl.credential.DidMethods.KEY
import org.trustweave.trust.dsl.credential.KeyAlgorithms.ED25519
import org.trustweave.trust.dsl.credential.KmsProviders.IN_MEMORY
import org.trustweave.trust.types.getOrThrow
import org.trustweave.credential.results.getOrThrow
import org.trustweave.did.identifiers.extractKeyId
import kotlinx.coroutines.runBlocking

fun main() = runBlocking {
    val trustWeave = TrustWeave.build {
        keys { provider(IN_MEMORY); algorithm(ED25519) }
        did { method(KEY) { algorithm(ED25519) } }  // First method becomes default
    }
    
    val (issuerDid, issuerDoc) = trustWeave.createDid().getOrThrow()  // Uses default "key"
    val (holderDid, _) = trustWeave.createDid().getOrThrow()
    val issuerKeyId = issuerDoc.verificationMethod.firstOrNull()?.extractKeyId()
        ?: error("No verification method on issuer DID document")
    
    val credential = trustWeave.issue {
        credential {
            type("PersonCredential")
            issuer(issuerDid)
            subject { id(holderDid.value); "name" to "Alice" }
        }
        signedBy(issuerDid, issuerKeyId)
    }.getOrThrow()

    trustWeave.verify { credential(credential) }.getOrThrow()
    println("✅ Valid credential issued and verified")
}
```

### Pattern 2: Production with Blockchain Anchoring

```kotlin
import org.trustweave.trust.dsl.credential.DidMethods.KEY
import org.trustweave.trust.dsl.credential.DidMethods.WEB
import org.trustweave.trust.dsl.credential.KeyAlgorithms.ED25519
import org.trustweave.trust.dsl.credential.KmsProviders.AWS
import org.trustweave.trust.dsl.credential.AnchorProviders.ALGORAND
import org.trustweave.did.identifiers.extractKeyId
fun main() = runBlocking {
    val trustWeave = TrustWeave.build {
        keys { provider(AWS); algorithm(ED25519) }  // Production KMS
        did { method(KEY) { algorithm(ED25519) }; method(WEB) { domain("yourdomain.com") } }
        anchor { chain("algorand:mainnet") { provider(ALGORAND) } }
    }
    
    val (issuerDid, issuerDoc) = trustWeave.createDid().getOrThrow()  // Uses default "key"
    val issuerKeyId = issuerDoc.verificationMethod.firstOrNull()?.extractKeyId()
        ?: error("No verification method on issuer DID document")
    
    val credential = trustWeave.issue {
        credential { type("EmployeeCredential"); issuer(issuerDid); subject { "name" to "Alice" } }
        signedBy(issuerDid, issuerKeyId)
    }.getOrThrow()

    // Anchor to blockchain for tamper-evidence
    trustWeave.blockchains.anchor(credential, VerifiableCredential.serializer(), "algorand:mainnet")
    println("✅ Credential issued and anchored")
}
```

### Pattern 3: Complete Workflow

End-to-end workflow with verification:

```kotlin
import org.trustweave.trust.TrustWeave
import org.trustweave.trust.dsl.credential.DidMethods.KEY
import org.trustweave.trust.dsl.credential.KeyAlgorithms.ED25519
import org.trustweave.trust.dsl.credential.KmsProviders.IN_MEMORY
import org.trustweave.trust.types.getOrThrow
import org.trustweave.credential.results.getOrThrow
import org.trustweave.did.identifiers.extractKeyId
import kotlinx.coroutines.runBlocking

fun main() = runBlocking {
    val trustWeave = TrustWeave.build {
        keys { provider(IN_MEMORY); algorithm(ED25519) }
        did { method(KEY) { algorithm(ED25519) } }
    }
    
    // Create issuer and holder (uses default "key" method)
    val (issuerDid, issuerDoc) = trustWeave.createDid().getOrThrow()
    val (holderDid, _) = trustWeave.createDid().getOrThrow()
    val issuerKeyId = issuerDoc.verificationMethod.firstOrNull()?.extractKeyId()
        ?: error("No verification method on issuer DID document")

    // Issue credential
    val credential = trustWeave.issue {
        credential {
            type("EducationCredential")
            issuer(issuerDid)
            subject {
                id(holderDid.value)
                "degree" to "Bachelor of Science"
                "university" to "Example University"
            }
        }
        signedBy(issuerDid, issuerKeyId)
    }.getOrThrow()

    // Verify
    trustWeave.verify { credential(credential) }.getOrThrow()
    println("✅ Credential verified successfully")
}
```

---

## Error Handling

Handle errors using Result pattern or try-catch:

```kotlin
import org.trustweave.trust.TrustWeave
import org.trustweave.trust.types.DidCreationResult
import org.trustweave.trust.types.getOrThrow

val trustWeave = TrustWeave.build { /* keys { }; did { } */ }

// Option 1: Using getOrThrow() — throws IllegalStateException on failure
val (did, doc) = trustWeave.createDid().getOrThrow()

// Option 2: Exhaustive when on DidCreationResult (preferred in production)
when (val result = trustWeave.createDid()) {
    is DidCreationResult.Success -> println("Created: ${result.did.value}")
    is DidCreationResult.Failure.MethodNotRegistered ->
        println("Method not registered: ${result.method}")
    is DidCreationResult.Failure.KeyGenerationFailed ->
        println("Key generation failed: ${result.reason}")
    is DidCreationResult.Failure.DocumentCreationFailed ->
        println("Document creation failed: ${result.reason}")
    is DidCreationResult.Failure.InvalidConfiguration ->
        println("Invalid configuration: ${result.reason}")
    is DidCreationResult.Failure.Other ->
        println("DID creation failed: ${result.reason}")
}

// Option 3: try-catch around getOrThrow() at a boundary
try {
    val (did, doc) = trustWeave.createDid().getOrThrow()
} catch (error: IllegalStateException) {
    println("Error: ${error.message}")
}
```

---

## When to Use What

| Use Case | Configuration |
|----------|---------------|
| Prototypes, demos, learning | Minimal: `TrustWeave.build { keys {...}; did {...} }` |
| Production, blockchain, multi-DID | Full: Add `anchor {...}`, custom KMS |

---

## Type-Safe Constants

Use these imports to avoid string typos:

```kotlin
import org.trustweave.trust.dsl.credential.*
```

| Object | Constants |
|--------|-----------|
| `DidMethods` | `KEY`, `WEB`, `ION`, `ETHR` |
| `KeyAlgorithms` | `ED25519`, `SECP256K1`, `RSA` |
| `KmsProviders` | `IN_MEMORY`, `AWS`, `AZURE`, `GOOGLE`, `HASHICORP`, `FORTANIX`, `THALES`, `CYBERARK`, `IBM` |
| `AnchorProviders` | `IN_MEMORY`, `ALGORAND`, `ETHEREUM`, `POLYGON`, `BASE`, `ARBITRUM` |
| `TrustProviders` | `IN_MEMORY` |
| `RevocationProviders` | `IN_MEMORY` |

For proof types use `org.trustweave.credential.model.ProofType` directly
(e.g. `ProofType.Ed25519Signature2020`, `ProofType.JsonWebSignature2020`).

For third-party/custom providers, use strings directly: `provider("myCustomKms")`

---

## Next Steps

Now that you've learned the facade API, you can:

1. **[Configure TrustWeave](configure-trustlayer.md)** - Learn full configuration options
2. **[Issue Credentials](issue-credentials.md)** - Deep dive into credential issuance
3. **[Verify Credentials](verify-credentials.md)** - Learn verification options
4. **[Manage Wallets](manage-wallets.md)** - Store and organize credentials

---

## Related Documentation

- **[Quick Start](../tutorials/getting-started/quick-start.md)** - Complete getting started guide
- **[TrustWeave Configuration](configure-trustlayer.md)** - Full configuration guide
- **[API Reference](../api-reference/core-api.md)** - Complete API documentation

