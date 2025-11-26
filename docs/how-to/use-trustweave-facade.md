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
- ✅ Understood when to use the facade vs. TrustLayer
- ✅ Learned how to customize facade defaults

## Quick Example

Here's a complete example showing the simplicity of the facade API:

```kotlin
import com.trustweave.TrustWeave
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

fun main() = runBlocking {
    // Step 1: One-line setup
    val trustweave = TrustWeave.create()

    // Step 2: Create DID (automatic defaults)
    val issuerDid = trustweave.dids.create()
    
    // Step 3: Issue credential (3 lines)
    val credential = trustweave.credentials.issue(
        issuer = issuerDid.id,
        subject = buildJsonObject {
            put("name", "Alice")
            put("role", "Engineer")
        },
        types = listOf("VerifiableCredential", "EmployeeCredential")
    )
    
    println("✅ Created DID: ${issuerDid.id}")
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

The facade provides sensible defaults out of the box:

```kotlin
val trustweave = TrustWeave.create()
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
val issuerDid = trustweave.dids.create()
```

**What this does:**
- ✅ Uses `did:key` method (default)
- ✅ Generates Ed25519 key pair
- ✅ Creates verification method
- ✅ Returns DID document

**Expected Result:** A DID string like `did:key:z6Mk...`

---

### Step 3: Issue a Credential

Issue a credential with minimal configuration:

```kotlin
val credential = trustweave.credentials.issue(
    issuer = issuerDid.id,
    subject = buildJsonObject {
        put("name", "Alice")
        put("role", "Engineer")
    },
    types = listOf("VerifiableCredential", "EmployeeCredential")
)
```

**What this does:**
- ✅ Creates credential structure
- ✅ Generates proof automatically
- ✅ Signs with issuer's key
- ✅ Returns signed credential

**Expected Result:** A verifiable credential with proof.

---

### Step 4: Verify the Credential

Verify the credential you just issued:

```kotlin
val result = trustweave.credentials.verify(credential)
if (result.valid) {
    println("✅ Credential is valid")
} else {
    println("❌ Credential invalid: ${result.errors}")
}
```

**Expected Result:** Verification result showing credential validity.

---

## Comparison: Facade vs. TrustLayer

### Using Facade (Simple)

```kotlin
// 3 lines total
val trustweave = TrustWeave.create()
val issuerDid = trustweave.dids.create()
val credential = trustweave.credentials.issue(...)
```

**Best for:**
- ✅ Rapid prototyping
- ✅ Quick examples
- ✅ Learning TrustWeave
- ✅ Simple use cases

### Using TrustLayer (Full Control)

```kotlin
// 20+ lines with full configuration
val trustLayer = TrustLayer.build {
    keys { provider("inMemory"); algorithm("Ed25519") }
    did { method("key") { algorithm("Ed25519") } }
    // ... more configuration
}
val issuerDid = trustLayer.createDid { method("key") }
val credential = trustLayer.issue { ... }
```

**Best for:**
- ✅ Production applications
- ✅ Custom configurations
- ✅ Multiple DID methods
- ✅ Blockchain anchoring
- ✅ Advanced features

---

## Customizing Facade Defaults

### Custom Configuration

You can customize the facade while keeping simplicity:

```kotlin
val trustweave = TrustWeave.create {
    didMethods {
        + DidKeyMethod()
        + DidWebMethod(domain = "example.com")
    }
    blockchains {
        "algorand:testnet" to algorandClient
    }
}
```

**What this does:**
- ✅ Keeps default KMS and proof types
- ✅ Adds custom DID methods
- ✅ Registers blockchain clients
- ✅ Still simpler than full TrustLayer config

---

## Common Patterns

### Pattern 1: Quick Prototype

For rapid prototyping and testing:

```kotlin
val trustweave = TrustWeave.create()

// Create identities
val issuerDid = trustweave.dids.create()
val holderDid = trustweave.dids.create()

// Issue credential
val credential = trustweave.credentials.issue(
    issuer = issuerDid.id,
    subject = buildJsonObject {
        put("id", holderDid.id)
        put("name", "Alice")
    },
    types = listOf("VerifiableCredential", "PersonCredential")
)

// Verify
val result = trustweave.credentials.verify(credential)
println("Valid: ${result.valid}")
```

### Pattern 2: Production with Customization

For production with some customization:

```kotlin
val trustweave = TrustWeave.create {
    // Use production KMS
    kmsProvider("awsKms")
    
    // Add multiple DID methods
    didMethods {
        + DidKeyMethod()
        + DidWebMethod(domain = "yourdomain.com")
    }
    
    // Register blockchains
    blockchains {
        "algorand:mainnet" to algorandClient
        "polygon:mainnet" to polygonClient
    }
}
```

### Pattern 3: Complete Workflow

End-to-end workflow using the facade:

```kotlin
fun main() = runBlocking {
    val trustweave = TrustWeave.create()
    
    // 1. Create issuer
    val issuerDid = trustweave.dids.create()
    
    // 2. Create holder
    val holderDid = trustweave.dids.create()
    
    // 3. Issue credential
    val credential = trustweave.credentials.issue(
        issuer = issuerDid.id,
        subject = buildJsonObject {
            put("id", holderDid.id)
            put("degree", "Bachelor of Science")
            put("university", "Example University")
        },
        types = listOf("VerifiableCredential", "EducationCredential")
    )
    
    // 4. Verify credential
    val verification = trustweave.credentials.verify(credential)
    if (verification.valid) {
        println("✅ Credential verified successfully")
    }
}
```

---

## Error Handling

Handle errors when using the facade:

```kotlin
import com.trustweave.core.exception.TrustWeaveException
import com.trustweave.did.exception.DidException
import com.trustweave.credential.exception.CredentialException

try {
    val trustweave = TrustWeave.create()
    val issuerDid = trustweave.dids.create()
    val credential = trustweave.credentials.issue(...)
} catch (error: TrustWeaveException) {
    when (error) {
        is DidException.DidMethodNotRegistered -> {
            println("DID method not available: ${error.method}")
        }
        is CredentialException.CredentialIssuanceFailed -> {
            println("Issuance failed: ${error.reason}")
        }
        else -> {
            println("Error: ${error.message}")
        }
    }
}
```

---

## When to Use Facade vs. TrustLayer

### Use Facade When:

- ✅ Building prototypes or demos
- ✅ Learning TrustWeave
- ✅ Simple use cases with defaults
- ✅ Quick examples and tutorials
- ✅ Testing and experimentation

### Use TrustLayer When:

- ✅ Production applications
- ✅ Need multiple DID methods
- ✅ Custom KMS providers
- ✅ Blockchain anchoring
- ✅ Trust registry management
- ✅ Advanced configurations

---

## Next Steps

Now that you've learned the facade API, you can:

1. **[Configure TrustLayer](configure-trustlayer.md)** - Learn full configuration options
2. **[Issue Credentials](issue-credentials.md)** - Deep dive into credential issuance
3. **[Verify Credentials](verify-credentials.md)** - Learn verification options
4. **[Manage Wallets](manage-wallets.md)** - Store and organize credentials

---

## Related Documentation

- **[Quick Start](../getting-started/quick-start.md)** - Complete getting started guide
- **[TrustLayer Configuration](configure-trustlayer.md)** - Full configuration guide
- **[API Reference](../api-reference/core-api.md)** - Complete API documentation

