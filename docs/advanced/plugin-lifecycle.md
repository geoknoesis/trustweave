---
title: Plugin Lifecycle Management
nav_exclude: true
---

# Plugin Lifecycle Management

TrustWeave provides lifecycle management for plugins that implement the `PluginLifecycle` interface.

## Overview

Plugins that implement `PluginLifecycle` can be initialized, started, stopped, and cleaned up through the TrustWeave facade. This is useful for plugins that need to:

- Initialize connections or resources
- Start background processes
- Clean up resources on shutdown
- Manage plugin state

## When Do You Need Lifecycle Methods?

**You typically DON'T need lifecycle methods for:**
- ✅ In-memory implementations (`InMemoryKeyManagementService`, `InMemoryWallet`, etc.)
- ✅ Simple test scenarios
- ✅ Quick start examples
- ✅ Most default TrustWeave configurations

**You DO need lifecycle methods when:**
- 🔧 Using database-backed services (need connection initialization)
- 🔧 Using remote services (need connection establishment)
- 🔧 Using file-based storage (need directory creation)
- 🔧 Using blockchain clients (need network connection setup)
- 🔧 Any plugin that requires external resources
- 🔧 Production deployments with persistent storage

**Example - When Lifecycle is Needed:**
```kotlin
// Database-backed wallet factory needs initialization
class DatabaseWalletFactory : WalletFactory, PluginLifecycle {
    private var connection: Connection? = null
    
    override suspend fun initialize(config: Map<String, Any?>): Boolean {
        val url = config["databaseUrl"] as? String ?: return false
        connection = DriverManager.getConnection(url)
        return true
    }
    
    override suspend fun start(): Boolean {
        // Start connection pool, background threads, etc.
        return connection != null
    }
    
    override suspend fun stop(): Boolean {
        // Stop background processes
        return true
    }
    
    override suspend fun cleanup() {
        connection?.close()
        connection = null
    }
    
    // ... implement WalletFactory methods ...
}
```

**Example - When Lifecycle is NOT Needed:**
```kotlin
// In-memory implementations don't need lifecycle
val TrustWeave = TrustWeave.create() // Uses InMemoryKeyManagementService
// No need to call initialize() or start()
val did = TrustWeave.dids.create() // Works immediately
```

## Plugin Lifecycle Interface

```kotlin
interface PluginLifecycle {
    suspend fun initialize(config: Map<String, Any?>): Boolean
    suspend fun start(): Boolean
    suspend fun stop(): Boolean
    suspend fun cleanup()
}
```

## Lifecycle Methods

### Initialize

Initialize plugins with configuration:

```kotlin
val TrustWeave = TrustWeave.create()

val config = mapOf(
    "database" to mapOf(
        "url" to "jdbc:postgresql://localhost/TrustWeave",
        "username" to "TrustWeave",
        "password" to "secret"
    ),
    "cache" to mapOf(
        "enabled" to true,
        "ttl" to 3600
    )
)

TrustWeave.initialize(config).fold(
    onSuccess = { 
        println("All plugins initialized successfully")
    },
    onFailure = { error ->
        when (error) {
            is TrustWeaveError.PluginInitializationFailed -> {
                println("Plugin ${error.pluginId} failed to initialize: ${error.reason}")
            }
            else -> {
                println("Initialization error: ${error.message}")
            }
        }
    }
)
```

### Start

Start plugins after initialization:

```kotlin
TrustWeave.start().fold(
    onSuccess = { 
        println("All plugins started successfully")
    },
    onFailure = { error ->
        println("Error starting plugins: ${error.message}")
    }
)
```

### Stop

Stop plugins before shutdown:

```kotlin
TrustWeave.stop().fold(
    onSuccess = { 
        println("All plugins stopped successfully")
    },
    onFailure = { error ->
        println("Error stopping plugins: ${error.message}")
    }
)
```

### Cleanup

Clean up plugin resources:

```kotlin
TrustWeave.cleanup().fold(
    onSuccess = { 
        println("All plugins cleaned up successfully")
    },
    onFailure = { error ->
        println("Error cleaning up plugins: ${error.message}")
    }
)
```

## Complete Lifecycle Example

