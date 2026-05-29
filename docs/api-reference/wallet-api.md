---
title: Wallet API Reference
nav_order: 40
parent: API Reference
---

# Wallet API Reference

Complete API reference for TrustWeave's Wallet system.

```kotlin
dependencies {
    implementation("org.trustweave:distribution-all:0.6.0")
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
    val capabilities: WalletCapabilities       // Derived from `is`-checks against capability interfaces
    suspend fun getStatistics(): WalletStatistics
}
```

There is **no** `supports(capability: KClass<T>)` on `Wallet`. Detect capabilities with either:
- Compile-time: `if (wallet is CredentialCollections) { ... }` / `wallet.withCollections { ... }` (preferred)
- Runtime: `wallet.capabilities.supports("collections")` (string lookup)

| Method | Returns | Notes |
|--------|---------|-------|
| `getStatistics()` | `WalletStatistics` | Default implementation derived from `list()` and capability checks. |

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

### CredentialCollections / CredentialTagging / CredentialOrganization

The combined `CredentialOrganization` capability is split into two narrower interfaces; wallets may implement either, both, or the combined alias.

```kotlin
interface CredentialCollections {
    suspend fun createCollection(name: String, description: String? = null): String
    suspend fun getCollection(collectionId: String): CredentialCollection?
    suspend fun listCollections(): List<CredentialCollection>
    suspend fun deleteCollection(collectionId: String): Boolean
    suspend fun addToCollection(credentialId: String, collectionId: String): Boolean
    suspend fun removeFromCollection(credentialId: String, collectionId: String): Boolean
    suspend fun getCredentialsInCollection(collectionId: String): List<VerifiableCredential>
}

interface CredentialTagging {
    suspend fun tagCredential(credentialId: String, tags: Set<String>): Boolean
    suspend fun untagCredential(credentialId: String, tags: Set<String>): Boolean
    suspend fun getTags(credentialId: String): Set<String>
    suspend fun getAllTags(): Set<String>
    suspend fun findByTag(tag: String): List<VerifiableCredential>

    // Tag-adjacent metadata lives here, not on a separate metadata interface
    suspend fun addMetadata(credentialId: String, metadata: Map<String, Any>): Boolean
    suspend fun getMetadata(credentialId: String): CredentialMetadata?
    suspend fun updateNotes(credentialId: String, notes: String?): Boolean
}

interface CredentialOrganization : CredentialCollections, CredentialTagging

#### Method summary

| Method | Returns | Exceptions | Notes |
|--------|---------|------------|-------|
| `createCollection` | `String` (collectionId) | `IllegalArgumentException` when the name already exists. | Use for folders/projects. |
| `addToCollection` / `removeFromCollection` | `Boolean` | – | `false` indicates missing credential or collection. |
| `tagCredential` / `untagCredential` | `Boolean` | – | Implementations may normalise tags to lowercase. |
| `addMetadata` | `Boolean` | `IllegalStateException` if metadata updates unsupported. | Ideal for provenance/data catalog info. |
```

### CredentialLifecycle

Optional interface for lifecycle management (`org.trustweave.wallet.CredentialLifecycle`).

```kotlin
import org.trustweave.credential.model.vc.VerifiableCredential

interface CredentialLifecycle {
    suspend fun archive(credentialId: String): Boolean
    suspend fun unarchive(credentialId: String): Boolean
    suspend fun getArchived(): List<VerifiableCredential>
    suspend fun refreshCredential(credentialId: String): VerifiableCredential?
}
```

#### Method summary

| Method | Returns | Notes |
|--------|---------|-------|
| `archive` | `Boolean` | `false` indicates the credential id was not found. Archived credentials are hidden from `Wallet.list()` queries. |
| `unarchive` | `Boolean` | `false` indicates the credential id was not found. |
| `getArchived` | `List<VerifiableCredential>` | Used by the default `Wallet.getStatistics()` implementation. |
| `refreshCredential` | `VerifiableCredential?` | Re-fetches the credential through its refresh service. Returns `null` if refresh failed or the credential is unknown. |

Wallet implementations that do not support lifecycle should simply not implement this
interface. Capability probing pattern:

```kotlin
val archived = (wallet as? CredentialLifecycle)?.getArchived().orEmpty()
```

### CredentialPresentation

Optional interface for creating presentations.

```kotlin
interface CredentialPresentation {
    suspend fun createPresentation(
        credentialIds: List<String>,
        holderDid: String,
        options: ProofOptions
    ): VerifiablePresentation

    suspend fun createSelectiveDisclosure(
        credentialIds: List<String>,
        disclosedFields: List<String>,
        holderDid: String,
        options: ProofOptions
    ): VerifiablePresentation
}
```

(`ProofOptions` is `org.trustweave.credential.proof.ProofOptions`; use **`proofOptions { … }`** / **`proofOptionsForPresentation { … }`** builders where appropriate.)

#### Method summary

