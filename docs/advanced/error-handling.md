# Error Handling

VeriCore provides structured error handling with rich context for better debugging and error recovery.

## Overview

All VeriCore API operations return `Result<T>`, which provides a consistent way to handle both success and failure cases. Errors are automatically converted to `VeriCoreError` types with structured context.

## Error Types

VeriCore uses a sealed hierarchy of error types that extend `VeriCoreException`. All errors include:
- **code**: String error code for programmatic handling
- **message**: Human-readable error message
- **context**: Map of additional context information
- **cause**: Optional underlying exception

### Complete Error Type Reference

| Error Type | Code | Properties | When It Occurs |
|------------|------|------------|----------------|
| `DidNotFound` | `DID_NOT_FOUND` | `did`, `availableMethods` | DID resolution fails |
| `DidMethodNotRegistered` | `DID_METHOD_NOT_REGISTERED` | `method`, `availableMethods` | Using unregistered DID method |
| `InvalidDidFormat` | `INVALID_DID_FORMAT` | `did`, `reason` | DID format validation fails |
| `CredentialInvalid` | `CREDENTIAL_INVALID` | `reason`, `credentialId`, `field` | Credential validation fails |
| `CredentialIssuanceFailed` | `CREDENTIAL_ISSUANCE_FAILED` | `reason`, `issuerDid` | Credential issuance fails |
| `ChainNotRegistered` | `CHAIN_NOT_REGISTERED` | `chainId`, `availableChains` | Using unregistered blockchain |
| `WalletCreationFailed` | `WALLET_CREATION_FAILED` | `reason`, `provider`, `walletId` | Wallet creation fails |
| `PluginNotFound` | `PLUGIN_NOT_FOUND` | `pluginId`, `pluginType` | Plugin lookup fails |
| `PluginInitializationFailed` | `PLUGIN_INITIALIZATION_FAILED` | `pluginId`, `reason` | Plugin initialization fails |
| `ValidationFailed` | `VALIDATION_FAILED` | `field`, `reason`, `value` | Input validation fails |
| `InvalidOperation` | `INVALID_OPERATION` | `message`, `context`, `cause` | Invalid operation attempted |
| `InvalidState` | `INVALID_STATE` | `message`, `context`, `cause` | Invalid state detected |
| `Unknown` | `UNKNOWN_ERROR` | `message`, `context`, `cause` | Unhandled exception |

### DID-Related Errors

```kotlin
// DID not found
VeriCoreError.DidNotFound(
    did = "did:key:z6Mk...",
    availableMethods = listOf("key", "web")
)

// DID method not registered
VeriCoreError.DidMethodNotRegistered(
    method = "web",
    availableMethods = listOf("key")
)

// Invalid DID format
VeriCoreError.InvalidDidFormat(
    did = "invalid-did",
    reason = "DID must match format: did:<method>:<identifier>"
)
```

### Credential-Related Errors

```kotlin
// Credential validation failed
VeriCoreError.CredentialInvalid(
    reason = "Credential issuer is required",
    credentialId = "urn:uuid:123",
    field = "issuer"
)

// Credential issuance failed
VeriCoreError.CredentialIssuanceFailed(
    reason = "Failed to sign credential",
    issuerDid = "did:key:issuer"
)
```

### Blockchain-Related Errors

```kotlin
// Chain not registered
VeriCoreError.ChainNotRegistered(
    chainId = "ethereum:mainnet",
    availableChains = listOf("algorand:testnet", "polygon:testnet")
)
```

### Wallet-Related Errors

```kotlin
// Wallet creation failed
VeriCoreError.WalletCreationFailed(
    reason = "Provider not found",
    provider = "database",
    walletId = "wallet-123"
)
```

### Plugin-Related Errors

```kotlin
// Plugin not found
VeriCoreError.PluginNotFound(
    pluginId = "waltid-credential",
    pluginType = "credential-service"
)

// Plugin initialization failed
VeriCoreError.PluginInitializationFailed(
    pluginId = "waltid-credential",
    reason = "Configuration missing"
)
```

### Validation Errors

```kotlin
// Validation failed
VeriCoreError.ValidationFailed(
    field = "issuer",
    reason = "Invalid DID format",
    value = "invalid-did"
)
```

### Generic Errors

