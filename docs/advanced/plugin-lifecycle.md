---
title: Plugin Lifecycle Management
nav_exclude: true
---

# Plugin Lifecycle Management

TrustWeave lets **your own integrations** implement **`PluginLifecycle`** when they need explicit startup/shutdown hooks (connections, background work, etc.).

> **Important:** The **`TrustWeave`** facade does **not** expose **`initialize()` / `start()` / `stop()` / `cleanup()`**. Normal apps use **`TrustWeave.build { }`** / **`TrustWeave.quickStart()`** and handle errors via **sealed results** and **domain exceptions**. The sections below describe **`PluginLifecycle` on custom components**, not methods on **`TrustWeave`**.

## Overview

Plugins that implement `PluginLifecycle` can be initialized, started, stopped, and cleaned up **by your application** (or a host container). This is useful for components that need to:

- Initialize connections or resources
- Start background processes
- Clean up resources on shutdown
- Manage plugin state

## When Do You Need Lifecycle Methods?

**You typically DON'T need lifecycle methods for:**

- In-memory implementations (`InMemoryKeyManagementService`, in-memory wallets, etc.)
- Simple test scenarios and quick starts
- Most default `TrustWeave.quickStart()` / `TrustWeave.build` setups

**You DO need lifecycle methods when:**

- Database-backed services (connection pools, migrations)
- Remote services (opening clients before first use)
- File-based storage (creating directories, locks)
- Long-lived blockchain or KMS adapters that need explicit connect/disconnect
- Any component that must release resources on shutdown

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
// In-memory TrustWeave — no PluginLifecycle calls required
val trustWeave = TrustWeave.quickStart()
val did = trustWeave.createDid { }.getOrThrowDid()
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

### Initialize (on your `PluginLifecycle` component)

Call **`initialize`** on **your** plugin instance (wallet factory, remote adapter, etc.), not on **`TrustWeave`**:

```kotlin
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

if (!myDatabaseBackedPlugin.initialize(config)) {
    error("Plugin failed to initialize")
}
```

### Start / stop / cleanup

Likewise, invoke **`start()`**, **`stop()`**, and **`cleanup()`** on implementations that need them (e.g. long-lived connections). The stock **`TrustWeave`** facade has no global **`TrustWeave.start()`** API.

## Complete lifecycle example (custom plugin + facade)

```kotlin
import org.trustweave.trust.TrustWeave
import org.trustweave.trust.dsl.credential.DidMethods.KEY
import org.trustweave.trust.dsl.credential.KeyAlgorithms.ED25519
import org.trustweave.trust.dsl.credential.KmsProviders.IN_MEMORY
import org.trustweave.trust.types.getOrThrowDid

suspend fun main() {
    val dbPlugin = MyDatabaseBackedPlugin() // implements PluginLifecycle
    dbPlugin.initialize(mapOf("databaseUrl" to "jdbc:postgresql://localhost/app"))
    dbPlugin.start()

    try {
        val trustWeave = TrustWeave.build {
            keys { provider(IN_MEMORY); algorithm(ED25519) }
            did { method(KEY) { algorithm(ED25519) } }
        }
        val did = trustWeave.createDid { }.getOrThrowDid()
        println("Created DID: ${did.value}")
    } finally {
        dbPlugin.stop()
        dbPlugin.cleanup()
    }
}
```

## Implementing PluginLifecycle

To implement lifecycle management in your plugin:

```kotlin
import org.trustweave.core.plugin.PluginLifecycle

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

`PluginLifecycle.initialize`, `start`, and `stop` return `Boolean` (`true` = success), and `cleanup` returns `Unit`. There is no `TrustWeave.initialize()` API — handle plugin failures yourself when wiring custom lifecycle-aware components:

```kotlin
import org.trustweave.core.exception.PluginException

val plugin = MyDatabaseBackedPlugin()
try {
    if (!plugin.initialize(configMap)) {
        throw PluginException.InitializationFailed(
            pluginId = "my-database-plugin",
            reason = "initialize() returned false"
        )
    }
    if (!plugin.start()) {
        throw PluginException.InitializationFailed(
            pluginId = "my-database-plugin",
            reason = "start() returned false"
        )
    }
} catch (e: PluginException.InitializationFailed) {
    println("Plugin ${e.pluginId} failed: ${e.reason}")
}
```

## Best Practices

### 1. Initialize your `PluginLifecycle` components before use

```kotlin
val plugin = MyRemoteAnchorClient()
check(plugin.initialize(configMap)) { "plugin init failed" }
plugin.start()
```

### 2. Use try/finally for cleanup

```kotlin
val plugin = MyRemoteAnchorClient()
plugin.initialize(emptyMap())
plugin.start()
try {
    val trustWeave = TrustWeave.build { /* uses plugin via registry if registered */ }
    // ...
} finally {
    plugin.stop()
    plugin.cleanup()
}
```

### 3. Surface failures clearly

Return **`false`** or throw **`PluginException.InitializationFailed`** from registration paths so callers do not proceed with half-initialized dependencies.

### 4. Implement lifecycle methods safely

```kotlin
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

- Error Handling](error-handling.md)
- Service Provider Interface](spi.md)
- API Reference](../api-reference/)

