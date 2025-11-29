---
title: Configure TrustWeave
nav_order: 2
parent: How-To Guides
keywords:
  - configuration
  - dsl
  - trustweave
  - setup
  - declarative
---

# Configure TrustWeave

This guide shows you how to configure TrustWeave using the declarative DSL. You'll learn how to set up key management, DID methods, blockchain anchors, and trust registries in a simple, readable way.

## Prerequisites

Before you begin, ensure you have:

- ✅ TrustWeave dependencies added to your project
- ✅ Basic understanding of DIDs, credentials, and blockchains
- ✅ Kotlin coroutines knowledge (for suspend functions)

## Expected Outcome

After completing this guide, you will have:

- ✅ A fully configured TrustWeave instance
- ✅ Multiple DID methods registered
- ✅ Blockchain anchors configured
- ✅ A trust registry set up
- ✅ Understanding of the declarative DSL approach

## Quick Example

Here's a complete example showing the power of declarative configuration:

```kotlin
import com.trustweave.trust.TrustWeave
import com.trustweave.trust.types.ProofType
import kotlinx.coroutines.runBlocking

fun main() = runBlocking {
    val trustWeave = TrustWeave.build {
        keys {
            provider("inMemory")
            algorithm("Ed25519")
        }

        did {
            method("key") {
                algorithm("Ed25519")
            }
            method("web") {
                domain("example.com")
            }
        }

        anchor {
            chain("algorand:testnet") {
                provider("algorand")
            }
        }

        trust {
            provider("inMemory")
        }
    }

    // Use the configured TrustWeave instance
    val did = trustWeave.createDid { method("key") }
    println("Created DID: $did")
}
```

**Expected Output:**
```
Created DID: did:key:z6MkhaXgBZDvotDkL5257faiztiGiC2QtKLGpbnnEGta2doK
```

## Step-by-Step Guide

### Step 1: Start with Basic Configuration

Begin by creating a minimal TrustWeave configuration with just key management:

```kotlin
val trustWeave = TrustWeave.build {
    keys {
        provider("inMemory")
        algorithm("Ed25519")
    }
}
```

**What this does:**
- Configures an in-memory key management service
- Sets Ed25519 as the default algorithm
- Provides the foundation for all cryptographic operations

**Expected Result:** A TrustWeave instance that can generate and manage keys.

---

### Step 2: Register DID Methods

Add DID method support to your configuration:

```kotlin
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
```

**What this does:**
- Registers the `did:key` method
- Configures it to use Ed25519 keys
- Enables DID creation with `createDid { method("key") }`

**Expected Result:** You can now create `did:key` identifiers.

---

### Step 3: Add Multiple DID Methods

Register additional DID methods for different use cases:

```kotlin
val trustWeave = TrustWeave.build {
    keys {
        provider("inMemory")
        algorithm("Ed25519")
    }

    did {
        method("key") {
            algorithm("Ed25519")
        }
        method("web") {
            domain("example.com")
        }
        method("ethr") {
            // Ethereum-specific configuration
            network("sepolia")
        }
    }
}
```

**What this does:**
- Registers `did:key` for local, self-sovereign identifiers
- Registers `did:web` for domain-based identifiers
- Registers `did:ethr` for Ethereum-based identifiers

**Expected Result:** You can create DIDs using any registered method.

---

### Step 4: Configure Blockchain Anchors

Add blockchain anchoring support for tamper evidence:

```kotlin
import com.trustweave.testkit.anchor.InMemoryBlockchainAnchorClient

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

    anchor {
        chain("algorand:testnet") {
            provider("algorand")
            // Or use test client for development
            // client(InMemoryBlockchainAnchorClient("algorand:testnet"))
        }
        chain("polygon:mainnet") {
            provider("polygon")
        }
    }
}
```

**What this does:**
- Registers Algorand testnet for development
- Registers Polygon mainnet for production
- Uses CAIP-2 chain identifiers (standard format)

**Expected Result:** You can anchor data to multiple blockchains.

---

### Step 5: Configure Trust Registry

Add trust registry for managing trusted issuers:

```kotlin
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

    anchor {
        chain("algorand:testnet") {
            provider("algorand")
        }
    }

    trust {
        provider("inMemory")
    }
}
```

**What this does:**
- Sets up an in-memory trust registry
- Enables trust anchor management
- Allows verification with trust checking

**Expected Result:** You can manage trusted issuers and verify credentials with trust validation.

---

### Step 6: Complete Configuration

Here's a complete production-ready configuration:

