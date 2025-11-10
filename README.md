# VeriCore

> Beautiful, type-safe trust and identity for Kotlin

A **neutral, reusable trust and identity core** library for Kotlin, designed to be domain-agnostic, chain-agnostic, DID-method-agnostic, and KMS-agnostic.

## Quick Start (30 Seconds) ⚡

```kotlin
import io.geoknoesis.vericore.VeriCore
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

fun main() = runBlocking {
    // 1. Create VeriCore instance
    val vericore = VeriCore.create()
    
    // 2. Create a DID
    val did = vericore.createDid()
    
    // 3. Issue a credential
    val credential = vericore.issueCredential(
        issuerDid = did.id,
        issuerKeyId = did.id,  // Simplified for demo
        credentialSubject = buildJsonObject {
            put("id", "did:key:subject")
            put("name", "Alice")
        },
        types = listOf("PersonCredential")
    )
    
    // 4. Verify the credential
    val result = vericore.verifyCredential(credential)
    println("Valid: ${result.valid}")
    
    // 5. Store in wallet
    val wallet = vericore.createWallet(holderDid = "did:key:alice")
    wallet.store(credential)
    
    println("✅ Complete!")
}
```

## Installation

Add VeriCore to your project:

```kotlin
dependencies {
    // All-in-one dependency (recommended)
    implementation("io.geoknoesis.vericore:vericore-core:1.0.0-SNAPSHOT")
    implementation("io.geoknoesis.vericore:vericore-testkit:1.0.0-SNAPSHOT")  // For testing
}
```

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

## Which API Should I Use? 🤔

VeriCore provides multiple APIs for different use cases:

### 1. **VeriCore Facade** (Recommended for Most Users) ✅

**Use when:** You want the simplest, most intuitive API

```kotlin
val vericore = VeriCore.create()
val did = vericore.createDid()
val credential = vericore.issueCredential(...)
```

**Pros:** Simple, type-safe, sensible defaults  
**Cons:** Less configuration flexibility

### 2. **Wallets Factory** (For Credential Storage) 📦

**Use when:** You need to store and manage credentials

```kotlin
val wallet = Wallets.inMemory(holderDid = "did:key:alice")
wallet.store(credential)
val credentials = wallet.query { byType("PersonCredential") }
```

**Pros:** Clean API, no builder exceptions  
**Cons:** In-memory only (for now)

### 3. **Direct APIs** (For Advanced Users) ⚙️

**Use when:** You need fine-grained control or advanced features

```kotlin
val didMethod = DidKeyMockMethod(kms)
val doc = didMethod.createDid(options)
```

**Pros:** Maximum flexibility and control  
**Cons:** More verbose, requires manual setup

### 4. **DSL** (For Complex Workflows) 🎨

**Use when:** Building complex trust layer configurations

```kotlin
val trustLayer = trustLayer {
    keys { provider("inMemory") }
    did { method("key") }
}
```

**Pros:** Declarative, readable configuration  
**Cons:** Learning curve for DSL syntax

---

## Getting Started Examples

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

### Example: Creating a DID (New Simple API)

```kotlin
import io.geoknoesis.vericore.VeriCore
import kotlinx.coroutines.runBlocking

fun main() = runBlocking {
    // Create VeriCore instance
    val vericore = VeriCore.create()
    
    // Create a DID (uses default "key" method)
    val document = vericore.createDid()
    println("Created DID: ${document.id}")
    
    // Resolve the DID
    val result = vericore.resolveDid(document.id)
    println("Resolved document: ${result.document?.id}")
}
```

<details>
<summary>Advanced: Using Direct APIs</summary>

```kotlin
import io.geoknoesis.vericore.did.*
import io.geoknoesis.vericore.testkit.did.DidKeyMockMethod
import io.geoknoesis.vericore.testkit.kms.InMemoryKeyManagementService
import kotlinx.coroutines.runBlocking

fun main() = runBlocking {
    // Setup: Create KMS and DID method
    val kms = InMemoryKeyManagementService()
    val didMethod = DidKeyMockMethod(kms)
    
    // Create a DID with typed options
    val options = DidCreationOptions(
        algorithm = DidCreationOptions.KeyAlgorithm.ED25519,
        purposes = listOf(DidCreationOptions.KeyPurpose.AUTHENTICATION)
    )
    val document = didMethod.createDid(options)
    println("Created DID: ${document.id}")
}
```

</details>
```

### Example: Managing Credentials with Wallets (New Simple API)

```kotlin
import io.geoknoesis.vericore.credential.wallet.Wallets
import io.geoknoesis.vericore.credential.models.VerifiableCredential
import io.geoknoesis.vericore.credential.wallet.CredentialOrganization
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

