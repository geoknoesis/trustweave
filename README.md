# VeriCore

A **neutral, reusable trust and identity core** library for Kotlin, designed to be domain-agnostic, chain-agnostic, DID-method-agnostic, and KMS-agnostic.

## Overview

VeriCore provides a modular architecture for building trust and identity systems that can be used across multiple contexts:

- **Earth Observation (EO) catalogues**
- **Spatial Web Nodes**
- **Agentic / LLM-based platforms**
- **Any application requiring decentralized identity and trust**

## Architecture

VeriCore is organized into six modules:

### Core Modules

- **`vericore-core`**: Shared types, exceptions, and common utilities
- **`vericore-json`**: JSON canonicalization and digest computation utilities
- **`vericore-kms`**: Key management service abstraction
- **`vericore-did`**: DID and DID Document management with pluggable DID methods
- **`vericore-anchor`**: Blockchain anchoring abstraction with chain-agnostic interfaces
- **`vericore-testkit`**: In-memory test implementations for all interfaces

## Key Features

- **Domain-agnostic**: No application-specific logic in core modules
- **Chain-agnostic**: Pluggable blockchain adapters via interfaces
- **DID-method-agnostic**: Pluggable DID methods via interfaces
- **KMS-agnostic**: Pluggable key management backends
- **Coroutine-based**: All I/O operations use Kotlin coroutines (`suspend` functions)
- **Type-safe**: Uses Kotlinx Serialization for type-safe JSON handling

## Quick Start

### Adding Dependencies

```kotlin
dependencies {
    implementation("io.geoknoesis.vericore:vericore-core:1.0.0-SNAPSHOT")
    implementation("io.geoknoesis.vericore:vericore-json:1.0.0-SNAPSHOT")
    implementation("io.geoknoesis.vericore:vericore-kms:1.0.0-SNAPSHOT")
    implementation("io.geoknoesis.vericore:vericore-did:1.0.0-SNAPSHOT")
    implementation("io.geoknoesis.vericore:vericore-anchor:1.0.0-SNAPSHOT")
    
    // For testing
    testImplementation("io.geoknoesis.vericore:vericore-testkit:1.0.0-SNAPSHOT")
}
```

### Example: Computing a JSON Digest

```kotlin
import io.geoknoesis.vericore.json.DigestUtils
import kotlinx.serialization.json.*

fun main() {
    val json = buildJsonObject {
        put("vcId", "vc-12345")
        put("issuer", "did:web:example.com")
    }
    
    // Canonicalize and compute digest
    val digest = DigestUtils.sha256DigestMultibase(json)
    println("Digest: $digest") // e.g., "uABC123..."
}
```

### Example: Creating a DID

```kotlin
import io.geoknoesis.vericore.did.*
import io.geoknoesis.vericore.testkit.did.DidKeyMockMethod
import io.geoknoesis.vericore.testkit.kms.InMemoryKeyManagementService
import kotlinx.coroutines.runBlocking

fun main() = runBlocking {
    // Setup: Create KMS and DID method
    val kms = InMemoryKeyManagementService()
    val didMethod = DidKeyMockMethod(kms)
    
    // Register the method
    DidRegistry.register(didMethod)
    
    // Create a DID
    val document = didMethod.createDid(mapOf("algorithm" to "Ed25519"))
    println("Created DID: ${document.id}")
    
    // Resolve the DID
    val result = DidRegistry.resolve(document.id)
    println("Resolved document: ${result.document?.id}")
}
```

### Example: Anchoring Data to a Blockchain

```kotlin
import io.geoknoesis.vericore.anchor.*
import io.geoknoesis.vericore.testkit.anchor.InMemoryBlockchainAnchorClient
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import kotlinx.serialization.Serializable

@Serializable
data class VerifiableCredentialDigest(
    val vcId: String,
    val vcDigest: String
)

fun main() = runBlocking {
    // Setup: Create and register blockchain client
    val client = InMemoryBlockchainAnchorClient("algorand:mainnet", "app-123")
    BlockchainRegistry.register("algorand:mainnet", client)
    
    // Create a digest object
    val digest = VerifiableCredentialDigest(
        vcId = "vc-12345",
        vcDigest = "uABC123..."
    )
    
    // Anchor it
    val result = anchorTyped(
        value = digest,
        serializer = VerifiableCredentialDigest.serializer(),
        targetChainId = "algorand:mainnet"
    )
    
    println("Anchored at: ${result.ref.txHash}")
    
    // Read it back
    val retrieved = readTyped<VerifiableCredentialDigest>(
        ref = result.ref,
        serializer = VerifiableCredentialDigest.serializer()
    )
    
    println("Retrieved: ${retrieved.vcId}")
}
```

### Example: Complete Workflow

