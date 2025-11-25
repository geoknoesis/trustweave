---
title: Credential Exchange Protocols - Versioning
---

# Credential Exchange Protocols - Versioning

Version information, deprecation notices, and migration guides for credential exchange protocols.

## Current Version

- **API Version**: 1.0.0-SNAPSHOT
- **Protocol Support**: DIDComm V2, OIDC4VCI, CHAPI
- **Kotlin Version**: 2.2.0+
- **Java Version**: 21+

## Version History

### 1.0.0-SNAPSHOT (Current)

**Initial Release**

- ✅ Protocol abstraction layer
- ✅ DIDComm V2 support
- ✅ OIDC4VCI support
- ✅ CHAPI support
- ✅ Protocol registry
- ✅ Persistent message storage
- ✅ Secret resolver
- ✅ Advanced features (archiving, replication, encryption at rest)

**Breaking Changes:**
- None (initial release)

**New Features:**
- Protocol abstraction layer
- Unified API for all protocols
- Protocol registry
- Persistent storage
- Secret resolver
- Advanced features

---

## Deprecation Policy

### Deprecation Timeline

1. **Deprecation Notice**: Features are marked as deprecated in documentation and code comments
2. **Deprecation Period**: Deprecated features remain functional for at least 2 major versions
3. **Removal**: Deprecated features are removed in a major version release

### Currently Deprecated

**None** - No features are currently deprecated.

---

## Migration Guides

### Migrating from Protocol-Specific APIs

If you're using protocol-specific APIs directly, you can migrate to the protocol abstraction layer.

#### Before: Direct Protocol Usage

```kotlin
// Direct DIDComm usage
val didCommService = DidCommFactory.createInMemoryService(kms, resolveDid)
val offer = didCommService.createOffer(
    issuerDid = "did:key:issuer",
    holderDid = "did:key:holder",
    preview = preview
)
```

#### After: Protocol Abstraction

```kotlin
// Using protocol abstraction
val registry = CredentialExchangeProtocolRegistry()
val didCommService = DidCommFactory.createInMemoryService(kms, resolveDid)
registry.register(DidCommExchangeProtocol(didCommService))

val offer = registry.offerCredential(
    protocolName = "didcomm",
    request = CredentialOfferRequest(
        issuerDid = "did:key:issuer",
        holderDid = "did:key:holder",
        credentialPreview = preview
    )
)
```

**Benefits:**
- Unified API across all protocols
- Easy protocol switching
- Consistent error handling
- Better testability

---

### Migrating Between Protocols

#### From DIDComm to OIDC4VCI

**When to migrate:**
- Moving from peer-to-peer to web-based
- Need OAuth integration
- Web application requirements

**Migration steps:**

1. **Register OIDC4VCI protocol:**
   ```kotlin
   val oidc4vciService = Oidc4VciService(
       credentialIssuerUrl = "https://issuer.example.com",
       kms = kms
   )
   registry.register(Oidc4VciExchangeProtocol(oidc4vciService))
   ```

2. **Update options:**
   ```kotlin
   // Before (DIDComm)
   options = mapOf(
       "fromKeyId" to "did:key:issuer#key-1",
       "toKeyId" to "did:key:holder#key-1"
   )
   
   // After (OIDC4VCI)
   options = mapOf(
       "credentialIssuer" to "https://issuer.example.com"
   )
   ```

3. **Update protocol name:**
   ```kotlin
   // Before
   registry.offerCredential("didcomm", request)
   
   // After
   registry.offerCredential("oidc4vci", request)
   ```

**Limitations:**
- OIDC4VCI doesn't support proof requests
- Requires HTTP connectivity
- Different error handling

---

#### From OIDC4VCI to DIDComm

**When to migrate:**
- Need end-to-end encryption
- Peer-to-peer communication
- Proof requests needed

**Migration steps:**

1. **Register DIDComm protocol:**
   ```kotlin
   val didCommService = DidCommFactory.createInMemoryService(kms, resolveDid)
   registry.register(DidCommExchangeProtocol(didCommService))
   ```

