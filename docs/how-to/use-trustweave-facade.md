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
import com.trustweave.trust.TrustWeave
import com.trustweave.did.resolver.DidResolutionResult
import com.trustweave.trust.types.VerificationResult
import com.trustweave.testkit.services.*
import kotlinx.coroutines.runBlocking

fun main() = runBlocking {
    // Step 1: Configure TrustWeave
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

    // Step 2: Create DID
    val issuerDid = trustWeave.createDid()

    // Step 3: Get key ID and issue credential
    val resolutionResult = trustWeave.resolveDid(issuerDid)
    val issuerDocument = when (resolutionResult) {
        is DidResolutionResult.Success -> resolutionResult.document
        else -> throw IllegalStateException("Failed to resolve issuer DID")
    }
    val keyId = issuerDocument.verificationMethod.firstOrNull()?.id?.substringAfter("#")
        ?: throw IllegalStateException("No verification method found")
    
    val credential = trustWeave.issueCredential(
        issuer = issuerDid.value,
        keyId = keyId,
        subject = mapOf(
            "id" to "did:key:holder",
            "name" to "Alice",
            "role" to "Engineer"
        ),
        credentialType = "EmployeeCredential"
    )

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
import com.trustweave.testkit.services.*

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
val issuerDid = trustWeave.createDid()  // Uses "key" method by default
```

**What this does:**
- ✅ Uses `did:key` method (default)
- ✅ Generates Ed25519 key pair
- ✅ Creates verification method
- ✅ Returns type-safe `Did` object

**Expected Result:** A `Did` object with value like `did:key:z6Mk...`

---

### Step 3: Issue a Credential

Issue a credential with minimal configuration:

```kotlin
// Get key ID first
val resolutionResult = trustWeave.resolveDid(issuerDid)
val issuerDocument = when (resolutionResult) {
    is com.trustweave.did.resolver.DidResolutionResult.Success -> resolutionResult.document
    else -> throw IllegalStateException("Failed to resolve issuer DID")
}
val keyId = issuerDocument.verificationMethod.firstOrNull()?.id?.substringAfter("#")
    ?: throw IllegalStateException("No verification method found")

val credential = trustWeave.issueCredential(
    issuer = issuerDid.value,
    keyId = keyId,
    subject = mapOf(
        "id" to "did:key:holder",
        "name" to "Alice",
        "role" to "Engineer"
    ),
    credentialType = "EmployeeCredential"
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
val result = trustWeave.verifyCredential(credential)
when (result) {
    is com.trustweave.trust.types.VerificationResult.Valid -> {
        println("✅ Credential is valid")
    }
    else -> {
        println("❌ Credential invalid: ${result.errors.joinToString()}")
    }
}
```

**Expected Result:** Verification result showing credential validity.

---

## Comparison: Facade vs. Full Configuration

### Using TrustWeave (Simple)

```kotlin
// Minimal setup
val trustWeave = TrustWeave.build {
    keys { provider("inMemory"); algorithm("Ed25519") }
    did { method("key") { algorithm("Ed25519") } }
}
val issuerDid = trustWeave.createDid()
val credential = trustWeave.issueCredential(...)
```

**Best for:**
- ✅ Rapid prototyping
- ✅ Quick examples
- ✅ Learning TrustWeave
- ✅ Simple use cases

### Using TrustWeave with Full Configuration

```kotlin
import com.trustweave.testkit.services.*

// Full configuration with all options
val trustWeave = TrustWeave.build {
    factories(
        kmsFactory = TestkitKmsFactory(),
        didMethodFactory = TestkitDidMethodFactory(),
        anchorClientFactory = TestkitBlockchainAnchorClientFactory(),
        trustRegistryFactory = TestkitTrustRegistryFactory(),
        statusListRegistryFactory = TestkitStatusListRegistryFactory()
    )
    keys { provider("inMemory"); algorithm("Ed25519") }
    did { method("key") { algorithm("Ed25519") } }
    anchor { chain("algorand:testnet") { provider("algorand") } }
    trust { provider("inMemory") }
    revocation { provider("inMemory") }
    // ... more configuration
}
val issuerDid = trustWeave.createDid { method("key") }
val credential = trustWeave.issue { ... }
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

You can customize TrustWeave while keeping simplicity:

```kotlin
import com.trustweave.testkit.services.*

val trustWeave = TrustWeave.build {
    factories(
        kmsFactory = TestkitKmsFactory(),
        didMethodFactory = TestkitDidMethodFactory(),
        anchorClientFactory = TestkitBlockchainAnchorClientFactory()
    )
    keys { provider("inMemory"); algorithm("Ed25519") }
    did {
        method("key") { algorithm("Ed25519") }
        method("web") { domain("example.com") }
    }
    anchor {
        chain("algorand:testnet") { provider("algorand") }
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
import com.trustweave.testkit.services.*

val trustWeave = TrustWeave.build {
    factories(
        kmsFactory = TestkitKmsFactory(),
        didMethodFactory = TestkitDidMethodFactory()
    )
    keys { provider("inMemory"); algorithm("Ed25519") }
    did { method("key") { algorithm("Ed25519") } }
}

// Create identities
val issuerDid = trustWeave.createDid()
val holderDid = trustWeave.createDid()

// Get key ID and issue credential
val resolutionResult = trustWeave.resolveDid(issuerDid)
val issuerDocument = when (resolutionResult) {
    is com.trustweave.did.resolver.DidResolutionResult.Success -> resolutionResult.document
    else -> throw IllegalStateException("Failed to resolve issuer DID")
}
val keyId = issuerDocument.verificationMethod.firstOrNull()?.id?.substringAfter("#")
    ?: throw IllegalStateException("No verification method found")

val credential = trustWeave.issueCredential(
    issuer = issuerDid.value,
    keyId = keyId,
    subject = mapOf("id" to holderDid.value, "name" to "Alice"),
    credentialType = "PersonCredential"
)

// Verify
val result = trustWeave.verifyCredential(credential)
when (result) {
    is VerificationResult.Valid -> println("Valid: true")
    else -> println("Valid: false")
}
```

### Pattern 2: Production with Customization

For production with some customization:

```kotlin
import com.trustweave.testkit.services.*

val trustWeave = TrustWeave.build {
    factories(
        kmsFactory = TestkitKmsFactory(),  // Use production KMS factory in production
        didMethodFactory = TestkitDidMethodFactory(),
        anchorClientFactory = TestkitBlockchainAnchorClientFactory()
    )
    // Use production KMS
    keys {
        provider("awsKms")  // or your production KMS
        algorithm("Ed25519")
    }

    // Add multiple DID methods
    did {
        method("key") { algorithm("Ed25519") }
        method("web") { domain("yourdomain.com") }
    }

    // Register blockchains
    anchor {
        chain("algorand:mainnet") { provider("algorand") }
        chain("polygon:mainnet") { provider("polygon") }
    }
}
```

### Pattern 3: Complete Workflow

End-to-end workflow using TrustWeave:

```kotlin
import com.trustweave.testkit.services.*

fun main() = runBlocking {
    val trustWeave = TrustWeave.build {
        factories(
            kmsFactory = TestkitKmsFactory(),
            didMethodFactory = TestkitDidMethodFactory()
        )
        keys { provider("inMemory"); algorithm("Ed25519") }
        did { method("key") { algorithm("Ed25519") } }
    }

    // 1. Create issuer
    val issuerDid = trustWeave.createDid()

    // 2. Create holder
    val holderDid = trustWeave.createDid()

    // 3. Get key ID and issue credential
    val resolutionResult = trustWeave.resolveDid(issuerDid)
    val issuerDocument = when (resolutionResult) {
        is com.trustweave.did.resolver.DidResolutionResult.Success -> resolutionResult.document
        else -> throw IllegalStateException("Failed to resolve issuer DID")
    }
    val keyId = issuerDocument.verificationMethod.firstOrNull()?.id?.substringAfter("#")
        ?: throw IllegalStateException("No verification method found")

    val credential = trustWeave.issueCredential(
        issuer = issuerDid.value,
        keyId = keyId,
        subject = mapOf(
            "id" to holderDid.value,
            "degree" to "Bachelor of Science",
            "university" to "Example University"
        ),
        credentialType = "EducationCredential"
    )

    // 4. Verify credential
    val verification = trustWeave.verifyCredential(credential)
    when (verification) {
        is VerificationResult.Valid -> {
            println("✅ Credential verified successfully")
        }
        else -> {
            println("❌ Credential verification failed")
        }
    }
}
```

---

## Error Handling

Handle errors when using TrustWeave:

```kotlin
import com.trustweave.core.exception.TrustWeaveException
import com.trustweave.did.resolver.DidResolutionResult

try {
    val trustWeave = TrustWeave.build {
        keys { provider("inMemory"); algorithm("Ed25519") }
        did { method("key") { algorithm("Ed25519") } }
    }
    val issuerDid = trustWeave.createDid()
    // ... issue credential
} catch (error: Exception) {
    when (error) {
        is TrustWeaveException -> {
            println("TrustWeave error: ${error.message}")
        }
        is IllegalStateException -> {
            println("Configuration error: ${error.message}")
        }
        else -> {
            println("Error: ${error.message}")
            error.printStackTrace()
        }
    }
}
```

---

## When to Use Facade vs. Full Configuration

### Use Simple Configuration When:

- ✅ Building prototypes or demos
- ✅ Learning TrustWeave
- ✅ Simple use cases with defaults
- ✅ Quick examples and tutorials
- ✅ Testing and experimentation

### Use Full Configuration When:

- ✅ Production applications
- ✅ Need multiple DID methods
- ✅ Custom KMS providers
- ✅ Blockchain anchoring
- ✅ Trust registry management
- ✅ Advanced configurations

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

