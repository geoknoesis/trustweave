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
import org.trustweave.trust.dsl.trustWeave
import org.trustweave.trust.dsl.credential.*
import org.trustweave.testkit.services.*
import kotlinx.coroutines.runBlocking

fun main() = runBlocking {
    val trustWeave = trustWeave {
        keys {
            provider(IN_MEMORY)  // Auto-discovered via SPI
            algorithm(ED25519)
        }

        did {
            method(KEY) {  // Auto-discovered via SPI
                algorithm(ED25519)
            }
            method(WEB) {  // Auto-discovered via SPI
                domain("example.com")
            }
        }

        anchor {
            chain("algorand:testnet") {
                provider(ALGORAND)  // Auto-discovered via SPI
            }
        }

        trust {
            provider(IN_MEMORY)
        }
        // KMS, DID methods, anchor clients, and CredentialService all auto-created!
    }

    // Use the configured TrustWeave instance
    val (did, _) = trustWeave.createDid().getOrThrow()
    println("Created DID: ${did.value}")
}
```

**Expected Output:**
```
Created DID: did:key:z6MkhaXgBZDvotDkL5257faiztiGiC2QtKLGpbnnEGta2doK
```

## Auto-Discovery via SPI

TrustWeave automatically discovers KMS, DID methods, and anchor clients via Java Service Provider Interface (SPI). When you specify a provider name, TrustWeave will automatically find and use the appropriate implementation from the classpath.

**Auto-Discovered Services:**
- **KMS** - Automatically discovered when using `keys { provider("inMemory") }` or other provider names
- **DID Methods** - Automatically discovered when using `did { method("key") }` or other method names
- **Anchor Clients** - Automatically discovered when using `anchor { chain(...) { provider(...) } }`

**Optional Factories (for services without SPI):**
- `trustRegistryFactory` - Optional when using `trust { provider(IN_MEMORY) }`
- `statusListRegistryFactory` - Optional when using `revocation { provider(IN_MEMORY) }`
- `walletFactory` - Optional when using `wallet { }`

**For Testing:**
Use testkit factories from `org.trustweave.testkit.services` (only for services without SPI):
- `TestkitTrustRegistryFactory()` - For trust registries
- `TestkitStatusListRegistryFactory()` - For revocation managers
- `TestkitWalletFactory()` - For wallets

**Note:** KMS, DID methods, and anchor clients are automatically discovered via SPI when testkit is on the classpath. No factories needed!

## Step-by-Step Guide

### Step 1: Start with Basic Configuration

Begin by creating a minimal TrustWeave configuration with just key management:

```kotlin
import org.trustweave.trust.dsl.trustWeave
import org.trustweave.trust.dsl.credential.*
import org.trustweave.testkit.services.*

