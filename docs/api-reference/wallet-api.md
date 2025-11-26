---
title: Wallet API Reference
nav_order: 5
parent: API Reference
---

# Wallet API Reference

Complete API reference for TrustWeave's Wallet system.

```kotlin
dependencies {
    implementation("com.trustweave:trustweave-core:1.0.0-SNAPSHOT")
}
```

**Result:** Gradle exposes the wallet interfaces and DSLs referenced throughout this reference.

## Overview

The Wallet API provides a unified interface for managing verifiable credentials and identities. It follows the Interface Segregation Principle (ISP) with composable capability interfaces.

## Core Interfaces

### Wallet

The main wallet interface that all wallets implement.

```kotlin
interface Wallet : CredentialStorage {
    val walletId: String
    val capabilities: WalletCapabilities
    fun <T : Any> supports(capability: KClass<T>): Boolean
    suspend fun getStatistics(): WalletStatistics
}

#### Method summary

| Method | Returns | Exceptions | Notes |
|--------|---------|------------|-------|
| `supports(capability)` | `Boolean` | – | Detect optional interfaces (presentation, DID management, etc.). |
| `getStatistics()` | `WalletStatistics` | `IllegalStateException` if the backing store cannot compute statistics. | Useful for dashboards and monitoring. |
```

### CredentialStorage

Core credential storage operations (always available).

```kotlin
interface CredentialStorage {
    suspend fun store(credential: VerifiableCredential): String
    suspend fun get(credentialId: String): VerifiableCredential?
    suspend fun list(filter: CredentialFilter? = null): List<VerifiableCredential>
    suspend fun delete(credentialId: String): Boolean
    suspend fun query(query: CredentialQueryBuilder.() -> Unit): List<VerifiableCredential>
}

#### Method summary

| Method | Parameters | Returns | Exceptions | Notes |
|--------|------------|---------|------------|-------|
| `store` | `credential` | `String` (stored id) | `IllegalStateException` if storage is read-only or full. | Canonicalises the credential content before persistence. |
| `get` | `credentialId` | `VerifiableCredential?` | – | Returns `null` when not found. |
| `list` | `filter` | `List<VerifiableCredential>` | – | `filter` is optional and may be ignored by simple stores. |
| `delete` | `credentialId` | `Boolean` | – | `true` if a credential was removed. |
| `query` | `CredentialQueryBuilder.() -> Unit` | `List<VerifiableCredential>` | `IllegalArgumentException` when a query clause is unsupported. | Compose filters via builder functions (`byIssuer`, `notExpired`, etc.). |
```

### CredentialOrganization

Optional interface for organizing credentials.

```kotlin
interface CredentialOrganization {
    // Collections
    suspend fun createCollection(name: String, description: String? = null): String
    suspend fun getCollection(collectionId: String): CredentialCollection?
    suspend fun listCollections(): List<CredentialCollection>
    suspend fun deleteCollection(collectionId: String): Boolean
    suspend fun addToCollection(credentialId: String, collectionId: String): Boolean
    suspend fun removeFromCollection(credentialId: String, collectionId: String): Boolean
    suspend fun getCredentialsInCollection(collectionId: String): List<VerifiableCredential>
    
    // Tags
    suspend fun tagCredential(credentialId: String, tags: Set<String>): Boolean
    suspend fun untagCredential(credentialId: String, tags: Set<String>): Boolean
    suspend fun getTags(credentialId: String): Set<String>
    suspend fun getAllTags(): Set<String>
    suspend fun findByTag(tag: String): List<VerifiableCredential>
    
    // Metadata
    suspend fun addMetadata(credentialId: String, metadata: Map<String, Any>): Boolean
    suspend fun getMetadata(credentialId: String): CredentialMetadata?
    suspend fun updateNotes(credentialId: String, notes: String?): Boolean
}

#### Method summary

| Method | Returns | Exceptions | Notes |
|--------|---------|------------|-------|
| `createCollection` | `String` (collectionId) | `IllegalArgumentException` when the name already exists. | Use for folders/projects. |
| `addToCollection` / `removeFromCollection` | `Boolean` | – | `false` indicates missing credential or collection. |
| `tagCredential` / `untagCredential` | `Boolean` | – | Implementations may normalise tags to lowercase. |
| `addMetadata` | `Boolean` | `IllegalStateException` if metadata updates unsupported. | Ideal for provenance/data catalog info. |
```

### CredentialLifecycle

Optional interface for lifecycle management.

```kotlin
interface CredentialLifecycle {
    suspend fun archive(credentialId: String): Boolean
    suspend fun unarchive(credentialId: String): Boolean
    suspend fun getArchived(): List<VerifiableCredential>
    suspend fun refreshCredential(credentialId: String): VerifiableCredential?
}
```

> Implementations may throw `UnsupportedOperationException` when lifecycle features are disabled.

### CredentialPresentation

Optional interface for creating presentations.

