---
title: Beginner Tutorial Series
nav_exclude: true
---

# Beginner Tutorial Series

A structured learning path for developers new to TrustWeave and decentralized identity. Each tutorial builds on the previous one, introducing concepts progressively.

## Learning Path Overview

This series takes you from zero to building production-ready applications with TrustWeave:

1. **Tutorial 1: Your First DID** - Create and understand DIDs
2. **Tutorial 2: Issuing Your First Credential** - Issue and verify credentials
3. **Tutorial 3: Managing Credentials with Wallets** - Store and organize credentials
4. **Tutorial 4: Building a Complete Workflow** - End-to-end issuer-holder-verifier flow
5. **Tutorial 5: Adding Blockchain Anchoring** - Anchor data for tamper evidence

## Prerequisites

Before starting:
- **Kotlin basics**: Variables, functions, classes, coroutines
- **Development environment**: Kotlin 2.2.0+, Java 21+, Gradle
- **Installation**: Follow the [Installation Guide](../getting-started/installation.md)

## Tutorial 1: Your First DID

**Duration:** 15-20 minutes
**Goal:** Create and resolve your first DID

### What You'll Learn

- What DIDs are and why they matter
- How to create a DID using `did:key`
- How to resolve a DID to get its document
- Basic error handling with `Result<T>`

### Step 1: Setup

Create a new Kotlin project and add dependencies:

```kotlin
// build.gradle.kts
dependencies {
    implementation("com.trustweave:trustweave-all:1.0.0-SNAPSHOT")
}
```

### Step 2: Create TrustWeave Instance

```kotlin
import com.trustweave.TrustWeave
import kotlinx.coroutines.runBlocking

fun main() = runBlocking {
    // Create TrustWeave with default configuration
    // This includes did:key method by default
    // Build TrustWeave instance (for tutorials, using testkit factories)
    val trustWeave = TrustWeave.build {
        factories(didMethodFactory = TestkitDidMethodFactory())  // Test-only factory
        keys { provider("inMemory"); algorithm("Ed25519") }
        did { method("key") { algorithm("Ed25519") } }
    }

    println("✅ TrustWeave initialized")
}
```

**What this does:** Creates a TrustWeave instance with default configuration, including the `did:key` method.

### Step 3: Create Your First DID

```kotlin
import com.trustweave.core.*

fun main() = runBlocking {
    // Build TrustWeave instance (for tutorials, using testkit factories)
    val trustWeave = TrustWeave.build {
        factories(didMethodFactory = TestkitDidMethodFactory())  // Test-only factory
        keys { provider("inMemory"); algorithm("Ed25519") }
        did { method("key") { algorithm("Ed25519") } }
    }

    // Create a DID using the default method (did:key)
    val did = trustWeave.createDid { method("key") }
    val resolution = trustWeave.resolveDid(did)
    when (resolution) {
        is DidResolutionResult.Success -> {
            println("✅ Created DID: ${did.value}")
            println("   Verification Methods: ${resolution.document.verificationMethod.size}")
        }
        is DidResolutionResult.Failure -> {
            println("❌ Failed to create DID: ${resolution.reason}")
        }
    }
}
```

**What this does:** Creates a new DID using the default `did:key` method. The DID document contains public keys for signing and verification.

**Result:** A `DidDocument` containing the DID identifier and verification methods.

### Step 4: Resolve the DID

```kotlin
fun main() = runBlocking {
    // Build TrustWeave instance (for tutorials, using testkit factories)
    val trustWeave = TrustWeave.build {
        factories(didMethodFactory = TestkitDidMethodFactory())  // Test-only factory
        keys { provider("inMemory"); algorithm("Ed25519") }
        did { method("key") { algorithm("Ed25519") } }
    }

    // Create a DID using the modern DSL
    val did = trustWeave.createDid { method("key") }
    println("Created DID: ${did.value}")

    // Resolve the DID we just created
    val resolution = trustWeave.resolveDid(did)
    when (resolution) {
        is DidResolutionResult.Success -> {
            println("✅ Resolved DID: ${did.value}")
            println("   Methods: ${resolution.document.verificationMethod.size}")
        }
        is DidResolutionResult.Failure -> {
            println("❌ Failed to resolve: ${resolution.reason}")
        }
    }
}
```

