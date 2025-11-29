---
title: TrustWeave Mental Model
nav_order: 7
parent: Introduction
keywords:
  - mental model
  - architecture
  - concepts
  - trustlayer
  - dsl
  - plugins
  - services
---

# TrustWeave Mental Model

Understanding how TrustWeave works at a conceptual level will help you use it effectively and confidently.

## Overview

TrustWeave is built on a **layered architecture** with clear separation between:
- **Facade Layer** (`TrustWeave`) - High-level, developer-friendly API
- **Service Layer** - Domain-specific services (DID, Credential, Wallet, etc.)
- **Plugin Layer** - Pluggable implementations (DID methods, KMS, blockchains)

```
┌─────────────────────────────────────────┐
│         Application Code                │
└──────────────┬──────────────────────────┘
               │
┌──────────────▼──────────────────────────┐
│         TrustWeave (Facade)             │
│  - createDid(), issue(), verify(), etc. │
└──────────────┬──────────────────────────┘
               │
┌──────────────▼──────────────────────────┐
│      TrustWeaveContext (Orchestrator)   │
│  - Coordinates services                 │
│  - Manages DSL builders                 │
└──────────────┬──────────────────────────┘
               │
       ┌───────┴───────┐
       │               │
┌──────▼──────┐ ┌──────▼──────┐
│ DID Service │ │ Credential  │
│             │ │ Service     │
└──────┬──────┘ └──────┬──────┘
       │               │
       └───────┬───────┘
               │
┌──────────────▼──────────────────────────┐
│         Plugin Layer                    │
│  - DID Methods (key, web, ion, etc.)    │
│  - KMS Providers (inMemory, AWS, etc.)  │
│  - Blockchain Clients (Algorand, etc.)  │
└─────────────────────────────────────────┘
```

## Core Components

### 1. TrustWeave (Main Entry Point)

`TrustWeave` is the **primary facade** for all operations. It provides:
- Type-safe DSL builders for configuration and operations
- Unified error handling (exceptions)
- Simplified API that hides complexity

**Key Characteristics:**
- All methods are **suspend functions** (coroutine-based)
- All methods **throw exceptions** on failure (use try-catch)
- Configuration is done via DSL builders

**Example:**
```kotlin
val trustWeave = TrustWeave.build {
    keys { provider("inMemory"); algorithm("Ed25519") }
    did { method("key") { algorithm("Ed25519") } }
}

// All operations throw exceptions
try {
    val did = trustWeave.createDid { method("key") }
    val credential = trustWeave.issue { ... }
} catch (error: TrustWeaveException) {
    // Handle error
}
```

### 2. TrustWeaveContext (Internal Orchestrator)

`TrustWeaveContext` coordinates between services. You rarely interact with it directly, but it:
- Manages DSL builders
- Routes operations to appropriate services
- Handles service lifecycle

**Access:** Use `trustWeave.getDslContext()` only when you need advanced operations.

### 3. Services (Domain Logic)

Services implement domain-specific logic:
- **DID Service**: Creates, resolves, updates DIDs
- **Credential Service**: Issues and verifies credentials
- **Wallet Service**: Manages credential storage
- **Trust Registry**: Manages trust anchors

Services are **configured** during `TrustWeave.build { }` and **used** via `TrustWeave` methods.

### 4. Plugins (Implementations)

Plugins provide concrete implementations:
- **DID Methods**: `did:key`, `did:web`, `did:ion`, etc.
- **KMS Providers**: `inMemory`, `AWS KMS`, `Azure Key Vault`, etc.
- **Blockchain Clients**: `Algorand`, `Polygon`, `Ethereum`, etc.

Plugins are **registered** during configuration and **selected** via provider names.

## Data Flow

### Credential Issuance Flow

```
1. Application calls: trustWeave.issue { ... }
   │
2. TrustWeave delegates to TrustWeaveContext
   │
3. TrustWeaveContext orchestrates:
   │
   ├─► DID Service: Resolve issuer DID
   │   └─► DID Method Plugin: Fetch DID document
   │
   ├─► KMS Provider: Get signing key
   │   └─► KMS Plugin: Retrieve key material
   │
   └─► Credential Service: Build credential + proof
       └─► Proof Generator: Create cryptographic proof
   │
4. Return VerifiableCredential to application
```

### Credential Verification Flow