```kotlin
interface CredentialPresentation {
    suspend fun createPresentation(
        credentialIds: List<String>,
        holderDid: String,
        options: PresentationOptions
    ): VerifiablePresentation
    
    suspend fun createSelectiveDisclosure(
        credentialIds: List<String>,
        disclosedFields: List<String>,
        holderDid: String,
        options: PresentationOptions
    ): VerifiablePresentation
}
```

#### Method summary

| Method | Purpose | Exceptions | Notes |
|--------|---------|------------|-------|
| `createPresentation` | Build a verifiable presentation from stored credential IDs. | `IllegalArgumentException` when required fields in `PresentationOptions` are missing. | Uses configured proof generator; ensure holder DID has signing keys. |
| `createSelectiveDisclosure` | Produce a filtered presentation revealing selected fields. | Same as above. | Default implementation delegates to `createPresentation`. |

### DidManagement

Optional interface for DID management.

```kotlin
interface DidManagement {
    val walletDid: String
    val holderDid: String
    suspend fun createDid(method: String, options: DidCreationOptions = DidCreationOptions()): String
    suspend fun createDid(method: String, configure: DidCreationOptionsBuilder.() -> Unit): String
    suspend fun getDids(): List<String>
    suspend fun getPrimaryDid(): String
    suspend fun setPrimaryDid(did: String): Boolean
    suspend fun resolveDid(did: String): Any? // DidDocument
}
```

| Method | Returns | Exceptions | Notes |
|--------|---------|------------|-------|
| `createDid` | DID string | `IllegalArgumentException` when method not registered. | Overload allows typed builder. |
| `getDids` / `getPrimaryDid` | `List<String>` / `String` | – | Use to display wallet inventory. |
| `resolveDid` | `DidDocument?` | `IllegalStateException` if no resolver configured. | Handy for UX that surfaces DID metadata. |

### KeyManagement

Optional interface for key management.

```kotlin
interface KeyManagement {
    suspend fun generateKey(
        algorithm: String,
        configure: KeyGenerationOptionsBuilder.() -> Unit = {}
    ): KeyInfo
    suspend fun getKeys(): List<KeyInfo>
    suspend fun getKey(keyId: String): KeyInfo?
    suspend fun deleteKey(keyId: String): Boolean
    suspend fun sign(keyId: String, data: ByteArray): ByteArray
}
```

| Method | Returns | Exceptions | Notes |
|--------|---------|------------|-------|
| `generateKey` | `KeyInfo` | `IllegalArgumentException` for unknown algorithms. | Delegates to the configured `KeyManagementService`. |
| `deleteKey` | `Boolean` | `IllegalStateException` if removal unsupported. | `true` indicates the key was removed. |
| `sign` | `ByteArray` | `IllegalStateException` when key missing/inactive. | Input should already be canonicalised. |

### CredentialIssuance

Optional interface for credential issuance.

```kotlin
interface CredentialIssuance {
    suspend fun issueCredential(
        subjectDid: String,
        credentialType: String,
        claims: Map<String, Any>,
        options: CredentialIssuanceOptions
    ): VerifiableCredential
}
```

## Data Models

### CredentialFilter

Filter criteria for listing credentials.

```kotlin
data class CredentialFilter(
    val issuer: String? = null,
    val type: List<String>? = null,
    val subjectId: String? = null,
    val expired: Boolean? = null,
    val revoked: Boolean? = null
)
```

### CredentialQueryBuilder

Fluent query builder for credentials.

```kotlin
class CredentialQueryBuilder {
    fun byIssuer(issuerDid: String)
    fun byType(type: String)
    fun byTypes(vararg types: String)
    fun bySubject(subjectId: String)
    fun notExpired()
    fun expired()
    fun notRevoked()
    fun revoked()
    fun valid()
}
```

### CredentialCollection

Collection model.

```kotlin
data class CredentialCollection(
    val id: String,
    val name: String,
    val description: String? = null,
    val createdAt: Instant = Instant.now(),
    val credentialCount: Int = 0
)
```

### CredentialMetadata

Metadata model.

```kotlin
data class CredentialMetadata(
    val credentialId: String,
    val notes: String? = null,
    val tags: Set<String> = emptySet(),
    val metadata: Map<String, Any> = emptyMap(),
    val createdAt: Instant = Instant.now(),
    val updatedAt: Instant = Instant.now()
)
```

### WalletCapabilities

Runtime capability discovery.

```kotlin
data class WalletCapabilities(
    val credentialStorage: Boolean = true,
    val credentialQuery: Boolean = true,
    val collections: Boolean = false,
    val tags: Boolean = false,
    val metadata: Boolean = false,
    val archive: Boolean = false,
    val refresh: Boolean = false,
    val createPresentation: Boolean = false,
    val selectiveDisclosure: Boolean = false,
    val didManagement: Boolean = false,
    val keyManagement: Boolean = false,
    val credentialIssuance: Boolean = false
) {
    fun supports(feature: String): Boolean
}
```

### WalletStatistics

Wallet statistics model.

```kotlin
data class WalletStatistics(
    val totalCredentials: Int = 0,
    val validCredentials: Int = 0,
    val expiredCredentials: Int = 0,
    val revokedCredentials: Int = 0,
    val collectionsCount: Int = 0,
    val tagsCount: Int = 0,
    val archivedCount: Int = 0
)
```