```kotlin
import com.trustweave.TrustWeave
import com.trustweave.core.*

suspend fun main() {
    // Create TrustWeave instance
    val TrustWeave = TrustWeave.create {
        // Configure plugins
        registerDidMethod(MyDidMethod())
        registerBlockchainClient("algorand:testnet", myClient)
    }
    
    try {
        // Initialize plugins
        TrustWeave.initialize().getOrThrow()
        println("Plugins initialized")
        
        // Start plugins
        TrustWeave.start().getOrThrow()
        println("Plugins started")
        
        // Use TrustWeave
        val did = TrustWeave.dids.create()
        println("Created DID: ${did.id}")
        
        // ... use TrustWeave ...
        
    } finally {
        // Stop plugins
        TrustWeave.stop().getOrThrow()
        println("Plugins stopped")
        
        // Cleanup plugins
        TrustWeave.cleanup().getOrThrow()
        println("Plugins cleaned up")
    }
}
```

## Implementing PluginLifecycle

To implement lifecycle management in your plugin:

```kotlin
import com.trustweave.spi.PluginLifecycle

class MyBlockchainClient : BlockchainAnchorClient, PluginLifecycle {
    private var initialized = false
    private var started = false
    private var connection: Connection? = null
    
    override suspend fun initialize(config: Map<String, Any?>): Boolean {
        return try {
            val url = config["url"] as? String ?: return false
            connection = createConnection(url)
            initialized = true
            true
        } catch (e: Exception) {
            false
        }
    }
    
    override suspend fun start(): Boolean {
        return if (initialized && connection != null) {
            connection?.connect()
            started = true
            true
        } else {
            false
        }
    }
    
    override suspend fun stop(): Boolean {
        return try {
            connection?.disconnect()
            started = false
            true
        } catch (e: Exception) {
            false
        }
    }
    
    override suspend fun cleanup() {
        connection?.close()
        connection = null
        initialized = false
    }
    
    // ... implement BlockchainAnchorClient methods ...
}
```

## Automatic Plugin Discovery

TrustWeave automatically discovers plugins that implement `PluginLifecycle` from:

- Key Management Services (KMS)
- Wallet Factories
- DID Methods
- Blockchain Clients
- Credential Services
- Proof Generators

Plugins are initialized in the order they are registered, and stopped in reverse order.

## Error Handling

Lifecycle methods return `Result<Unit>` for error handling:

```kotlin
val result = TrustWeave.initialize()
result.fold(
    onSuccess = { 
        println("Initialization successful")
    },
    onFailure = { error ->
        when (error) {
            is TrustWeaveError.PluginInitializationFailed -> {
                println("Plugin ${error.pluginId} failed: ${error.reason}")
                // Handle specific plugin failure
            }
            else -> {
                println("Initialization error: ${error.message}")
            }
        }
    }
)
```

## Best Practices

### 1. Always Initialize Before Use

```kotlin
// ✅ Good: Initialize before use
val TrustWeave = TrustWeave.create()
TrustWeave.initialize().getOrThrow()
TrustWeave.start().getOrThrow()

// Use TrustWeave
val did = TrustWeave.createDid().getOrThrow()
```

### 2. Use Try-Finally for Cleanup

```kotlin
// ✅ Good: Always cleanup
val TrustWeave = TrustWeave.create()
try {
    TrustWeave.initialize().getOrThrow()
    TrustWeave.start().getOrThrow()
    
    // Use TrustWeave
    // ...
} finally {
    TrustWeave.stop().getOrThrow()
    TrustWeave.cleanup().getOrThrow()
}
```

### 3. Handle Initialization Errors

```kotlin
// ✅ Good: Handle initialization errors
val result = TrustWeave.initialize()
if (result.isFailure) {
    println("Initialization failed: ${result.exceptionOrNull()?.message}")
    // Handle error or exit
    return
}

TrustWeave.start().getOrThrow()
```

### 4. Implement Lifecycle Methods Properly

```kotlin
// ✅ Good: Proper lifecycle implementation
override suspend fun initialize(config: Map<String, Any?>): Boolean {
    return try {
        // Initialize resources
        // Return true on success, false on failure
        true
    } catch (e: Exception) {
        false
    }
}

override suspend fun start(): Boolean {
    // Start services
    // Return true on success, false on failure
    return true
}

override suspend fun stop(): Boolean {
    // Stop services
    // Return true on success, false on failure
    return true
}

override suspend fun cleanup() {
    // Cleanup resources
    // Don't throw exceptions
}
```

## Related Documentation

- [Error Handling](error-handling.md)
- [Service Provider Interface](spi.md)
- [API Reference](../api-reference/)

