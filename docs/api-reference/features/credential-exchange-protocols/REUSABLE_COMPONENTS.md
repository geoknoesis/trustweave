---
title: Reusable Components Across Protocols
redirect_from:
  - /features/credential-exchange-protocols/REUSABLE_COMPONENTS/
parent: Feature Reference
grand_parent: API Reference
---

# Reusable Components Across Protocols

## Overview

Many components implemented for DIDComm are reusable across other protocols (OIDC4VCI, CHAPI, etc.). This document outlines what's reusable and how to use it.

> **TODO (audit 2026-05):** Locations below are aspirational. As of this audit:
>
> - `KeyEncryption`, `LocalKeyStore`, `MessageEncryption`, `EncryptionKeyManager`, `KeyRotationPolicy`/`KeyRotationManager`, `ReplicationManager`, `*MessageStorage`, `EncryptedFileLocalKeyStore`, `HybridKmsSecretResolver`, `KmsSecretResolver` all live under `credentials/plugins/didcomm/src/main/kotlin/org/trustweave/credential/didcomm/...` — **not** in `credential-core`.
> - The `ProtocolMessage` / generic `ProtocolMessageStorage<T>` / generic `PostgresMessageStorage<T>` / `S3MessageArchiver` / `PostgresFullTextSearch` / `PostgresMessageAnalytics` types referenced below **do not currently exist** in the codebase. The DIDComm-specific equivalents (`DidCommMessage`, `DidCommMessageStorage`, `PostgresDidCommMessageStorage`, `MongoDidCommMessageStorage`) do exist.
> - Treat the rest of this document as a *design proposal* for cross-protocol abstraction, not a description of the current API.

## ✅ Fully Reusable Components

### 1. Encryption & Key Management

**Location**: `credentials/credential-core/src/main/kotlin/org.trustweave/credential/`

#### KeyEncryption (`crypto/secret/encryption/KeyEncryption.kt`)
- **Reusable**: ✅ Yes - Generic AES-256-GCM encryption
- **Usage**: Any protocol that needs to encrypt keys locally
- **Example**:
  ```kotlin
  val keyEncryption = KeyEncryption(masterKey)
  val encrypted = keyEncryption.encrypt(keyData)
  ```

#### LocalKeyStore (`crypto/secret/LocalKeyStore.kt`)
- **Reusable**: ✅ Yes - Generic key storage interface
- **Usage**: OIDC4VCI, CHAPI, or any protocol using keys
- **Example**:
  ```kotlin
  val keyStore: LocalKeyStore = EncryptedFileLocalKeyStore(...)
  keyStore.store("key-id", secret)
  ```

#### MessageEncryption (`storage/encryption/MessageEncryption.kt`)
- **Reusable**: ✅ Yes - Encrypts any data at rest
- **Usage**: OIDC4VCI offers, CHAPI messages, any protocol data
- **Example**:
  ```kotlin
  val encryption = AesMessageEncryption(encryptionKey, keyVersion = 1)
  val encrypted = encryption.encrypt(messageBytes)
  ```

#### EncryptionKeyManager (`storage/encryption/EncryptionKeyManager.kt`)
- **Reusable**: ✅ Yes - Key versioning and rotation
- **Usage**: Any protocol needing encryption key management
- **Example**:
  ```kotlin
  val keyManager = InMemoryEncryptionKeyManager()
  val newVersion = keyManager.rotateKey()
  ```

#### KeyRotationPolicy (`crypto/rotation/KeyRotationPolicy.kt`)
- **Reusable**: ✅ Yes - Any protocol using cryptographic keys
- **Usage**: OIDC4VCI, CHAPI, or any key-based protocol
- **Example**:
  ```kotlin
  val policy = TimeBasedRotationPolicy(maxAgeDays = 90)
  val shouldRotate = policy.shouldRotate(keyId, metadata)
  ```

### 2. Storage Infrastructure

