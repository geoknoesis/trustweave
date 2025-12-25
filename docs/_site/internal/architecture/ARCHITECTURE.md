# TrustWeave Architecture & Modules

## Overview

TrustWeave provides a modular architecture for building trust and identity systems that can be used across multiple contexts:

- **Earth Observation (EO) catalogues**
- **Spatial Web Nodes**
- **Agentic / Large Language Model (LLM)-based platforms**
- **Any application requiring decentralized identity and trust**

## Architecture

TrustWeave is organized into a domain-centric structure with core modules and plugin implementations:

### Core Modules (in `core/` directory)

- **`TrustWeave-common`**: Base types, exceptions, credential APIs (includes SPI interfaces)
- **`TrustWeave-json`**: JSON canonicalization and digest computation utilities
- **`TrustWeave-trust`**: Trust registry and trust layer
- **`TrustWeave-kms`**: Key Management Service (KMS) abstraction
- **`TrustWeave-did`**: Decentralized Identifier (DID) and DID Document management with pluggable DID methods
- **`TrustWeave-anchor`**: Blockchain anchoring abstraction with chain-agnostic interfaces
- **`TrustWeave-testkit`**: In-memory test implementations for all interfaces

### Plugin Modules

- **DID Plugins** (`did/plugins/`): DID method implementations (key, web, ion, ethr, etc.)
- **KMS Plugins** (`kms/plugins/`): KMS implementations (aws, azure, google, hashicorp, waltid)
- **Chain Plugins** (`chains/plugins/`): Blockchain adapters (algorand, polygon, ethereum, base, etc.)

All plugins use hierarchical Maven group IDs:
- DID plugins: `org.trustweave.did:*`
- KMS plugins: `org.trustweave.kms:*`
- Chain plugins: `org.trustweave.chains:*`

## Key Features

- **Domain-agnostic**: No application-specific logic in core modules
- **Chain-agnostic**: Pluggable blockchain adapters via interfaces
- **Decentralized Identifier (DID)-method-agnostic**: Pluggable DID methods via interfaces
- **Key Management Service (KMS)-agnostic**: Pluggable key management backends
- **Coroutine-based**: All I/O operations use Kotlin coroutines (`suspend` functions)
- **Type-safe**: Uses Kotlinx Serialization for type-safe JSON handling

## Module Details

### trustweave-common

Shared types and utilities:
- `TrustWeaveException`: Base exception class
- `NotFoundException`: Resource not found exception
- `InvalidOperationException`: Invalid operation exception
- `TrustWeaveConstants`: Common constants

### TrustWeave-json

JSON canonicalization and digest utilities with performance optimizations:
- `DigestUtils.canonicalizeJson()`: Canonicalize JSON with stable key ordering
- `DigestUtils.sha256DigestMultibase()`: Compute SHA-256 digest with multibase encoding
- **Performance Features**:
  - Digest caching (configurable, enabled by default)
  - Reused JSON serializer instances
  - Thread-safe operations
  - Cache management: `clearCache()`, `getCacheSize()`

**Performance Configuration**:
```kotlin
import org.trustweave.json.DigestUtils

// Disable caching for memory-constrained environments
DigestUtils.enableDigestCache = false

// Check cache size
val cacheSize = DigestUtils.getCacheSize()

// Clear cache if needed
DigestUtils.clearCache()
```

### TrustWeave-kms

Key management abstraction:
- `KeyManagementService`: Interface for key operations
- `KeyHandle`: Key metadata container
- Supports Ed25519, secp256k1, and extensible to other algorithms

### TrustWeave-did

DID and DID Document management:
- `Did`: DID identifier model
- `DidDocument`: W3C DID Core-compliant document model
- `DidMethod`: Interface for DID method implementations
- `DidMethodRegistry`: Instance-scoped registry for pluggable DID methods

### TrustWeave-anchor

Blockchain anchoring abstraction with comprehensive type safety and error handling:

