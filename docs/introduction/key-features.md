# Key Features

VeriCore provides a comprehensive set of features for building decentralized identity and trust systems.

## Core Capabilities

### 1. Decentralized Identifiers (DIDs)

- **Pluggable DID Methods**: Support for any DID method via the `DidMethod` interface
- **DID Document Management**: W3C DID Core-compliant document handling
- **DID Resolution**: Chain-agnostic DID resolution through the registry pattern
- **Multiple Method Support**: Works with did:key, did:web, did:ion, did:algo, and more

### 2. Blockchain Anchoring

- **Chain-Agnostic Interface**: Write once, anchor to any blockchain
- **CAIP-2 Compatible**: Uses Chain Agnostic Improvement Proposal 2 for chain identification
- **Type-Safe Anchoring**: Helper functions for type-safe payload anchoring
- **Read/Write Operations**: Both anchoring and reading anchored data

### 3. Key Management

- **KMS Abstraction**: Pluggable key management service interface
- **Multiple Algorithms**: Support for Ed25519, secp256k1, and extensible to others
- **Key Operations**: Generate, retrieve, sign, and delete keys
- **Public Key Formats**: Support for JWK and multibase formats

### 4. JSON Canonicalization

- **Stable Ordering**: Canonical JSON with lexicographically sorted keys
- **Digest Computation**: SHA-256 digest with multibase encoding (base58btc)
- **Consistent Hashing**: Same content always produces the same digest
- **Nested Structures**: Handles complex nested JSON objects and arrays

### 5. Service Provider Interface (SPI)

- **Automatic Discovery**: Adapters discovered via Java ServiceLoader
- **Runtime Registration**: Register adapters without code changes
- **Modular Design**: Each adapter module is independent and optional

## Technical Features

### Coroutine-Based

All I/O operations use Kotlin coroutines (`suspend` functions), enabling:

- Non-blocking operations
- Easy composition of async operations
- Integration with Kotlin's concurrency model

### Type-Safe JSON

Uses Kotlinx Serialization for:

- Type-safe JSON serialization/deserialization
- Compile-time type checking
- Reduced runtime errors

### Testability

Comprehensive test support:

- In-memory implementations for all interfaces
- Test utilities for common scenarios
- Integration test helpers

## Integration Modules

### walt.id Integration

- Key management via walt.id
- DID methods: did:key, did:web
- Automatic SPI discovery

### GoDiddy Integration

- Universal Resolver for DID resolution
- Universal Registrar for DID creation
- Universal Issuer for VC issuance
- Universal Verifier for VC verification
- Support for 20+ DID methods

### Blockchain Adapters

- **Algorand**: Full support for Algorand mainnet and testnet
- **Polygon**: Polygon blockchain integration
- Extensible to any blockchain via the `BlockchainAnchorClient` interface

## Benefits

1. **Flexibility**: Mix and match components based on your needs
2. **Portability**: Switch between implementations without code changes
3. **Testability**: Easy to test with in-memory implementations
4. **Maintainability**: Clear separation of concerns and interfaces
5. **Extensibility**: Easy to add new adapters and implementations

## Next Steps

- See [Use Cases](use-cases.md) for real-world applications
- Explore the [Architecture Overview](architecture-overview.md)
- Get started with [Installation](getting-started/installation.md)