## Provider Configuration

### WalletCreationOptions

`WalletCreationOptions` is shared by the TrustWeave facade, the Trust Layer DSL, and custom `WalletFactory` implementations. It removes the need for untyped configuration blobs while still allowing provider-specific extensions.

```kotlin
import com.trustweave.spi.services.WalletCreationOptionsBuilder

val options = WalletCreationOptionsBuilder().apply {
    label = "Production Wallet"
    storagePath = "/var/lib/TrustWeave/wallets/holder-42"
    enableOrganization = true
    enablePresentation = true
    property("connectionString", System.getenv("WALLET_DB_URL"))
}.build()
```

| Field | Type | Purpose |
|-------|------|---------|
| `label` | `String?` | Optional human readable label shown in dashboards or UIs |
| `storagePath` | `String?` | File system or bucket path for providers that persist data |
| `encryptionKey` | `String?` | Secret material for at-rest encryption |
| `enableOrganization` | `Boolean` | Signals that collection/tag capabilities should be enabled |
| `enablePresentation` | `Boolean` | Enables selective disclosure and presentation builders |
| `additionalProperties` | `Map<String, Any?>` | Provider-specific extensions added via `property("key", value)` |

A custom factory receives the same object:

```kotlin
class PostgresWalletFactory : WalletFactory {
    override suspend fun create(
        providerName: String,
        walletId: String?,
        walletDid: String?,
        holderDid: String?,
        options: WalletCreationOptions
    ): Any {
        val connection = options.additionalProperties["connectionString"] as? String
            ?: error("connectionString is required")
        require(options.enablePresentation) { "Presentations must be enabled for Postgres wallets" }
        // build and return wallet instance
    }
}
```

## Extension Functions

### Type-Safe Capability Access

```kotlin
inline fun <T> Wallet.withOrganization(block: (CredentialOrganization) -> T): T?
inline fun <T> Wallet.withLifecycle(block: (CredentialLifecycle) -> T): T?
inline fun <T> Wallet.withPresentation(block: (CredentialPresentation) -> T): T?
inline fun <T> Wallet.withDidManagement(block: (DidManagement) -> T): T?
inline fun <T> Wallet.withKeyManagement(block: (KeyManagement) -> T): T?
inline fun <T> Wallet.withIssuance(block: (CredentialIssuance) -> T): T?
```

## WalletDirectory

Instance-scoped registry for wallet management.

```kotlin
val directory = WalletDirectory()
directory.register(wallet)
val retrieved = directory.get(wallet.walletId)
val didWallet = directory.getByDid("did:key:holder")
val orgWallets = directory.findByCapability(CredentialOrganization::class)
directory.unregister(wallet.walletId)
directory.clear()
```

> **Heads up:** `WalletDirectory` only indexes `walletDid` / `holderDid` values for wallets that implement `DidManagement`. Pure storage wallets will still resolve by `walletId`, but `getByDid` will return `null`.

## WalletBuilder

The Trust Layer DSL exposes a `wallet { ... }` builder backed by `WalletCreationOptionsBuilder`:

```kotlin
val wallet = trustLayer.wallet {
    id("my-wallet")
    holder("did:key:holder")
    enableOrganization()
    enablePresentation()
    option("connectionString", "jdbc:postgresql://localhost/TrustWeave")
}
```

Available builder functions:

| Function | Description |
|----------|-------------|
| `id(String)` | Override the generated wallet identifier |
| `holder(String)` | Set the holder DID (required) |
| `walletDid(String)` | Override the wallet DID (defaults to `did:key:test-wallet-<id>`) |
| `provider(String)` | Select a provider by name (defaults to `inMemory`) |
| `inMemory()` / `basic()` | Convenience methods that set the provider |
| `enableOrganization()` | Turns on collections, tags, and metadata features |
| `enablePresentation()` | Enables presentation and selective disclosure support |
| `option(key, value)` | Add provider-specific configuration (retrievable via `options.additionalProperties[key]`) |

## Implementations

### BasicWallet

Minimal wallet implementation (storage only).

```kotlin
class BasicWallet(
    override val walletId: String = UUID.randomUUID().toString()
) : Wallet
```

### InMemoryWallet

Full-featured in-memory wallet for testing.

```kotlin
class InMemoryWallet(
    override val walletId: String = UUID.randomUUID().toString(),
    override val walletDid: String = "did:key:test-wallet-$walletId",
    override val holderDid: String = "did:key:test-holder-$walletId"
) : Wallet,
    CredentialOrganization,
    CredentialLifecycle,
    CredentialPresentation
```

## Usage Examples

See the [Wallet API Tutorial](../tutorials/wallet-api-tutorial.md) for comprehensive usage examples.

## Related Documentation

- [Wallets Core Concept](../core-concepts/wallets.md)
- [Wallet API Tutorial](../tutorials/wallet-api-tutorial.md)
- [Verifiable Credentials](../core-concepts/verifiable-credentials.md)

