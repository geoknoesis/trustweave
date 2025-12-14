---
title: Manage Wallets
nav_order: 6
parent: How-To Guides
keywords:
  - wallet
  - credentials
  - storage
  - organization
  - collections
  - tags
  - presentation
---

# Manage Wallets

This guide shows you how to create wallets, store credentials, organize them with collections and tags, and create verifiable presentations.

## Quick Example

Here's a complete example that creates a wallet, stores a credential, and organizes it:

```kotlin
import com.trustweave.trust.TrustWeave
import com.trustweave.did.resolver.DidResolutionResult
import kotlinx.coroutines.runBlocking

fun main() = runBlocking {
    // Create TrustWeave instance
    val trustWeave = TrustWeave.build {
        keys {
            provider("inMemory")
            algorithm("Ed25519")
        }
        did {
            method("key") {
                algorithm("Ed25519")
            }
        }
    }

    // Create wallet
    import com.trustweave.trust.types.WalletCreationResult
    import com.trustweave.trust.types.DidCreationResult
    import com.trustweave.trust.types.IssuanceResult
    
    val walletResult = trustWeave.wallet {
        holder("did:key:holder-placeholder")
    }
    
    val wallet = when (walletResult) {
        is WalletCreationResult.Success -> walletResult.wallet
        else -> {
            println("❌ Failed to create wallet: ${walletResult.reason}")
            return@runBlocking
        }
    }

    // Issue and store credential
    val issuerDidResult = trustWeave.createDid { method("key") }
    val issuerDid = when (issuerDidResult) {
        is DidCreationResult.Success -> issuerDidResult.did
        else -> {
            println("❌ Failed to create issuer DID: ${issuerDidResult.reason}")
            return@runBlocking
        }
    }
    
    // Get key ID for signing
    val resolutionResult = trustWeave.resolveDid(issuerDid)
    val issuerDocument = when (resolutionResult) {
        is DidResolutionResult.Success -> resolutionResult.document
        else -> throw IllegalStateException("Failed to resolve issuer DID")
    }
    val verificationMethod = issuerDocument.verificationMethod.firstOrNull()
        ?: throw IllegalStateException("No verification method found")
    val issuerKeyId = verificationMethod.id.substringAfter("#")
    
    val issuanceResult = trustWeave.issue {
        credential {
            type("VerifiableCredential", "PersonCredential")
            issuer(issuerDid.value)
            subject {
                id("did:key:holder-placeholder")
                "name" to "Alice Example"
            }
        }
        signedBy(issuerDid = issuerDid.value, keyId = issuerKeyId)
    }
    
    val credential = when (issuanceResult) {
        is IssuanceResult.Success -> issuanceResult.credential
        else -> {
            println("❌ Failed to issue credential: ${issuanceResult.reason}")
            return@runBlocking
        }
    }

    val credentialId = wallet.store(credential)
    println("✅ Stored credential: $credentialId")
}
```

**Expected Output:**
```
✅ Stored credential: urn:uuid:...
```

## Step-by-Step Guide

### Step 1: Create a Wallet

Create a wallet for a holder:

```kotlin
import com.trustweave.trust.types.WalletCreationResult

val walletResult = trustWeave.wallet {
    holder("did:key:holder")
}

val wallet = when (walletResult) {
    is WalletCreationResult.Success -> walletResult.wallet
    else -> {
        println("Failed to create wallet: ${walletResult.reason}")
        return@runBlocking // or handle appropriately
    }
}
```

### Step 2: Store Credentials

Store credentials in the wallet:

```kotlin
val credentialId = wallet.store(credential)
println("Stored credential ID: $credentialId")
```

### Step 3: Retrieve Credentials

Get credentials by ID or list all:

```kotlin
// Get by ID
val credential = wallet.get(credentialId)

// List all
val allCredentials = wallet.list()
```

### Step 4: Organize Credentials (Optional)

Use collections and tags to organize credentials:

```kotlin
wallet.withOrganization { org ->
    // Create collection
    val collectionId = org.createCollection("Education", "Academic credentials")

    // Add credential to collection
    org.addToCollection(credentialId, collectionId)

    // Add tags
    org.tagCredential(credentialId, setOf("degree", "verified"))
}
```

## Creating Wallets

### Basic Wallet

Create a simple wallet for credential storage:

```kotlin
val walletResult = trustWeave.wallet {
    holder("did:key:holder")
}

val wallet = when (walletResult) {
    is WalletCreationResult.Success -> walletResult.wallet
    else -> {
        println("Failed to create wallet: ${walletResult.reason}")
        return@runBlocking // or handle appropriately
    }
}
```

