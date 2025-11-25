---
title: API Reference
nav_order: 4
---

# API Reference

Complete API reference for TrustWeave.

## Core API

- **[Core API](core-api.md)**: TrustWeave facade API
  - DID operations (`dids.create()`, `dids.resolve()`, `dids.update()`, `dids.deactivate()`)
  - Credential operations (`credentials.issue()`, `credentials.verify()`)
  - Wallet operations (`wallets.create()`)
  - Blockchain anchoring (`blockchains.anchor()`, `blockchains.read()`)
  - Smart contract operations (`contracts.draft()`, `contracts.bindContract()`, `contracts.executeContract()`, etc.)
  - Plugin lifecycle management (`initialize()`, `start()`, `stop()`, `cleanup()`)
  - Error handling with `TrustWeaveError` types

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

## Error Handling

Most TrustWeave API operations throw `TrustWeaveError` exceptions on failure. Some operations (like contract operations) return `Result<T>` directly. Check the method signature for each operation.

```kotlin
import com.trustweave.core.*

try {
    val did = trustweave.dids.create()
    val credential = trustweave.credentials.issue(...)
    val wallet = trustweave.wallets.create(holderDid = did.id)
} catch (error: TrustWeaveError) {
    when (error) {
        is TrustWeaveError.DidMethodNotRegistered -> {
            println("Method not registered: ${error.method}")
            println("Available methods: ${error.availableMethods}")
        }
        is TrustWeaveError.ChainNotRegistered -> {
            println("Chain not registered: ${error.chainId}")
            println("Available chains: ${error.availableChains}")
        }
        else -> println("Error: ${error.message}")
    }
}
```

See [Error Handling](../advanced/error-handling.md) for detailed error handling patterns.

## Configuration

TrustWeave is configured during creation using a DSL:

```kotlin
val trustweave = TrustWeave.create {
    kms = InMemoryKeyManagementService()
    walletFactory = TestkitWalletFactory()
    
    didMethods {
        + DidKeyMethod()
        + DidWebMethod()
    }
    
    blockchains {
        "algorand:testnet" to algorandClient
        "polygon:mainnet" to polygonClient
    }
    
    credentialServices {
        + MyCredentialService()
    }
}
```

## Plugin Lifecycle

Manage plugin initialization and cleanup:

```kotlin
val trustweave = TrustWeave.create()

// Initialize plugins
try {
    trustweave.initialize()
    trustweave.start()
    
    // Use TrustWeave
    // ...
    
    trustweave.stop()
    trustweave.cleanup()
} catch (error: TrustWeaveError) {
    println("Plugin lifecycle error: ${error.message}")
}
```

See [Plugin Lifecycle](../advanced/plugin-lifecycle.md) for detailed plugin lifecycle management.

## Related Documentation

- [Error Handling](../advanced/error-handling.md) - Error handling patterns
- [Plugin Lifecycle](../advanced/plugin-lifecycle.md) - Plugin lifecycle management
- [Core Concepts](../core-concepts/README.md) - Core concepts and usage
- [Tutorials](../tutorials/README.md) - Step-by-step guides

