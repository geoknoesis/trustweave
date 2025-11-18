# Key Features

VeriCore provides a comprehensive set of features for building decentralized identity and trust systems.

## Core Capabilities

### 1. Decentralized Identifiers (DIDs)

- **Pluggable DID Methods**: Support for any DID method via the `DidMethod` interface
- **DID Document Management**: W3C DID Core-compliant document handling with all verification relationships
- **DID Resolution**: Chain-agnostic DID resolution through the registry pattern
- **Multiple Method Support**: Works with did:key, did:web, did:ion, did:algo, and more
- **Verification Relationships**: Support for authentication, assertionMethod, keyAgreement, capabilityInvocation, and capabilityDelegation
- **JSON-LD Context**: Full support for JSON-LD contexts in DID Documents
- **DID Document Metadata**: Structured metadata with temporal fields (created, updated, etc.)

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

### 6. Web of Trust

- **Trust Registry**: Manage trust anchors and discover trust paths between DIDs
- **Trust Path Discovery**: Find trust relationships using graph traversal algorithms
- **Trust Scores**: Calculate trust scores based on path length and anchor strength
- **Credential Type Filtering**: Filter trust anchors by credential type
- **Integration with Verification**: Built-in trust registry checking during credential verification

### 7. Delegation Chains

- **Capability Delegation**: Delegate credential issuance and other capabilities to other DIDs
- **Multi-Hop Delegation**: Support for hierarchical delegation chains
- **Delegation Verification**: Verify delegation chains end-to-end
- **Integration with Credentials**: Automatic delegation checking for delegated credentials

### 8. Proof Purpose Validation

- **Purpose Verification**: Validate that proof purposes match DID Document verification relationships
- **Relationship Checking**: Ensure proofs are used only for their intended purposes
- **W3C Compliance**: Full compliance with W3C DID Core proof purpose requirements

### 9. Error Handling

- **Structured Error Types**: Sealed hierarchy of `VeriCoreError` types with context
- **Result-Based API**: All operations return `Result<T>` for consistent error handling
- **Input Validation**: Automatic validation of DIDs, credentials, and chain IDs
- **Error Context**: Rich context information for debugging and error recovery

### 10. Plugin Lifecycle Management

- **Lifecycle Methods**: Initialize, start, stop, and cleanup plugins
- **Automatic Discovery**: VeriCore automatically discovers plugins that implement `PluginLifecycle`
- **Error Handling**: Lifecycle methods return `Result<Unit>` for error handling
- **Plugin Configuration**: Support for plugin-specific configuration during initialization

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