```kotlin
// Invalid operation
VeriCoreError.InvalidOperation(
    code = "INVALID_OPERATION",
    message = "Operation not allowed in current state",
    context = mapOf("operation" to "createDid", "state" to "stopped"),
    cause = null
)

// Invalid state
VeriCoreError.InvalidState(
    code = "INVALID_STATE",
    message = "VeriCore not initialized",
    context = emptyMap(),
    cause = null
)

// Unknown error (catch-all)
VeriCoreError.Unknown(
    code = "UNKNOWN_ERROR",
    message = "Unexpected error occurred",
    context = emptyMap(),
    cause = originalException
)
```

## Error Handling Patterns

### Basic Error Handling

```kotlin
import com.geoknoesis.vericore.VeriCore
import com.geoknoesis.vericore.core.*

val vericore = VeriCore.create()

// Handle errors with fold
val result = vericore.createDid()
result.fold(
    onSuccess = { did -> 
        println("Created DID: ${did.id}")
    },
    onFailure = { error ->
        when (error) {
            is VeriCoreError.DidMethodNotRegistered -> {
                println("Method not registered: ${error.method}")
                println("Available methods: ${error.availableMethods}")
            }
            else -> {
                println("Error: ${error.message}")
                error.context.forEach { (key, value) ->
                    println("  $key: $value")
                }
            }
        }
    }
)
```

### Using getOrThrow for Simple Cases

```kotlin
// For simple cases where you want to throw on error
val did = vericore.createDid().getOrThrow()

// For better error messages, use getOrThrowError
val did = vericore.createDid().getOrThrowError() // Throws VeriCoreError
```

### Error Context

All errors include context information that can help with debugging:

```kotlin
val result = vericore.anchor(data, serializer, "ethereum:mainnet")
result.fold(
    onSuccess = { anchor -> println("Anchored: ${anchor.ref.txHash}") },
    onFailure = { error ->
        when (error) {
            is VeriCoreError.ChainNotRegistered -> {
                println("Chain ID: ${error.chainId}")
                println("Available chains: ${error.availableChains}")
                println("Context: ${error.context}")
            }
            else -> println("Error: ${error.message}")
        }
    }
)
```

### Converting Exceptions to Errors

VeriCore automatically converts exceptions to `VeriCoreError`:

```kotlin
import com.geoknoesis.vericore.core.toVeriCoreError

try {
    // Some operation that might throw
    val result = someOperation()
} catch (e: Exception) {
    val error = e.toVeriCoreError()
    println("Error code: ${error.code}")
    println("Context: ${error.context}")
}
```

## Result Utilities

VeriCore provides extension functions for working with `Result<T>`:

### mapError

Transform errors in a Result:

```kotlin
val result = vericore.createDid()
    .mapError { it.toVeriCoreError() }
```

### combine

Combine multiple Results:

```kotlin
val results = listOf(
    vericore.createDid(),
    vericore.createDid(),
    vericore.createDid()
)

val combined = results.combine { dids ->
    dids.map { it.id }
}

combined.fold(
    onSuccess = { ids -> println("Created DIDs: $ids") },
    onFailure = { error -> println("Error: ${error.message}") }
)
```

### mapAsync

Batch operations with async mapping:

```kotlin
val dids = listOf("did:key:1", "did:key:2", "did:key:3")
val results = dids.mapAsync { did ->
    vericore.resolveDid(did)
}

results.fold(
    onSuccess = { resolutions -> 
        resolutions.forEach { println("Resolved: ${it.document?.id}") }
    },
    onFailure = { error -> println("Error: ${error.message}") }
)
```

## Input Validation

VeriCore validates inputs before operations to catch errors early:

### DID Validation

```kotlin
import com.geoknoesis.vericore.core.DidValidator

// Validate DID format
val validation = DidValidator.validateFormat("did:key:z6Mk...")
if (!validation.isValid()) {
    val error = validation as ValidationResult.Invalid
    println("Validation failed: ${error.message}")
    println("Field: ${error.field}")
    println("Value: ${error.value}")
}

// Validate DID method
val availableMethods = listOf("key", "web")
val methodValidation = DidValidator.validateMethod("did:key:z6Mk...", availableMethods)
if (!methodValidation.isValid()) {
    println("Method not supported")
}
```

### Credential Validation

```kotlin
import com.geoknoesis.vericore.core.CredentialValidator

// Validate credential structure
val validation = CredentialValidator.validateStructure(credential)
if (!validation.isValid()) {
    val error = validation as ValidationResult.Invalid
    println("Credential validation failed: ${error.message}")
    println("Field: ${error.field}")
}

// Validate proof
val proofValidation = CredentialValidator.validateProof(credential)
if (!proofValidation.isValid()) {
    println("Credential missing proof")
}
```

### Chain ID Validation