```kotlin
import io.geoknoesis.vericore.anchor.*
import io.geoknoesis.vericore.did.*
import io.geoknoesis.vericore.json.DigestUtils
import io.geoknoesis.vericore.testkit.anchor.InMemoryBlockchainAnchorClient
import io.geoknoesis.vericore.testkit.did.DidKeyMockMethod
import io.geoknoesis.vericore.testkit.kms.InMemoryKeyManagementService
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import kotlinx.serialization.Serializable

@Serializable
data class VerifiableCredentialDigest(
    val vcId: String,
    val vcDigest: String,
    val issuer: String
)

fun main() = runBlocking {
    // 1. Setup services
    val kms = InMemoryKeyManagementService()
    val didMethod = DidKeyMockMethod(kms)
    val anchorClient = InMemoryBlockchainAnchorClient("algorand:mainnet")
    
    DidRegistry.register(didMethod)
    BlockchainRegistry.register("algorand:mainnet", anchorClient)
    
    // 2. Create a DID for the issuer
    val issuerDoc = didMethod.createDid(mapOf("algorithm" to "Ed25519"))
    val issuerDid = issuerDoc.id
    
    // 3. Create a verifiable credential payload
    val vcPayload = buildJsonObject {
        put("vcId", "vc-12345")
        put("issuer", issuerDid)
        put("credentialSubject", buildJsonObject {
            put("id", "subject-123")
            put("type", "Person")
        })
    }
    
    // 4. Compute digest
    val digest = DigestUtils.sha256DigestMultibase(vcPayload)
    
    // 5. Create digest object and anchor it
    val digestObj = VerifiableCredentialDigest(
        vcId = "vc-12345",
        vcDigest = digest,
        issuer = issuerDid
    )
    
    val anchorResult = anchorTyped(
        value = digestObj,
        serializer = VerifiableCredentialDigest.serializer(),
        targetChainId = "algorand:mainnet"
    )
    
    println("Issuer DID: $issuerDid")
    println("VC Digest: $digest")
    println("Anchored at: ${anchorResult.ref.txHash}")
    
    // 6. Verify by reading back
    val retrieved = readTyped<VerifiableCredentialDigest>(
        ref = anchorResult.ref,
        serializer = VerifiableCredentialDigest.serializer()
    )
    
    assert(retrieved.vcDigest == digest)
    println("Verification successful!")
}
```

## Module Details

### vericore-core

Shared types and utilities:
- `VeriCoreException`: Base exception class
- `NotFoundException`: Resource not found exception
- `InvalidOperationException`: Invalid operation exception
- `VeriCoreConstants`: Common constants

### vericore-json

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
import io.geoknoesis.vericore.json.DigestUtils

// Disable caching for memory-constrained environments
DigestUtils.enableDigestCache = false

// Check cache size
val cacheSize = DigestUtils.getCacheSize()

// Clear cache if needed
DigestUtils.clearCache()
```

### vericore-kms

Key management abstraction:
- `KeyManagementService`: Interface for key operations
- `KeyHandle`: Key metadata container
- Supports Ed25519, secp256k1, and extensible to other algorithms

### vericore-did

DID and DID Document management:
- `Did`: DID identifier model
- `DidDocument`: W3C DID Core-compliant document model
- `DidMethod`: Interface for DID method implementations
- `DidRegistry`: Registry for pluggable DID methods

### vericore-anchor

Blockchain anchoring abstraction with comprehensive type safety and error handling:

**Core Components**:
- `BlockchainAnchorClient`: Interface for blockchain operations
- `AbstractBlockchainAnchorClient`: Base class reducing code duplication across adapters
- `AnchorRef`: Chain-agnostic anchor reference (CAIP-2 style)
- `AnchorResult`: Result of anchoring operation
- `BlockchainRegistry`: Registry for pluggable blockchain clients
- `ChainId`: Type-safe chain identifier (CAIP-2 format)
- `BlockchainIntegrationHelper`: Utility for SPI-based adapter discovery
- Helper functions: `anchorTyped()`, `readTyped()`

**Type-Safe Configuration**:
```kotlin
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
```

**Exception Handling**:
```kotlin
import io.geoknoesis.vericore.anchor.exceptions.*

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
- `GanacheOptions`: Ganache local node configuration (implements `Closeable`)
- `IndyOptions`: Hyperledger Indy configuration

**Exception Hierarchy**:
- `BlockchainException`: Base exception with chain ID and operation context
- `BlockchainTransactionException`: Transaction failures with txHash, payloadSize, gasUsed
- `BlockchainConnectionException`: Connection failures with endpoint information
- `BlockchainConfigurationException`: Configuration errors with config key details
- `BlockchainUnsupportedOperationException`: Unsupported operations