**What this does:** Resolves the DID to retrieve its document, demonstrating that DIDs are resolvable identifiers.

### Step 5: Handle Errors Properly

```kotlin
fun main() = runBlocking {
    // Build TrustWeave instance (for tutorials, using testkit factories)
    val trustWeave = TrustWeave.build {
        factories(didMethodFactory = TestkitDidMethodFactory())  // Test-only factory
        keys { provider("inMemory"); algorithm("Ed25519") }
        did { method("key") { algorithm("Ed25519") } }
    }

    // Try to resolve a non-existent DID
    val resolution = trustWeave.resolveDid("did:key:invalid")
    when (resolution) {
        is DidResolutionResult.Success -> {
            println("Resolved: ${resolution.document.id}")
        }
        is DidResolutionResult.Failure -> {
            when (resolution) {
                is DidResolutionResult.Failure.NotFound -> {
                    println("❌ DID not found: did:key:invalid")
                }
                is DidResolutionResult.Failure.InvalidFormat -> {
                    println("❌ Invalid DID format: ${resolution.reason}")
                }
                else -> {
                    println("❌ Error: ${resolution.reason}")
                }
            }
        }
    }
}
```

**What this does:** Demonstrates proper error handling using TrustWeave's structured error types.

### Key Takeaways

- DIDs are self-sovereign identifiers that you control
- `did:key` is included by default and works offline
- Always use `fold()` for error handling in production code
- Error types provide actionable information for recovery

### Next Steps

- Try creating DIDs with different methods (requires registration)
- Explore the DID document structure
- Read [Core Concepts: DIDs](../core-concepts/dids.md)

---

## Tutorial 2: Issuing Your First Credential

**Duration:** 20-25 minutes
**Goal:** Issue and verify a verifiable credential

### What You'll Learn

- What verifiable credentials are
- How to issue a credential
- How to verify a credential
- Understanding credential structure

### Step 1: Create Issuer and Holder DIDs

```kotlin
fun main() = runBlocking {
    // Build TrustWeave instance (for tutorials, using testkit factories)
    val trustWeave = TrustWeave.build {
        factories(didMethodFactory = TestkitDidMethodFactory())  // Test-only factory
        keys { provider("inMemory"); algorithm("Ed25519") }
        did { method("key") { algorithm("Ed25519") } }
    }

    // Create issuer DID (the organization issuing credentials)
    val issuerDid = trustWeave.createDid { method("key") }
    println("Issuer DID: ${issuerDid.value}")

    // Create holder DID (the person receiving the credential)
    val holderDid = trustWeave.createDid { method("key") }
    println("Holder DID: ${holderDid.value}")
}
```

**What this does:** Sets up the two parties needed for credential issuance: issuer and holder.

### Step 2: Issue a Credential

```kotlin
import com.trustweave.credential.*

fun main() = runBlocking {
    // Build TrustWeave instance (for tutorials, using testkit factories)
    val trustWeave = TrustWeave.build {
        factories(
            kmsFactory = TestkitKmsFactory(),  // Test-only factory
            didMethodFactory = TestkitDidMethodFactory()  // Test-only factory
        )
        keys { provider("inMemory"); algorithm("Ed25519") }
        did { method("key") { algorithm("Ed25519") } }
    }

    // Create DIDs
    val issuerDid = trustWeave.createDid { method("key") }
    val holderDid = trustWeave.createDid { method("key") }

    // Get the first verification method from issuer's DID document
    val issuerResolution = trustWeave.resolveDid(issuerDid)
    val issuerDoc = when (issuerResolution) {
        is DidResolutionResult.Success -> issuerResolution.document
        else -> throw IllegalStateException("Failed to resolve issuer DID")
    }
    val issuerKeyId = issuerDoc.verificationMethod.firstOrNull()?.id?.substringAfter("#")
        ?: throw IllegalStateException("No verification method found")

    // Issue a credential
    val credentialResult = TrustWeave.issueCredential(
        issuerDid = issuerDid.value,
        issuerKeyId = issuerKeyId,
        credentialSubject = mapOf(
            "id" to holderDid.value,
            "name" to "Alice",
            "degree" to "Bachelor of Science",
            "university" to "Example University"
        ),
        types = listOf("VerifiableCredential", "EducationalCredential")
    )

    credentialResult.fold(
        onSuccess = { credential ->
            println("✅ Credential issued")
            println("   ID: ${credential.id}")
            println("   Issuer: ${credential.issuer}")
            println("   Subject: ${credential.credentialSubject}")
            println("   Types: ${credential.type}")
        },
        onFailure = { error ->
            println("❌ Failed to issue credential: ${error.message}")
        }
    )
}
```

