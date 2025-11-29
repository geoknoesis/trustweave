---
title: Migrating to TrustWeave 1.0.0
---

# Migrating to TrustWeave 1.0.0

This guide helps you migrate from earlier versions of TrustWeave to version 1.0.0.

## Overview of Changes

Version 1.0.0 introduces several improvements:

- **Type-safe options**: Replaces map-based configuration with typed options classes
- **Result-based APIs**: All operations return `Result<T>` for consistent error handling
- **Enhanced error types**: Structured error hierarchy with rich context
- **Plugin lifecycle**: New lifecycle management APIs

## Migration Checklist

- [ ] Update dependencies to 1.0.0-SNAPSHOT
- [ ] Migrate to type-safe options
- [ ] Update error handling to use `Result<T>`
- [ ] Update plugin lifecycle calls
- [ ] Test thoroughly
- [ ] Update documentation references

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
import com.trustweave.anchor.options.AlgorandOptions
import com.trustweave.anchor.ChainId

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

```kotlin
val result = TrustWeave.anchor(data, serializer, chainId)
result.fold(
    onSuccess = { anchor ->
        println("Anchored: ${anchor.ref.txHash}")
    },
    onFailure = { error ->
        when (error) {
            is TrustWeaveError.ChainNotRegistered -> {
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

```kotlin
val result = TrustWeave.createDid()
result.fold(
    onSuccess = { did ->
        println("Created: ${did.id}")
    },
    onFailure = { error ->
        when (error) {
            is TrustWeaveError.DidMethodNotRegistered -> {
                println("Method not registered: ${error.method}")
                println("Available methods: ${error.availableMethods}")
            }
            is TrustWeaveError.InvalidDidFormat -> {
                println("Invalid format: ${error.reason}")
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

## 4. Migrating Plugin Lifecycle

### Before

```kotlin
// No explicit lifecycle management
val TrustWeave = TrustWeave.create()
// Use immediately
```

### After

```kotlin
val TrustWeave = TrustWeave.create()

// Initialize plugins
TrustWeave.initialize().fold(
    onSuccess = { println("Plugins initialized") },
    onFailure = { error ->
        println("Initialization error: ${error.message}")
    }
)

// Start plugins
TrustWeave.start().fold(
    onSuccess = { println("Plugins started") },
    onFailure = { error -> println("Start error: ${error.message}") }
)

// Use TrustWeave
// ...

// Stop plugins (cleanup)
TrustWeave.stop().fold(
    onSuccess = { println("Plugins stopped") },
    onFailure = { error -> println("Stop error: ${error.message}") }
)
```

## 5. Migrating Type-Safe Chain IDs

### Before

```kotlin
val chainId = "algorand:testnet"  // Typo-prone string
```

### After

```kotlin
import com.trustweave.anchor.ChainId

val chainId = ChainId.Algorand.Testnet  // Compile-time safe
// Or use string with validation
val chainId = "algorand:testnet"  // Still supported, but less safe
```

## Common Migration Issues

### Issue 1: Missing Error Handling

**Problem**: Code assumes operations always succeed

**Solution**: Always handle `Result<T>` with `fold()` or `getOrThrow()` (only for tests)

### Issue 2: Type Mismatches

**Problem**: Using old map-based options with new APIs

**Solution**: Migrate to type-safe options classes

### Issue 3: Missing Lifecycle Calls

**Problem**: Plugins not initialized before use

**Solution**: Call `initialize()` and `start()` before using TrustWeave

## Testing Your Migration

1. **Run Tests**: Ensure all tests pass with new APIs
2. **Check Error Handling**: Verify error handling works correctly
3. **Validate Configuration**: Ensure type-safe options are configured correctly
4. **Test Lifecycle**: Verify plugin lifecycle is managed properly

## Getting Help

If you encounter issues during migration:

1. Check [Error Handling](../advanced/error-handling.md) for error patterns
2. Review [API Reference](../api-reference/core-api.md) for API changes
3. Open an issue on GitHub with migration details
4. Contact support at [www.geoknoesis.com](https://www.geoknoesis.com)

