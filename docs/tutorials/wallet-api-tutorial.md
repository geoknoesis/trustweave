---
title: Wallet API Tutorial
nav_exclude: true
---

# Wallet API Tutorial

This tutorial provides a comprehensive guide to using TrustWeave's Wallet API. You'll learn how to create wallets, store credentials, organize them, and create presentations.

```kotlin
dependencies {
    implementation("org.trustweave:common:0.6.0")
    implementation("org.trustweave:trust:0.6.0")
    implementation("org.trustweave:testkit:0.6.0")
}
```

**Result:** Wallet APIs, **`TrustWeave`**, and testkit factories used in this tutorial.

> Tip: Run **`./gradlew :distribution:examples:runQuickStartSample`** (or follow [Quick start](getting-started/quick-start.md)) for runnable samples aligned with the snippets below.

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
// Kotlin stdlib
import kotlinx.coroutines.runBlocking

// TrustWeave core
import org.trustweave.trust.TrustWeave
import org.trustweave.trust.dsl.credential.DidMethods
import org.trustweave.trust.dsl.credential.KeyAlgorithms
import org.trustweave.trust.dsl.credential.KmsProviders
import org.trustweave.trust.types.getOrThrow
import org.trustweave.core.exception.TrustWeaveException

fun main() = runBlocking {
    val trustWeave = TrustWeave.build {
        keys { provider(KmsProviders.IN_MEMORY); algorithm(KeyAlgorithms.ED25519) }
        did { method(DidMethods.KEY) { algorithm(KeyAlgorithms.ED25519) } }
    }

    try {
        val wallet = trustWeave.wallet {
            holder("did:key:holder")
            provider("inMemory")
            enablePresentation()
        }.getOrThrow()

        println("Wallet ID: ${wallet.walletId}")
    } catch (error: TrustWeaveException) {
        println("Wallet creation failed: ${error.message}")
    }
}
```

**Outcome:** Creates a wallet via **`TrustWeave.wallet { }`** and unwraps **`WalletCreationResult`** with **`getOrThrow()`** (see [result types](../api-reference/result-types-guide.md)).

### Same pattern with `TrustWeave.build`

Use **`TrustWeave.build { keys { ... }; did { ... }; factories(walletFactory = ...) }`** when you need explicit KMS/DID/wallet factory wiring instead of defaults-only demos.

### Testkit Wallets

`BasicWallet` and `InMemoryWallet` remain available for lightweight unit tests:

```kotlin
import org.trustweave.testkit.credential.BasicWallet
import org.trustweave.testkit.credential.InMemoryWallet

val basic = BasicWallet()
val inMemory = InMemoryWallet(holderDid = "did:key:test-holder")
```

**Outcome:** Shows the lightweight testkit wallets you can use in unit tests or prototypes.

## Storing Credentials

### Basic Storage

In 0.6.0 the `VerifiableCredential` constructor was tightened to use typed
identifiers (`Issuer`, `CredentialSubject`, `kotlinx.datetime.Instant`,
`List<CredentialType>`). Always build credentials through the issuance DSL — never
the raw constructor — and store the result. Storage itself is unchanged.

```kotlin
import org.trustweave.credential.results.IssuanceResult
import org.trustweave.did.identifiers.Did

// 1. Issue via the DSL (full walkthrough in the Credential Issuance tutorial).
val credential = (trustWeave.issue {
    credential {
        issuer(issuerDid)
        subject {
            id(holderDid)
            claim("degree", "Bachelor of Science")
        }
    }
    signedBy(issuerDid = Did(issuerDid), keyId = "key-1")
} as IssuanceResult.Success).credential

// 2. Store it.
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
import org.trustweave.wallet.CredentialFilter

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
import org.trustweave.credential.proof.ProofOptions
import org.trustweave.credential.proof.ProofPurpose

