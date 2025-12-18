---
title: Use TrustWeave Facade for Quick Setup
nav_order: 1
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

- ✅ TrustWeave dependencies added to your project
- ✅ Basic understanding of DIDs and verifiable credentials
- ✅ Kotlin coroutines knowledge

## Expected Outcome

After completing this guide, you will have:

- ✅ Created a TrustWeave instance with one line
- ✅ Issued your first credential with minimal code
- ✅ Understood when to use the facade vs. full configuration
- ✅ Learned how to customize facade defaults

## Quick Example

Here's a complete example showing the simplicity of the facade API:

```kotlin
import com.trustweave.trust.dsl.trustWeave
import kotlinx.coroutines.runBlocking

fun main() = runBlocking {
    trustWeave {
        keys { provider("inMemory"); algorithm("Ed25519") }
        did { method("key") { algorithm("Ed25519") } }
    }.run {
        // Create DID (returns Did + DidDocument)
        val (issuerDid, issuerDoc) = createDid { method("key") }.getOrThrow()
        
        // Issue credential using DSL
        val credential = issue {
            credential {
                type("EmployeeCredential")
                issuer(issuerDid)
                subject {
                    id("did:key:holder")
                    "name" to "Alice"
                    "role" to "Engineer"
                }
            }
            signedBy(issuerDid, issuerDoc.verificationMethod.first().id.substringAfter("#"))
        }.getOrThrow()

        println("✅ Created DID: ${issuerDid.value}")
        println("✅ Issued credential: ${credential.id}")
    }
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
import com.trustweave.trust.dsl.trustWeave

val tw = trustWeave {
    keys { provider("inMemory"); algorithm("Ed25519") }
    did { method("key") { algorithm("Ed25519") } }
}
```

**What this does:**
- ✅ Configures in-memory key management (Ed25519)
- ✅ Registers `did:key` method
- ✅ Sets up default proof types
- ✅ Initializes all required services

**Expected Result:** A fully functional TrustWeave instance ready to use.

---

### Step 2: Create a DID

Create a DID with automatic defaults:

```kotlin
val (issuerDid, issuerDoc) = tw.createDid { method("key") }.getOrThrow()
```

**What this does:**
- ✅ Uses `did:key` method
- ✅ Generates Ed25519 key pair
- ✅ Creates verification method
- ✅ Returns `Did` + `DidDocument` (no separate resolution needed!)

**Expected Result:** A `Did` object with value like `did:key:z6Mk...` and its document.

---

### Step 3: Issue a Credential

Issue a credential using the DSL:

```kotlin
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
    signedBy(issuerDid, issuerDoc.verificationMethod.first().id.substringAfter("#"))
}.getOrThrow()
```

**What this does:**
- ✅ Creates credential structure via DSL
- ✅ Generates proof automatically
- ✅ Signs with issuer's key
- ✅ Returns signed credential

**Expected Result:** A verifiable credential with proof.

---

### Step 4: Verify the Credential

Verify the credential you just issued:

```kotlin
tw.verify { credential(credential) }.getOrThrow()
println("✅ Credential is valid")
```

**Expected Result:** Verification passes or throws with error details.

---

## Comparison: Minimal vs. Full Configuration

### Minimal Setup

```kotlin
trustWeave {
    keys { provider("inMemory"); algorithm("Ed25519") }
    did { method("key") { algorithm("Ed25519") } }
}.run {
    val (did, doc) = createDid { method("key") }.getOrThrow()
    val cred = issue {
        credential { type("MyCredential"); issuer(did); subject { "claim" to "value" } }
        signedBy(did, doc.verificationMethod.first().id.substringAfter("#"))
    }.getOrThrow()
}
```

**Best for:** Prototyping, examples, learning, simple use cases.

### Full Configuration

```kotlin
trustWeave {
    keys { provider("inMemory"); algorithm("Ed25519") }
    did { method("key") { algorithm("Ed25519") }; method("web") { domain("example.com") } }
    anchor { chain("algorand:testnet") { provider("algorand") } }
}.run {
    val (did, doc) = createDid { method("key") }.getOrThrow()
    val cred = issue {
        credential { type("MyCredential"); issuer(did); subject { "claim" to "value" } }
        signedBy(did, doc.verificationMethod.first().id.substringAfter("#"))
    }.getOrThrow()
    
    // Anchor to blockchain
    blockchains.anchor(cred, VerifiableCredential.serializer(), "algorand:testnet").getOrThrow()
}
```

**Best for:** Production, multiple DID methods, blockchain anchoring, advanced features.

---

## Customizing Defaults

### Adding DID Methods and Blockchain Anchoring