### Wallet with Organization

Enable organization features (collections, tags, metadata):

```kotlin
val walletResult = trustWeave.wallet {
    holder("did:key:holder")
    enableOrganization()
}

val wallet = when (walletResult) {
    is WalletCreationResult.Success -> walletResult.wallet
    else -> {
        println("Failed to create wallet: ${walletResult.reason}")
        return@runBlocking // or handle appropriately
    }
}
```

### Wallet with Presentation

Enable presentation creation:

```kotlin
val walletResult = trustWeave.wallet {
    holder("did:key:holder")
    enablePresentation()
}

val wallet = when (walletResult) {
    is WalletCreationResult.Success -> walletResult.wallet
    else -> {
        println("Failed to create wallet: ${walletResult.reason}")
        return@runBlocking // or handle appropriately
    }
}
```

### Full-Featured Wallet

Enable all features:

```kotlin
val walletResult = trustWeave.wallet {
    holder("did:key:holder")
    id("my-wallet-id")  // Optional: custom wallet ID
    enableOrganization()
    enablePresentation()
}

val wallet = when (walletResult) {
    is WalletCreationResult.Success -> walletResult.wallet
    else -> {
        println("Failed to create wallet: ${walletResult.reason}")
        return@runBlocking // or handle appropriately
    }
}
```

## Storing Credentials

### Basic Storage

Store a single credential:

```kotlin
val credentialId = wallet.store(credential)
```

### Batch Storage

Store multiple credentials:

```kotlin
val credentials = listOf(credential1, credential2, credential3)
val credentialIds = credentials.map { wallet.store(it) }
println("Stored ${credentialIds.size} credentials")
```

### Storage with Error Handling

Handle storage errors:

```kotlin
try {
    val credentialId = wallet.store(credential)
    println("Stored: $credentialId")
} catch (error: Exception) {
    println("Error: ${error.message}")
    error.printStackTrace()
}
```

## Organizing Credentials

### Collections

Group credentials into collections:

```kotlin
wallet.withOrganization { org ->
    // Create collection
    val educationId = org.createCollection(
        name = "Education",
        description = "Academic credentials"
    )

    // Add credential to collection
    org.addToCollection(credentialId, educationId)

    // Get credentials in collection
    val educationCreds = org.getCredentialsInCollection(educationId)

    // List all collections
    val collections = org.listCollections()
}
```

### Tags

Tag credentials for flexible querying:

```kotlin
wallet.withOrganization { org ->
    // Add tags
    org.tagCredential(credentialId, setOf("degree", "verified", "active"))

    // Get tags for credential
    val tags = org.getTags(credentialId)

    // Find credentials by tag
    val verifiedCreds = org.findByTag("verified")

    // Get all tags
    val allTags = org.getAllTags()
}
```

### Metadata

Add custom metadata to credentials:

```kotlin
wallet.withOrganization { org ->
    // Add metadata
    org.addMetadata(credentialId, mapOf(
        "category" to "education",
        "storedAt" to System.currentTimeMillis(),
        "source" to "university"
    ))

    // Get metadata
    val metadata = org.getMetadata(credentialId)

    // Add notes
    org.updateNotes(credentialId, "Important credential for job applications")
}
```

## Querying Credentials

### List All Credentials

Get all credentials in the wallet:

```kotlin
val allCredentials = wallet.list()
```

### Query with Filters

Query credentials using filters:

```kotlin
val results = wallet.query {
    byIssuer("did:key:issuer")
    notExpired()
    byType("PersonCredential")
}
```

### Query by Collection

Get credentials in a specific collection:

```kotlin
wallet.withOrganization { org ->
    val collection = org.listCollections().firstOrNull { it.name == "Education" }
    if (collection != null) {
        val creds = org.getCredentialsInCollection(collection.id)
    }
}
```

### Query by Tag

Find credentials with specific tags:

```kotlin
wallet.withOrganization { org ->
    val verifiedCreds = org.findByTag("verified")
    val activeCreds = org.findByTag("active")
}
```

## Creating Presentations

### Basic Presentation

Create a verifiable presentation:

```kotlin
wallet.withPresentation { pres ->
    val presentation = pres.createPresentation(
        credentialIds = listOf(credentialId),
        holderDid = "did:key:holder",
        options = PresentationOptions(
            holderDid = "did:key:holder",
            challenge = "job-application-${System.currentTimeMillis()}"
        )
    )

    println("Created presentation: ${presentation.id}")
}
```

