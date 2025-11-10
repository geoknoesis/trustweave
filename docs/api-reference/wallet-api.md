# Wallet API Reference

Complete API reference for VeriCore's Wallet system.

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

### DidManagement

Optional interface for DID management.

```kotlin
interface DidManagement {
    val walletDid: String
    val holderDid: String
    suspend fun createDid(method: String, options: Map<String, Any?> = emptyMap()): String
    suspend fun getDids(): List<String>
    suspend fun getPrimaryDid(): String
    suspend fun setPrimaryDid(did: String): Boolean
    suspend fun resolveDid(did: String): Any? // DidDocument
}
```

### KeyManagement

Optional interface for key management.

```kotlin
interface KeyManagement {
    suspend fun generateKey(algorithm: String, options: Map<String, Any?> = emptyMap()): String
    suspend fun getKeys(): List<KeyInfo>
    suspend fun getKey(keyId: String): KeyInfo?
    suspend fun deleteKey(keyId: String): Boolean
    suspend fun sign(keyId: String, data: ByteArray): ByteArray
}
```

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

Builder for creating wallets.

```kotlin
class WalletBuilder {
    fun withWalletId(id: String): WalletBuilder
    fun withWalletDid(did: String): WalletBuilder
    fun withHolderDid(did: String): WalletBuilder
    fun enableOrganization(): WalletBuilder
    fun enableLifecycle(): WalletBuilder
    fun enablePresentation(presentationService: Any? = null): WalletBuilder
    fun enableDidManagement(didRegistry: Any): WalletBuilder
    fun enableKeyManagement(kms: Any): WalletBuilder
    fun enableIssuance(credentialIssuer: Any): WalletBuilder
    suspend fun build(): Wallet
}
```

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

