# Wallet API Tutorial

This tutorial provides a comprehensive guide to using VeriCore's Wallet API. You'll learn how to create wallets, store credentials, organize them, and create presentations.

## Prerequisites

- Basic understanding of Kotlin
- Familiarity with coroutines
- Understanding of [DIDs](../core-concepts/dids.md) and [Verifiable Credentials](../core-concepts/verifiable-credentials.md)

## Table of Contents

1. [Creating a Wallet](#creating-a-wallet)
2. [Storing Credentials](#storing-credentials)
3. [Retrieving Credentials](#retrieving-credentials)
4. [Organizing Credentials](#organizing-credentials)
5. [Querying Credentials](#querying-credentials)
6. [Creating Presentations](#creating-presentations)
7. [Lifecycle Management](#lifecycle-management)
8. [Advanced Features](#advanced-features)

## Creating a Wallet

### Facade Wallet (Recommended)

```kotlin
import com.geoknoesis.vericore.VeriCore
import kotlinx.coroutines.runBlocking

fun main() = runBlocking {
    val vericore = VeriCore.create()

    val wallet = vericore.createWallet(
        holderDid = "did:key:holder"
    ) {
        label = "Holder Wallet"
        enableOrganization = true
        enablePresentation = true
        property("storagePath", "/var/lib/vericore/wallets/holder")
    }.getOrThrow()

    println("Wallet ID: ${wallet.walletId}")
}
```

### Trust Layer DSL

```kotlin
import com.geoknoesis.vericore.credential.dsl.trustLayer
import kotlinx.coroutines.runBlocking

fun main() = runBlocking {
    val trustLayer = trustLayer {
        keys { provider("inMemory") }
        did { method("key") }
    }

    val wallet = trustLayer.wallet {
        id("team-wallet")
        holder("did:key:team-holder")
        enableOrganization()
        enablePresentation()
        option("connectionString", "jdbc:postgresql://localhost/wallets")
    }

    println("Wallet DID: ${wallet.walletId}")
}
```

### Testkit Wallets

`BasicWallet` and `InMemoryWallet` remain available for lightweight unit tests:

```kotlin
import com.geoknoesis.vericore.testkit.credential.BasicWallet
import com.geoknoesis.vericore.testkit.credential.InMemoryWallet

val basic = BasicWallet()
val inMemory = InMemoryWallet(holderDid = "did:key:test-holder")
```

## Storing Credentials

### Basic Storage

```kotlin
import com.geoknoesis.vericore.credential.models.VerifiableCredential
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

// Create a credential
val credential = VerifiableCredential(
    id = "https://example.com/credentials/123",
    type = listOf("VerifiableCredential", "PersonCredential"),
    issuer = "did:key:issuer",
    credentialSubject = buildJsonObject {
        put("id", "did:key:subject")
        put("name", "Alice")
        put("email", "alice@example.com")
    },
    issuanceDate = "2023-01-01T00:00:00Z"
)

// Store it
val credentialId = wallet.store(credential)
println("Stored credential: $credentialId")
```

### Storing Multiple Credentials

```kotlin
val credentials = listOf(credential1, credential2, credential3)
val credentialIds = credentials.map { wallet.store(it) }
println("Stored ${credentialIds.size} credentials")
```

## Retrieving Credentials

### Get by ID

```kotlin
val credential = wallet.get(credentialId)
if (credential != null) {
    println("Found credential: ${credential.id}")
} else {
    println("Credential not found")
}
```

### List All Credentials

```kotlin
val allCredentials = wallet.list()
println("Total credentials: ${allCredentials.size}")
```

### List with Filter

```kotlin
import com.geoknoesis.vericore.credential.wallet.CredentialFilter

val workCredentials = wallet.list(
    filter = CredentialFilter(
        issuer = "did:key:work-issuer",
        type = listOf("WorkCredential")
    )
)
```

## Organizing Credentials

### Collections

Create collections to group related credentials:

```kotlin
if (wallet is CredentialOrganization) {
    // Create a collection
    val workCollection = wallet.createCollection(
        name = "Work Credentials",
        description = "Professional credentials and certifications"
    )
    
    // Add credentials to collection
    wallet.addToCollection(credentialId, workCollection)
    
    // Get credentials in collection
    val workCreds = wallet.getCredentialsInCollection(workCollection)
    println("Work credentials: ${workCreds.size}")
    
    // List all collections
    val collections = wallet.listCollections()
    collections.forEach { collection ->
        println("Collection: ${collection.name} (${collection.credentialCount} credentials)")
    }
}
```

### Tags

Tag credentials for easy filtering:

```kotlin
if (wallet is CredentialOrganization) {
    // Tag a credential
    wallet.tagCredential(credentialId, setOf("important", "verified", "work"))
    
    // Get tags for a credential
    val tags = wallet.getTags(credentialId)
    println("Tags: $tags")
    
    // Find credentials by tag
    val importantCreds = wallet.findByTag("important")
    
    // Get all tags
    val allTags = wallet.getAllTags()
    println("All tags: $allTags")
    
    // Remove tags
    wallet.untagCredential(credentialId, setOf("work"))
}
```

### Metadata

Add custom metadata to credentials:

```kotlin
if (wallet is CredentialOrganization) {
    // Add metadata
    wallet.addMetadata(credentialId, mapOf(
        "source" to "issuer.com",
        "verified" to true,
        "priority" to "high"
    ))
    
    // Get metadata
    val metadata = wallet.getMetadata(credentialId)
    println("Metadata: ${metadata?.metadata}")
    
    // Add notes
    wallet.updateNotes(credentialId, "This credential was verified manually")
}
```

## Querying Credentials

### Basic Query

```kotlin
val credentials = wallet.query {
    byIssuer("did:key:issuer")
    byType("PersonCredential")
    notExpired()
}
```

### Complex Query

```kotlin
val validWorkCredentials = wallet.query {
    byTypes("WorkCredential", "EmploymentCredential", "CertificationCredential")
    bySubject("did:key:subject")
    notExpired()
    notRevoked()
    valid() // Has proof, not expired, not revoked
}
```

### Query Examples

```kotlin
// Find expired credentials
val expired = wallet.query {
    expired()
}

// Find revoked credentials
val revoked = wallet.query {
    revoked()
}

// Find credentials by issuer and type
val specific = wallet.query {
    byIssuer("did:key:university")
    byType("DegreeCredential")
}
```

## Creating Presentations

### Basic Presentation

```kotlin
import com.geoknoesis.vericore.credential.PresentationOptions

if (wallet is CredentialPresentation) {
    val presentation = wallet.createPresentation(
        credentialIds = listOf(credentialId1, credentialId2),
        holderDid = "did:key:holder",
        options = PresentationOptions(
            holderDid = "did:key:holder",
            proofType = "Ed25519Signature2020",
            challenge = "random-challenge-123",
            domain = "example.com"
        )
    )
    
    println("Created presentation with ${presentation.verifiableCredential.size} credentials")
}
```

### Selective Disclosure

Reveal only specific fields:

```kotlin
if (wallet is CredentialPresentation) {
    val selectivePresentation = wallet.createSelectiveDisclosure(
        credentialIds = listOf(credentialId),
        disclosedFields = listOf(
            "name",
            "email",
            "credentialSubject.degree.name"
        ),
        holderDid = "did:key:holder",
        options = PresentationOptions(...)
    )
}
```

## Lifecycle Management

### Archiving Credentials

```kotlin
if (wallet is CredentialLifecycle) {
    // Archive old credentials
    wallet.archive(oldCredentialId)
    
    // Get archived credentials
    val archived = wallet.getArchived()
    println("Archived credentials: ${archived.size}")
    
    // Unarchive if needed
    wallet.unarchive(oldCredentialId)
}
```

### Refreshing Credentials

```kotlin
if (wallet is CredentialLifecycle) {
    // Refresh a credential (if refresh service available)
    val refreshed = wallet.refreshCredential(credentialId)
    if (refreshed != null) {
        println("Credential refreshed")
    }
}
```

## Advanced Features

### Wallet Statistics

Get an overview of your wallet:

```kotlin
val stats = wallet.getStatistics()
println("""
    Wallet Statistics:
    - Total credentials: ${stats.totalCredentials}
    - Valid credentials: ${stats.validCredentials}
    - Expired credentials: ${stats.expiredCredentials}
    - Revoked credentials: ${stats.revokedCredentials}
    - Collections: ${stats.collectionsCount}
    - Tags: ${stats.tagsCount}
    - Archived: ${stats.archivedCount}
""".trimIndent())
```

### Type-Safe Capability Checking

```kotlin
// Check capabilities at compile-time
when {
    wallet is CredentialOrganization && wallet is CredentialPresentation -> {
        // Full-featured wallet
        wallet.createCollection("My Collection")
        wallet.createPresentation(...)
    }
    wallet is CredentialOrganization -> {
        // Organization-only wallet
        wallet.createCollection("My Collection")
    }
    else -> {
        // Basic wallet
        wallet.store(credential)
    }
}
```

### Extension Functions

Use extension functions for elegant code:

```kotlin
// Organization features
wallet.withOrganization { org ->
    val collectionId = org.createCollection("Work")
    org.tagCredential(credentialId, setOf("important"))
}

// Lifecycle features
wallet.withLifecycle { lifecycle ->
    lifecycle.archive(oldCredentialId)
}

// Presentation features
wallet.withPresentation { presentation ->
    val vp = presentation.createPresentation(...)
}
```

### Wallet Directory

Manage multiple wallets with an instance-scoped directory:

```kotlin
import com.geoknoesis.vericore.credential.wallet.WalletDirectory

val directory = WalletDirectory()

// Register wallets
val wallet1 = BasicWallet()
val wallet2 = InMemoryWallet()
directory.register(wallet1)
directory.register(wallet2)

// Find wallets
val retrieved = directory.get(wallet1.walletId)
val byDid = directory.getByDid("did:key:wallet")

// Find wallets with specific capabilities
val orgWallets = directory.findByCapability(CredentialOrganization::class)
val walletsWithCollections = directory.findByCapability("collections")
```

## Complete Example

Here's a complete example combining all features:

```kotlin
import com.geoknoesis.vericore.testkit.credential.InMemoryWallet
import com.geoknoesis.vericore.credential.models.VerifiableCredential
import com.geoknoesis.vericore.credential.PresentationOptions
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

fun main() = runBlocking {
    // Create wallet
    val wallet = InMemoryWallet(
        walletDid = "did:key:wallet",
        holderDid = "did:key:holder"
    )
    
    // Store credentials
    val credential1 = createCredential("Alice", "alice@example.com")
    val credential2 = createCredential("Bob", "bob@example.com")
    
    val id1 = wallet.store(credential1)
    val id2 = wallet.store(credential2)
    
    // Organize credentials
    val workCollection = wallet.createCollection("Work Credentials")
    wallet.addToCollection(id1, workCollection)
    wallet.tagCredential(id1, setOf("important", "verified"))
    
    // Query credentials
    val important = wallet.query {
        byIssuer("did:key:issuer")
        valid()
    }
    
    // Create presentation
    val presentation = wallet.createPresentation(
        credentialIds = listOf(id1),
        holderDid = wallet.holderDid,
        options = PresentationOptions(
            holderDid = wallet.holderDid,
            proofType = "Ed25519Signature2020"
        )
    )
    
    // Get statistics
    val stats = wallet.getStatistics()
    println("Wallet has ${stats.totalCredentials} credentials")
}

fun createCredential(name: String, email: String): VerifiableCredential {
    return VerifiableCredential(
        type = listOf("VerifiableCredential", "PersonCredential"),
        issuer = "did:key:issuer",
        credentialSubject = buildJsonObject {
            put("id", "did:key:subject")
            put("name", name)
            put("email", email)
        },
        issuanceDate = "2023-01-01T00:00:00Z"
    )
}
```

## Best Practices

1. **Use type-safe checks** (`wallet is CredentialOrganization`) when possible
2. **Organize credentials** early using collections and tags
3. **Query efficiently** by combining filters
4. **Use selective disclosure** to minimize data exposure
5. **Archive instead of delete** to maintain history
6. **Monitor statistics** to track wallet health

## Next Steps

- Explore the [Wallet API Reference](../api-reference/wallet-api.md)
- Learn about [DIDs](../core-concepts/dids.md) and [Verifiable Credentials](../core-concepts/verifiable-credentials.md)
- Check out [Advanced Topics](../advanced/README.md) for custom implementations