#### ProtocolMessage Interface (`storage/ProtocolMessage.kt`)
- **Reusable**: ✅ Yes - Generic message interface
- **Usage**: All protocols should implement this
- **Example**:
  ```kotlin
  data class DidCommMessage(...) : ProtocolMessage {
      override val messageId: String get() = id
      override val messageType: String get() = type
      // ... implement other properties
  }
  ```

#### ProtocolMessageStorage Interface (`storage/ProtocolMessageStorage.kt`)
- **Reusable**: ✅ Yes - Generic storage interface
- **Usage**: All protocols can use this
- **Example**:
  ```kotlin
  val storage: ProtocolMessageStorage<DidCommMessage> =
      PostgresMessageStorage(serializer, dataSource)
  ```

#### PostgresMessageStorage (`storage/database/PostgresMessageStorage.kt`)
- **Reusable**: ✅ Yes - Works with any ProtocolMessage
- **Usage**: DIDComm, OIDC4VCI, CHAPI, or any protocol
- **Example**:
  ```kotlin
  // DIDComm
  val didCommStorage = PostgresMessageStorage(
      serializer = DidCommMessage.serializer(),
      dataSource = dataSource,
      tableName = "didcomm_messages"
  )

  // OIDC4VCI
  val oidcStorage = PostgresMessageStorage(
      serializer = Oidc4VciOffer.serializer(),
      dataSource = dataSource,
      tableName = "oidc4vci_offers"
  )
  ```

### 3. Advanced Features

#### Message Archiving (`storage/archive/`)
- **Reusable**: ✅ Yes - Can archive any data
- **Usage**: OIDC4VCI offers, CHAPI requests, any protocol data
- **Example**:
  ```kotlin
  val archiver = S3MessageArchiver(storage, s3Client, bucketName)
  val result = archiver.archiveMessages(policy)
  ```

#### Message Replication (`storage/replication/ReplicationManager.kt`)
- **Reusable**: ✅ Yes - Works with any ProtocolMessageStorage
- **Usage**: High availability for any protocol
- **Example**:
  ```kotlin
  val replicationManager = ReplicationManager(
      primary = storage1,
      replicas = listOf(storage2, storage3),
      replicationMode = ReplicationMode.ASYNC
  )
  ```

#### Advanced Search (`storage/search/`)
- **Reusable**: ✅ Partially - PostgreSQL full-text search is generic
- **Usage**: Search any stored protocol messages
- **Example**:
  ```kotlin
  val search = PostgresFullTextSearch(dataSource)
  val results = search.fullTextSearch("credential offer")
  ```

#### Message Analytics (`storage/analytics/`)
- **Reusable**: ✅ Partially - Analytics logic is generic
- **Usage**: Analyze traffic for any protocol
- **Example**:
  ```kotlin
  val analytics = PostgresMessageAnalytics(dataSource)
  val stats = analytics.getStatistics(startTime, endTime)
  ```

## 🔄 Needs Abstraction (Currently DIDComm-Specific)

### Storage Implementations
- **Current**: `PostgresDidCommMessageStorage`, `MongoDidCommMessageStorage`
- **Solution**: Use generic `PostgresMessageStorage<T>`, `MongoMessageStorage<T>`
- **Status**: ✅ Generic implementations created

### Storage Interfaces
- **Current**: `DidCommMessageStorage` uses `DidCommMessage`
- **Solution**: Use generic `ProtocolMessageStorage<T>`
- **Status**: ✅ Generic interface created

## ❌ Not Reusable (Protocol-Specific)

### DIDComm-Specific Components
1. **DidCommMessage Model** - DIDComm-specific structure
2. **DidCommPacker** - DIDComm packing/unpacking
3. **DidCommCrypto** - DIDComm-specific encryption adapters

## Usage Examples

### OIDC4VCI with Generic Storage