**What this does:** Issues a verifiable credential from the issuer to the holder, containing educational information.

**Result:** A `VerifiableCredential` with cryptographic proof that can be verified.

### Step 3: Verify the Credential

```kotlin
fun main() = runBlocking {
    // Build TrustWeave instance (for tutorials, using testkit factories)
    val trustWeave = TrustWeave.build {
        factories(
            kmsFactory = TestkitKmsFactory(),  // Test-only factory
            didMethodFactory = TestkitDidMethodFactory()  // Test-only factory
        )
        keys { provider("inMemory"); algorithm("Ed25519") }
        did { method("key") { algorithm("Ed25519") } }
    }

    // ... (create DIDs and issue credential from Step 2) ...

    val credential = credentialResult.getOrThrow()

    // Verify the credential
    val verificationResult = TrustWeave.verifyCredential(credential)

    verificationResult.fold(
        onSuccess = { verification ->
            if (verification.valid) {
                println("✅ Credential is valid")
                println("   Proof valid: ${verification.proofValid}")
                println("   Not expired: ${verification.notExpired}")
                println("   Not revoked: ${verification.notRevoked}")

                if (verification.warnings.isNotEmpty()) {
                    println("   Warnings: ${verification.warnings}")
                }
            } else {
                println("❌ Credential is invalid")
                println("   Errors: ${verification.errors}")
            }
        },
        onFailure = { error ->
            println("❌ Verification failed: ${error.message}")
        }
    )
}
```

**What this does:** Verifies the credential's proof, expiration, and revocation status.

### Step 4: Add Expiration Date

```kotlin
import java.time.Instant
import java.time.temporal.ChronoUnit

fun main() = runBlocking {
    // Build TrustWeave instance (for tutorials, using testkit factories)
    val trustWeave = TrustWeave.build {
        factories(
            kmsFactory = TestkitKmsFactory(),  // Test-only factory
            didMethodFactory = TestkitDidMethodFactory()  // Test-only factory
        )
        keys { provider("inMemory"); algorithm("Ed25519") }
        did { method("key") { algorithm("Ed25519") } }
    }

    // ... (create DIDs) ...

    // Issue credential with expiration date (1 year from now)
    val expirationDate = Instant.now().plus(365, ChronoUnit.DAYS)

    val credentialResult = TrustWeave.issueCredential(
        issuerDid = issuerDid.value,
        issuerKeyId = issuerKeyId,
        credentialSubject = mapOf(
            "id" to holderDid.value,
            "name" to "Alice",
            "degree" to "Bachelor of Science"
        ),
        types = listOf("VerifiableCredential", "EducationalCredential"),
        expirationDate = expirationDate
    )

    // ... (verify credential) ...
}
```

**What this does:** Adds an expiration date to the credential, demonstrating credential lifecycle management.

### Key Takeaways

- Credentials contain claims about a subject (holder)
- Credentials are cryptographically signed by the issuer
- Verification checks proof, expiration, and revocation
- Always check verification warnings, not just validity

### Next Steps

- Add more claims to credentials
- Explore different credential types
- Read [Core Concepts: Verifiable Credentials](../core-concepts/verifiable-credentials.md)

---

## Tutorial 3: Managing Credentials with Wallets

**Duration:** 25-30 minutes
**Goal:** Store, organize, and query credentials using wallets

### What You'll Learn

- What wallets are and their purpose
- How to create wallets
- How to store credentials in wallets
- How to query and retrieve credentials
- Wallet organization features

### Step 1: Create a Wallet

