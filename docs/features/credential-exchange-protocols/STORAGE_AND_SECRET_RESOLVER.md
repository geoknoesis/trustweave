---
title: Persistent Storage & Secret Resolver Implementation
---

# Persistent Storage & Secret Resolver Implementation

## Overview

This document describes the implementation of persistent message storage and SecretResolver for DIDComm V2 plugin.

## Components Implemented

### 1. Persistent Message Storage

#### Storage Interface
- **Location**: `credentials/plugins/didcomm/src/main/kotlin/com/trustweave/credential/didcomm/storage/DidCommMessageStorage.kt`
- **Purpose**: Abstract interface for message persistence
- **Features**:
  - Store and retrieve messages
  - Query by DID, thread, filters
  - Pagination support
  - Message deletion

#### Implementations

**In-Memory Storage**
- **Location**: `credentials/plugins/didcomm/src/main/kotlin/com/trustweave/credential/didcomm/storage/InMemoryDidCommMessageStorage.kt`
- **Use Case**: Testing and development
- **Limitation**: Data lost on restart

**PostgreSQL Storage**
- **Location**: `credentials/plugins/didcomm/src/main/kotlin/com/trustweave/credential/didcomm/storage/database/PostgresDidCommMessageStorage.kt`
- **Use Case**: Production deployments
- **Features**:
  - Full SQL support
  - Indexed queries
  - Transaction support
  - JSONB storage for message data

#### Database Schema

**Tables:**
- `didcomm_messages` - Main messages table
- `didcomm_message_dids` - Index for DID lookups
- `didcomm_message_threads` - Index for thread lookups

**Migration Script:**
- **Location**: `credentials/plugins/didcomm/src/main/resources/db/migration/V1__create_didcomm_messages.sql`

### 2. Secret Resolver

#### Local Key Store
- **Location**: `credentials/plugins/didcomm/src/main/kotlin/com/trustweave/credential/didcomm/crypto/secret/LocalKeyStore.kt`
- **Purpose**: Store DIDComm keys locally (for ECDH operations)
- **Implementations**:
  - `InMemoryLocalKeyStore` - For testing
  - `EncryptedFileLocalKeyStore` - For production (to be implemented)

#### KMS Secret Resolver
- **Location**: `credentials/plugins/didcomm/src/main/kotlin/com/trustweave/credential/didcomm/crypto/secret/KmsSecretResolver.kt`
- **Purpose**: Bridge KMS with didcomm-java library
- **Strategy**: Uses local key store for DIDComm keys

#### Hybrid Secret Resolver
- **Location**: `credentials/plugins/didcomm/src/main/kotlin/com/trustweave/credential/didcomm/crypto/secret/HybridKmsSecretResolver.kt`
- **Purpose**: Recommended approach for production
- **Strategy**: 
  - DIDComm keys stored locally (for ECDH)
  - Other keys use cloud KMS (for signing)

## Usage Examples

### Database-Backed Service

```kotlin
import com.zaxxer.hikari.HikariDataSource
import com.trustweave.credential.didcomm.*
import com.trustweave.credential.didcomm.storage.database.PostgresDidCommMessageStorage

// Create data source
val dataSource = HikariDataSource().apply {
    jdbcUrl = "jdbc:postgresql://localhost:5432/trustweave"
    username = "user"
    password = "pass"
}

// Create storage
val storage = PostgresDidCommMessageStorage(dataSource)

// Create packer
val packer = DidCommFactory.createPacker(kms, resolveDid)

// Create database-backed service
val didCommService = DidCommFactory.createDatabaseService(
    packer = packer,
    resolveDid = resolveDid,
    storage = storage
)
```

### Hybrid Secret Resolver

```kotlin
import com.trustweave.credential.didcomm.crypto.secret.*

// Create local key store for DIDComm keys
val localKeyStore = InMemoryLocalKeyStore() // Or EncryptedFileLocalKeyStore

// Create hybrid resolver
val secretResolver = HybridKmsSecretResolver(
    localKeyStore = localKeyStore,
    cloudKms = cloudKms // Optional
)

// Create service with custom resolver
val didCommService = DidCommFactory.createInMemoryServiceWithSecretResolver(
    kms = kms,
    resolveDid = resolveDid,
    secretResolver = secretResolver
)
```

### Storing DIDComm Keys Locally

```kotlin
// Generate DIDComm key pair
val keyPair = generateKeyPair() // Your key generation logic

// Create Secret from key pair
val secret = Secret(
    id = "did:key:issuer#key-1",
    type = Secret.Type.JSON_WEB_KEY_2020,
    privateKeyJwk = keyPairToJwk(keyPair) // Convert to JWK format
)

// Store in local key store
localKeyStore.store("did:key:issuer#key-1", secret)
```

## Architecture

### Storage Architecture

```
DidCommService
    │
    ├── InMemoryDidCommService (uses InMemoryDidCommMessageStorage)
    │
    └── DatabaseDidCommService (uses PostgresDidCommMessageStorage)
            │
            └── DidCommMessageStorage
                    ├── InMemoryDidCommMessageStorage
                    └── PostgresDidCommMessageStorage
```

### Secret Resolver Architecture

```
DidCommCryptoProduction
    │
    └── SecretResolver
            ├── SecretResolverInMemory (default)
            ├── KmsSecretResolver (with local key store)
            └── HybridKmsSecretResolver (recommended)
                    │
                    └── LocalKeyStore
                            ├── InMemoryLocalKeyStore
                            └── EncryptedFileLocalKeyStore (to be implemented)
```

## Security Considerations

### Message Storage
- **Encryption at Rest**: Consider encrypting message JSON in database
- **Access Control**: Implement row-level security for multi-tenant scenarios
- **Data Retention**: Implement message expiration and cleanup
- **Backup**: Regular backups with encryption

### Secret Resolver
- **Key Storage**: Local keys must be encrypted at rest
- **Key Rotation**: Implement key rotation policies
- **Key Access**: Limit access to key storage
- **Audit Logging**: Log all key access operations

## Performance Considerations

### Database Storage
- **Connection Pooling**: Use connection pools (e.g., HikariCP)
- **Indexing**: Proper indexes for common queries
- **Pagination**: Always use pagination for large result sets
- **Caching**: Consider caching frequently accessed messages

### Secret Resolver
- **Key Caching**: Cache resolved secrets to avoid repeated lookups
- **Lazy Loading**: Load keys only when needed
- **Key Preloading**: Preload keys for known DIDs

## Migration Path

### From In-Memory to Database

1. Implement storage interface
2. Create database storage implementation
3. Update service factory to accept storage
4. Migrate existing messages (if any)
5. Update configuration

### From Placeholder to Production Crypto

1. Add didcomm-java dependency
2. Implement SecretResolver
3. Update crypto adapter
4. Test with real keys
5. Deploy to production

## Future Enhancements

- Message archiving to cold storage
- Message replication for high availability
- Message encryption at rest
- Advanced search capabilities
- Message analytics and reporting
- EncryptedFileLocalKeyStore implementation
- MongoDB storage implementation
- Key rotation automation

