# Architecture Overview

VeriCore follows a modular, pluggable architecture that enables flexibility and extensibility.

## Module Structure

VeriCore is organized into several modules:

```
vericore/
├── vericore-core          # Shared types and exceptions
├── vericore-json          # JSON canonicalization and digests
├── vericore-kms           # Key management abstraction
├── vericore-did           # DID and DID Document management
├── vericore-anchor        # Blockchain anchoring abstraction
├── vericore-testkit       # Test implementations
├── vericore-waltid        # walt.id integration (optional)
├── vericore-godiddy       # GoDiddy integration (optional)
├── vericore-algorand      # Algorand adapter (optional)
└── vericore-polygon       # Polygon adapter (optional)
```

## Core Modules

### vericore-core
- Base exception classes
- Common constants
- Shared types

### vericore-json
- JSON canonicalization
- Digest computation (SHA-256 + multibase)
- No dependencies on other VeriCore modules

### vericore-kms
- `KeyManagementService` interface
- Key generation, signing, retrieval
- Algorithm-agnostic design

### vericore-did
- `DidMethod` interface
- DID Document models (W3C compliant)
- `DidMethodRegistry` for method registration (instance-scoped)

### vericore-anchor
- `BlockchainAnchorClient` interface
- `AnchorRef` for chain-agnostic references
- `BlockchainAnchorRegistry` for client registration (instance-scoped)

### vericore-testkit
- In-memory implementations
- Test utilities
- Mock implementations for testing

## Integration Modules

### vericore-waltid
- walt.id-based KMS and DID methods
- SPI-based discovery
- Supports did:key and did:web

### vericore-godiddy
- HTTP integration with GoDiddy services
- Universal Resolver, Registrar, Issuer, Verifier
- Supports 20+ DID methods

### vericore-algorand
- Algorand blockchain adapter
- Mainnet and testnet support
- Implements `BlockchainAnchorClient`

### vericore-polygon
- Polygon blockchain adapter
- Implements `BlockchainAnchorClient`

## Design Patterns

### Scoped Registry Pattern

Registries are owned by the application context rather than global singletons:

```kotlin
val didRegistry = DidMethodRegistry().apply { register(didMethod) }
val blockchainRegistry = BlockchainAnchorRegistry().apply { register(chainId, client) }

val config = VeriCoreConfig(
    kms = kms,
    walletFactory = walletFactory,
    didRegistry = didRegistry,
    blockchainRegistry = blockchainRegistry,
    credentialRegistry = CredentialServiceRegistry.create()
)
val vericore = VeriCore.create(config)
```

### Service Provider Interface (SPI)

Adapters can be automatically discovered via Java ServiceLoader:

```kotlin
// Automatic discovery
val result = WaltIdIntegration.discoverAndRegister()
```

### Interface-Based Design

All external dependencies are abstracted through interfaces:

- `KeyManagementService` - Key operations
- `DidMethod` - DID operations
- `BlockchainAnchorClient` - Blockchain operations

## Data Flow

### DID Creation Flow

```
Application
    ↓
VeriCoreContext.getDidMethod("key")
    ↓
DidMethod.createDid()
    ↓
KeyManagementService.generateKey()
    ↓
DidDocument (returned)
```

### Blockchain Anchoring Flow

```
Application
    ↓
VeriCoreContext.getBlockchainClient("algorand:mainnet")
    ↓
BlockchainAnchorClient.writePayload()
    ↓
AnchorResult (with AnchorRef)
```

### Integrity Verification Flow

```
Blockchain Anchor
    ↓
Verifiable Credential (with digest)
    ↓
Linkset (with digest)
    ↓
Artifacts (with digests)
    ↓
Verification Result
```

## Dependencies

### Core Module Dependencies

```
vericore-core
    (no dependencies)

vericore-json
    → vericore-core

vericore-kms
    → vericore-core

vericore-did
    → vericore-core
    → vericore-kms

vericore-anchor
    → vericore-core
    → vericore-json

vericore-testkit
    → vericore-core
    → vericore-json
    → vericore-kms
    → vericore-did
    → vericore-anchor
```

### Integration Module Dependencies

```
vericore-waltid
    → vericore-core
    → vericore-kms
    → vericore-did

vericore-godiddy
    → vericore-core
    → vericore-did
    → vericore-kms
    → vericore-json

vericore-algorand
    → vericore-core
    → vericore-anchor
    → vericore-json

vericore-polygon
    → vericore-core
    → vericore-anchor
    → vericore-json
```

## Extensibility

### Adding a New DID Method

1. Implement `DidMethod` interface
2. Optionally implement `DidMethodProvider` for SPI
3. Register via `DidMethodRegistry.register()`

### Adding a New Blockchain Adapter

1. Implement `BlockchainAnchorClient` interface
2. Optionally implement `BlockchainAnchorClientProvider` for SPI
3. Register via `BlockchainAnchorRegistry.register()`

### Adding a New KMS Backend

1. Implement `KeyManagementService` interface
2. Optionally implement `KeyManagementServiceProvider` for SPI
3. Use directly or register via SPI

## Next Steps

- Learn about [Core Modules](modules/core-modules.md)
- Explore [Integration Modules](integrations/README.md)
- See [Examples](examples/README.md) for practical usage
- Review the [Trust Layer Setup Checklist](../core-concepts/trust-registry.md#trust-layer-setup-checklist) before wiring issuance or verification flows