```kotlin
import com.trustweave.wallet.*

fun main() = runBlocking {
    val TrustWeave = TrustWeave.create()

    // Create holder DID
    val holderDid = trustWeave.createDid { method("key") }

    // Create wallet for the holder
    val wallet = trustWeave.wallet {
        holder(holderDid.value)
        type("inMemory")
    }

    walletResult.fold(
        onSuccess = { wallet ->
            println("✅ Wallet created: ${wallet.walletId}")
            println("   Holder: ${wallet.holderDid}")
        },
        onFailure = { error ->
            println("❌ Failed to create wallet: ${error.message}")
        }
    )
}
```

**What this does:** Creates a wallet for storing credentials. Wallets provide secure storage and management.

### Step 2: Store Credentials

```kotlin
fun main() = runBlocking {
    // Build TrustWeave instance (for tutorials, using testkit factories)
    val trustWeave = TrustWeave.build {
        factories(
            kmsFactory = TestkitKmsFactory(),  // Test-only factory
            didMethodFactory = TestkitDidMethodFactory()  // Test-only factory
        )
        keys { provider("inMemory"); algorithm("Ed25519") }
        did { method("key") { algorithm("Ed25519") } }
    }

    // Create DIDs and issue credential (from Tutorial 2)
    val issuerDid = trustWeave.createDid { method("key") }
    val holderDid = trustWeave.createDid { method("key") }
    val credential = /* ... issue credential ... */

    // Create wallet
    val wallet = trustWeave.wallet {
        holder(holderDid.value)
        type("inMemory")
    }

    // Store credential in wallet
    val storeResult = wallet.storeCredential(credential)

    storeResult.fold(
        onSuccess = { storedId ->
            println("✅ Credential stored: $storedId")
        },
        onFailure = { error ->
            println("❌ Failed to store credential: ${error.message}")
        }
    )
}
```

**What this does:** Stores a credential in the wallet, making it available for later retrieval.

### Step 3: Query Credentials

```kotlin
fun main() = runBlocking {
    // Build TrustWeave instance (for tutorials, using testkit factories)
    val trustWeave = TrustWeave.build {
        factories(
            kmsFactory = TestkitKmsFactory(),  // Test-only factory
            didMethodFactory = TestkitDidMethodFactory()  // Test-only factory
        )
        keys { provider("inMemory"); algorithm("Ed25519") }
        did { method("key") { algorithm("Ed25519") } }
    }

    // ... (create wallet and store credentials) ...

    // Query all credentials
    val allCredentials = wallet.queryCredentials()
    println("Total credentials: ${allCredentials.size}")

    // Query by type
    val educationalCreds = wallet.queryCredentials(
        type = "EducationalCredential"
    )
    println("Educational credentials: ${educationalCreds.size}")

    // Query by issuer
    val issuerCreds = wallet.queryCredentials(
        issuer = issuerDid.value
    )
    println("Credentials from issuer: ${issuerCreds.size}")
}
```

**What this does:** Demonstrates querying credentials by various criteria.

### Step 4: Use Organization Features

```kotlin
fun main() = runBlocking {
    // Build TrustWeave instance (for tutorials, using testkit factories)
    val trustWeave = TrustWeave.build {
        factories(
            kmsFactory = TestkitKmsFactory(),  // Test-only factory
            didMethodFactory = TestkitDidMethodFactory()  // Test-only factory
        )
        keys { provider("inMemory"); algorithm("Ed25519") }
        did { method("key") { algorithm("Ed25519") } }
    }

    // Create wallet with organization features enabled
    val wallet = trustWeave.wallet {
        holder(holderDid.value)
        type("inMemory")
        // Organization features can be configured here
    }

    // Store credentials
    val credential = /* ... */
    wallet.storeCredential(credential).getOrThrow()

    // Use organization features
    wallet.withOrganization { org ->
        // Create a collection
        val collection = org.createCollection("Education")

        // Add credential to collection
        org.addToCollection(collection.id, credential.id)

        // Tag credential
        org.tagCredential(credential.id, "diploma")
        org.tagCredential(credential.id, "bachelor")

        // Query by collection
        val educationCreds = org.queryByCollection(collection.id)
        println("Education collection: ${educationCreds.size} credentials")

        // Query by tag
        val diplomaCreds = org.queryByTag("diploma")
        println("Diploma credentials: ${diplomaCreds.size}")
    }
}
```

