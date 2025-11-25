# Getting Started with TrustWeave

This guide provides detailed examples and tutorials for getting started with TrustWeave.

## Quick Start (30 Seconds) ⚡

```kotlin
import com.trustweave.TrustWeave
import com.trustweave.did.didCreationOptions
import com.trustweave.spi.services.WalletCreationOptionsBuilder
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

fun main() = runBlocking {
    val TrustWeave = trustweave.create()

    val didDocument = trustweave.createDid(
        method = "key",
        options = didCreationOptions {
            algorithm = com.trustweave.did.DidCreationOptions.KeyAlgorithm.ED25519
        }
    ).getOrThrow()

    val credential = trustweave.issueCredential(
        issuerDid = didDocument.id,
        issuerKeyId = didDocument.id,
        credentialSubject = buildJsonObject {
            put("id", "did:key:subject")
            put("name", "Alice")
        },
        types = listOf("PersonCredential")
    ).getOrThrow()

    val verification = trustweave.verifyCredential(credential).getOrThrow()
    println("Credential valid: ${verification.valid}")

    val wallet = trustweave.createWallet(
        holderDid = "did:key:alice"
    ) {
        label = "Alice Wallet"
        enablePresentation = true
        property("autoUnlock", true)
    }.getOrThrow()

    wallet.store(credential)
    println("✅ Stored credential in ${wallet.walletId}")
}
```

## Developer Experience Highlights ✨

- **Typed configuration builders everywhere.** Create Decentralized Identifiers (DIDs), wallets, and service adapters with compile-time safe options (`didCreationOptions { … }`, `WalletCreationOptionsBuilder`, `credentialServiceCreationOptions { … }`).
- **Predictable error handling.** All facade operations return `Result<T>` so you decide whether to `getOrThrow()`, fold, or map errors.
- **Composable DSLs.** Configure the Trust Layer once and reuse it for workflows: credential issuance, verification, wallets, delegation.
- **Service Provider Interface (SPI)-ready from day one.** Drop in your own `WalletFactory` or `CredentialServiceProvider` without reflection or map juggling.

```kotlin
import com.trustweave.spi.services.credentialServiceCreationOptions

val options = credentialServiceCreationOptions {
    enabled = true
    endpoint = "https://issuer.example.com"
    apiKey = System.getenv("ISSUER_API_KEY")
    property("batchSize", 100)
}
```

## Installation

Add TrustWeave to your project:

```kotlin
dependencies {
    // All-in-one dependency (recommended)
    implementation("com.trustweave:TrustWeave-core:1.0.0-SNAPSHOT")
    implementation("com.trustweave:TrustWeave-testkit:1.0.0-SNAPSHOT")  // For testing
}
```

## Examples

### Example: Computing a JSON Digest

```kotlin
import com.trustweave.json.DigestUtils
import kotlinx.serialization.json.*

fun main() {
    val json = buildJsonObject {
        put("vcId", "vc-12345")
        put("issuer", "did:web:example.com")
    }
    
    // Canonicalize and compute digest
    val digest = DigestUtils.sha256DigestMultibase(json)
    println("Digest: $digest") // e.g., "uABC123..."
}
```

### Example: Creating a DID (New Simple API)

```kotlin
import com.trustweave.TrustWeave
import com.trustweave.core.*
import com.trustweave.did.*
import kotlinx.coroutines.runBlocking

fun main() = runBlocking {
    // Create TrustWeave instance
    val TrustWeave = trustweave.create()
    
    // Use native did:key plugin (most widely-used)
    // Add dependency: implementation("com.trustweave.did:key:1.0.0-SNAPSHOT")
    
    // Create a DID with error handling
    val document = trustweave.dids.create()
    println("Created DID: ${document.id}")
    
    // Resolve the DID
    val resolution = trustweave.dids.resolve(document.id)
    val resolveResult = Result.success(resolution)
    resolveResult.fold(
        onSuccess = { resolution ->
            resolveResult.fold(
                onSuccess = { resolution ->
                    if (resolution.document != null) {
                        println("Resolved document: ${resolution.document.id}")
                    } else {
                        println("DID not found")
                    }
                },
                onFailure = { error ->
                    when (error) {
                        is TrustWeaveError.DidNotFound -> {
                            println("DID not found: ${error.did}")
                        }
                        is TrustWeaveError.InvalidDidFormat -> {
                            println("Invalid DID format: ${error.reason}")
                        }
                        else -> {
                            println("Error resolving DID: ${error.message}")
                        }
                    }
                }
            )
        },
        onFailure = { error ->
            when (error) {
                is TrustWeaveError.DidMethodNotRegistered -> {
                    println("DID method not registered: ${error.method}")
                    println("Available methods: ${error.availableMethods}")
                }
                else -> {
                    println("Error creating DID: ${error.message}")
                }
            }
        }
    )
    
    // Or use simple API for straightforward cases
    // Use native did:key (most widely-used)
    val document2 = trustweave.dids.create("key") {
        algorithm = KeyAlgorithm.ED25519
    }
    println("Created DID: ${document.id}")
}
```