```kotlin
// 1. Make OIDC4VCI offer implement ProtocolMessage
data class Oidc4VciOffer(
    val id: String,
    val type: String,
    val issuerDid: String,
    // ... other fields
) : ProtocolMessage {
    override val messageId: String get() = id
    override val messageType: String get() = type
    override val from: String? get() = issuerDid
    override val to: List<String> get() = listOf(holderDid)
    // ... implement other properties
}

// 2. Use generic storage
val oidcStorage = PostgresMessageStorage(
    serializer = Oidc4VciOffer.serializer(),
    dataSource = dataSource,
    tableName = "oidc4vci_offers",
    encryption = AesMessageEncryption(encryptionKey)
)

// 3. Use encryption
val encryption = AesMessageEncryption(
    encryptionKey = EncryptionKeyManager.generateRandomKey(),
    keyVersion = 1
)

// 4. Use key rotation
val rotationPolicy = TimeBasedRotationPolicy(maxAgeDays = 90)
val rotationManager = KeyRotationManager(keyStore, kms, rotationPolicy)
```

### CHAPI with Generic Storage

```kotlin
// 1. Make CHAPI offer implement ProtocolMessage
data class ChapiOffer(
    val id: String,
    val type: String,
    val issuerDid: String,
    // ... other fields
) : ProtocolMessage {
    override val messageId: String get() = id
    override val messageType: String get() = type
    override val from: String? get() = issuerDid
    override val to: List<String> get() = emptyList()
    // ... implement other properties
}

// 2. Use generic storage
val chapiStorage = PostgresMessageStorage(
    serializer = ChapiOffer.serializer(),
    dataSource = dataSource,
    tableName = "chapi_offers"
)

// 3. Use archiving
val archiver = S3MessageArchiver(chapiStorage, s3Client, bucketName)
val policy = AgeBasedArchivePolicy(maxAgeDays = 30)
archiver.archiveMessages(policy)
```

## Migration Guide

### For DIDComm

1. **Update DidCommMessage**:
   ```kotlin
   data class DidCommMessage(...) : ProtocolMessage {
       override val messageId: String get() = id
       // ... implement ProtocolMessage properties
   }
   ```

2. **Use Generic Storage**:
   ```kotlin
   val genericStorage = PostgresMessageStorage(
       serializer = DidCommMessage.serializer(),
       dataSource = dataSource,
       tableName = "didcomm_messages"
   )

   val didCommStorage = DidCommMessageStorageAdapter(genericStorage)
   ```

### For OIDC4VCI

1. **Create ProtocolMessage Implementation**:
   ```kotlin
   data class Oidc4VciOffer(...) : ProtocolMessage { ... }
   ```

2. **Use Generic Storage**:
   ```kotlin
   val storage = PostgresMessageStorage(
       serializer = Oidc4VciOffer.serializer(),
       dataSource = dataSource,
       tableName = "oidc4vci_offers"
   )
   ```

3. **Replace In-Memory Maps**:
   ```kotlin
   // Before
   private val offers = mutableMapOf<String, Oidc4VciOffer>()

   // After
   private val storage: ProtocolMessageStorage<Oidc4VciOffer> = ...
   ```

### For CHAPI

1. **Create ProtocolMessage Implementation**:
   ```kotlin
   data class ChapiOffer(...) : ProtocolMessage { ... }
   ```

2. **Use Generic Storage**:
   ```kotlin
   val storage = PostgresMessageStorage(
       serializer = ChapiOffer.serializer(),
       dataSource = dataSource,
       tableName = "chapi_offers"
   )
   ```

## Benefits

1. **Code Reuse**: Share storage, encryption, and key management across protocols
2. **Consistency**: Same storage patterns across all protocols
3. **Maintainability**: Fix bugs once, benefit everywhere
4. **Features**: Get archiving, replication, search, analytics for free
5. **Flexibility**: Easy to add new protocols

## Summary

✅ **6 Fully Reusable Components**: Encryption, key management, rotation, archiving, replication, generic storage

✅ **4 Reusable with Abstraction**: Storage implementations, search, analytics (now abstracted)

❌ **3 Protocol-Specific**: DIDComm message models, packing, crypto adapters

All reusable components are now in `credentials/credential-core` and can be used by any protocol!

