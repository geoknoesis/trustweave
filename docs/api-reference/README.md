---
title: API Reference
nav_order: 60
keywords:
  - api reference
  - api
  - methods
  - functions
  - documentation
---

# API Reference

Complete API reference for TrustWeave.

## Quick Reference

| Category | Methods | Page |
|----------|---------|------|
| **DIDs** | `createDid()`, `resolveDid()`, `updateDid()`, `deactivateDid()` | [Core API](core-api.md#did-operations) |
| **Credentials** | `issue()`, `verify()`, `present()` | [Core API](core-api.md#credential-operations) |
| **Wallets** | `create()`, `store()`, `query()`, `present()` | [Wallet API](wallet-api.md) |
| **Blockchain** | `anchor()`, `read()`, `verify()` | [Core API](core-api.md#blockchain-operations) |
| **Smart Contracts** | `draft()`, `bindContract()`, `executeContract()` | [Smart Contract API](smart-contract-api.md) |
| **Trust** | `addTrustAnchor()`, `isTrustedIssuer()`, `findTrustPath()` | [Core API](core-api.md#trust-operations) |

## Core API

- **[Core API](core-api.md)**: TrustWeave facade API
  - DID operations (`createDid()`, `resolveDid()`, `updateDid()`, `deactivateDid()`)
  - Credential operations (`issue()`, `verify()`)
  - Wallet operations (`wallet { }`)
  - Blockchain anchoring (`blockchains.anchor()`, `blockchains.read()`)
  - Smart contract operations (`contracts.draft()`, `contracts.bindContract()`, `contracts.executeContract()`, etc.)
  - Trust operations (`trust { }`, `addTrustAnchor()`, `isTrustedIssuer()`)
  - Error handling with sealed result types (`DidCreationResult`, `IssuanceResult`, etc.)

## Service APIs

- **[Wallet API](wallet-api.md)**: Wallet operations and capabilities
  - Credential storage
  - Credential organization (collections, tags, metadata)
  - Credential lifecycle (archive, refresh)
  - Credential presentation
  - DID management
  - Key management
  - Credential issuance

- **[Credential Service API](credential-service-api.md)**: Credential service SPI
  - Credential issuance
  - Credential verification
  - Presentation creation
  - Presentation verification
  - Provider registration
  - Type-safe options

- **[Smart Contract API](smart-contract-api.md)**: Smart Contract operations
  - Contract creation and lifecycle
  - Binding with verifiable credentials
  - Blockchain anchoring
  - Contract execution
  - Condition evaluation
  - State management

## Error handling

TrustWeave is **hybrid**: many facade methods return **sealed results** (`IssuanceResult`, `VerificationResult`, `DidCreationResult`, `WalletCreationResult`, …). Use exhaustive **`when`** for those. **Exceptions** (`TrustWeaveException`, `BlockchainException`, domain types) appear on throwing paths, **`getOrThrow()`** helpers, and some integrations. **Smart contracts** and other services often return **`Result<T>`**.

```kotlin
import org.trustweave.credential.results.IssuanceResult
import org.trustweave.trust.types.DidCreationResult
import org.trustweave.trust.types.getOrThrowDid
import org.trustweave.credential.results.getOrThrow
import org.trustweave.anchor.exceptions.BlockchainException
import org.trustweave.testkit.services.*

val did = trustWeave.createDid { method(KEY) }.getOrThrowDid()

when (val issued = trustWeave.issue { /* ... */ }) {
    is IssuanceResult.Success -> { /* use issued.credential */ }
    is IssuanceResult.Failure -> { /* handle */ }
}

try {
    trustWeave.blockchains.anchor(data, serializer, "algorand:testnet")
} catch (e: BlockchainException.ChainNotRegistered) {
    println("Available: ${e.availableChains}")
}
```

See [Error handling](../advanced/error-handling.md) and [API patterns — results vs exceptions](../getting-started/api-patterns.md#api-contract-results-vs-exceptions).

## Configuration

TrustWeave is configured during creation using a DSL:

```kotlin
import org.trustweave.testkit.services.*
val trustWeave = TrustWeave.build {
    factories(
        walletFactory = TestkitWalletFactory()
    )
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
    }
    anchor {
        chain("algorand:testnet") {
            provider(ALGORAND)
        }
        chain("polygon:mainnet") {
            provider(POLYGON)
        }
    }
}
```

## Plugin Lifecycle

Manage plugin initialization and cleanup:

```kotlin
import org.trustweave.testkit.services.*
val trustWeave = TrustWeave.build {
    keys { provider(IN_MEMORY); algorithm(ED25519) }
    did { method(KEY) { algorithm(ED25519) } }
}

// Use TrustWeave
try {
    val did = trustWeave.createDid().getOrThrowDid()
    val credential = trustWeave.issue { ... }.getOrThrow()
    // ...
} catch (error: IllegalStateException) {
    println("Error: ${error.message}")
} finally {
    trustWeave.close()  // Clean up resources
}
```

See [Plugin Lifecycle](../advanced/plugin-lifecycle.md) for detailed plugin lifecycle management.

## Next Steps

**New to TrustWeave?**
- Getting Started](../getting-started/README.md) - Installation and setup
- Quick Start](../getting-started/quick-start.md) - Your first application
- Core Concepts](../core-concepts/README.md) - Understand DIDs, credentials, and wallets
- Mental Model](../introduction/mental-model.md) - Understand how TrustWeave works

**Ready to dive deeper?**
- Error Handling](../advanced/error-handling.md) - Error handling patterns
- Plugin Lifecycle](../advanced/plugin-lifecycle.md) - Plugin lifecycle management
- Common Patterns](../getting-started/common-patterns.md) - Production-ready patterns
- Tutorials](../tutorials/README.md) - Step-by-step guides
- Use Case Scenarios](../scenarios/README.md) - Real-world examples

**Looking for specific APIs?**
- Core API](core-api.md) - TrustWeave facade API (DIDs, credentials, wallets, blockchain)
- Wallet API](wallet-api.md) - Wallet operations and capabilities
- Credential Service API](credential-service-api.md) - Credential service SPI
- Smart Contract API](smart-contract-api.md) - Smart contract operations

