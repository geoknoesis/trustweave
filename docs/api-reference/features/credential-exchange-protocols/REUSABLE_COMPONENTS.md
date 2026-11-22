---
title: Reusable Components Across Protocols
redirect_from:
  - /features/credential-exchange-protocols/REUSABLE_COMPONENTS/
parent: Feature Reference
grand_parent: API Reference
---

# Reusable Components Across Protocols

This reference maps which credential-exchange abstractions are already shared across protocols in `credentials:credential-api` / `credentials:credential-models-mp`, and which storage, encryption, and key-rotation building blocks currently live inside `credentials:plugins:didcomm`.

## Shared Protocol-Agnostic Layer (`credentials:credential-api` & `credentials:credential-models-mp`)

These components are protocol-agnostic and shared by DIDComm, OIDC4VCI, OID4VP, SIOP, CHAPI, and custom plugins:

- **`org.trustweave.credential.exchange.CredentialExchangeProtocol`** — Common SPI implemented by every exchange protocol plugin.
- **`org.trustweave.credential.exchange.ExchangeService` / `ExchangeServices`** — Unified facade with manual registration and `ServiceLoader` auto-discovery.
- **`org.trustweave.credential.exchange.registry.ExchangeProtocolRegistry`** — Registry and capability inspection (`ExchangeProtocolCapabilities`, `ExchangeOperation`).
- **`org.trustweave.credential.exchange.request.{ExchangeRequest, ProofExchangeRequest}`** — Sealed, typed request hierarchy.
- **`org.trustweave.credential.exchange.response.{ExchangeResponse, ProofExchangeResponse}`** — Sealed responses wrapping opaque `ExchangeMessageEnvelope` payloads.
- **`org.trustweave.credential.exchange.result.ExchangeResult`** — Sealed result hierarchy with functional combinators (`map`, `flatMap`, `fold`, `recover`).
- **`org.trustweave.credential.exchange.options.ExchangeOptions`** — Structured metadata carrier for protocol-specific parameters.

## Concrete Infrastructure Currently in `credentials:plugins:didcomm`

The encryption, key-storage, rotation, storage, archiving, replication, search, and analytics utilities live under `credentials/plugins/didcomm/src/main/kotlin/org/trustweave/credential/didcomm/`. They are usable directly when depending on `credentials:plugins:didcomm`, and are candidates for extraction into a shared module if additional protocols need the same persistence primitives.

### 1. Secret Storage, Encryption & Rotation

| Component | Package (`org.trustweave.credential.didcomm.*`) | Purpose |
|-----------|------------------------------------------------|---------|
| `KeyEncryption` | `crypto.secret.encryption` | AES-256-GCM encryption for local secret material |
| `LocalKeyStore`, `EncryptedFileLocalKeyStore` | `crypto.secret` | Local secret storage interface and authenticated encrypted-file implementation (v2 purpose AAD) |
| `KmsSecretResolver`, `HybridKmsSecretResolver` | `crypto.secret` | Bridges `KeyManagementService` and local key stores into `didcomm-java`'s `SecretResolver` |
| `KeyRotationPolicy`, `KeyRotationManager`, `ScheduledKeyRotation` | `crypto.rotation` | Rotation policy evaluation and scheduling helpers |
| `MessageEncryption` / `AesMessageEncryption` | `storage.encryption` | Authenticated message-at-rest encryption with row-bound AAD and keyed blind indexes (`blindIndex`) |
| `EncryptionKeyManager` | `storage.encryption` | Encryption key versioning and rotation |

### 2. Message Persistence, Archiving, Replication, Search & Analytics

| Component | Package (`org.trustweave.credential.didcomm.*`) | Purpose |
|-----------|------------------------------------------------|---------|
| `DidCommMessageStorage` | `storage` | Persistence interface for `DidCommMessage` records |
| `InMemoryDidCommMessageStorage` | `storage` | In-memory implementation for local dev and tests |
| `PostgresDidCommMessageStorage` | `storage.database` | PostgreSQL storage supporting encrypted rows and keyed blind indexes |
| `MongoDidCommMessageStorage` | `storage.database` | MongoDB storage backend |
| `MessageArchiver`, `ArchivePolicy` | `storage.archive` | Retention and archival policy interfaces |
| `ReplicationManager` | `storage.replication` | Primary/replica storage replication coordinator |
| `AdvancedSearch`, `PostgresFullTextSearch` | `storage.search` | Structured search and optional PostgreSQL full-text search (disabled by default for encrypted rows) |
| `MessageAnalytics`, `PostgresMessageAnalytics` | `storage.analytics` | Message traffic analytics over stored DIDComm messages |

### 3. Protocol-Specific DIDComm Components

These remain intentionally DIDComm-specific:

- `DidCommMessage` / `DidCommEnvelope` (`models/`)
- `DidCommPacker` (`packing/`)
- `DidCommCrypto` / `DidCommCryptoDidcomm` (`crypto/`)
- `CredentialProtocol`, `ProofProtocol`, `BasicMessageProtocol` (`protocol/`)

## Cross-Protocol Extraction Guidance

Generic `ProtocolMessage` / `ProtocolMessageStorage<T>` wrappers are not currently part of `credentials:credential-api`. Until those components are extracted into a shared persistence module:

- Use `ExchangeService` and `ExchangeMessageEnvelope` in `credentials:credential-api` for protocol-agnostic orchestration.
- Use protocol-specific storage where provided (for example, `PostgresDidCommMessageStorage` for DIDComm, or `Oidc4VciIssuerStateStore` in `credentials:oidc4vci-server`).
- If you build a new exchange plugin that needs persistence or encryption, model its state store directly against its own domain types rather than coupling to `DidCommMessage`.

## Related Documentation

- **[API Reference](API_REFERENCE.md)** — Complete `CredentialExchangeProtocol`, `ExchangeService`, and `ExchangeProtocolRegistry` reference
- **[Protocol Implementation Guide](implementation-guide.md)** — Implementing and registering a new `CredentialExchangeProtocol` plugin
- **[DIDComm Implementation](didcomm.md)** — DIDComm V2 module details