```kotlin
import com.geoknoesis.vericore.core.ChainIdValidator

// Validate chain ID format
val validation = ChainIdValidator.validateFormat("algorand:testnet")
if (!validation.isValid()) {
    println("Invalid chain ID format")
}

// Validate chain is registered
val availableChains = listOf("algorand:testnet", "polygon:testnet")
val registeredValidation = ChainIdValidator.validateRegistered("ethereum:mainnet", availableChains)
if (!registeredValidation.isValid()) {
    println("Chain not registered")
}
```

## Best Practices

### 1. Always Handle Errors

```kotlin
// ❌ Bad: Ignoring errors
val did = vericore.createDid().getOrThrow()

// ✅ Good: Handling errors explicitly
val result = vericore.createDid()
result.fold(
    onSuccess = { did -> /* handle success */ },
    onFailure = { error -> /* handle error */ }
)
```

### 2. Use Error Context

```kotlin
// ✅ Good: Use error context for debugging
val result = vericore.anchor(data, serializer, chainId)
result.fold(
    onSuccess = { /* success */ },
    onFailure = { error ->
        logger.error("Anchoring failed", error)
        logger.debug("Error context: ${error.context}")
        logger.debug("Error code: ${error.code}")
    }
)
```

### 3. Check Error Types

```kotlin
// ✅ Good: Handle specific error types
val result = vericore.createDid(method = "web")
result.fold(
    onSuccess = { /* success */ },
    onFailure = { error ->
        when (error) {
            is VeriCoreError.DidMethodNotRegistered -> {
                // Suggest available methods
                println("Method 'web' not available. Try: ${error.availableMethods}")
            }
            is VeriCoreError.InvalidDidFormat -> {
                // Show format requirements
                println("Invalid format: ${error.reason}")
            }
            else -> {
                // Generic error handling
                println("Error: ${error.message}")
            }
        }
    }
)
```

### 4. Validate Inputs Early

```kotlin
// ✅ Good: Validate before operation
val did = "did:key:z6Mk..."
val validation = DidValidator.validateFormat(did)
if (!validation.isValid()) {
    return Result.failure(VeriCoreError.InvalidDidFormat(did, validation.errorMessage() ?: ""))
}

val result = vericore.resolveDid(did)
```

### 5. Use Result Utilities

```kotlin
// ✅ Good: Use combine for batch operations
val dids = listOf("did:key:1", "did:key:2", "did:key:3")
val results = dids.map { vericore.resolveDid(it) }

val combined = results.combine { resolutions ->
    resolutions.mapNotNull { it.document?.id }
}

combined.fold(
    onSuccess = { ids -> println("Resolved: $ids") },
    onFailure = { error -> println("Error: ${error.message}") }
)
```

## Plugin Lifecycle Errors

When managing plugin lifecycles, errors are handled automatically:

```kotlin
val vericore = VeriCore.create()

// Initialize plugins
vericore.initialize().fold(
    onSuccess = { println("Plugins initialized") },
    onFailure = { error ->
        when (error) {
            is VeriCoreError.PluginInitializationFailed -> {
                println("Plugin ${error.pluginId} failed to initialize: ${error.reason}")
            }
            else -> println("Error: ${error.message}")
        }
    }
)

// Start plugins
vericore.start().fold(
    onSuccess = { println("Plugins started") },
    onFailure = { error -> println("Error starting plugins: ${error.message}") }
)

// Stop plugins
vericore.stop().fold(
    onSuccess = { println("Plugins stopped") },
    onFailure = { error -> println("Error stopping plugins: ${error.message}") }
)

// Cleanup plugins
vericore.cleanup().fold(
    onSuccess = { println("Plugins cleaned up") },
    onFailure = { error -> println("Error cleaning up: ${error.message}") }
)
```

## Migration Guide

If you're migrating from exception-based error handling to Result-based:

### Before (Exception-based)

```kotlin
try {
    val did = vericore.createDid()
    val credential = vericore.issueCredential(...)
} catch (e: IllegalArgumentException) {
    println("Invalid argument: ${e.message}")
} catch (e: Exception) {
    println("Error: ${e.message}")
}
```

### After (Result-based)

```kotlin
val didResult = vericore.createDid()
didResult.fold(
    onSuccess = { did ->
        val credentialResult = vericore.issueCredential(...)
        credentialResult.fold(
            onSuccess = { credential -> /* success */ },
            onFailure = { error -> /* handle error */ }
        )
    },
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

## Related Documentation

- [API Reference](../api-reference/)
- [Verification Policies](verification-policies.md)
- [Plugin Lifecycle](../advanced/plugin-lifecycle.md)

