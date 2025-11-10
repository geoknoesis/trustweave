# VeriCore Anchor Package

## Overview

The `io.geoknoesis.vericore.anchor` package provides chain-agnostic interfaces and utilities for blockchain anchoring operations with comprehensive type safety, error handling, and code reuse.

## Package Structure

### Core Types
- **`BlockchainAnchorClient`**: Main interface for blockchain anchoring operations
- **`AbstractBlockchainAnchorClient`**: Base class reducing code duplication across adapters (~40% reduction)
- **`AnchorRef`**: Reference to a blockchain anchor (chain ID + transaction hash)
- **`AnchorResult`**: Result of anchoring operations
Use `BlockchainAnchorRegistry` instances (or `VeriCoreContext.blockchainRegistry()`) to manage clients explicitly.
- **`ChainId`**: Type-safe chain identifier (CAIP-2 format) with compile-time validation

### Helper Functions
- **`anchorTyped`**: Type-safe helper for anchoring serializable values
- **`readTyped`**: Type-safe helper for reading anchored values

### Subpackages

#### `exceptions`
Structured exception hierarchy with rich error context:
- **`BlockchainException`**: Base exception with chain ID and operation context
- **`BlockchainTransactionException`**: Transaction failures (includes txHash, payloadSize, gasUsed)
- **`BlockchainConnectionException`**: Connection failures (includes endpoint)
- **`BlockchainConfigurationException`**: Configuration errors (includes config key)
- **`BlockchainUnsupportedOperationException`**: Unsupported operations

#### `options`
Type-safe configuration options (sealed class hierarchy):
- **`BlockchainAnchorClientOptions`**: Base sealed class
- **`AlgorandOptions`**: Algorand SDK configuration
- **`PolygonOptions`**: Polygon/Ethereum Web3j configuration (implements `Closeable`)
- **`GanacheOptions`**: Ganache local node configuration (implements `Closeable`)
- **`IndyOptions`**: Hyperledger Indy configuration

#### `spi`
Service Provider Interface utilities:
- **`BlockchainIntegrationHelper`**: Utility for SPI-based adapter discovery and registration

## Usage Examples

### Basic Usage with Type-Safe Options

```kotlin
import io.geoknoesis.vericore.anchor.*
import io.geoknoesis.vericore.anchor.options.AlgorandOptions
import io.geoknoesis.vericore.anchor.ChainId

// Type-safe chain ID
val chainId = ChainId.Algorand.Testnet

// Type-safe options (compile-time validation)
val options = AlgorandOptions(
    algodUrl = "https://testnet-api.algonode.cloud",
    privateKey = "base64-key"
)

// Create client with type-safe configuration
val client = AlgorandBlockchainAnchorClient(chainId.toString(), options)
val registry = BlockchainAnchorRegistry()
registry.register(chainId.toString(), client)

// Anchor data
val payload = buildJsonObject { put("data", "value") }
val result = client.writePayload(payload)

// Read anchored data
val readResult = client.readPayload(result.ref)
```

### Type-Safe Anchoring

```kotlin
import io.geoknoesis.vericore.anchor.*
import kotlinx.serialization.Serializable

@Serializable
data class MyData(val value: String)

val data = MyData("test")
val typedResult = anchorTyped(
    value = data,
    serializer = MyData.serializer(),
    targetChainId = ChainId.Algorand.Testnet.toString()
)

// Read back
val retrieved = readTyped<MyData>(
    ref = typedResult.ref,
    serializer = MyData.serializer()
)
```

### Error Handling

```kotlin
import io.geoknoesis.vericore.anchor.exceptions.*

try {
    val result = client.writePayload(payload)
} catch (e: BlockchainTransactionException) {
    // Rich error context available
    println("Transaction failed: ${e.message}")
    println("Chain: ${e.chainId}")
    println("Operation: ${e.operation}")
    println("TxHash: ${e.txHash}")
    println("PayloadSize: ${e.payloadSize}B")
    println("GasUsed: ${e.gasUsed}")
} catch (e: BlockchainConnectionException) {
    println("Connection failed: ${e.message}")
    println("Endpoint: ${e.endpoint}")
} catch (e: BlockchainConfigurationException) {
    println("Configuration error: ${e.message}")
    println("Config key: ${e.configKey}")
}
```

### Resource Management (Web3j Clients)

```kotlin
import io.geoknoesis.vericore.anchor.options.PolygonOptions

// PolygonOptions implements Closeable
val options = PolygonOptions(
    rpcUrl = "https://polygon-rpc.com",
    privateKey = "0x..."
)

val client = PolygonBlockchainAnchorClient(ChainId.Eip155.PolygonMainnet.toString(), options)

// Use try-with-resources pattern
client.use {
    val result = it.writePayload(payload)
    // Client automatically closes Web3j connection
}
```

### Chain ID Type Safety

```kotlin
import io.geoknoesis.vericore.anchor.ChainId

// Predefined chain IDs (compile-time safe)
val algorandTestnet = ChainId.Algorand.Testnet
val polygonMainnet = ChainId.Eip155.PolygonMainnet
val ganacheLocal = ChainId.Eip155.GanacheLocal
val indyTestnet = ChainId.Indy.BCovrinTestnet

// Parse from string
val parsed = ChainId.parse("algorand:testnet") // Returns ChainId.Algorand.Testnet

// Custom chain IDs
val custom = ChainId.Custom("custom:chain", "custom")

// Validate chain ID format
val isValid = ChainId.isValid("algorand:testnet") // true
```

## Chain ID Format

Chain IDs follow CAIP-2 format and are type-safe:

**Algorand**:
- `ChainId.Algorand.Mainnet` → `"algorand:mainnet"`
- `ChainId.Algorand.Testnet` → `"algorand:testnet"`
- `ChainId.Algorand.Betanet` → `"algorand:betanet"`

**EIP-155 (Ethereum/Polygon)**:
- `ChainId.Eip155.PolygonMainnet` → `"eip155:137"`
- `ChainId.Eip155.MumbaiTestnet` → `"eip155:80001"`
- `ChainId.Eip155.GanacheLocal` → `"eip155:1337"`

**Hyperledger Indy**:
- `ChainId.Indy.SovrinMainnet` → `"indy:mainnet:sovrin"`
- `ChainId.Indy.SovrinStaging` → `"indy:testnet:sovrin-staging"`
- `ChainId.Indy.BCovrinTestnet` → `"indy:testnet:bcovrin"`

## Architecture Benefits

### Code Reuse
- **`AbstractBlockchainAnchorClient`**: Reduces duplication by ~40% across adapters
- Common storage management, error handling, and `AnchorRef` construction
- Template methods for blockchain-specific operations

### Type Safety
- **Sealed classes** for options prevent runtime configuration errors
- **Type-safe chain IDs** prevent typos and invalid values
- Compile-time validation of configuration

### Error Handling
- **Structured exceptions** with rich context for debugging
- Consistent error message format
- Operation-specific exception types

### Resource Management
- **`Closeable` interface** for Web3j-based clients
- Automatic resource cleanup
- Proper connection lifecycle management

## Thread Safety

`BlockchainAnchorRegistry` instances are thread-safe for concurrent access when using the default implementation.
- **`BlockchainAnchorClient`** implementations should be thread-safe
- All operations use Kotlin coroutines for non-blocking I/O
- In-memory storage uses `ConcurrentHashMap` for thread safety

## Performance Considerations

- In-memory fallback storage for testing and development
- Efficient `AnchorRef` construction
- Optimized error handling paths
- Resource pooling for network clients (where applicable)