**Core Components**:
- `BlockchainAnchorClient`: Interface for blockchain operations
- `AbstractBlockchainAnchorClient`: Base class reducing code duplication across adapters
- `AnchorRef`: Chain-agnostic anchor reference (CAIP-2 style)
- `AnchorResult`: Result of anchoring operation
- `BlockchainAnchorRegistry`: Instance-scoped registry for pluggable blockchain clients
- `ChainId`: Type-safe chain identifier (CAIP-2 format)
- `BlockchainIntegrationHelper`: Utility for SPI-based adapter discovery
- Helper functions: `anchorTyped()`, `readTyped()`

**Type-Safe Configuration**:
```kotlin
import org.trustweave.anchor.options.AlgorandOptions
import org.trustweave.anchor.ChainId

// Type-safe chain ID
val chainId = ChainId.Algorand.Testnet

// Type-safe options (compile-time validation)
val options = AlgorandOptions(
    algodUrl = "https://testnet-api.algonode.cloud",
    privateKey = "base64-key"
)

// Create client with type-safe configuration
val client = AlgorandBlockchainAnchorClient(chainId.toString(), options)
```

**Exception Handling**:
```kotlin
import org.trustweave.anchor.exceptions.*

try {
    val result = client.writePayload(payload)
} catch (e: BlockchainTransactionException) {
    // Rich error context: chainId, txHash, payloadSize, gasUsed
    println("Transaction failed: ${e.message}")
    println("Chain: ${e.chainId}")
    println("TxHash: ${e.txHash}")
    println("PayloadSize: ${e.payloadSize}B")
}
```

**Available Options Types**:
- `AlgorandOptions`: Algorand SDK configuration
- `PolygonOptions`: Polygon/Ethereum Web3j configuration (implements `Closeable`)
- `EthereumOptions`: Ethereum mainnet Web3j configuration (implements `Closeable`)
- `BaseOptions`: Base blockchain Web3j configuration (implements `Closeable`)
- `ArbitrumOptions`: Arbitrum blockchain Web3j configuration (implements `Closeable`)
- `GanacheOptions`: Ganache local node configuration (implements `Closeable`)
- `IndyOptions`: Hyperledger Indy configuration

**Exception Hierarchy**:
- `BlockchainException`: Base exception with chain ID and operation context
- `BlockchainTransactionException`: Transaction failures with txHash, payloadSize, gasUsed
- `BlockchainConnectionException`: Connection failures with endpoint information
- `BlockchainConfigurationException`: Configuration errors with config key details
- `BlockchainUnsupportedOperationException`: Unsupported operations

### TrustWeave-testkit

In-memory test implementations and test utilities:
- `InMemoryKeyManagementService`: In-memory KMS for testing
- `InMemoryBlockchainAnchorClient`: In-memory blockchain client for testing
- `DidKeyMockMethod`: Mock DID method implementation
- `TrustWeaveTestFixture`: Unified test fixture builder for setting up complete test environments
- `EoTestIntegration`: Reusable EO test scenarios with TestContainers support
- `BaseEoIntegrationTest`: Base class for EO integration tests
- `TestDataBuilders`: Builders for creating VC, Linkset, and artifact structures
- `IntegrityVerifier`: Utilities for verifying integrity chains

**Test Fixture Example**:
```kotlin
import org.trustweave.testkit.TrustWeaveTestFixture

TrustWeaveTestFixture.builder()
    .withInMemoryBlockchainClient("algorand:testnet")
    .build()
    .use { fixture ->
        val issuerDoc = fixture.createIssuerDid()
        val client = fixture.getBlockchainClient("algorand:testnet")
    }
```

## Design Principles

1. **Neutrality**: Core modules contain no domain-specific or chain-specific logic
2. **Pluggability**: All external dependencies (blockchains, DID methods, KMS) are pluggable via interfaces
3. **Coroutines**: All I/O operations use Kotlin coroutines for async/await patterns
4. **Type Safety**: Leverages Kotlinx Serialization for type-safe JSON handling, sealed classes for options and chain IDs
5. **Testability**: Provides test implementations for all interfaces
6. **Code Quality**: Consistent code formatting via ktlint, comprehensive error handling
7. **Performance**: Optimized JSON operations and configurable digest caching
8. **Resource Management**: Proper cleanup for network clients (Closeable interface)

## Next Steps

- See [Available Plugins](PLUGINS.md) for plugin details
- Read [Getting Started](GETTING_STARTED.md) for examples
- Check [API Guide](API_GUIDE.md) for API usage