**What this does:** Demonstrates wallet organization features: collections, tags, and metadata.

### Key Takeaways

- Wallets provide secure credential storage
- Use `InMemory` provider for testing, persistent providers for production
- Organization features help manage large numbers of credentials
- Query capabilities enable efficient credential retrieval

### Next Steps

- Explore presentation features (selective disclosure)
- Try different wallet providers
- Read [Core Concepts: Wallets](../core-concepts/wallets.md)
- Complete [Wallet API Tutorial](wallet-api-tutorial.md)

---

## Tutorial 4: Building a Complete Workflow

**Duration:** 30-35 minutes
**Goal:** Build an end-to-end issuer-holder-verifier workflow

### What You'll Learn

- Complete credential lifecycle
- Issuer, holder, and verifier roles
- Presentation creation and verification
- Real-world workflow patterns

### Step 1: Setup All Parties

```kotlin
fun main() = runBlocking {
    val TrustWeave = TrustWeave.create()

    // Issuer: University issuing degrees
    val issuerDid = trustWeave.createDid { method("key") }
    val issuerResolution = trustWeave.resolveDid(issuerDid)
    val issuerDoc = when (issuerResolution) {
        is DidResolutionResult.Success -> issuerResolution.document
        else -> throw IllegalStateException("Failed to resolve issuer DID")
    }
    val issuerKeyId = issuerDoc.verificationMethod.firstOrNull()?.id?.substringAfter("#")
        ?: throw IllegalStateException("No verification method found")

    // Holder: Student receiving degree
    val holderDid = trustWeave.createDid { method("key") }
    val holderWallet = trustWeave.wallet {
        holder(holderDid.value)
        type("inMemory")
    }

    // Verifier: Employer verifying degree
    val verifierDid = trustWeave.createDid { method("key") }

    println("✅ All parties set up")
    println("   Issuer: ${issuerDid.value}")
    println("   Holder: ${holderDid.value}")
    println("   Verifier: ${verifierDid.value}")
}
```

**What this does:** Sets up the three parties in a typical credential workflow.

### Step 2: Issue Credential (Issuer)

```kotlin
fun main() = runBlocking {
    // Build TrustWeave instance (for tutorials, using testkit factories)
    val trustWeave = TrustWeave.build {
        factories(
            kmsFactory = TestkitKmsFactory(),  // Test-only factory
            didMethodFactory = TestkitDidMethodFactory()  // Test-only factory
        )
        keys { provider("inMemory"); algorithm("Ed25519") }
        did { method("key") { algorithm("Ed25519") } }
    }

    // ... (setup parties) ...

    // ISSUER: Issue credential
    val credential = TrustWeave.issueCredential(
        issuerDid = issuerDid.value,
        issuerKeyId = issuerKeyId,
        credentialSubject = mapOf(
            "id" to holderDid.value,
            "name" to "Alice",
            "degree" to "Bachelor of Science in Computer Science",
            "university" to "Example University",
            "graduationDate" to "2024-05-15"
        ),
        types = listOf("VerifiableCredential", "EducationalCredential")
    ).getOrThrow()

    println("✅ Credential issued by issuer")
}
```

### Step 3: Store Credential (Holder)

```kotlin
fun main() = runBlocking {
    // Build TrustWeave instance (for tutorials, using testkit factories)
    val trustWeave = TrustWeave.build {
        factories(
            kmsFactory = TestkitKmsFactory(),  // Test-only factory
            didMethodFactory = TestkitDidMethodFactory()  // Test-only factory
        )
        keys { provider("inMemory"); algorithm("Ed25519") }
        did { method("key") { algorithm("Ed25519") } }
    }

    // ... (setup and issue) ...

    // HOLDER: Store credential in wallet
    val storedId = holderWallet.storeCredential(credential).getOrThrow()
    println("✅ Credential stored by holder: $storedId")
}
```

### Step 4: Create Presentation (Holder)

