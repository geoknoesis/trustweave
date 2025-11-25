# TrustWeave

> The Foundation for Decentralized Trust and Identity

A **neutral, reusable trust and identity core** library for Kotlin, designed to be domain-agnostic, chain-agnostic, Decentralized Identifier (DID)-method-agnostic, and Key Management Service (KMS)-agnostic.

## Quick Start (30 Seconds) ⚡

```kotlin
import com.trustweave.TrustWeave
import com.trustweave.did.didCreationOptions
import com.trustweave.spi.services.WalletCreationOptionsBuilder
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

fun main() = runBlocking {
    val TrustWeave = trustweave.create()

    val didDocument = trustweave.createDid(
        method = "key",
        options = didCreationOptions {
            algorithm = com.trustweave.did.DidCreationOptions.KeyAlgorithm.ED25519
        }
    ).getOrThrow()

    val credential = trustweave.issueCredential(
        issuerDid = didDocument.id,
        issuerKeyId = didDocument.id,
        credentialSubject = buildJsonObject {
            put("id", "did:key:subject")
            put("name", "Alice")
        },
        types = listOf("PersonCredential")
    ).getOrThrow()

    val verification = trustweave.verifyCredential(credential).getOrThrow()
    println("Credential valid: ${verification.valid}")

    val wallet = trustweave.createWallet(
        holderDid = "did:key:alice"
    ) {
        label = "Alice Wallet"
        enablePresentation = true
        property("autoUnlock", true)
    }.getOrThrow()

    wallet.store(credential)
    println("✅ Stored credential in ${wallet.walletId}")
}
```

## Developer Experience Highlights ✨

- **Typed configuration builders everywhere.** Create Decentralized Identifiers (DIDs), wallets, and service adapters with compile-time safe options (`didCreationOptions { … }`, `WalletCreationOptionsBuilder`, `credentialServiceCreationOptions { … }`).
- **Predictable error handling.** All facade operations return `Result<T>` so you decide whether to `getOrThrow()`, fold, or map errors.
- **Composable DSLs.** Configure the Trust Layer once and reuse it for workflows: credential issuance, verification, wallets, delegation.
- **Service Provider Interface (SPI)-ready from day one.** Drop in your own `WalletFactory` or `CredentialServiceProvider` without reflection or map juggling.

```kotlin
import com.trustweave.spi.services.credentialServiceCreationOptions

val options = credentialServiceCreationOptions {
    enabled = true
    endpoint = "https://issuer.example.com"
    apiKey = System.getenv("ISSUER_API_KEY")
    property("batchSize", 100)
}
```

## Installation

Add TrustWeave to your project:

```kotlin
dependencies {
    // All-in-one dependency (recommended)
    implementation("com.trustweave:TrustWeave-core:1.0.0-SNAPSHOT")
    implementation("com.trustweave:TrustWeave-testkit:1.0.0-SNAPSHOT")  // For testing
}
```

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
- DID plugins: `com.trustweave.did:*`
- KMS plugins: `com.trustweave.kms:*`
- Chain plugins: `com.trustweave.chains:*`

## Key Features

- **Domain-agnostic**: No application-specific logic in core modules
- **Chain-agnostic**: Pluggable blockchain adapters via interfaces
- **Decentralized Identifier (DID)-method-agnostic**: Pluggable DID methods via interfaces
- **Key Management Service (KMS)-agnostic**: Pluggable key management backends
- **Coroutine-based**: All I/O operations use Kotlin coroutines (`suspend` functions)
- **Type-safe**: Uses Kotlinx Serialization for type-safe JSON handling

## Which API Should I Use? 🤔

TrustWeave provides multiple APIs for different use cases:

### 1. **TrustWeave Facade** (Recommended for Most Users) ✅

**Use when:** You want the simplest, most intuitive API

```kotlin
val TrustWeave = trustweave.create()

// Simple usage with error handling
val didResult = trustweave.createDid()
didResult.fold(
    onSuccess = { did -> println("Created: ${did.id}") },
    onFailure = { error -> println("Error: ${error.message}") }
)

// Or use getOrThrow for simple cases
val did = trustweave.createDid().getOrThrow()
val credential = trustweave.issueCredential(...).getOrThrow()
```

