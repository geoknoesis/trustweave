# API Reference

Complete API reference for VeriCore.

## Core API

- **[Core API](core-api.md)**: VeriCore facade API
  - DID operations (`createDid()`, `resolveDid()`)
  - Credential operations (`issueCredential()`, `verifyCredential()`)
  - Wallet operations (`createWallet()`)
  - Blockchain anchoring (`anchor()`, `readAnchor()`)
  - Plugin lifecycle management (`initialize()`, `start()`, `stop()`, `cleanup()`)
  - Error handling with `VeriCoreError` types
  - Input validation utilities
  - Result utilities

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

## Error Handling

All VeriCore API operations return `Result<T>` for consistent error handling:

```kotlin
import com.geoknoesis.vericore.core.*

val result = vericore.createDid()
result.fold(
    onSuccess = { did -> println("Created: ${did.id}") },
    onFailure = { error ->
        when (error) {
            is VeriCoreError.DidMethodNotRegistered -> {
                println("Method not registered: ${error.method}")
            }
            else -> println("Error: ${error.message}")
        }
    }
)
```

See [Error Handling](../advanced/error-handling.md) for detailed error handling patterns.

## Input Validation

VeriCore validates inputs before operations:

```kotlin
import com.geoknoesis.vericore.core.*

// Validate DID format
val validation = DidValidator.validateFormat("did:key:z6Mk...")
if (!validation.isValid()) {
    println("Invalid DID format")
}

// Validate credential structure
val validation = CredentialValidator.validateStructure(credential)
if (!validation.isValid()) {
    println("Credential validation failed")
}

// Validate chain ID
val validation = ChainIdValidator.validateFormat("algorand:testnet")
if (!validation.isValid()) {
    println("Invalid chain ID format")
}
```

## Plugin Lifecycle

Manage plugin initialization and cleanup:

```kotlin
val vericore = VeriCore.create()

// Initialize plugins
vericore.initialize().getOrThrow()

// Start plugins
vericore.start().getOrThrow()

// Use VeriCore
// ...

// Stop plugins
vericore.stop().getOrThrow()

// Cleanup plugins
vericore.cleanup().getOrThrow()
```

See [Plugin Lifecycle](../advanced/plugin-lifecycle.md) for detailed plugin lifecycle management.

## Related Documentation

- [Error Handling](../advanced/error-handling.md) - Error handling patterns
- [Plugin Lifecycle](../advanced/plugin-lifecycle.md) - Plugin lifecycle management
- [Core Concepts](../core-concepts/README.md) - Core concepts and usage
- [Tutorials](../tutorials/README.md) - Step-by-step guides

