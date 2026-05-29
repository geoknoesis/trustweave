---
title: TrustWeave Glossary
nav_exclude: true
---

# TrustWeave Glossary

Standard terminology used throughout TrustWeave documentation.

> **Version:** 0.6.0

## Core Terms

### Facade
The **TrustWeave** class is the main entry point (facade pattern). It provides a unified API for all TrustWeave operations.

**Example:**
```kotlin
import kotlinx.coroutines.runBlocking
import org.trustweave.trust.TrustWeave
import org.trustweave.trust.types.getOrThrowDid

fun main() = runBlocking {
    val trustWeave = TrustWeave.quickStart()
    val did = trustWeave.createDid { }.getOrThrowDid()
}
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
    override suspend fun issue(request: IssuanceRequest): IssuanceResult = TODO()
}
```

**Related:** [Credential Service API](api-reference/credential-service-api.md)

### Provider
Factory pattern implementations that create service instances:
- **WalletFactory**: Creates wallet instances
- **DidMethodProvider**: Creates DID method implementations

(Note: there is no `CredentialServiceProvider` SPI — `CredentialService` is assembled by `TrustWeave.build { ... }` from the discovered proof engines and credential SPIs.)

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
- **CredentialService** / **`CredentialServices`**: Default credential issuance and verification (`issue(IssuanceRequest)`, `verify(...)`, presentations); constructed explicitly or by **`TrustWeave.build`**

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
jsonData {
    "id" to "did:key:holder"
    "name" to "Alice"
    "degree" to "B.S. Computer Science"
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
val wallet = trustWeave.wallet {
    id("holder-wallet")
    holder("did:key:holder")
    enableOrganization()
    enablePresentation()
}
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

**Related:** [Plugin Lifecycle](api-reference/advanced/plugin-lifecycle.md)

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

**Related:** [Plugin Lifecycle](api-reference/advanced/plugin-lifecycle.md)

## Error Terms

### TrustWeaveException
Base type for TrustWeave failures that are modeled as **exceptions**. Domain modules add sealed subclasses such as **`DidException`**, **`BlockchainException`**, **`WalletException`**, **`PluginException`**, **`ConfigException`**, and **`SerializationException`**.

**Example:**
```kotlin
import org.trustweave.did.exception.DidException

DidException.DidMethodNotRegistered(
    method = "web",
    availableMethods = listOf("key")
)
```

**Related:** [Error handling](api-reference/advanced/error-handling.md)

### Result&lt;T&gt;
Kotlin **`Result`** is used by some services (for example smart-contract helpers). The **`TrustWeave`** facade often returns **sealed result types** instead (`DidCreationResult`, `IssuanceResult`, `VerificationResult`, …).

**Example:**
```kotlin
import org.trustweave.trust.types.getOrThrowDid
import org.trustweave.trust.dsl.credential.DidMethods.KEY

val did = trustWeave.createDid { method(KEY) }.getOrThrowDid()
```

**Related:** [Error Handling](api-reference/advanced/error-handling.md)

## Related Documentation

- [Core Concepts](core-concepts/README.md) - Detailed explanations
- [API Reference](api-reference/README.md) - Complete API documentation
- [Quick Start](getting-started/quick-start.md) - Getting started guide

