---
title: TrustWeave Glossary
nav_exclude: true
---

# TrustWeave Glossary

Standard terminology used throughout TrustWeave documentation.

> **Version:** 1.0.0-SNAPSHOT

## Core Terms

### Facade
The **TrustWeave** class is the main entry point (facade pattern). It provides a unified API for all TrustWeave operations.

**Example:**
```kotlin
val trustweave = TrustWeave.create()
val did = trustweave.dids.create()
```

**Related:** [Core API Reference](api-reference/core-api.md)

### Service
Concrete implementations that perform operations:
- **CredentialService**: Issues and verifies credentials
- **WalletService**: Manages wallet operations
- **KeyManagementService**: Handles cryptographic keys

**Example:**
```kotlin
class MyCredentialService : CredentialService {
    override suspend fun issueCredential(...): VerifiableCredential
}
```

**Related:** [Credential Service API](api-reference/credential-service-api.md)

### Provider
Factory pattern implementations that create service instances:
- **CredentialServiceProvider**: Creates credential services
- **WalletFactory**: Creates wallet instances
- **DidMethodProvider**: Creates DID method implementations

**Example:**
```kotlin
class MyWalletFactory : WalletFactory {
    override suspend fun create(...): Wallet
}
```

**Related:** [Wallet API](api-reference/wallet-api.md)

### Registry
Collections that manage multiple implementations:
- **DidMethodRegistry**: Manages DID methods (did:key, did:web, etc.)
- **BlockchainAnchorRegistry**: Manages blockchain clients
- **CredentialServiceRegistry**: Manages credential services

**Example:**
```kotlin
val registry = DidMethodRegistry()
registry.register(DidKeyMethod())
registry.register(DidWebMethod())
```

**Related:** [Core Concepts](core-concepts/dids.md)

## DID Terms

### DID (Decentralized Identifier)
A self-sovereign identifier following the `did:method:identifier` pattern.

**Example:** `did:key:z6MkhaXgBZDvotDkL5257faiztiGiC2QtKLGpbnnEGta2doK`

**Related:** [DIDs](core-concepts/dids.md)

### DID Method
Implementation of a specific DID method specification (e.g., did:key, did:web, did:ion).

**Example:**
```kotlin
class DidKeyMethod : DidMethod {
    override val methodName: String = "key"
}
```

**Related:** [DIDs](core-concepts/dids.md)

### DID Document
JSON-LD structure containing public keys, services, and capabilities for a DID.

**Related:** [DIDs](core-concepts/dids.md)

## Credential Terms

### Verifiable Credential (VC)
Tamper-evident attestation following the W3C VC Data Model.

**Related:** [Verifiable Credentials](core-concepts/verifiable-credentials.md)

### Credential Subject
The entity about which claims are being made in a credential.

**Example:**
```kotlin
buildJsonObject {
    put("id", "did:key:holder")
    put("name", "Alice")
    put("degree", "B.S. Computer Science")
}
```

**Related:** [Verifiable Credentials](core-concepts/verifiable-credentials.md)

### Issuer
Entity that creates and signs a verifiable credential.

**Related:** [Verifiable Credentials](core-concepts/verifiable-credentials.md)

### Holder
Entity that receives and stores a verifiable credential.

**Related:** [Wallets](core-concepts/wallets.md)

### Verifier
Entity that verifies a verifiable credential or presentation.

**Related:** [Verifiable Credentials](core-concepts/verifiable-credentials.md)

### Proof
Cryptographic signature binding the issuer to the credential content.

**Related:** [Verifiable Credentials](core-concepts/verifiable-credentials.md)

## Wallet Terms

### Wallet
Container for storing and managing verifiable credentials and DIDs.

**Related:** [Wallets](core-concepts/wallets.md)

### Wallet Provider
Implementation that creates wallet instances (InMemory, Database, etc.).

**Example:**
```kotlin
val wallet = trustweave.wallets.create(
    holderDid = "did:key:holder",
    type = WalletType.InMemory
)
```

**Related:** [Wallet API](api-reference/wallet-api.md)

### Capability
Optional interface that a wallet may implement (e.g., CredentialOrganization, CredentialPresentation).

**Example:**
```kotlin
if (wallet.supports(CredentialOrganization::class)) {
    wallet.withOrganization { org ->
        org.createCollection("Education")
    }
}
```

**Related:** [Wallet API](api-reference/wallet-api.md)

## Blockchain Terms

### Blockchain Anchoring
Process of writing data digests to a blockchain for tamper evidence and timestamping.

**Related:** [Blockchain Anchoring](core-concepts/blockchain-anchoring.md)

### Anchor Client
Implementation that writes data to a specific blockchain.

**Example:**
```kotlin
class AlgorandAnchorClient : BlockchainAnchorClient {
    override suspend fun writePayload(payload: JsonElement): AnchorResult
}
```

**Related:** [Blockchain Anchoring](core-concepts/blockchain-anchoring.md)

### Chain ID
Identifier for a blockchain network following CAIP-2 format (e.g., `algorand:testnet`).

**Related:** [Blockchain Anchoring](core-concepts/blockchain-anchoring.md)

## Key Management Terms

### Key Management Service (KMS)
Service that manages cryptographic keys (generation, storage, signing).

**Example:**
```kotlin
class InMemoryKeyManagementService : KeyManagementService {
    override suspend fun generateKey(...): KeyInfo
}
```

**Related:** [Key Management](core-concepts/key-management.md)

### Key ID
Identifier for a specific key within a DID document.

**Example:** `did:key:z6Mk...#key-1`

**Related:** [Key Management](core-concepts/key-management.md)

## Plugin Terms

### Plugin
Extensible component that implements TrustWeave interfaces (DID methods, anchor clients, etc.).

**Related:** [Plugin Lifecycle](advanced/plugin-lifecycle.md)

### Plugin Lifecycle
Methods for initializing, starting, stopping, and cleaning up plugins.

**Example:**
```kotlin
interface PluginLifecycle {
    suspend fun initialize(config: Map<String, Any?>): Boolean
    suspend fun start(): Boolean
    suspend fun stop(): Boolean
    suspend fun cleanup()
}
```

**Related:** [Plugin Lifecycle](advanced/plugin-lifecycle.md)

## Error Terms

### TrustWeaveError
Sealed hierarchy of structured error types with context.

**Example:**
```kotlin
TrustWeaveError.DidMethodNotRegistered(
    method = "web",
    availableMethods = listOf("key")
)
```

**Related:** [Error Handling](advanced/error-handling.md)

### Result<T>
Kotlin Result type used for all TrustWeave operations.

**Example:**
```kotlin
try {
    val did = trustweave.dids.create()
    println("Created: ${did.id}")
} catch (error: TrustWeaveError) {
    println("Error: ${error.message}")
}
```

**Related:** [Error Handling](advanced/error-handling.md)

## Related Documentation

- [Core Concepts](core-concepts/README.md) - Detailed explanations
- [API Reference](api-reference/README.md) - Complete API documentation
- [Quick Start](getting-started/quick-start.md) - Getting started guide