```kotlin
import com.trustweave.presentation.*

fun main() = runBlocking {
    val TrustWeave = TrustWeave.create()

    // ... (setup, issue, store) ...

    // HOLDER: Create presentation for verifier
    val presentationResult = holderWallet.withPresentation { pres ->
        pres.createPresentation(
            credentials = listOf(credential),
            holderDid = holderDid.value,
            challenge = "verifier-challenge-123",  // Nonce from verifier
            domain = "example-employer.com"
        )
    }

    presentationResult.fold(
        onSuccess = { presentation ->
            println("✅ Presentation created by holder")
            println("   Challenge: ${presentation.challenge}")
            println("   Domain: ${presentation.domain}")
        },
        onFailure = { error ->
            println("❌ Failed to create presentation: ${error.message}")
        }
    )
}
```

**What this does:** Creates a verifiable presentation that the holder can share with the verifier.

### Step 5: Verify Presentation (Verifier)

```kotlin
fun main() = runBlocking {
    val TrustWeave = TrustWeave.create()

    // ... (complete workflow above) ...

    val presentation = presentationResult.getOrThrow()

    // VERIFIER: Verify presentation
    val verificationResult = TrustWeave.verifyPresentation(
        presentation = presentation,
        challenge = "verifier-challenge-123",  // Must match
        domain = "example-employer.com"  // Must match
    )

    verificationResult.fold(
        onSuccess = { verification ->
            if (verification.valid) {
                println("✅ Presentation verified by verifier")
                println("   All credentials valid: ${verification.allCredentialsValid}")
                println("   Proof valid: ${verification.proofValid}")
            } else {
                println("❌ Presentation invalid")
                println("   Errors: ${verification.errors}")
            }
        },
        onFailure = { error ->
            println("❌ Verification failed: ${error.message}")
        }
    )
}
```

**What this does:** Verifies the presentation, ensuring credentials are valid and the presentation proof is correct.

### Step 6: Selective Disclosure (Advanced)

```kotlin
fun main() = runBlocking {
    // Build TrustWeave instance (for tutorials, using testkit factories)
    val trustWeave = TrustWeave.build {
        factories(
            kmsFactory = TestkitKmsFactory(),  // Test-only factory
            didMethodFactory = TestkitDidMethodFactory()  // Test-only factory
        )
        keys { provider("inMemory"); algorithm("Ed25519") }
        did { method("key") { algorithm("Ed25519") } }
    }

    // ... (setup and issue) ...

    // HOLDER: Create presentation with selective disclosure
    // Only reveal degree and university, hide name and graduation date
    val presentation = holderWallet.withPresentation { pres ->
        pres.createPresentation(
            credentials = listOf(credential),
            holderDid = holderDid.value,
            challenge = "challenge-123",
            domain = "example.com",
            revealFields = mapOf(
                credential.id to listOf("degree", "university")
            )
        )
    }.getOrThrow()

    println("✅ Presentation with selective disclosure created")
    // Verifier only sees degree and university, not name or graduation date
}
```

**What this does:** Demonstrates selective disclosure, allowing holders to reveal only specific fields.

### Key Takeaways

- Three-party workflow: issuer → holder → verifier
- Presentations allow holders to share credentials securely
- Challenge and domain prevent replay attacks
- Selective disclosure protects privacy

### Next Steps

- Add revocation checking
- Implement credential expiration handling
- Read [Common Patterns](../getting-started/common-patterns.md)
- Explore [Scenarios](../scenarios/README.md) for domain-specific workflows

---

## Tutorial 5: Adding Blockchain Anchoring

**Duration:** 25-30 minutes
**Goal:** Anchor data to blockchain for tamper evidence

### What You'll Learn

- What blockchain anchoring is
- How to anchor data
- How to read anchored data
- When to use anchoring

### Step 1: Register Blockchain Client

```kotlin
import com.trustweave.anchor.*
import com.trustweave.anchor.options.*

fun main() = runBlocking {
    val TrustWeave = TrustWeave.create {
        blockchains {
            // Register Algorand testnet client
            "algorand:testnet" to AlgorandBlockchainAnchorClient(
                chainId = "algorand:testnet",
                options = AlgorandOptions(
                    algodUrl = "https://testnet-api.algonode.cloud",
                    privateKey = "your-private-key"
                )
            )
        }
    }

    println("✅ Blockchain client registered")
}
```

**What this does:** Registers a blockchain client for anchoring operations.

### Step 2: Anchor Data