```kotlin
trustWeave {
    keys { provider("inMemory"); algorithm("Ed25519") }
    did {
        method("key") { algorithm("Ed25519") }
        method("web") { domain("example.com") }
    }
    anchor { chain("algorand:testnet") { provider("algorand") } }
}
```

**What this does:**
- ✅ Registers multiple DID methods (`did:key`, `did:web`)
- ✅ Configures blockchain anchoring
- ✅ All with minimal, readable DSL syntax

---

## Common Patterns

### Pattern 1: Quick Prototype

For rapid prototyping and testing:

```kotlin
fun main() = runBlocking {
    trustWeave {
        keys { provider("inMemory"); algorithm("Ed25519") }
        did { method("key") { algorithm("Ed25519") } }
    }.run {
        val (issuerDid, issuerDoc) = createDid { method("key") }.getOrThrow()
        val (holderDid, _) = createDid { method("key") }.getOrThrow()
        
        val credential = issue {
            credential {
                type("PersonCredential")
                issuer(issuerDid)
                subject { id(holderDid.value); "name" to "Alice" }
            }
            signedBy(issuerDid, issuerDoc.verificationMethod.first().id.substringAfter("#"))
        }.getOrThrow()

        verify { credential(credential) }.getOrThrow()
        println("✅ Valid credential issued and verified")
    }
}
```

### Pattern 2: Production with Blockchain Anchoring

```kotlin
fun main() = runBlocking {
    trustWeave {
        keys { provider("awsKms"); algorithm("Ed25519") }
        did { method("key") { algorithm("Ed25519") }; method("web") { domain("yourdomain.com") } }
        anchor { chain("algorand:mainnet") { provider("algorand") } }
    }.run {
        val (issuerDid, issuerDoc) = createDid { method("key") }.getOrThrow()
        
        val credential = issue {
            credential { type("EmployeeCredential"); issuer(issuerDid); subject { "name" to "Alice" } }
            signedBy(issuerDid, issuerDoc.verificationMethod.first().id.substringAfter("#"))
        }.getOrThrow()

        // Anchor to blockchain for tamper-evidence
        blockchains.anchor(credential, VerifiableCredential.serializer(), "algorand:mainnet").getOrThrow()
        println("✅ Credential issued and anchored")
    }
}
```

### Pattern 3: Complete Workflow

End-to-end workflow with verification:

```kotlin
fun main() = runBlocking {
    trustWeave {
        keys { provider("inMemory"); algorithm("Ed25519") }
        did { method("key") { algorithm("Ed25519") } }
    }.run {
        // Create issuer and holder
        val (issuerDid, issuerDoc) = createDid { method("key") }.getOrThrow()
        val (holderDid, _) = createDid { method("key") }.getOrThrow()

        // Issue credential
        val credential = issue {
            credential {
                type("EducationCredential")
                issuer(issuerDid)
                subject {
                    id(holderDid.value)
                    "degree" to "Bachelor of Science"
                    "university" to "Example University"
                }
            }
            signedBy(issuerDid, issuerDoc.verificationMethod.first().id.substringAfter("#"))
        }.getOrThrow()

        // Verify
        verify { credential(credential) }.getOrThrow()
        println("✅ Credential verified successfully")
    }
}
```

---

## Error Handling

Handle errors using Result pattern or try-catch:

```kotlin
// Option 1: Using getOrThrow() - throws on error
trustWeave { ... }.run {
    val (did, doc) = createDid { method("key") }.getOrThrow()  // Throws if fails
}

// Option 2: Using fold() - handle success and failure
trustWeave { ... }.run {
    createDid { method("key") }.fold(
        onSuccess = { (did, doc) -> println("Created: ${did.value}") },
        onFailure = { error -> println("Failed: ${error.message}") }
    )
}

// Option 3: Using getOrElse() - provide default
trustWeave { ... }.run {
    val result = createDid { method("key") }.getOrElse { 
        println("Error: ${it.message}")
        return@run
    }
}
```

---

## When to Use What

| Use Case | Configuration |
|----------|---------------|
| Prototypes, demos, learning | Minimal: `trustWeave { keys {...}; did {...} }` |
| Production, blockchain, multi-DID | Full: Add `anchor {...}`, custom KMS |

---

## Next Steps

Now that you've learned the facade API, you can:

1. **[Configure TrustWeave](configure-trustlayer.md)** - Learn full configuration options
2. **[Issue Credentials](issue-credentials.md)** - Deep dive into credential issuance
3. **[Verify Credentials](verify-credentials.md)** - Learn verification options
4. **[Manage Wallets](manage-wallets.md)** - Store and organize credentials

---

## Related Documentation

- **[Quick Start](../getting-started/quick-start.md)** - Complete getting started guide
- **[TrustWeave Configuration](configure-trustlayer.md)** - Full configuration guide
- **[API Reference](../api-reference/core-api.md)** - Complete API documentation