| Method | Purpose | Exceptions | Notes |
|--------|---------|------------|-------|
| `createPresentation` | Build a verifiable presentation from stored credential IDs. | `IllegalArgumentException` when credential IDs are missing or unknown. | Uses configured proof generator; pass challenge/domain/proof suite via **`ProofOptions`**. |
| `createSelectiveDisclosure` | Produce a filtered presentation revealing selected fields. | Same as above. | Default implementation delegates to `createPresentation`. |

### DidManagement

Optional interface for DID management (`org.trustweave.wallet.DidManagement`).

```kotlin
import org.trustweave.did.DidCreationOptions
import org.trustweave.did.DidCreationOptionsBuilder
import org.trustweave.did.model.DidDocument

interface DidManagement {
    val walletDid: String
    val holderDid: String
    suspend fun createDid(method: String, options: DidCreationOptions = DidCreationOptions()): String
    suspend fun createDid(method: String, configure: DidCreationOptionsBuilder.() -> Unit): String
    suspend fun getDids(): List<String>
    suspend fun getPrimaryDid(): String
    suspend fun setPrimaryDid(did: String): Boolean
    suspend fun resolveDid(did: String): DidDocument?
}
```

| Method | Returns | Exceptions | Notes |
|--------|---------|------------|-------|
| `createDid` | DID string | `IllegalArgumentException` when method not registered. | Overload allows typed builder. |
| `getDids` / `getPrimaryDid` | `List<String>` / `String` | – | Use to display wallet inventory. |
| `resolveDid` | `DidDocument?` | `IllegalStateException` if no resolver configured. | Handy for UX that surfaces DID metadata. |

### KeyManagement

Optional interface for key management (`org.trustweave.wallet.KeyManagement`).

```kotlin
interface KeyManagement {
    suspend fun generateKey(
        algorithm: String,
        options: Map<String, Any?> = emptyMap()
    ): String                                    // returns the new key id
    suspend fun getKeys(): List<KeyInfo>
    suspend fun getKey(keyId: String): KeyInfo?
    suspend fun deleteKey(keyId: String): Boolean
    suspend fun sign(keyId: String, data: ByteArray): ByteArray
}
```

| Method | Returns | Notes |
|--------|---------|-------|
| `generateKey` | `String` (key id) | Algorithm name + provider-specific options map. |
| `deleteKey` | `Boolean` | `true` indicates the key was removed. |
| `sign` | `ByteArray` | `keyId` is a plain `String`. |

### CredentialIssuance

Optional interface for credential issuance (`org.trustweave.wallet.CredentialIssuance`).

```kotlin
import org.trustweave.credential.proof.ProofOptions
import org.trustweave.credential.model.vc.VerifiableCredential

interface CredentialIssuance {
    suspend fun issueCredential(
        subjectDid: String,
        credentialType: String,
        claims: Map<String, Any>,
        options: ProofOptions? = null
    ): VerifiableCredential
}
```

Most production code should delegate to `org.trustweave.credential.CredentialService.issue(IssuanceRequest)` (or the `trustWeave.issue { ... }` DSL) rather than calling wallet-facing issuance directly.

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
import kotlinx.datetime.Instant
import kotlinx.datetime.Clock

data class CredentialCollection(
    val id: String,
    val name: String,
    val description: String? = null,
    val createdAt: Instant = Clock.System.now(),
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
    val createdAt: Instant = Clock.System.now(),
    val updatedAt: Instant = Clock.System.now()
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

`WalletCreationOptions` is shared by the TrustWeave facade, the `TrustWeave.build { wallet { ... } }` DSL, and custom `WalletFactory` implementations. It removes the need for untyped configuration blobs while still allowing provider-specific extensions.

```kotlin
import org.trustweave.wallet.services.WalletCreationOptionsBuilder

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
import org.trustweave.wallet.Wallet
import org.trustweave.wallet.services.WalletCreationOptions
import org.trustweave.wallet.services.WalletFactory

class PostgresWalletFactory : WalletFactory {
    override suspend fun create(
        providerName: String,
        walletId: String?,
        walletDid: String?,
        holderDid: String?,
        options: WalletCreationOptions
    ): Wallet {
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
// Reified type parameter (no KClass argument):
val orgWallets = directory.findByCapability<CredentialOrganization>()
// Or by feature name (matches WalletCapabilities.supports):
val collectionsWallets = directory.findByCapability("collections")
directory.unregister(wallet.walletId)
directory.clear()
```

> **Heads up:** `WalletDirectory` only indexes `walletDid` / `holderDid` values for wallets that implement `DidManagement`. Pure storage wallets will still resolve by `walletId`, but `getByDid` will return `null`.

## WalletBuilder

The TrustWeave wallet DSL exposes a `wallet { ... }` builder backed by `WalletCreationOptionsBuilder`:

```kotlin
val wallet = trustWeave.wallet {
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

- Wallets Core Concept](../core-concepts/wallets.md)
- Wallet API Tutorial](../tutorials/wallet-api-tutorial.md)
- Verifiable Credentials](../core-concepts/verifiable-credentials.md)