**Pros:** Simple, type-safe, sensible defaults, consistent error handling  
**Cons:** Less configuration flexibility

### 2. **Wallets & Wallet Factory** (For Credential Storage) 📦

**Use when:** You need type-safe credential storage with optional capabilities

```kotlin
val wallet = trustweave.createWallet(
    holderDid = "did:key:alice"
) {
    label = "Alice Wallet"
    enableOrganization = true
    enablePresentation = true
    property("storagePath", "/var/lib/wallets/alice")
}.getOrThrow()
```

Need an in-memory instance for tests? `Wallets.inMemory(holderDid)` still exists for quick fixtures.

**Pros:** Typed options, capability toggles, no reflection  
**Cons:** Provide your own `WalletFactory` for production storage

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

### Typed Configuration Building Blocks

| What | Builder | Example |
|------|---------|---------|
| DID creation | `didCreationOptions { … }` | `algorithm = DidCreationOptions.KeyAlgorithm.ED25519` |
| Wallet creation | `WalletCreationOptionsBuilder` via `createWallet { … }` | `enablePresentation = true; property("storagePath", "/data/wallets")` |
| Credential services | `credentialServiceCreationOptions { … }` | `endpoint = "https://issuer.example.com"; apiKey = secret` |

Every builder lives in a public package, so the same typed DSL is available whether you call the facade, wire up a `TrustLayerConfig`, or implement your own SPI provider.

---

## Getting Started Examples

### Example: Computing a JSON Digest

```kotlin
import com.trustweave.json.DigestUtils
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
import com.trustweave.TrustWeave
import com.trustweave.core.*
import com.trustweave.did.*
import kotlinx.coroutines.runBlocking

fun main() = runBlocking {
    // Create TrustWeave instance
    val TrustWeave = trustweave.create()
    
    // Use native did:key plugin (most widely-used)
    // Add dependency: implementation("com.trustweave.did:key:1.0.0-SNAPSHOT")
    
    // Create a DID with error handling
    val document = trustweave.dids.create()
    println("Created DID: ${document.id}")
    
    // Resolve the DID
    val resolution = trustweave.dids.resolve(document.id)
    val resolveResult = Result.success(resolution)
    resolveResult.fold(
        onSuccess = { resolution ->
            resolveResult.fold(
                onSuccess = { resolution ->
                    if (resolution.document != null) {
                        println("Resolved document: ${resolution.document.id}")
                    } else {
                        println("DID not found")
                    }
                },
                onFailure = { error ->
                    when (error) {
                        is TrustWeaveError.DidNotFound -> {
                            println("DID not found: ${error.did}")
                        }
                        is TrustWeaveError.InvalidDidFormat -> {
                            println("Invalid DID format: ${error.reason}")
                        }
                        else -> {
                            println("Error resolving DID: ${error.message}")
                        }
                    }
                }
            )
        },
        onFailure = { error ->
            when (error) {
                is TrustWeaveError.DidMethodNotRegistered -> {
                    println("DID method not registered: ${error.method}")
                    println("Available methods: ${error.availableMethods}")
                }
                else -> {
                    println("Error creating DID: ${error.message}")
                }
            }
        }
    )
    
    // Or use simple API for straightforward cases
    // Use native did:key (most widely-used)
    val document2 = trustweave.dids.create("key") {
        algorithm = KeyAlgorithm.ED25519
    }
    println("Created DID: ${document.id}")
}
```

<details>
<summary>Advanced: Using Direct APIs</summary>

```kotlin
import com.trustweave.did.*
import com.trustweave.testkit.did.DidKeyMockMethod
import com.trustweave.testkit.kms.InMemoryKeyManagementService
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
import com.trustweave.TrustWeave
import com.trustweave.credential.wallet.WalletProvider
import com.trustweave.credential.wallet.Wallets
import com.trustweave.credential.models.VerifiableCredential
import com.trustweave.credential.wallet.CredentialOrganization
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

    // Or create a custom wallet via TrustWeave with typed options
    val TrustWeave = trustweave.create()
    val customWallet = trustweave.createWallet(
        holderDid = "did:key:holder",
        provider = WalletProvider.custom("postgres")
    ) {
        label = "Production Wallet"
        storagePath = "/var/lib/wallets/holder"
        property("connectionString", System.getenv("WALLET_DB_URL"))
    }.getOrThrow()
    println("Custom wallet provider registered: ${customWallet.javaClass.simpleName}")
    
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

### Example: Anchoring Data to Multiple Blockchains (Ethereum, Base, Arbitrum)

You can anchor data to multiple EVM-compatible chains using TrustWeave's chain-agnostic interface:

```kotlin
import com.trustweave.TrustWeave
import com.trustweave.ethereum.*
import com.trustweave.base.*
import com.trustweave.arbitrum.*
import com.trustweave.testkit.anchor.InMemoryBlockchainAnchorClient
import kotlinx.serialization.json.*