```kotlin
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString

@Serializable
data class ImportantData(
    val id: String,
    val timestamp: String,
    val value: String
)

fun main() = runBlocking {
    val TrustWeave = TrustWeave.create {
        // ... (register blockchain client) ...
    }

    // Create data to anchor
    val data = ImportantData(
        id = "data-123",
        timestamp = Instant.now().toString(),
        value = "Important information"
    )

    // Anchor to blockchain
    val anchorResult = trustWeave.blockchains.anchor(
        data = data,
        serializer = ImportantData.serializer(),
        chainId = "algorand:testnet"
    )

    anchorResult.fold(
        onSuccess = { anchor ->
            println("✅ Data anchored")
            println("   Transaction: ${anchor.ref.txHash}")
            println("   Block: ${anchor.ref.blockNumber}")
            println("   Timestamp: ${anchor.timestamp}")
        },
        onFailure = { error ->
            println("❌ Anchoring failed: ${error.message}")
        }
    )
}
```

**What this does:** Anchors data to the blockchain, creating a tamper-evident timestamp.

### Step 3: Read Anchored Data

```kotlin
fun main() = runBlocking {
    val TrustWeave = TrustWeave.create {
        // ... (register blockchain client) ...
    }

    // ... (anchor data) ...
    val anchorRef = anchorResult.getOrThrow().ref

    // Read anchored data
    val readResult = TrustWeave.readAnchor<ImportantData>(
        ref = anchorRef,
        serializer = ImportantData.serializer()
    )

    readResult.fold(
        onSuccess = { data ->
            println("✅ Read anchored data: $data")
        },
        onFailure = { error ->
            println("❌ Failed to read: ${error.message}")
        }
    )
}
```

**What this does:** Reads anchored data from the blockchain and verifies it matches the on-chain digest.

### Step 4: Anchor Credential Status List

```kotlin
fun main() = runBlocking {
    val TrustWeave = TrustWeave.create {
        // ... (register blockchain client) ...
    }

    // Create status list for revocation
    val statusList = TrustWeave.createStatusList(
        issuerDid = issuerDid.value,
        purpose = StatusPurpose.REVOCATION
    ).getOrThrow()

    // Anchor status list to blockchain
    // This makes revocation status tamper-evident
    val anchorResult = trustWeave.blockchains.anchor(
        data = statusList,
        serializer = StatusListCredential.serializer(),
        chainId = "algorand:testnet"
    )

    anchorResult.fold(
        onSuccess = { anchor ->
            println("✅ Status list anchored")
            println("   Can verify revocation status on-chain")
        },
        onFailure = { error ->
            println("❌ Failed to anchor status list: ${error.message}")
        }
    )
}
```

**What this does:** Demonstrates anchoring revocation status lists for tamper-evident revocation checking.

### Key Takeaways

- Anchoring creates tamper-evident timestamps
- Only digests are stored on-chain, not full data
- Useful for revocation lists, audit logs, and provenance
- Different blockchains have different latency and cost characteristics

### Next Steps

- Explore different blockchain options
- Learn about anchoring strategies
- Read [Core Concepts: Blockchain Anchoring](../core-concepts/blockchain-anchoring.md)
- Read [Blockchain-Anchored Revocation](../core-concepts/blockchain-anchored-revocation.md)

---

## What's Next?

After completing this tutorial series, you're ready to:

1. **Build Real Applications**: Use the patterns you've learned in production
2. **Explore Advanced Topics**:
   - [Key Rotation](../advanced/key-rotation.md)
   - [Verification Policies](../advanced/verification-policies.md)
   - [Error Recovery Patterns](../advanced/error-handling.md#error-recovery-patterns)
3. **Study Domain Scenarios**: See how TrustWeave is used in [real-world scenarios](../scenarios/README.md)
4. **Contribute**: Help improve TrustWeave by [creating plugins](../contributing/creating-plugins.md)

## Additional Resources

- [API Reference](../api-reference/core-api.md) - Complete API documentation
- [Core Concepts](../core-concepts/README.md) - Deep dives into concepts
- [Common Patterns](../getting-started/common-patterns.md) - Production patterns
- [Troubleshooting](../getting-started/troubleshooting.md) - Debugging guide
- [FAQ](../faq.md) - Frequently asked questions

