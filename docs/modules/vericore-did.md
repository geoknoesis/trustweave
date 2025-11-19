# vericore-did

The `vericore-did` module provides Decentralized Identifier (DID) and DID Document management with support for pluggable DID methods.

```kotlin
dependencies {
    implementation("com.geoknoesis.vericore:vericore-did:1.0.0-SNAPSHOT")
    implementation("com.geoknoesis.vericore:vericore-core:1.0.0-SNAPSHOT")
    implementation("com.geoknoesis.vericore:vericore-kms:1.0.0-SNAPSHOT")
}
```

**Result:** Gradle exposes the DID registry, DID method interfaces, and DID Document models so you can create, resolve, update, and deactivate DIDs using any supported DID method.

## Overview

The `vericore-did` module provides:

- **DidMethod Interface** – contract for DID method implementations
- **DID Document Models** – W3C-compliant DID Document structures
- **DidMethodRegistry** – instance-scoped registry for managing DID methods
- **DID Resolution** – unified interface for resolving DIDs across methods
- **DID Operations** – create, resolve, update, and deactivate operations
- **SPI Support** – service provider interface for auto-discovery of DID method implementations

## Key Components

### DidMethod Interface

```kotlin
import com.geoknoesis.vericore.did.*

interface DidMethod {
    val method: String  // e.g., "key", "web", "ion"
    
    suspend fun createDid(options: DidCreationOptions): DidDocument
    suspend fun resolveDid(did: String): DidResolutionResult
    suspend fun updateDid(did: String, updater: (DidDocument) -> DidDocument): DidDocument
    suspend fun deactivateDid(did: String): Boolean
}
```

**What this does:** Defines the contract for DID operations that all DID method implementations must fulfill.

**Outcome:** Enables VeriCore to support multiple DID methods (key, web, ethr, ion, etc.) through a unified interface.

### DidMethodRegistry

```kotlin
val registry = DidMethodRegistry()
registry.register("key", keyDidMethod)
registry.register("web", webDidMethod)

val method = registry.get("key")
val didDoc = method?.createDid(options)
```

**What this does:** Provides instance-scoped registration and retrieval of DID methods.

**Outcome:** Allows multiple DID methods to coexist in the same application context.

### DID Document Models

The module includes W3C-compliant models for:

- `DidDocument` – complete DID Document structure
- `VerificationMethod` – public key and verification methods
- `Service` – service endpoints
- `DidResolutionResult` – resolution response with metadata

**What this does:** Provides type-safe, serializable models for DID documents that comply with W3C DID Core specification.

**Outcome:** Ensures interoperability with other DID implementations and proper serialization.

## Usage Example

```kotlin
import com.geoknoesis.vericore.did.*
import com.geoknoesis.vericore.kms.*
import java.util.ServiceLoader

// Discover DID method provider via SPI
val providers = ServiceLoader.load(DidMethodProvider::class.java)
val keyProvider = providers.find { it.supportedMethods.contains("key") }

// Create DID method with KMS
val kms = InMemoryKeyManagementService()
val options = didCreationOptions {
    algorithm = KeyAlgorithm.Ed25519
}

val method = keyProvider?.create("key", options)
val didDoc = method?.createDid(options)

println("Created DID: ${didDoc.id}")

// Resolve DID
val resolutionResult = method?.resolveDid(didDoc.id)
println("Resolved DID: ${resolutionResult?.didDocument?.id}")
```

**What this does:** Uses SPI to discover a DID method provider, creates a DID using the did:key method, and then resolves it.

**Outcome:** Enables seamless DID operations across different DID methods.

## Supported DID Methods

VeriCore provides implementations for:

- **did:key** (`com.geoknoesis.vericore.did:key`) – Native did:key implementation. See [Key DID Integration Guide](../integrations/key-did.md).
- **did:web** (`com.geoknoesis.vericore.did:web`) – Web DID method. See [Web DID Integration Guide](../integrations/web-did.md).
- **did:ethr** (`com.geoknoesis.vericore.did:ethr`) – Ethereum DID method. See [Ethereum DID Integration Guide](../integrations/ethr-did.md).
- **did:ion** (`com.geoknoesis.vericore.did:ion`) – Microsoft ION DID method. See [ION DID Integration Guide](../integrations/ion-did.md).
- **did:jwk** (`com.geoknoesis.vericore.did:jwk`) – JWK DID method. See [JWK DID Integration Guide](../integrations/jwk-did.md).
- **did:peer** (`com.geoknoesis.vericore.did:peer`) – Peer DID method. See [Peer DID Integration Guide](../integrations/peer-did.md).
- **did:plc** (`com.geoknoesis.vericore.did:plc`) – PLC DID method. See [PLC DID Integration Guide](../integrations/plc-did.md).
- **did:ens** (`com.geoknoesis.vericore.did:ens`) – ENS DID method. See [ENS DID Integration Guide](../integrations/ens-did.md).
- **did:polygon** (`com.geoknoesis.vericore.did:polygon`) – Polygon DID method. See [Polygon DID Integration Guide](../integrations/polygon-did.md).
- **did:sol** (`com.geoknoesis.vericore.did:sol`) – Solana DID method. See [Solana DID Integration Guide](../integrations/sol-did.md).
- **did:cheqd** (`com.geoknoesis.vericore.did:cheqd`) – Cheqd DID method. See [Cheqd DID Integration Guide](../integrations/cheqd-did.md).

See the [DID Integration Guides](../integrations/README.md) for detailed information about each method.

## Dependencies

- Depends on [`vericore-core`](vericore-core.md) for core types and exceptions
- Depends on [`vericore-kms`](vericore-kms.md) for key operations
- Depends on [`vericore-spi`](vericore-spi.md) for service abstractions

## Next Steps

- Review [DID Concepts](../core-concepts/dids.md) for understanding DIDs
- Explore [DID Integration Guides](../integrations/README.md) for specific method setups
- See [DID Operations Tutorial](../tutorials/did-operations-tutorial.md) for step-by-step examples
- Check [Creating Plugins](../contributing/creating-plugins.md) to implement custom DID methods