### vericore-testkit

In-memory test implementations and test utilities:
- `InMemoryKeyManagementService`: In-memory KMS for testing
- `InMemoryBlockchainAnchorClient`: In-memory blockchain client for testing
- `DidKeyMockMethod`: Mock DID method implementation
- `VeriCoreTestFixture`: Unified test fixture builder for setting up complete test environments
- `EoTestIntegration`: Reusable EO test scenarios with TestContainers support
- `BaseEoIntegrationTest`: Base class for EO integration tests
- `TestDataBuilders`: Builders for creating VC, Linkset, and artifact structures
- `IntegrityVerifier`: Utilities for verifying integrity chains

**Test Fixture Example**:
```kotlin
import io.geoknoesis.vericore.testkit.VeriCoreTestFixture

VeriCoreTestFixture.builder()
    .withInMemoryBlockchainClient("algorand:testnet")
    .build()
    .use { fixture ->
        val issuerDoc = fixture.createIssuerDid()
        val client = fixture.getBlockchainClient("algorand:testnet")
    }
```

## Building

The project uses Gradle Wrapper for consistent builds:

**Unix/Linux/macOS:**
```bash
./gradlew build
```

**Windows:**
```powershell
.\gradlew.bat build
```

**Build without tests:**
```bash
./gradlew build -x test
```

## Testing

```bash
./gradlew test
```

**Run specific test:**
```bash
./gradlew :vericore-ganache:test --tests "*GanacheEoIntegrationTest*"
```

## Code Quality

The project includes code quality tools:

**Check code formatting:**
```bash
./gradlew ktlintCheck
```

**Auto-format code:**
```bash
./gradlew ktlintFormat
```

## Requirements

- **Java 21+**: Required for compilation and runtime
- **Kotlin 2.2.0+**: Included via Gradle plugin
- **Gradle 8.5+**: Automatically downloaded via Gradle Wrapper (no manual installation needed)
- **Docker** (optional): Required for `vericore-ganache` tests using TestContainers

## Design Principles

1. **Neutrality**: Core modules contain no domain-specific or chain-specific logic
2. **Pluggability**: All external dependencies (blockchains, DID methods, KMS) are pluggable via interfaces
3. **Coroutines**: All I/O operations use Kotlin coroutines for async/await patterns
4. **Type Safety**: Leverages Kotlinx Serialization for type-safe JSON handling, sealed classes for options and chain IDs
5. **Testability**: Provides test implementations for all interfaces
6. **Code Quality**: Consistent code formatting via ktlint, comprehensive error handling
7. **Performance**: Optimized JSON operations and configurable digest caching
8. **Resource Management**: Proper cleanup for network clients (Closeable interface)

## Future Extensions

Future adapter modules (outside this core library) can provide:

- **Blockchain Adapters**: Algorand, Ethereum, Polygon, etc.
- **DID Method Implementations**: did:web, did:key, did:ion, etc.
- **KMS Backends**: AWS KMS, HashiCorp Vault, Hardware Security Modules, etc.

## walt.id Integration

VeriCore includes an optional `vericore-waltid` module that provides walt.id-based implementations of VeriCore interfaces using the SPI (Service Provider Interface) pattern.

### Using walt.id Adapters

Add the walt.id adapter module to your dependencies:

```kotlin
dependencies {
    implementation("io.geoknoesis.vericore:vericore-waltid:1.0.0-SNAPSHOT")
}
```

### Automatic Discovery via SPI

walt.id adapters are automatically discovered via Java ServiceLoader:

```kotlin
import io.geoknoesis.vericore.waltid.WaltIdIntegration
import kotlinx.coroutines.runBlocking

fun main() = runBlocking {
    // Automatically discover and register walt.id adapters
    val result = WaltIdIntegration.discoverAndRegister()
    
    println("Registered DID methods: ${result.registeredDidMethods}")
    // Output: Registered DID methods: [key, web]
    
    // Use VeriCore APIs as normal - walt.id adapters are now registered
    val didDocument = DidRegistry.resolve("did:key:...")
}
```

### Manual Setup

You can also manually configure walt.id integration:

```kotlin
import io.geoknoesis.vericore.waltid.WaltIdIntegration
import io.geoknoesis.vericore.waltid.WaltIdKeyManagementService

fun main() = runBlocking {
    // Create walt.id KMS
    val kms = WaltIdKeyManagementService()
    
    // Setup integration with specific DID methods
    val result = WaltIdIntegration.setup(
        kms = kms,
        didMethods = listOf("key", "web")
    )
    
    // Create a DID using walt.id
    val keyMethod = DidRegistry.get("key")
    val document = keyMethod!!.createDid(mapOf("algorithm" to "Ed25519"))
    println("Created DID: ${document.id}")
}
```

