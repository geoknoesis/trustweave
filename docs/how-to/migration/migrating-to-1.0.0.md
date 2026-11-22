---
title: Migrating to TrustWeave 1.0.0
redirect_from:
  - /migration/migrating-to-1.0.0/
parent: Migration Guides
grand_parent: How-To Guides
---

# Migrating to TrustWeave 1.0.0 (Planned)

> **Release note:** TrustWeave `1.0.0` is **not yet released**; the current release line is **`0.7.0`**. If you are upgrading from `0.6.x` to `0.7.0`, see [Migrating to TrustWeave 0.7.0](migrating-to-0.7.0.md). The patterns below describe the modern `0.7.0+` API surface targeting `1.0.0` stability.

## Overview of Changes

The modern TrustWeave API surface introduces several improvements:

- **Type-safe options**: Replaces map-based configuration with typed options classes
- **Sealed result APIs**: Core domain operations return TrustWeave sealed result types (`IssuanceResult`, `VerificationResult`, `DidCreationResult`, `RevocationResult`, `TrustOperationResult`, `KeyIdResult`, `SignResult`, etc.) for exhaustive `when` handling
- **Enhanced error types**: Structured `TrustWeaveException` hierarchy with rich context
- **Resource lifecycle**: Explicit `close()` / `closeAsync()` lifecycle management on `TrustWeave`

## Migration Checklist

- Update dependencies to `0.7.0` using `implementation(platform("org.trustweave:distribution-bom:0.7.0"))`
- Migrate to type-safe options
- Update error handling to use TrustWeave sealed result types (e.g., `revokeResult { }`, `trustResult { }`, `getKeyIdResult(...)`)
- Close `TrustWeave` instances on shutdown (`close()` / `closeAsync()`)
- Test thoroughly
- Update documentation references

## 1. Migrating to Type-Safe Options

### Before (Map-based)

```kotlin
val client = AlgorandBlockchainAnchorClient(
    chainId = "algorand:testnet",
    options = mapOf(
        "algodUrl" to "https://testnet-api.algonode.cloud",
        "privateKey" to "base64-key"
    )
)
```

### After (Type-safe)

```kotlin
import org.trustweave.anchor.options.AlgorandOptions
import org.trustweave.anchor.ChainId

val chainId = ChainId.Algorand.Testnet
val options = AlgorandOptions(
    algodUrl = "https://testnet-api.algonode.cloud",
    privateKey = "base64-key"
)
val client = AlgorandBlockchainAnchorClient(chainId.toString(), options)
```

**Benefits:**
- Compile-time validation
- Better IDE autocomplete
- Type safety prevents configuration errors

## 2. Migrating to Result-Based APIs

### Before (Exception-based)

```kotlin
try {
    val result = client.writePayload(payload)
    println("Anchored: ${result.ref.txHash}")
} catch (e: TrustWeaveException) {
    println("Error: ${e.message}")
}
```

### After (Result-based)

Anchoring is exposed through the **`trustWeave.blockchains`** service (a `BlockchainService`). `anchor(...)` is `suspend` and returns an `AnchorResult` on success — failures surface as typed `BlockchainException` subclasses. Wrap with `runCatching` to use a `Result<T>` flow:

```kotlin
import org.trustweave.anchor.exceptions.BlockchainException

val result = runCatching {
    trustWeave.blockchains.anchor(data, serializer, chainId)
}
result.fold(
    onSuccess = { anchor ->
        println("Anchored: ${anchor.ref.txHash}")
    },
    onFailure = { error ->
        when (error) {
            is BlockchainException.ChainNotRegistered -> {
                println("Chain not registered: ${error.chainId}")
                println("Available chains: ${error.availableChains}")
            }
            else -> println("Error: ${error.message}")
        }
    }
)
```

**Benefits:**
- Explicit error handling
- Structured error types
- Better error context

## 3. Migrating Error Handling

### Before

```kotlin
try {
    val did = TrustWeave.createDid()
} catch (e: IllegalArgumentException) {
    println("Invalid argument: ${e.message}")
} catch (e: Exception) {
    println("Error: ${e.message}")
}
```

### After

`TrustWeave.createDid(...)` is a `suspend` method that returns the sealed `DidCreationResult`. Pattern-match instead of calling `.fold` on a `Result<T>`:

```kotlin
import org.trustweave.trust.quickStart
import org.trustweave.trust.types.DidCreationResult

val trustWeave = TrustWeave.quickStart()

when (val result = trustWeave.createDid()) {
    is DidCreationResult.Success -> println("Created: ${result.did}")
    is DidCreationResult.Failure.MethodNotRegistered -> {
        println("Method not registered: ${result.method}")
        println("Available methods: ${result.availableMethods}")
    }
    is DidCreationResult.Failure -> println("Error: $result")
}
```

## 4. Migrating Plugin Lifecycle

### Before

```kotlin
// No explicit lifecycle management
val trustWeave = TrustWeave.quickStart()
// Use immediately; resources released when GC’d (not ideal for production)
```

### After

```kotlin
import org.trustweave.trust.quickStart
import kotlinx.coroutines.runBlocking
import org.trustweave.trust.TrustWeave

fun main() = runBlocking {
    val trustWeave = TrustWeave.quickStart()
    try {
        // Use trustWeave
    } finally {
        trustWeave.close()
    }
}
```

If your own adapters implement **`PluginLifecycle`**, call your hooks from the same composition root (see [Plugin lifecycle](../../api-reference/advanced/plugin-lifecycle.md)). The **`TrustWeave`** facade does not expose **`initialize()`** / **`start()`** / **`stop()`**.

## 5. Migrating Type-Safe Chain IDs

### Before

```kotlin
val chainId = "algorand:testnet"  // Typo-prone string
```

### After

```kotlin
import org.trustweave.anchor.ChainId

val chainId = ChainId.Algorand.Testnet  // Compile-time safe
// Or use string with validation
val chainId = "algorand:testnet"  // Still supported, but less safe
```

## Common Migration Issues

### Issue 1: Missing Error Handling

**Problem**: Code assumes operations always succeed

**Solution**: Handle sealed result variants exhaustively with `when` (use `getOrThrow()` only in tests or simple scripts)

### Issue 2: Type Mismatches

**Problem**: Using old map-based options with new APIs

**Solution**: Migrate to type-safe options classes

### Issue 3: Missing Lifecycle Calls

**Problem**: Plugins not initialized before use

**Solution**: The `TrustWeave` facade does not expose `initialize()` / `start()` / `stop()`. Build it via `TrustWeave.build { … }` (or `TrustWeave.quickStart()`) and call `trustWeave.close()` on shutdown. If your own adapters implement `PluginLifecycle`, drive their hooks from the same composition root — see [Plugin lifecycle](../../api-reference/advanced/plugin-lifecycle.md).

## Testing Your Migration

1. **Run Tests**: Ensure all tests pass with new APIs
2. **Check Error Handling**: Verify error handling works correctly
3. **Validate Configuration**: Ensure type-safe options are configured correctly
4. **Test Lifecycle**: Verify plugin lifecycle is managed properly

## Getting Help

If you encounter issues during migration:

1. Check [Error Handling](../../api-reference/advanced/error-handling.md) for error patterns
2. Review [API Reference](../../api-reference/core-api.md) for API changes
3. Open an issue on GitHub with migration details
4. Contact support at [www.geoknoesis.com](https://www.geoknoesis.com)