if (wallet is CredentialPresentation) {
    val presentation = wallet.createPresentation(
        credentialIds = listOf(credentialId1, credentialId2),
        holderDid = "did:key:holder",
        options = ProofOptions(
            purpose = ProofPurpose.Authentication,
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
import org.trustweave.credential.proof.ProofOptions
import org.trustweave.credential.proof.ProofPurpose

if (wallet is CredentialPresentation) {
    val selectivePresentation = wallet.createSelectiveDisclosure(
        credentialIds = listOf(credentialId),
        disclosedFields = listOf(
            "name",
            "email",
            "credentialSubject.degree.name"
        ),
        holderDid = "did:key:holder",
        options = ProofOptions(purpose = ProofPurpose.Authentication)
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
import org.trustweave.testkit.credential.BasicWallet
import org.trustweave.testkit.credential.InMemoryWallet
import org.trustweave.wallet.CredentialOrganization
import org.trustweave.wallet.WalletDirectory

val directory = WalletDirectory()

// Register wallets
val wallet1 = BasicWallet()
val wallet2 = InMemoryWallet()
directory.register(wallet1)
directory.register(wallet2)

// Find wallets
val retrieved = directory.get(wallet1.walletId)
val byDid = directory.getByDid("did:key:wallet")

// Find wallets with specific capabilities (compile-time, reified)
val orgWallets: List<CredentialOrganization> = directory.findByCapability()
// Or by capability feature name (runtime, from WalletCapabilities)
val walletsWithCollections = directory.findByCapability("collections")
```

## Complete Example

Here's a complete example combining all features:

```kotlin
// Kotlin stdlib
import kotlinx.coroutines.runBlocking

// TrustWeave core
import org.trustweave.trust.TrustWeave
import org.trustweave.trust.dsl.credential.DidMethods
import org.trustweave.trust.dsl.credential.KeyAlgorithms
import org.trustweave.trust.dsl.credential.KmsProviders
import org.trustweave.trust.types.getOrThrow
import org.trustweave.credential.model.vc.VerifiableCredential
import org.trustweave.credential.proof.ProofOptions
import org.trustweave.credential.proof.ProofPurpose
import org.trustweave.wallet.CredentialOrganization
import org.trustweave.wallet.CredentialPresentation
import org.trustweave.wallet.DidManagement

fun main() = runBlocking {
    val trustWeave = TrustWeave.build {
        keys { provider(KmsProviders.IN_MEMORY); algorithm(KeyAlgorithms.ED25519) }
        did { method(DidMethods.KEY) { algorithm(KeyAlgorithms.ED25519) } }
    }
    val wallet = trustWeave.wallet {
        holder("did:key:holder")
        provider("inMemory")
        enablePresentation()
    }.getOrThrow()

    // `credential1`/`credential2` come from trustWeave.issue { ... }.getOrThrow()
    // — see the credential-issuance tutorial for the issuance DSL.
    val credential1: VerifiableCredential = TODO("issue with trustWeave.issue { ... }")
    val credential2: VerifiableCredential = TODO("issue with trustWeave.issue { ... }")

    val id1 = wallet.store(credential1)
    val id2 = wallet.store(credential2)

    // Organize credentials (requires CredentialOrganization capability)
    if (wallet is CredentialOrganization) {
        val workCollection = wallet.createCollection("Work Credentials")
        wallet.addToCollection(id1, workCollection)
        wallet.tagCredential(id1, setOf("important", "verified"))
    }

    // Query credentials
    val important = wallet.query {
        byIssuer("did:key:issuer")
        valid()
    }

    // Create presentation (requires CredentialPresentation capability)
    val holderDid = (wallet as? DidManagement)?.holderDid ?: "did:key:holder"
    if (wallet is CredentialPresentation) {
        val presentation = wallet.createPresentation(
            credentialIds = listOf(id1),
            holderDid = holderDid,
            options = ProofOptions(purpose = ProofPurpose.Authentication)
        )
        println("Created presentation with ${presentation.verifiableCredential.size} credentials")
    }

    // Get statistics
    val stats = wallet.getStatistics()
    println("Wallet has ${stats.totalCredentials} credentials")
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
- Check out [Advanced Topics](../api-reference/advanced/README.md) for custom implementations