val TrustWeave = trustweave.create {
    blockchain {
        // Register multiple blockchain adapters
        register(EthereumBlockchainAnchorClient.MAINNET, InMemoryBlockchainAnchorClient(EthereumBlockchainAnchorClient.MAINNET))
        register(BaseBlockchainAnchorClient.MAINNET, InMemoryBlockchainAnchorClient(BaseBlockchainAnchorClient.MAINNET))
        register(ArbitrumBlockchainAnchorClient.MAINNET, InMemoryBlockchainAnchorClient(ArbitrumBlockchainAnchorClient.MAINNET))
    }
}

// Anchor to Ethereum (highest security, higher fees)
val ethereumResult = trustweave.anchor(EthereumBlockchainAnchorClient.MAINNET, payload).getOrThrow()

// Anchor to Base (Coinbase L2, lower fees, fast)
val baseResult = trustweave.anchor(BaseBlockchainAnchorClient.MAINNET, payload).getOrThrow()

// Anchor to Arbitrum (largest L2 by TVL, lower fees)
val arbitrumResult = trustweave.anchor(ArbitrumBlockchainAnchorClient.MAINNET, payload).getOrThrow()
```

### Example: Anchoring Data to a Blockchain (Original)

```kotlin
import com.trustweave.anchor.*
import com.trustweave.testkit.anchor.InMemoryBlockchainAnchorClient
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
import com.trustweave.anchor.AnchorResult
import com.trustweave.anchor.BlockchainAnchorRegistry
import com.trustweave.anchor.anchorTyped
import com.trustweave.anchor.readTyped
import com.trustweave.did.DidMethodRegistry
import com.trustweave.json.DigestUtils
import com.trustweave.testkit.anchor.InMemoryBlockchainAnchorClient
import com.trustweave.testkit.did.DidKeyMockMethod
import com.trustweave.testkit.kms.InMemoryKeyManagementService
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
    val issuerDoc = didMethod.createDid()
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

### TrustWeave-core

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
import com.trustweave.json.DigestUtils

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
import com.trustweave.anchor.options.AlgorandOptions
import com.trustweave.anchor.ChainId

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
import com.trustweave.anchor.exceptions.*

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
import com.trustweave.testkit.TrustWeaveTestFixture

TrustWeaveTestFixture.builder()
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
./gradlew :TrustWeave-ganache:test --tests "*GanacheEoIntegrationTest*"
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
- **Docker** (optional): Required for `com.trustweave.chains:ganache` tests using TestContainers

## Design Principles

1. **Neutrality**: Core modules contain no domain-specific or chain-specific logic
2. **Pluggability**: All external dependencies (blockchains, DID methods, KMS) are pluggable via interfaces
3. **Coroutines**: All I/O operations use Kotlin coroutines for async/await patterns
4. **Type Safety**: Leverages Kotlinx Serialization for type-safe JSON handling, sealed classes for options and chain IDs
5. **Testability**: Provides test implementations for all interfaces
6. **Code Quality**: Consistent code formatting via ktlint, comprehensive error handling
7. **Performance**: Optimized JSON operations and configurable digest caching
8. **Resource Management**: Proper cleanup for network clients (Closeable interface)

## Available Plugins

TrustWeave ships with comprehensive plugin support:

### DID Method Plugins