val trustWeave = trustWeave {
    keys {
        provider(IN_MEMORY)  // Auto-discovered via SPI
        algorithm(ED25519)
    }
    // KMS auto-created!
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
import org.trustweave.trust.dsl.trustWeave
import org.trustweave.trust.dsl.credential.*
import org.trustweave.testkit.services.*

val trustWeave = trustWeave {
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
```

**What this does:**
- Registers the `did:key` method
- Configures it to use Ed25519 keys
- Enables DID creation with `createDid().getOrThrow()` (uses default method from config)

**Expected Result:** You can now create `did:key` identifiers.

---

### Step 3: Add Multiple DID Methods

Register additional DID methods for different use cases:

```kotlin
import org.trustweave.trust.dsl.trustWeave
import org.trustweave.trust.dsl.credential.*
import org.trustweave.testkit.services.*

val trustWeave = trustWeave {
    // KMS and DID methods auto-discovered via SPI
    keys {
        provider(IN_MEMORY)
        algorithm(ED25519)
    }

    did {
        method(KEY) {
            algorithm(ED25519)
        }
        method(WEB) {
            domain("example.com")
        }
            method(ETHR) {
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
import org.trustweave.trust.dsl.trustWeave
import org.trustweave.trust.dsl.credential.*
import org.trustweave.testkit.services.*

val trustWeave = trustWeave {
    // KMS, DID methods, and anchor clients auto-discovered via SPI
    keys {
        provider(IN_MEMORY)
        algorithm(ED25519)
    }

    did {
        method(KEY) {
            algorithm(ED25519)
        }
    }

    anchor {
        chain("algorand:testnet") {
            provider(ALGORAND)
            // Or use inMemory for testing
            // inMemory()
        }
        chain("polygon:mainnet") {
            provider(POLYGON)
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
import org.trustweave.trust.dsl.trustWeave
import org.trustweave.trust.dsl.credential.*
import org.trustweave.testkit.services.*

val trustWeave = trustWeave {
    factories(
        trustRegistryFactory = TestkitTrustRegistryFactory()  // Only needed for trust registry
    )
    // KMS, DID methods, and anchor clients auto-discovered via SPI
    keys {
        provider(IN_MEMORY)
        algorithm(ED25519)
    }

    did {
        method(KEY) {
            algorithm(ED25519)
        }
    }

    anchor {
        chain("algorand:testnet") {
            provider(ALGORAND)
        }
    }

    trust {
        provider(IN_MEMORY)
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
import org.trustweave.trust.dsl.trustWeave
import org.trustweave.trust.dsl.credential.*
import org.trustweave.testkit.services.*

val trustWeave = trustWeave {
    factories(
        trustRegistryFactory = TestkitTrustRegistryFactory()  // Only needed for trust registry
    )
    // KMS, DID methods, and anchor clients auto-discovered via SPI
    keys {
        provider(IN_MEMORY)  // Use "awsKms" or "azureKeyVault" in production
        algorithm(ED25519)
    }

    did {
        method(KEY) {
            algorithm(ED25519)
        }
        method(WEB) {
            domain("example.com")
        }
    }

    anchor {
        chain("algorand:testnet") {
            provider(ALGORAND)
        }
        chain("polygon:mainnet") {
            provider(POLYGON)
        }
    }

    trust {
        provider(IN_MEMORY)
    }

    credentials {
        defaultProofType(org.trustweave.trust.types.ProofType.Ed25519Signature2020)
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
import org.trustweave.testkit.services.*

// Declarative, readable configuration
val trustWeave = trustWeave {
    factories(
        trustRegistryFactory = TestkitTrustRegistryFactory()  // Only needed for trust registry
    )
    // KMS, DID methods, and anchor clients auto-discovered via SPI
    keys { provider(IN_MEMORY); algorithm(ED25519) }
    did { method(KEY) { algorithm(ED25519) } }
    anchor {
        chain("algorand:testnet") { provider(ALGORAND) }
        chain("polygon:mainnet") { provider(POLYGON) }
    }
    trust { provider(IN_MEMORY) }
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
import org.trustweave.trust.dsl.trustWeave
import org.trustweave.trust.dsl.credential.*
import org.trustweave.testkit.services.*

val trustWeave = trustWeave {
    factories(
        trustRegistryFactory = TestkitTrustRegistryFactory()  // Only needed for trust registry
    )
    // KMS, DID methods, and anchor clients auto-discovered via SPI
    keys {
        provider(IN_MEMORY)
        algorithm(ED25519)
    }
    did {
        method(KEY) {
            algorithm(ED25519)
        }
    }
    anchor {
        chain("algorand:testnet") {
            inMemory()
        }
    }
    trust {
        provider(IN_MEMORY)
    }
}
```

### Pattern 2: Production Configuration

For production with real services:

```kotlin
val trustWeave = trustWeave {
    keys {
        provider(AWS)  // Production KMS
        algorithm(ED25519)
    }
    did {
        method(KEY) {
            algorithm(ED25519)
        }
        method(WEB) {
            domain("yourdomain.com")
        }
    }
    anchor {
        chain("algorand:mainnet") {
            provider(ALGORAND)
            // Production client configuration
        }
        chain("polygon:mainnet") {
            provider(POLYGON)
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
import org.trustweave.trust.dsl.trustWeave
import org.trustweave.trust.dsl.credential.*
import org.trustweave.testkit.services.*

val trustWeave = trustWeave {
    // KMS and DID methods auto-discovered via SPI
    keys {
        provider(IN_MEMORY)
        algorithm(ED25519)
    }
    did {
        method(KEY) {
            algorithm(ED25519)
        }
        method(WEB) {
            domain("example.com")
        }
            method(ETHR) {
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
import org.trustweave.core.exception.TrustWeaveException
import org.trustweave.testkit.services.*

try {
    val trustWeave = trustWeave {
        factories(
            kmsFactory = TestkitKmsFactory(),
            didMethodFactory = TestkitDidMethodFactory()
        )
        keys {
            provider(IN_MEMORY)
            algorithm(ED25519)
        }
        did {
            method(KEY) {
                algorithm(ED25519)
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

