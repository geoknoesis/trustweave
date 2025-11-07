# VeriCore Ganache Adapter

Ethereum-compatible blockchain adapter for VeriCore using Ganache (local Ethereum node).

**Important**: This adapter requires Ganache to be running. It does not fall back to in-memory storage - all operations interact directly with the Ganache blockchain.

## Prerequisites

**Docker** must be installed and running. Testcontainers will automatically download and run Ganache in a Docker container.

No Node.js, npm, or Ganache CLI installation needed!

The tests use Testcontainers to:
1. Automatically download the `trufflesuite/ganache-cli` Docker image (if not already present)
2. Start Ganache in a container before tests
3. Automatically stop and clean up the container after tests

**Note**: Docker Desktop or Docker Engine must be running for tests to work.

## Usage

### Basic Setup

```kotlin
import io.geoknoesis.vericore.ganache.GanacheIntegration
import io.geoknoesis.vericore.anchor.*

// Private key is REQUIRED - no fallback to in-memory storage
val client = GanacheIntegration.setup(
    chainId = "eip155:1337",
    rpcUrl = "http://localhost:8545",
    privateKey = "0xac0974bec39a17e36ba4a6b4d238ff944bacb478cbed5efcae784d7bf4f2ff80"
)

val payload = buildJsonObject { put("data", "test") }
val result = client.writePayload(payload)
```

### Direct Client Usage with Type-Safe Options

```kotlin
import io.geoknoesis.vericore.ganache.GanacheBlockchainAnchorClient
import io.geoknoesis.vericore.anchor.*
import io.geoknoesis.vericore.anchor.options.GanacheOptions
import io.geoknoesis.vericore.anchor.ChainId

// Type-safe chain ID
val chainId = ChainId.Eip155.GanacheLocal

// Type-safe options (compile-time validation)
val options = GanacheOptions(
    rpcUrl = "http://localhost:8545",
    privateKey = "0xac0974bec39a17e36ba4a6b4d238ff944bacb478cbed5efcae784d7bf4f2ff80"
)

// Create client with type-safe configuration
val client = GanacheBlockchainAnchorClient(chainId.toString(), options)

// Use try-with-resources for automatic cleanup (GanacheOptions implements Closeable)
client.use {
    val payload = buildJsonObject { put("data", "test") }
    val anchorResult = it.writePayload(payload)
    
    // Read back from blockchain
    val retrieved = it.readPayload(anchorResult.ref)
}
```

### Direct Client Usage (Legacy Map-based Options)

```kotlin
import io.geoknoesis.vericore.ganache.GanacheBlockchainAnchorClient
import io.geoknoesis.vericore.anchor.*

val client = GanacheBlockchainAnchorClient(
    chainId = "eip155:1337",
    options = mapOf(
        "rpcUrl" to "http://localhost:8545",
        "privateKey" to "0xac0974bec39a17e36ba4a6b4d238ff944bacb478cbed5efcae784d7bf4f2ff80"
    )
)

val payload = buildJsonObject { put("data", "test") }
val anchorResult = client.writePayload(payload)

// Read back from blockchain
val retrieved = client.readPayload(anchorResult.ref)
```

## Configuration Options

**Type-Safe Options (Recommended)**:
```kotlin
import io.geoknoesis.vericore.anchor.options.GanacheOptions

val options = GanacheOptions(
    rpcUrl = "http://localhost:8545",  // Optional, default: "http://localhost:8545"
    privateKey = "0x..."               // Required: Private key for signing transactions
)
```

**Map-based Options (Legacy)**:
- `rpcUrl` (String, optional): Ganache RPC endpoint. Default: `"http://localhost:8545"`
- `privateKey` (String, **required**): Private key for signing transactions (hex format, with or without `0x` prefix)
- `contractAddress` (String, optional): Contract address if using a smart contract

**Note**: `GanacheOptions` implements `Closeable` for proper Web3j connection cleanup. Use `use {}` pattern for automatic resource management.

## Testing

**Ganache runs automatically in a Docker container via Testcontainers.** No manual setup required!

### Running Tests

Simply run the tests - Ganache will be started automatically in Docker:

```bash
# Run all tests (Ganache container starts automatically)
./gradlew :vericore-ganache:test

# Run specific test
./gradlew :vericore-ganache:test --tests "*GanacheEoIntegrationTest*"
```

### Requirements

- **Docker** must be installed and running
- Docker Desktop or Docker Engine must be accessible
- The `trufflesuite/ganache-cli` image will be downloaded automatically on first run

### How It Works

Tests use Testcontainers to:
1. Download the Ganache Docker image (`trufflesuite/ganache-cli:latest`) if needed
2. Start a Ganache container with deterministic accounts
3. Expose the RPC endpoint on a random port
4. Run tests against the containerized Ganache instance
5. Automatically stop and remove the container after tests complete

## How It Works

1. **Transaction Submission**: Payloads are encoded as JSON and stored in Ethereum transaction data fields
2. **Transaction Reading**: Payloads are retrieved by reading transaction data from the blockchain
3. **No Fallback**: All operations require a running Ganache instance - no in-memory fallback

## Error Handling

The adapter uses structured exception handling with rich error context:

```kotlin
import io.geoknoesis.vericore.anchor.exceptions.*

try {
    val result = client.writePayload(payload)
} catch (e: BlockchainTransactionException) {
    // Rich error context available
    println("Transaction failed: ${e.message}")
    println("Chain: ${e.chainId}")
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

**Exception Types**:
- `BlockchainTransactionException`: Transaction failures (includes txHash, payloadSize, gasUsed)
- `BlockchainConnectionException`: Connection failures (includes endpoint)
- `BlockchainConfigurationException`: Configuration errors (includes config key)
- `IllegalArgumentException`: Missing required configuration (e.g., privateKey)

## Chain ID

**Type-Safe Chain ID (Recommended)**:
```kotlin
import io.geoknoesis.vericore.anchor.ChainId

val chainId = ChainId.Eip155.GanacheLocal  // "eip155:1337"
```

**String-based Chain ID (Legacy)**:
Default chain ID: `"eip155:1337"` (Ganache default)

You can use any Ethereum-compatible chain ID format: `"eip155:<chainId>"`