```
1. Application calls: trustWeave.verify { credential(...) }
   │
2. TrustWeaveContext orchestrates:
   │
   ├─► Credential Service: Validate structure
   │
   ├─► DID Service: Resolve issuer DID
   │   └─► DID Method Plugin: Fetch DID document
   │
   ├─► Proof Verifier: Verify signature
   │   └─► KMS Provider: Get public key
   │
   ├─► Revocation Service: Check revocation status
   │   └─► Status List Manager: Query status list
   │
   └─► Trust Registry: Check issuer trust (if enabled)
   │
3. Return CredentialVerificationResult
```

## Configuration Model

TrustWeave uses a **builder pattern** for configuration:

```kotlin
TrustWeave.build {
    // Configure KMS
    keys {
        provider("inMemory")  // Select KMS plugin
        algorithm("Ed25519")  // Select algorithm
    }

    // Configure DID methods
    did {
        method("key") {        // Register did:key method
            algorithm("Ed25519")
        }
        method("web") {        // Register did:web method
            domain("example.com")
        }
    }

    // Configure blockchain anchors
    anchor {
        chain("algorand:testnet") {
            provider("algorand")
        }
    }

    // Configure trust registry
    trust {
        provider("inMemory")
    }
}
```

**Key Points:**
- Configuration is **type-safe** (compile-time checks)
- Plugins are **registered** by name/provider
- Configuration is **immutable** after creation

## Error Handling Model

TrustWeave uses **two error handling patterns**:

### 1. Exception-Based (TrustLayer Methods)

All `TrustWeave` methods throw exceptions:

```kotlin
import com.trustweave.did.exception.DidException
import com.trustweave.did.exception.DidException.DidMethodNotRegistered
import com.trustweave.core.exception.TrustWeaveException

try {
    val did = trustWeave.createDid { method("key") }
} catch (error: DidException) {
    when (error) {
        is DidMethodNotRegistered -> {
            // Handle method not registered
        }
        else -> {
            // Handle other DID errors
        }
    }
} catch (error: TrustWeaveException) {
    // Handle TrustWeave errors with error codes
} catch (error: Exception) {
    // Handle other errors
}
```

**Why exceptions?** Simpler API for common operations, familiar pattern for Kotlin developers.

### 2. Result-Based (Lower-Level APIs)

Some lower-level APIs return `Result<T>`:

```kotlin
val result = someService.operation()
result.fold(
    onSuccess = { value -> /* handle success */ },
    onFailure = { error -> /* handle error */ }
)
```

**Why Result?** More functional style, better for composition, explicit error handling.

## Key Design Principles

### 1. Type Safety

- DSL builders provide compile-time type checking
- Invalid configurations fail at compile time
- IDE autocomplete guides correct usage

### 2. Pluggability

- All external dependencies via interfaces
- Easy to swap implementations
- Test with in-memory, deploy with production plugins

### 3. Coroutines

- All operations are suspend functions
- Non-blocking by default
- Easy to compose async operations

### 4. Domain-Agnostic

- No domain-specific logic in core
- Works for any use case (education, healthcare, IoT, etc.)
- Domain logic lives in your application

## Common Patterns

### Pattern 1: Create → Configure → Use

```kotlin
// 1. Create and configure
val trustWeave = TrustWeave.build { ... }

// 2. Use
val did = trustWeave.createDid { ... }
val credential = trustWeave.issue { ... }
```

### Pattern 2: Error Handling

```kotlin
import com.trustweave.did.exception.DidException
import com.trustweave.core.exception.TrustWeaveException

try {
    val result = trustWeave.operation { ... }
    // Use result
} catch (error: DidException) {
    // Handle DID-specific errors
} catch (error: TrustWeaveException) {
    // Handle TrustWeave errors with error codes
} catch (error: Exception) {
    // Handle other errors
}
```

### Pattern 3: Service Composition

```kotlin
// Create DIDs
val issuerDid = trustWeave.createDid { ... }
val holderDid = trustWeave.createDid { ... }

// Issue credential
val credential = trustWeave.issue {
    credential { issuer(issuerDid); subject { id(holderDid) } }
    by(issuerDid = issuerDid, keyId = "$issuerDid#key-1")
}

// Store in wallet
val wallet = trustWeave.wallet { holder(holderDid) }
wallet.store(credential)
```

## Next Steps

- [Quick Start](../getting-started/quick-start.md) - Hands-on introduction
- [Core Concepts](../core-concepts/README.md) - Deep dives into DIDs, VCs, etc.
- [API Reference](../api-reference/core-api.md) - Complete API documentation
- [Architecture Overview](architecture-overview.md) - Technical architecture details