```kotlin
val trustWeave = TrustWeave.build {
    keys {
        provider("inMemory")  // Use "awsKms" or "azureKeyVault" in production
        algorithm("Ed25519")
    }

    did {
        method("key") {
            algorithm("Ed25519")
        }
        method("web") {
            domain("example.com")
        }
    }

    anchor {
        chain("algorand:testnet") {
            provider("algorand")
        }
        chain("polygon:mainnet") {
            provider("polygon")
        }
    }

    trust {
        provider("inMemory")
    }

    credentials {
        defaultProofType(com.trustweave.trust.types.ProofType.Ed25519Signature2020)
        autoAnchor(false)
    }
}
```

**Expected Result:** A fully configured TrustWeave ready for production use.

---

## Why This Approach is Better

### Before (Imperative Style)

```kotlin
// Verbose, imperative configuration
val kms = InMemoryKeyManagementService()
val didMethod = DidKeyMethod(kms)
val didRegistry = DidMethodRegistry()
didRegistry.register("key", didMethod)

val algorandClient = AlgorandBlockchainAnchorClient(...)
val polygonClient = PolygonBlockchainAnchorClient(...)
val anchorRegistry = BlockchainAnchorRegistry()
anchorRegistry.register("algorand:testnet", algorandClient)
anchorRegistry.register("polygon:mainnet", polygonClient)

val trustRegistry = InMemoryTrustRegistry()
val config = TrustWeaveConfig(
    kms = kms,
    didRegistry = didRegistry,
    anchorRegistry = anchorRegistry,
    trustRegistry = trustRegistry
)
val trustWeave = TrustWeave(config)
```

**Problems:**
- ❌ 20+ lines of boilerplate
- ❌ Hard to read and understand
- ❌ Easy to make mistakes
- ❌ Difficult to maintain

### After (Declarative DSL)

```kotlin
// Declarative, readable configuration
val trustWeave = TrustWeave.build {
    keys { provider("inMemory"); algorithm("Ed25519") }
    did { method("key") { algorithm("Ed25519") } }
    anchor {
        chain("algorand:testnet") { provider("algorand") }
        chain("polygon:mainnet") { provider("polygon") }
    }
    trust { provider("inMemory") }
}
```

**Benefits:**
- ✅ 80% less code
- ✅ Reads like documentation
- ✅ Type-safe with IDE autocomplete
- ✅ Easy to understand and maintain

---

## Common Patterns

### Pattern 1: Development Configuration

For local development and testing:

```kotlin
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
    anchor {
        chain("algorand:testnet") {
            client(InMemoryBlockchainAnchorClient("algorand:testnet"))
        }
    }
    trust {
        provider("inMemory")
    }
}
```

### Pattern 2: Production Configuration

For production with real services:

```kotlin
val trustWeave = TrustWeave.build {
    keys {
        provider("awsKms")  // Production KMS
        algorithm("Ed25519")
    }
    did {
        method("key") {
            algorithm("Ed25519")
        }
        method("web") {
            domain("yourdomain.com")
        }
    }
    anchor {
        chain("algorand:mainnet") {
            provider("algorand")
            // Production client configuration
        }
        chain("polygon:mainnet") {
            provider("polygon")
        }
    }
    trust {
        provider("database")  // Persistent trust registry
    }
}
```

### Pattern 3: Multi-Method Configuration

Supporting multiple DID methods:

```kotlin
val trustWeave = TrustWeave.build {
    keys {
        provider("inMemory")
        algorithm("Ed25519")
    }
    did {
        method("key") {
            algorithm("Ed25519")
        }
        method("web") {
            domain("example.com")
        }
        method("ethr") {
            network("sepolia")
        }
        method("polygon") {
            network("testnet")
        }
    }
}
```

---

## Error Handling

Handle configuration errors gracefully:

```kotlin
import com.trustweave.core.exception.TrustWeaveException

try {
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
} catch (error: TrustWeaveException) {
    when (error) {
        is TrustWeaveException.PluginNotFound -> {
            println("Provider not found: ${error.pluginId}")
            println("Available providers: ${error.context["availablePlugins"]}")
        }
        is TrustWeaveException.PluginInitializationFailed -> {
            println("Failed to initialize: ${error.reason}")
        }
        else -> {
            println("Configuration error: ${error.message}")
        }
    }
}
```

---

## Next Steps

Now that you've configured TrustWeave, you can:

1. **[Create DIDs](create-dids.md)** - Create decentralized identifiers
2. **[Issue Credentials](issue-credentials.md)** - Issue verifiable credentials
3. **[Anchor to Blockchain](blockchain-anchoring.md)** - Anchor data for tamper evidence
4. **[Manage Trust Anchors](../advanced/trust-registry.md)** - Configure trusted issuers

---

## Related Documentation

- **[DSL Guide](../getting-started/dsl-guide.md)** - Deep dive into DSL features
- **[API Reference](../api-reference/core-api.md)** - Complete API documentation
- **[Core Concepts](../core-concepts/README.md)** - Understanding TrustWeave architecture

