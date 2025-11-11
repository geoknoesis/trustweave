# Wallets

> Wallet guidance in VeriCore is authored by [Geoknoesis LLC](https://www.geoknoesis.com). Geoknoesis designs the composable wallet model and offers commercial support for production deployments.

## What is a wallet?

In VeriCore a wallet is a secure container for verifiable credentials, keys, and DID helpers. The API follows the Interface Segregation Principle—each capability is expressed as a separate interface and can be mixed in as needed.

## Why wallets matter

- **Credential lifecycle hub** – store, query, archive, and organise credentials issued to or by an entity.  
- **Identity surface** – expose DID creation, key management, and presentation builders through a consistent Kotlin DSL.  
- **Extensibility** – implement only the capabilities your use case requires (portable from in-memory to persistent stores).

## How VeriCore composes wallets

| Interface | Responsibility |
|-----------|----------------|
| `Wallet` | Base type exposing `walletId`, capability discovery (`supports`), and statistics. |
| `CredentialStorage` | Core CRUD + query operations for credentials. |
| `CredentialOrganization` | Collections, tags, metadata management. |
| `CredentialLifecycle` | Archive/unarchive and refresh flows. |
| `CredentialPresentation` | Build verifiable presentations and selective disclosures. |
| `DidManagement` | Create and list DIDs scoped to the wallet. |
| `KeyManagement` | Delegate key generation/lookup to underlying KMS. |

See the [Wallet API Reference](../api-reference/wallet-api.md) for detailed signatures and extension helpers.

### Example: building a wallet via the DSL

```kotlin
import com.geoknoesis.vericore.trust.wallet
import com.geoknoesis.vericore.trust.trustLayer

val trustLayer = trustLayer {
    keys { provider("inMemory") }
    did { method("key") {} }
}

val wallet = trustLayer.wallet {
    holder("did:key:holder-123")
    enableOrganization()
    enablePresentation()
    option("storagePath", "/data/wallets/holder-123")
}

// Store and query
wallet.store(credential)
val byIssuer = wallet.query {
    byIssuer("did:key:issuer-xyz")
    notExpired()
}
```

### Example: capability detection

```kotlin
if (wallet.supports(com.geoknoesis.vericore.credential.wallet.CredentialPresentation::class)) {
    wallet.withPresentation {
        createPresentation(
            credentialIds = listOf("cred-1"),
            holderDid = "did:key:holder-123",
            options = com.geoknoesis.vericore.credential.PresentationOptions(
                holderDid = "did:key:holder-123",
                proofType = "Ed25519Signature2020",
                keyId = "did:key:holder-123#key-1"
            )
        )
    }
}
```

## Practical usage tips

- **Capability checks** – gate optional features (`withPresentation`, `withOrganization`, etc.) to keep code resilient across wallet implementations.  
- **Interoperate via interfaces** – accept `CredentialStorage` or `CredentialPresentation` rather than concrete classes to ease testing.  
- **Workspace-scoped wallets** – generate wallet IDs and DID prefixes that align with your tenancy model; the DSL lets you set them explicitly.  
- **Persistence** – implement your own `WalletFactory` and register it (see [Wallet API Reference – Factories](../api-reference/wallet-api.md#factories)).

## See also

- [Wallet API Reference](../api-reference/wallet-api.md) for all interfaces and helpers.  
- [Quick Start](../getting-started/quick-start.md#step-4-issue-a-credential-and-store-it) for storing credentials.  
- [DIDs](dids.md) to understand `DidManagement`.  
- [Verifiable Credentials](verifiable-credentials.md) for the data wallet stores.
# Wallets

> Wallet guidance in VeriCore is authored by [Geoknoesis LLC](https://www.geoknoesis.com). Geoknoesis designs the composable wallet model and offers commercial support for production deployments.

## What is a Wallet?

A **Wallet** in VeriCore is a secure container for managing your credentials and identities. It provides a unified interface for storing, organizing, and using verifiable credentials and DIDs.

## Wallet Capabilities

VeriCore wallets support different capabilities through a **composable interface design**:

### Core Capabilities (Always Available)

- **Credential Storage**: Store, retrieve, and delete credentials
- **Query**: Search and filter credentials

### Optional Capabilities

- **Organization**: Collections, tags, and metadata
- **Lifecycle**: Archive and refresh credentials
- **Presentation**: Create verifiable presentations
- **DID Management**: Create and manage DIDs
- **Key Management**: Generate and manage keys
- **Issuance**: Issue credentials

## Wallet Types

### Basic Wallet

A **Basic Wallet** provides only core credential storage:

```kotlin
import com.geoknoesis.vericore.testkit.credential.BasicWallet

val wallet = BasicWallet()
// Supports: store, get, list, delete, query
```

### Full-Featured Wallet

A **Full-Featured Wallet** implements all capabilities:

```kotlin
import com.geoknoesis.vericore.testkit.credential.InMemoryWallet

val wallet = InMemoryWallet(
    walletDid = "did:key:wallet",
    holderDid = "did:key:holder"
)
// Supports: All capabilities
```

## Type-Safe Capability Checking

VeriCore uses Kotlin's type system for **compile-time** capability checking:

```kotlin
val wallet: Wallet = createWallet()

// Core operations (always available)
val id = wallet.store(credential)
val credential = wallet.get(id)

// Optional capabilities (type-safe check)
if (wallet is CredentialOrganization) {
    wallet.createCollection("My Collection")
    wallet.tagCredential(id, setOf("important"))
}

if (wallet is CredentialPresentation) {
    val presentation = wallet.createPresentation(
        credentialIds = listOf(id),
        holderDid = wallet.holderDid,
        options = PresentationOptions(...)
    )
}
```

## Extension Functions

VeriCore provides extension functions for elegant capability access:

```kotlin
// Using extension functions
wallet.withOrganization { org ->
    val collectionId = org.createCollection("Work Credentials")
    org.addToCollection(credentialId, collectionId)
}

wallet.withLifecycle { lifecycle ->
    lifecycle.archive(oldCredentialId)
    val archived = lifecycle.getArchived()
}

wallet.withPresentation { presentation ->
    val vp = presentation.createPresentation(
        credentialIds = listOf(credentialId),
        holderDid = "did:key:holder",
        options = PresentationOptions(...)
    )
}
```

## Runtime Capability Discovery

For dynamic scenarios (e.g., UI), use runtime capability checking:

```kotlin
val wallet: Wallet = createWallet()

// Check capabilities
if (wallet.capabilities.collections) {
    // Show collection UI
}

if (wallet.capabilities.didManagement) {
    // Show DID management UI
}

// Or check by feature name
if (wallet.capabilities.supports("collections")) {
    // Show collection UI
}
```

## Wallet Directory

Create your own directory when you need to manage multiple wallets:

```kotlin
import com.geoknoesis.vericore.credential.wallet.WalletDirectory

val directory = WalletDirectory()

// Register wallets
val wallet = createWallet()
directory.register(wallet)

// Get by ID
val retrieved = directory.get(wallet.walletId)

// Get by DID (if DidManagement supported)
val byDid = directory.getByDid("did:key:wallet")

// Find wallets with specific capability (type-safe)
val orgWallets = directory.findByCapability(CredentialOrganization::class)

// Find wallets by feature name (dynamic)
val walletsWithCollections = directory.findByCapability("collections")
```

## Wallet Statistics

Get an overview of your wallet:

```kotlin
val stats = wallet.getStatistics()
println("Total credentials: ${stats.totalCredentials}")
println("Valid credentials: ${stats.validCredentials}")
println("Collections: ${stats.collectionsCount}")
println("Tags: ${stats.tagsCount}")
```

## Common Patterns

### Storing Credentials

```kotlin
val wallet: Wallet = createWallet()

// Store a credential
val credentialId = wallet.store(credential)

// Store multiple credentials
val ids = credentials.map { wallet.store(it) }
```

### Organizing Credentials

```kotlin
if (wallet is CredentialOrganization) {
    // Create a collection
    val workCollection = wallet.createCollection(
        name = "Work Credentials",
        description = "Professional credentials"
    )
    
    // Add credentials to collection
    wallet.addToCollection(credentialId, workCollection)
    
    // Tag credentials
    wallet.tagCredential(credentialId, setOf("important", "verified"))
    
    // Add metadata
    wallet.addMetadata(credentialId, mapOf(
        "source" to "issuer.com",
        "verified" to true
    ))
}
```

### Querying Credentials

```kotlin
// Simple query
val credentials = wallet.query {
    byIssuer("did:key:issuer")
    byType("PersonCredential")
    notExpired()
    valid()
}

// Complex query
val workCredentials = wallet.query {
    byTypes("WorkCredential", "EmploymentCredential")
    bySubject(holderDid)
    notRevoked()
}
```

### Creating Presentations

```kotlin
if (wallet is CredentialPresentation) {
    // Create a presentation
    val presentation = wallet.createPresentation(
        credentialIds = listOf(credentialId1, credentialId2),
        holderDid = holderDid,
        options = PresentationOptions(
            holderDid = holderDid,
            proofType = "Ed25519Signature2020",
            challenge = "random-challenge-string"
        )
    )
    
    // Selective disclosure
    val selective = wallet.createSelectiveDisclosure(
        credentialIds = listOf(credentialId),
        disclosedFields = listOf("name", "email"),
        holderDid = holderDid,
        options = PresentationOptions(...)
    )
}
```

## Security Considerations

1. **Key Management**: Store keys securely (use hardware security modules when possible)
2. **Credential Storage**: Encrypt credentials at rest
3. **Access Control**: Implement proper access control for wallet operations
4. **Backup**: Regularly backup your wallet
5. **Revocation**: Check credential revocation status before use

## Best Practices

1. **Use type-safe checks** (`wallet is CredentialOrganization`) instead of runtime checks when possible
2. **Organize credentials** using collections and tags
3. **Archive old credentials** instead of deleting them
4. **Use selective disclosure** to minimize data exposure
5. **Verify credentials** before storing them
6. **Monitor wallet statistics** to track credential health

## Next Steps

- Check out the [Wallet API Tutorial](../tutorials/wallet-api-tutorial.md) for hands-on examples
- Explore the [Wallet API Reference](../api-reference/wallet-api.md)
- Learn about [DIDs](dids.md) and [Verifiable Credentials](verifiable-credentials.md)