### Selective Disclosure

Create a presentation with selective disclosure:

```kotlin
wallet.withPresentation { pres ->
    val presentation = pres.createSelectiveDisclosure(
        credentialIds = listOf(credentialId),
        disclosedFields = listOf("name", "email"),  // Only reveal these fields
        holderDid = "did:key:holder",
        options = PresentationOptions(...)
    )
}
```

## Wallet Capabilities

Check what capabilities a wallet supports:

```kotlin
// Check if wallet supports organization
if (wallet is CredentialOrganization) {
    // Use organization features
}

// Using extension functions (recommended)
wallet.withOrganization { org ->
    // Organization features available
}

wallet.withPresentation { pres ->
    // Presentation features available
}
```

## Common Patterns

### Pattern 1: Organize After Storage

Organize credentials immediately after storing:

```kotlin
val credentialId = wallet.store(credential)

wallet.withOrganization { org ->
    // Create or get collection
    val collectionId = org.createCollection("Work", "Professional credentials")

    // Add to collection
    org.addToCollection(credentialId, collectionId)

    // Add tags
    org.tagCredential(credentialId, setOf("work", "verified"))

    // Add metadata
    org.addMetadata(credentialId, mapOf(
        "storedAt" to System.currentTimeMillis()
    ))
}
```

### Pattern 2: Query and Organize

Query credentials and organize them:

```kotlin
// Find all education credentials
val educationCreds = wallet.query {
    byType("EducationCredential")
}

// Organize them
wallet.withOrganization { org ->
    val collectionId = org.createCollection("Education", "All education credentials")
    educationCreds.forEach { cred ->
        val credId = wallet.store(cred)  // If not already stored
        org.addToCollection(credId, collectionId)
        org.tagCredential(credId, setOf("education"))
    }
}
```

### Pattern 3: Wallet Statistics

Get wallet statistics:

```kotlin
val stats = wallet.getStatistics()
println("Total credentials: ${stats.totalCredentials}")
println("Collections: ${stats.collectionsCount}")
println("Tags: ${stats.tagsCount}")
```

## Error Handling

Wallet operations return sealed results. Always handle errors:

```kotlin
import com.trustweave.trust.types.WalletCreationResult

val walletResult = trustWeave.wallet {
    holder("did:key:holder")
}

val wallet = when (walletResult) {
    is WalletCreationResult.Success -> walletResult.wallet
    is WalletCreationResult.Failure.InvalidHolderDid -> {
        println("Invalid holder DID: ${walletResult.holderDid}")
        println("Reason: ${walletResult.reason}")
        return@runBlocking // or handle appropriately
    }
    is WalletCreationResult.Failure.FactoryNotConfigured -> {
        println("Wallet factory not configured: ${walletResult.reason}")
        return@runBlocking
    }
    is WalletCreationResult.Failure.StorageFailed -> {
        println("Storage failed: ${walletResult.reason}")
        walletResult.cause?.printStackTrace()
        return@runBlocking
    }
    is WalletCreationResult.Failure.Other -> {
        println("Error: ${walletResult.reason}")
        walletResult.cause?.printStackTrace()
        return@runBlocking
    }
}

// Wallet operations (store, get, list) may still throw exceptions
// or return Result types depending on the implementation
val credentialId = wallet.store(credential)
```

## API Reference

For complete API documentation, see:
- **[Wallet API](../api-reference/wallet-api.md)** - Complete wallet API reference
- **[Core API - wallet()](../api-reference/core-api.md#wallet)** - Wallet creation DSL

## Related Concepts

- **[Wallets](../core-concepts/wallets.md)** - Understanding what wallets are
- **[Verifiable Credentials](../core-concepts/verifiable-credentials.md)** - Understanding credentials

## Related How-To Guides

- **[Issue Credentials](issue-credentials.md)** - Issue credentials to store
- **[Verify Credentials](verify-credentials.md)** - Verify stored credentials

## Next Steps

**Ready to issue credentials?**
- [Issue Credentials](issue-credentials.md) - Issue credentials to store in wallet

**Want to create presentations?**
- Enable presentation capability and use `withPresentation { }`

**Want to learn more?**
- [Wallets Concept](../core-concepts/wallets.md) - Deep dive into wallets
- [Wallet API Tutorial](../tutorials/wallet-api-tutorial.md) - Comprehensive tutorial