- **`com.trustweave.did:key`** - Native did:key implementation (most widely-used DID method). See [Key DID Integration Guide](docs/integrations/key-did.md).
- **`com.trustweave.did:web`** - Web DID method for HTTP/HTTPS-based resolution. See [Web DID Integration Guide](docs/integrations/web-did.md).
- **`com.trustweave.did:ethr`** - Ethereum DID method with blockchain anchoring. See [Ethereum DID Integration Guide](docs/integrations/ethr-did.md).
- **`com.trustweave.did:ion`** - Microsoft ION DID method using Sidetree protocol. See [ION DID Integration Guide](docs/integrations/ion-did.md).
- **`com.trustweave.did:polygon`** - Polygon DID method (lower fees than Ethereum). See [Polygon DID Integration Guide](docs/integrations/polygon-did.md).
- **`com.trustweave.did:sol`** - Solana DID method with program integration. See [Solana DID Integration Guide](docs/integrations/sol-did.md).
- **`com.trustweave.did:peer`** - Peer-to-peer DID method (no external registry). See [Peer DID Integration Guide](docs/integrations/peer-did.md).
- **`com.trustweave.did:jwk`** - W3C-standard did:jwk using JSON Web Keys directly. See [JWK DID Integration Guide](docs/integrations/jwk-did.md).
- **`com.trustweave.did:ens`** - Ethereum Name Service (ENS) resolver integration. See [ENS DID Integration Guide](docs/integrations/ens-did.md).
- **`com.trustweave.did:plc`** - Personal Linked Container (PLC) for AT Protocol. See [PLC DID Integration Guide](docs/integrations/plc-did.md).
- **`com.trustweave.did:cheqd`** - Cheqd network DID method with payment features. See [Cheqd DID Integration Guide](docs/integrations/cheqd-did.md).

### Blockchain Anchor Plugins