fun main() = runBlocking {
    // Create a wallet using the new factory
    val wallet = Wallets.inMemory(holderDid = "did:key:holder")
    
    // Store a credential
    val credential = VerifiableCredential(
        type = listOf("VerifiableCredential", "PersonCredential"),
        issuer = "did:key:issuer",
        credentialSubject = buildJsonObject {
            put("id", "did:key:subject")
            put("name", "Alice")
            put("email", "alice@example.com")
        },
        issuanceDate = "2023-01-01T00:00:00Z"
    )
    
    val credentialId = wallet.store(credential)
    
    // Organize credentials
    if (wallet is CredentialOrganization) {
        val collection = wallet.createCollection("Work Credentials")
        wallet.addToCollection(credentialId, collection)
        wallet.tagCredential(credentialId, setOf("important", "verified"))
    }
    
    // Query credentials
    val credentials = wallet.query {
        byIssuer("did:key:issuer")
        byType("PersonCredential")
        notExpired()
        valid()
    }
    
    // Create a presentation
    if (wallet is CredentialPresentation) {
        val presentation = wallet.createPresentation(
            credentialIds = listOf(credentialId),
            holderDid = wallet.holderDid,
            options = PresentationOptions(
                holderDid = wallet.holderDid,
                proofType = "Ed25519Signature2020"
            )
        )
        println("Created presentation with ${presentation.verifiableCredential.size} credentials")
    }
    
    // Get wallet statistics
    val stats = wallet.getStatistics()
    println("Wallet has ${stats.totalCredentials} credentials")
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
val blockchainRegistry = BlockchainAnchorRegistry().apply {
    register("algorand:mainnet", client)
}
    
    // Create a digest object
    val digest = VerifiableCredentialDigest(
        vcId = "vc-12345",
        vcDigest = "uABC123..."
    )
    
    // Anchor it
val result = blockchainRegistry.anchorTyped(
        value = digest,
        serializer = VerifiableCredentialDigest.serializer(),
        targetChainId = "algorand:mainnet"
    )
    
    println("Anchored at: ${result.ref.txHash}")
    
    // Read it back
val retrieved = blockchainRegistry.readTyped<VerifiableCredentialDigest>(
        ref = result.ref,
        serializer = VerifiableCredentialDigest.serializer()
    )
    
    println("Retrieved: ${retrieved.vcId}")
}
```

### Example: Complete Workflow

```kotlin
import io.geoknoesis.vericore.anchor.AnchorResult
import io.geoknoesis.vericore.anchor.BlockchainAnchorRegistry
import io.geoknoesis.vericore.anchor.anchorTyped
import io.geoknoesis.vericore.anchor.readTyped
import io.geoknoesis.vericore.did.DidMethodRegistry
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
    
    val didRegistry = DidMethodRegistry().apply { register(didMethod) }
    val blockchainRegistry = BlockchainAnchorRegistry().apply {
        register("algorand:mainnet", anchorClient)
    }
    
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
    
    val anchorResult = blockchainRegistry.anchorTyped(
        value = digestObj,
        serializer = VerifiableCredentialDigest.serializer(),
        targetChainId = "algorand:mainnet"
    )
    
    println("Issuer DID: $issuerDid")
    println("VC Digest: $digest")
    println("Anchored at: ${anchorResult.ref.txHash}")
    
    // 6. Verify by reading back
    val retrieved = blockchainRegistry.readTyped<VerifiableCredentialDigest>(
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
- `DidMethodRegistry`: Instance-scoped registry for pluggable DID methods

### vericore-anchor

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
import io.geoknoesis.vericore.did.DidMethodRegistry
import kotlinx.coroutines.runBlocking

fun main() = runBlocking {
    // Automatically discover and register walt.id adapters
    val registry = DidMethodRegistry()
    val result = WaltIdIntegration.discoverAndRegister(registry)
    
    println("Registered DID methods: ${result.registeredDidMethods}")
    // Output: Registered DID methods: [key, web]
    
    // Use VeriCore APIs as normal - walt.id adapters are now registered
    val didDocument = registry.resolve("did:key:...")
}
```

### Manual Setup

You can also manually configure walt.id integration:

```kotlin
import io.geoknoesis.vericore.waltid.WaltIdIntegration
import io.geoknoesis.vericore.waltid.WaltIdKeyManagementService
import io.geoknoesis.vericore.did.DidMethodRegistry

fun main() = runBlocking {
    // Create walt.id KMS
    val kms = WaltIdKeyManagementService()
    val registry = DidMethodRegistry()
    
    // Setup integration with specific DID methods
    val result = WaltIdIntegration.setup(
        kms = kms,
        registry = registry,
        didMethods = listOf("key", "web")
    )
    
    // Create a DID using walt.id
    val keyMethod = registry.get("key")
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
import io.geoknoesis.vericore.did.DidMethodRegistry
import kotlinx.coroutines.runBlocking

fun main() = runBlocking {
    // Automatically discover and register godiddy adapters
    val registry = DidMethodRegistry()
    val result = GodiddyIntegration.discoverAndRegister(registry)
    
    println("Registered DID methods: ${result.registeredDidMethods}")
    // Output: Registered DID methods: [key, web, ion, ethr, ...]
    
    // Use VeriCore APIs as normal - godiddy adapters are now registered
    val didDocument = registry.resolve("did:key:...")
}
```

### Manual Setup with Custom Configuration

You can manually configure godiddy integration with a custom base URL:

```kotlin
import io.geoknoesis.vericore.godiddy.GodiddyIntegration
import io.geoknoesis.vericore.did.DidMethodRegistry

fun main() = runBlocking {
    // Setup integration with custom base URL (for self-hosted instances)
    val registry = DidMethodRegistry()
    val result = GodiddyIntegration.setup(
        baseUrl = "https://custom.godiddy.com",
        registry = registry,
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
    registry = DidMethodRegistry(),
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
import io.geoknoesis.vericore.did.DidMethodRegistry
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*

fun main() = runBlocking {
    val registry = DidMethodRegistry()
    val result = GodiddyIntegration.discoverAndRegister(registry)
    
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

## License

VeriCore is available under a dual-licensing model:

### Community License (AGPL v3.0)

VeriCore is available free of charge under the **GNU Affero General Public License v3.0** (AGPL v3.0) for:
- Open-source projects (OSI-approved licenses)
- Educational and research purposes
- Personal projects
- Non-commercial use

See [LICENSE](LICENSE) for the full AGPL v3.0 license text.

### Commercial License

For proprietary and commercial use, VeriCore is available under a **Commercial License** that includes:
- ✅ Use in proprietary/commercial software
- ✅ Distribution in commercial products
- ✅ Priority technical support
- ✅ Indemnification against IP claims
- ✅ Regular updates and security patches

**Commercial licensing inquiries:**
- Email: licensing@geoknoesis.com
- Website: https://geoknoesis.com/licensing

See [LICENSE-COMMERCIAL.md](LICENSE-COMMERCIAL.md) for commercial license terms.

### Which License Do I Need?

- **Open-source project** → Use AGPL v3.0 (free)
- **Commercial/proprietary software** → Purchase Commercial License
- **SaaS application** → Purchase Commercial License
- **Enterprise deployment** → Purchase Commercial License
- **Unsure?** → Contact us at licensing@geoknoesis.com

## Contributing

[Contributing guidelines]

## Changelog

See [CHANGELOG.md](CHANGELOG.md) for detailed information about changes, improvements, and migration guides.

## Additional Documentation

Comprehensive documentation is available in the [`docs/`](docs/) directory:

- **[Documentation Index](docs/DOCUMENTATION_INDEX.md)**: Complete documentation index
- **[Core Concepts](docs/core-concepts/README.md)**: Introduction to DIDs, VCs, Wallets, and more
- **[Wallet API Tutorial](docs/tutorials/wallet-api-tutorial.md)**: Complete guide to using wallets
- **[Use Case Scenarios](docs/getting-started/)**: Real-world scenarios including:
  - [Earth Observation](docs/getting-started/earth-observation-scenario.md) - Data integrity verification
  - [Academic Credentials](docs/getting-started/academic-credentials-scenario.md) - University credential system
  - [Professional Identity](docs/getting-started/professional-identity-scenario.md) - Professional credential wallet
  - [Proof of Location](docs/getting-started/proof-of-location-scenario.md) - Geospatial location proofs
  - [Spatial Web Authorization](docs/getting-started/spatial-web-authorization-scenario.md) - DID-based spatial authorization
- **[API Reference](docs/api-reference/)**: Detailed API documentation
- **[Getting Started](docs/getting-started/)**: Installation and quick start guides
- **[Optimization Summary](OPTIMIZATION_COMPLETE.md)**: Summary of codebase optimizations
- **[Module READMEs](vericore-anchor/src/main/kotlin/io/geoknoesis/vericore/anchor/README.md)**: Detailed module documentation


