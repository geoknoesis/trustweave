# Wallet API Tutorial

This tutorial provides a comprehensive guide to using TrustWeave's Wallet API. You'll learn how to create wallets, store credentials, organize them, and create presentations.

```kotlin
dependencies {
    implementation("com.trustweave:trustweave-common:1.0.0-SNAPSHOT")
    implementation("com.trustweave:trustweave-trust:1.0.0-SNAPSHOT")
    implementation("com.trustweave:trustweave-testkit:1.0.0-SNAPSHOT")
}
```

**Result:** Gives you the wallet DSL, trust layer builders, and in-memory implementations used throughout this tutorial.

> Tip: The runnable quick-start sample (`./gradlew :TrustWeave-examples:runQuickStartSample`) mirrors the core flows below. Clone it as a starting point before wiring more advanced wallet logic.

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

### Service API Wallet (Recommended)

```kotlin
import com.trustweave.TrustWeave
import com.trustweave.wallet.WalletCreationOptions
import kotlinx.coroutines.runBlocking

fun main() = runBlocking {
    val trustweave = TrustWeave.create()

    try {
        val wallet = trustweave.wallets.create(
            holderDid = "did:key:holder",
            options = WalletCreationOptions(
                label = "Holder Wallet",
                enableOrganization = true,
                enablePresentation = true
            )
        )

        println("Wallet ID: ${wallet.walletId}")
        println("Holder: ${wallet.holderDid}")
    } catch (error: TrustWeaveError) {
        println("Wallet creation failed: ${error.message}")
    }
}
```

**Outcome:** Creates a production-style wallet via the TrustWeave service API, complete with organization/presentation capabilities.

### Trust Layer DSL

```kotlin
import com.trustweave.credential.dsl.trustLayer
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

**Outcome:** Builds a wallet from the trust-layer DSL, handy when you need more control over KMS/DID configuration.

### Testkit Wallets

`BasicWallet` and `InMemoryWallet` remain available for lightweight unit tests:

```kotlin
import com.trustweave.testkit.credential.BasicWallet
import com.trustweave.testkit.credential.InMemoryWallet

val basic = BasicWallet()
val inMemory = InMemoryWallet(holderDid = "did:key:test-holder")
```

**Outcome:** Shows the lightweight testkit wallets you can use in unit tests or prototypes.

## Storing Credentials

### Basic Storage

```kotlin
import com.trustweave.credential.models.VerifiableCredential
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

**Outcome:** Persists a credential in the wallet and returns its storage identifier.

### Storing Multiple Credentials

```kotlin
val credentials = listOf(credential1, credential2, credential3)
val credentialIds = credentials.map { wallet.store(it) }
println("Stored ${credentialIds.size} credentials")
```

**Outcome:** Demonstrates bulk storage patterns for credential lists.

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

**Outcome:** Retrieves a stored credential by ID and handles the nullable response.

### List All Credentials

```kotlin
val allCredentials = wallet.list()
println("Total credentials: ${allCredentials.size}")
```

**Outcome:** Lists every credential in the wallet—useful for dashboards or audits.

### List with Filter

```kotlin
import com.trustweave.credential.wallet.CredentialFilter

val workCredentials = wallet.list(
    filter = CredentialFilter(
        issuer = "did:key:work-issuer",
        type = listOf("WorkCredential")
    )
)
```

**Outcome:** Shows how filter criteria narrow the result set without writing manual queries.

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

**Outcome:** Creates collections, adds credentials, and lists collection metadata when the wallet supports organization.

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

**Outcome:** Demonstrates tagging workflows—ideal for building saved searches or UI filters.

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
import com.trustweave.credential.PresentationOptions

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
import com.trustweave.credential.wallet.WalletDirectory

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
import com.trustweave.testkit.credential.InMemoryWallet
import com.trustweave.credential.models.VerifiableCredential
import com.trustweave.credential.PresentationOptions
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

fun main() = runBlocking {
    // Create wallet using TrustWeave service API
    val trustweave = TrustWeave.create()
    val wallet = trustweave.wallets.create(holderDid = "did:key:holder")
    
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