<details>
<summary>Advanced: Using Direct APIs</summary>

```kotlin
import com.trustweave.did.*
import com.trustweave.testkit.did.DidKeyMockMethod
import com.trustweave.testkit.kms.InMemoryKeyManagementService
import kotlinx.coroutines.runBlocking

fun main() = runBlocking {
    // Setup: Create KMS and DID method
    val kms = InMemoryKeyManagementService()
    val didMethod = DidKeyMockMethod(kms)
    
    // Create a DID with typed options
    val options = DidCreationOptions(
        algorithm = DidCreationOptions.KeyAlgorithm.ED25519,
        purposes = listOf(DidCreationOptions.KeyPurpose.AUTHENTICATION)
    )
    val document = didMethod.createDid(options)
    println("Created DID: ${document.id}")
}
```

</details>

### Example: Managing Credentials with Wallets (New Simple API)

```kotlin
import com.trustweave.TrustWeave
import com.trustweave.credential.wallet.WalletProvider
import com.trustweave.credential.wallet.Wallets
import com.trustweave.credential.models.VerifiableCredential
import com.trustweave.credential.wallet.CredentialOrganization
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

fun main() = runBlocking {
    // Create a wallet using the new factory
    val wallet = Wallets.inMemory(holderDid = "did:key:holder")
    
    // Store a credential
    val credential = VerifiableCredential(
        type = listOf("VerifiableCredential", "PersonCredential"),
        issuer = "did:key:issuer",
        credentialSubject = buildJsonObject {
            put("id", "did:key:subject")
            put("name", "Alice")
            put("email", "alice@example.com")
        },
        issuanceDate = "2023-01-01T00:00:00Z"
    )
    
    val credentialId = wallet.store(credential)
    
    // Organize credentials
    if (wallet is CredentialOrganization) {
        val collection = wallet.createCollection("Work Credentials")
        wallet.addToCollection(credentialId, collection)
        wallet.tagCredential(credentialId, setOf("important", "verified"))
    }
    
    // Query credentials
    val credentials = wallet.query {
        byIssuer("did:key:issuer")
        byType("PersonCredential")
        notExpired()
        valid()
    }

    // Or create a custom wallet via TrustWeave with typed options
    val TrustWeave = trustweave.create()
    val customWallet = trustweave.createWallet(
        holderDid = "did:key:holder",
        provider = WalletProvider.custom("postgres")
    ) {
        label = "Production Wallet"
        storagePath = "/var/lib/wallets/holder"
        property("connectionString", System.getenv("WALLET_DB_URL"))
    }.getOrThrow()
    println("Custom wallet provider registered: ${customWallet.javaClass.simpleName}")
    
    // Create a presentation
    if (wallet is CredentialPresentation) {
        val presentation = wallet.createPresentation(
            credentialIds = listOf(credentialId),
            holderDid = wallet.holderDid,
            options = PresentationOptions(
                holderDid = wallet.holderDid,
                proofType = "Ed25519Signature2020"
            )
        )
        println("Created presentation with ${presentation.verifiableCredential.size} credentials")
    }
    
    // Get wallet statistics
    val stats = wallet.getStatistics()
    println("Wallet has ${stats.totalCredentials} credentials")
}
```

### Example: Anchoring Data to Multiple Blockchains (Ethereum, Base, Arbitrum)

You can anchor data to multiple EVM-compatible chains using TrustWeave's chain-agnostic interface:

```kotlin
import com.trustweave.TrustWeave
import com.trustweave.ethereum.*
import com.trustweave.base.*
import com.trustweave.arbitrum.*
import com.trustweave.testkit.anchor.InMemoryBlockchainAnchorClient
import kotlinx.serialization.json.*

val TrustWeave = trustweave.create {
    blockchain {
        // Register multiple blockchain adapters
        register(EthereumBlockchainAnchorClient.MAINNET, InMemoryBlockchainAnchorClient(EthereumBlockchainAnchorClient.MAINNET))
        register(BaseBlockchainAnchorClient.MAINNET, InMemoryBlockchainAnchorClient(BaseBlockchainAnchorClient.MAINNET))
        register(ArbitrumBlockchainAnchorClient.MAINNET, InMemoryBlockchainAnchorClient(ArbitrumBlockchainAnchorClient.MAINNET))
    }
}

// Anchor to Ethereum (highest security, higher fees)
val ethereumResult = trustweave.anchor(EthereumBlockchainAnchorClient.MAINNET, payload).getOrThrow()

// Anchor to Base (Coinbase L2, lower fees, fast)
val baseResult = trustweave.anchor(BaseBlockchainAnchorClient.MAINNET, payload).getOrThrow()

// Anchor to Arbitrum (largest L2 by TVL, lower fees)
val arbitrumResult = trustweave.anchor(ArbitrumBlockchainAnchorClient.MAINNET, payload).getOrThrow()
```