- **`com.trustweave.chains:ethereum`** - Ethereum mainnet anchoring with Sepolia testnet support. See [Ethereum Anchor Integration Guide](docs/integrations/ethereum-anchor.md).
- **`com.trustweave.chains:base`** - Base (Coinbase L2) anchoring with fast confirmations and lower fees. See [Base Anchor Integration Guide](docs/integrations/base-anchor.md).
- **`com.trustweave.chains:arbitrum`** - Arbitrum One (largest L2 by TVL) anchoring. See [Arbitrum Anchor Integration Guide](docs/integrations/arbitrum-anchor.md).
- **`com.trustweave.chains:algorand`** - Algorand blockchain anchoring. See [Algorand Integration Guide](docs/integrations/algorand.md).
- **`com.trustweave.chains:polygon`** - Polygon blockchain anchoring. See [Integration Modules](docs/integrations/README.md#blockchain-anchor-integrations).
- **`com.trustweave.chains:ganache`** - Local developer anchoring using Ganache. See [Integration Modules](docs/integrations/README.md#blockchain-anchor-integrations).

### Key Management Service Plugins

- **`com.trustweave.kms:aws`** - AWS Key Management Service integration. See [AWS KMS Integration Guide](docs/integrations/aws-kms.md).
- **`com.trustweave.kms:azure`** - Azure Key Vault integration. See [Azure KMS Integration Guide](docs/integrations/azure-kms.md).
- **`com.trustweave.kms:google`** - Google Cloud KMS integration. See [Google KMS Integration Guide](docs/integrations/google-kms.md).
- **`com.trustweave.kms:hashicorp`** - HashiCorp Vault Transit engine integration. See [HashiCorp Vault KMS Integration Guide](docs/integrations/hashicorp-vault-kms.md).

See [Supported Plugins](docs/plugins.md) for a comprehensive table view of all plugins, or [Integration Modules](docs/integrations/README.md) for detailed integration guides.

## walt.id Integration

TrustWeave includes an optional `TrustWeave-waltid` module that provides walt.id-based implementations of TrustWeave interfaces using the SPI (Service Provider Interface) pattern.

### Using walt.id Adapters

Add the walt.id adapter module to your dependencies:

```kotlin
dependencies {
    implementation("com.trustweave:TrustWeave-waltid:1.0.0-SNAPSHOT")
}
```

### Automatic Discovery via SPI

walt.id adapters are automatically discovered via Java ServiceLoader:

```kotlin
import com.trustweave.waltid.WaltIdIntegration
import com.trustweave.did.DidMethodRegistry
import kotlinx.coroutines.runBlocking

fun main() = runBlocking {
    // Automatically discover and register walt.id adapters
    val registry = DidMethodRegistry()
    val result = WaltIdIntegration.discoverAndRegister(registry)
    
    println("Registered DID methods: ${result.registeredDidMethods}")
    // Output: Registered DID methods: [key, web]
    
    // Use TrustWeave APIs as normal - walt.id adapters are now registered
    val didDocument = registry.resolve("did:key:...")
}
```

### Manual Setup

You can also manually configure walt.id integration:

```kotlin
import com.trustweave.waltid.WaltIdIntegration
import com.trustweave.waltid.WaltIdKeyManagementService
import com.trustweave.did.DidMethodRegistry

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
    val document = keyMethod!!.createDid()
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

TrustWeave defines SPI interfaces for adapter discovery:

- `KeyManagementServiceProvider`: For KMS implementations
- `DidMethodProvider`: For DID method implementations

These interfaces allow any adapter module to be discovered automatically at runtime.

## godiddy Integration

TrustWeave includes an optional `TrustWeave-godiddy` module that provides HTTP-based integration with godiddy services (Universal Resolver, Universal Registrar, Universal Issuer, Universal Verifier) using the SPI pattern.

### Using godiddy Adapters

Add the godiddy adapter module to your dependencies:

```kotlin
dependencies {
    implementation("com.trustweave:TrustWeave-godiddy:1.0.0-SNAPSHOT")
}
```

### Automatic Discovery via SPI

godiddy adapters are automatically discovered via Java ServiceLoader:

```kotlin
import com.trustweave.godiddy.GodiddyIntegration
import com.trustweave.did.DidMethodRegistry
import kotlinx.coroutines.runBlocking

fun main() = runBlocking {
    // Automatically discover and register godiddy adapters
    val registry = DidMethodRegistry()
    val result = GodiddyIntegration.discoverAndRegister(registry)
    
    println("Registered DID methods: ${result.registeredDidMethods}")
    // Output: Registered DID methods: [key, web, ion, ethr, ...]
    
    // Use TrustWeave APIs as normal - godiddy adapters are now registered
    val didDocument = registry.resolve("did:key:...")
}
```

### Manual Setup with Custom Configuration

You can manually configure godiddy integration with a custom base URL:

```kotlin
import com.trustweave.godiddy.GodiddyIntegration
import com.trustweave.did.DidMethodRegistry

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
import com.trustweave.godiddy.GodiddyIntegration
import com.trustweave.did.DidMethodRegistry
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

TrustWeave is available under a dual-licensing model:

### Community License (AGPL v3.0)

TrustWeave is available free of charge under the **GNU Affero General Public License v3.0** (AGPL v3.0) for:
- Open-source projects (OSI-approved licenses)
- Educational and research purposes
- Personal projects
- Non-commercial use

See [LICENSE](LICENSE) for the full AGPL v3.0 license text.

### Commercial License

For proprietary and commercial use, TrustWeave is available under a **Commercial License** that includes:
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

## Funding & Support

TrustWeave is an open-source project built and maintained by [Geoknoesis LLC](https://www.geoknoesis.com). If you find TrustWeave useful, please consider supporting its development:

- **GitHub Sponsors** - Support us directly on GitHub (click the "Sponsor" button)
- **Open Collective** - Transparent financial management for organizations
- **Buy Me a Coffee** - Simple one-time donations
- **Commercial Licensing** - For enterprise use, see [Commercial License](LICENSE-COMMERCIAL.md)

Your support helps us:
- Continue core feature development
- Maintain and improve documentation
- Support the community
- Keep TrustWeave free and open source

For detailed information about funding options, see [Funding Plan](docs/FUNDING_PLAN.md).

## Security

Security vulnerabilities should be reported privately. See [SECURITY.md](SECURITY.md) for our security policy and reporting guidelines.

**Report security issues to:** security@geoknoesis.com

## Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md) for guidelines on how to contribute to TrustWeave.

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
- **[Credential Service API](docs/api-reference/credential-service-api.md)**: Typed SPI for issuers and verifiers
- **[Getting Started](docs/getting-started/)**: Installation and quick start guides
- **[Optimization Summary](OPTIMIZATION_COMPLETE.md)**: Summary of codebase optimizations
- **[Module READMEs](TrustWeave-anchor/src/main/kotlin/io/geoknoesis/TrustWeave/anchor/README.md)**: Detailed module documentation