2. **Update options:**
   ```kotlin
   // Before (OIDC4VCI)
   options = mapOf(
       "credentialIssuer" to "https://issuer.example.com"
   )
   
   // After (DIDComm)
   options = mapOf(
       "fromKeyId" to "did:key:issuer#key-1",
       "toKeyId" to "did:key:holder#key-1"
   )
   ```

3. **Update protocol name:**
   ```kotlin
   // Before
   registry.offerCredential("oidc4vci", request)
   
   // After
   registry.offerCredential("didcomm", request)
   ```

**Benefits:**
- End-to-end encryption
- Proof request support
- Peer-to-peer communication

---

### Migrating Storage Implementations

#### From In-Memory to Database Storage

**Before:**
```kotlin
val didCommService = DidCommFactory.createInMemoryService(kms, resolveDid)
```

**After:**
```kotlin
// Setup database storage
val dataSource = createDataSource()
val storage = PostgresDidCommMessageStorage(dataSource)

// Create database-backed service
val didCommService = DidCommFactory.createDatabaseService(
    packer = packer,
    resolveDid = resolveDid,
    storage = storage
)
```

**Migration steps:**

1. **Setup database:**
   - Create database schema
   - Run migrations
   - Configure connection pool

2. **Update service creation:**
   - Replace `createInMemoryService` with `createDatabaseService`
   - Provide storage implementation

3. **Test migration:**
   - Verify messages are stored
   - Check message retrieval
   - Test error handling

---

## Compatibility Matrix

### Protocol Compatibility

| Protocol | DIDComm | OIDC4VCI | CHAPI |
|----------|---------|----------|-------|
| **DIDComm** | ✅ | ⚠️ Limited | ❌ |
| **OIDC4VCI** | ⚠️ Limited | ✅ | ❌ |
| **CHAPI** | ❌ | ❌ | ✅ |

**Legend:**
- ✅ Fully compatible
- ⚠️ Limited compatibility (some features may not work)
- ❌ Not compatible

### API Compatibility

| Version | 1.0.0-SNAPSHOT |
|---------|----------------|
| **1.0.0-SNAPSHOT** | ✅ Compatible |

---

## Upgrade Guide

### Upgrading to Latest Version

1. **Check current version:**
   ```kotlin
   // Check your current dependencies
   // In build.gradle.kts:
   implementation("com.trustweave:credential-core:1.0.0-SNAPSHOT")
   ```

2. **Update dependencies:**
   ```kotlin
   // Update to latest version
   implementation("com.trustweave:credential-core:1.0.0-SNAPSHOT")
   ```

3. **Review changelog:**
   - Check for breaking changes
   - Review new features
   - Check deprecation notices

4. **Update code:**
   - Apply migration guides if needed
   - Update deprecated APIs
   - Test thoroughly

5. **Verify:**
   - Run tests
   - Check error handling
   - Verify protocol behavior

---

## Future Versions

### Planned Features (Future Versions)

- OIDC4VP (OpenID Connect for Verifiable Presentations)
- SIOPv2 (Self-Issued OpenID Provider v2)
- WACI (Wallet and Credential Interactions)
- Protocol capability negotiation
- Protocol fallback/retry
- Metrics and observability per protocol

### Version Roadmap

- **1.1.0**: Additional protocols (OIDC4VP, SIOPv2)
- **1.2.0**: Protocol capability negotiation
- **2.0.0**: Major API improvements (if needed)

---

## Support Policy

### Supported Versions

- **Current Version**: Full support
- **Previous Major Version**: Security fixes only
- **Older Versions**: No support

### Support Timeline

- **Major Versions**: Supported for 2 years
- **Minor Versions**: Supported until next minor version
- **Patch Versions**: Supported until next patch version

---

## Related Documentation

- **[Quick Start](./QUICK_START.md)** - Get started quickly
- **[API Reference](./API_REFERENCE.md)** - Complete API documentation
- **[Error Handling](./ERROR_HANDLING.md)** - Error handling guide
- **[Troubleshooting](./TROUBLESHOOTING.md)** - Common issues and solutions