### Supported Features

The walt.id adapter module provides:

- **Key Management**: `WaltIdKeyManagementService` implementing `KeyManagementService`
- **DID Methods**: 
  - `did:key` via `WaltIdKeyMethod`
  - `did:web` via `WaltIdWebMethod`
- **SPI Discovery**: Automatic discovery and registration via Java ServiceLoader

### SPI Provider Interfaces

VeriCore defines SPI interfaces for adapter discovery:

- `KeyManagementServiceProvider`: For KMS implementations
- `DidMethodProvider`: For DID method implementations

These interfaces allow any adapter module to be discovered automatically at runtime.

## godiddy Integration

VeriCore includes an optional `vericore-godiddy` module that provides HTTP-based integration with godiddy services (Universal Resolver, Universal Registrar, Universal Issuer, Universal Verifier) using the SPI pattern.

### Using godiddy Adapters

Add the godiddy adapter module to your dependencies:

```kotlin
dependencies {
    implementation("io.geoknoesis.vericore:vericore-godiddy:1.0.0-SNAPSHOT")
}
```

### Automatic Discovery via SPI

godiddy adapters are automatically discovered via Java ServiceLoader:

```kotlin
import io.geoknoesis.vericore.godiddy.GodiddyIntegration
import kotlinx.coroutines.runBlocking

fun main() = runBlocking {
    // Automatically discover and register godiddy adapters
    val result = GodiddyIntegration.discoverAndRegister()
    
    println("Registered DID methods: ${result.registeredDidMethods}")
    // Output: Registered DID methods: [key, web, ion, ethr, ...]
    
    // Use VeriCore APIs as normal - godiddy adapters are now registered
    val didDocument = DidRegistry.resolve("did:key:...")
}
```

### Manual Setup with Custom Configuration

You can manually configure godiddy integration with a custom base URL:

```kotlin
import io.geoknoesis.vericore.godiddy.GodiddyIntegration

fun main() = runBlocking {
    // Setup integration with custom base URL (for self-hosted instances)
    val result = GodiddyIntegration.setup(
        baseUrl = "https://custom.godiddy.com",
        didMethods = listOf("key", "web", "ion") // Optional: specify methods
    )
    
    // Access service clients directly
    val resolver = result.resolver
    val registrar = result.registrar
    val issuer = result.issuer
    val verifier = result.verifier
    
    // Use services
    val resolutionResult = resolver?.resolveDid("did:key:...")
}
```

### Supported Services

The godiddy adapter module provides:

- **Universal Resolver**: Resolve DIDs across multiple methods
- **Universal Registrar**: Create and manage DIDs
- **Universal Issuer**: Issue Verifiable Credentials
- **Universal Verifier**: Verify Verifiable Credentials
- **DID Methods**: Supports all methods available via Universal Resolver (key, web, ion, ethr, polygonid, cheqd, dock, indy, and many more)

### Configuration Options

The godiddy integration supports the following configuration options:

```kotlin
val result = GodiddyIntegration.discoverAndRegister(
    options = mapOf(
        "baseUrl" to "https://resolver.godiddy.com", // Default: public godiddy service
        "timeout" to 30000L, // HTTP timeout in milliseconds
        "apiKey" to "your-api-key" // Optional: for authenticated requests
    )
)
```

### Example: Issuing and Verifying Credentials

```kotlin
import io.geoknoesis.vericore.godiddy.GodiddyIntegration
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*

fun main() = runBlocking {
    val result = GodiddyIntegration.discoverAndRegister()
    
    // Issue a credential
    val credential = buildJsonObject {
        put("id", "vc-12345")
        put("type", buildJsonArray { add("VerifiableCredential") })
        put("issuer", "did:key:...")
        put("credentialSubject", buildJsonObject {
            put("id", "subject-123")
        })
    }
    
    val issuedCredential = result.issuer?.issueCredential(
        credential = credential,
        options = mapOf("format" to "json-ld")
    )
    
    // Verify the credential
    val verificationResult = result.verifier?.verifyCredential(
        credential = issuedCredential ?: credential,
        options = emptyMap()
    )
    
    println("Verified: ${verificationResult?.verified}")
}
```

## Contributing

[Contributing guidelines]

## Changelog

See [CHANGELOG.md](CHANGELOG.md) for detailed information about changes, improvements, and migration guides.

## Additional Documentation

- **[Documentation Index](docs/DOCUMENTATION_INDEX.md)**: Complete documentation index
- **[Optimization Summary](OPTIMIZATION_COMPLETE.md)**: Summary of codebase optimizations
- **[Module READMEs](vericore-anchor/src/main/kotlin/io/geoknoesis/vericore/anchor/README.md)**: Detailed module documentation