### Example: Anchoring Data to a Blockchain (Original)

```kotlin
import com.trustweave.anchor.*
import com.trustweave.testkit.anchor.InMemoryBlockchainAnchorClient
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import kotlinx.serialization.Serializable

@Serializable
data class VerifiableCredentialDigest(
    val vcId: String,
    val vcDigest: String
)

fun main() = runBlocking {
    // Setup: Create and register blockchain client
    val client = InMemoryBlockchainAnchorClient("algorand:mainnet", "app-123")
val blockchainRegistry = BlockchainAnchorRegistry().apply {
    register("algorand:mainnet", client)
}
    
    // Create a digest object
    val digest = VerifiableCredentialDigest(
        vcId = "vc-12345",
        vcDigest = "uABC123..."
    )
    
    // Anchor it
val result = blockchainRegistry.anchorTyped(
        value = digest,
        serializer = VerifiableCredentialDigest.serializer(),
        targetChainId = "algorand:mainnet"
    )
    
    println("Anchored at: ${result.ref.txHash}")
    
    // Read it back
val retrieved = blockchainRegistry.readTyped<VerifiableCredentialDigest>(
        ref = result.ref,
        serializer = VerifiableCredentialDigest.serializer()
    )
    
    println("Retrieved: ${retrieved.vcId}")
}
```

### Example: Complete Workflow

```kotlin
import com.trustweave.anchor.AnchorResult
import com.trustweave.anchor.BlockchainAnchorRegistry
import com.trustweave.anchor.anchorTyped
import com.trustweave.anchor.readTyped
import com.trustweave.did.DidMethodRegistry
import com.trustweave.json.DigestUtils
import com.trustweave.testkit.anchor.InMemoryBlockchainAnchorClient
import com.trustweave.testkit.did.DidKeyMockMethod
import com.trustweave.testkit.kms.InMemoryKeyManagementService
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import kotlinx.serialization.Serializable

@Serializable
data class VerifiableCredentialDigest(
    val vcId: String,
    val vcDigest: String,
    val issuer: String
)

fun main() = runBlocking {
    // 1. Setup services
    val kms = InMemoryKeyManagementService()
    val didMethod = DidKeyMockMethod(kms)
    val anchorClient = InMemoryBlockchainAnchorClient("algorand:mainnet")
    
    val didRegistry = DidMethodRegistry().apply { register(didMethod) }
    val blockchainRegistry = BlockchainAnchorRegistry().apply {
        register("algorand:mainnet", anchorClient)
    }
    
    // 2. Create a DID for the issuer
    val issuerDoc = didMethod.createDid()
    val issuerDid = issuerDoc.id
    
    // 3. Create a verifiable credential payload
    val vcPayload = buildJsonObject {
        put("vcId", "vc-12345")
        put("issuer", issuerDid)
        put("credentialSubject", buildJsonObject {
            put("id", "subject-123")
            put("type", "Person")
        })
    }
    
    // 4. Compute digest
    val digest = DigestUtils.sha256DigestMultibase(vcPayload)
    
    // 5. Create digest object and anchor it
    val digestObj = VerifiableCredentialDigest(
        vcId = "vc-12345",
        vcDigest = digest,
        issuer = issuerDid
    )
    
    val anchorResult = blockchainRegistry.anchorTyped(
        value = digestObj,
        serializer = VerifiableCredentialDigest.serializer(),
        targetChainId = "algorand:mainnet"
    )
    
    println("Issuer DID: $issuerDid")
    println("VC Digest: $digest")
    println("Anchored at: ${anchorResult.ref.txHash}")
    
    // 6. Verify by reading back
    val retrieved = blockchainRegistry.readTyped<VerifiableCredentialDigest>(
        ref = anchorResult.ref,
        serializer = VerifiableCredentialDigest.serializer()
    )
    
    assert(retrieved.vcDigest == digest)
    println("Verification successful!")
}
```

## Next Steps

- Read the [API Guide](API_GUIDE.md) to understand which API to use
- Explore [Architecture & Modules](ARCHITECTURE.md) for detailed module information
- Check out [Available Plugins](PLUGINS.md) for integration options
- See [Development Guide](DEVELOPMENT.md) for building and testing

