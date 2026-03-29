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
    implementation("org.trustweave:distribution-all:0.6.0")
}
```

### Step 2: Create TrustWeave Instance

```kotlin
import org.trustweave.trust.TrustWeave
import org.trustweave.trust.dsl.credential.DidMethods
import org.trustweave.trust.dsl.credential.KeyAlgorithms
import org.trustweave.testkit.services.*
import kotlinx.coroutines.runBlocking

fun main() = runBlocking {
    // Build TrustWeave instance (auto-discovered via SPI)
    val trustWeave = TrustWeave.build {
        keys { provider(IN_MEMORY); algorithm(KeyAlgorithms.ED25519) }  // Auto-discovered via SPI
        did { method(DidMethods.KEY) { algorithm(KeyAlgorithms.ED25519) } }  // Auto-discovered via SPI
        // KMS, DID methods, and CredentialService all auto-created!
    }

    println("✅ TrustWeave initialized")
}
```

**What this does:** Creates a TrustWeave instance with default configuration, including the `did:key` method.

### Step 3: Create Your First DID

```kotlin
import org.trustweave.trust.TrustWeave
import org.trustweave.trust.dsl.credential.DidMethods
import org.trustweave.trust.dsl.credential.KeyAlgorithms
import org.trustweave.did.resolver.DidResolutionResult
import org.trustweave.did.resolver.errorMessage
import org.trustweave.testkit.services.*
import kotlinx.coroutines.runBlocking

fun main() = runBlocking {
    // Build TrustWeave instance (auto-discovered via SPI)
    val trustWeave = TrustWeave.build {
        keys { provider(IN_MEMORY); algorithm(KeyAlgorithms.ED25519) }  // Auto-discovered via SPI
        did { method(DidMethods.KEY) { algorithm(KeyAlgorithms.ED25519) } }  // Auto-discovered via SPI
        // KMS, DID methods, and CredentialService all auto-created!
    }

    // Create a DID using the default method (did:key)
    val did = trustWeave.createDid { method(DidMethods.KEY) }
    val resolution = trustWeave.resolveDid(did)
    when (resolution) {
        is DidResolutionResult.Success -> {
            println("✅ Created DID: ${did.value}")
            println("   Verification Methods: ${resolution.document.verificationMethod.size}")
        }
        is DidResolutionResult.Failure -> {
            println("❌ Failed to create DID: ${resolution.errorMessage}")
        }
    }
}
```

**What this does:** Creates a new DID using the default `did:key` method. The DID document contains public keys for signing and verification.

**Result:** A `DidDocument` containing the DID identifier and verification methods.

### Step 4: Resolve the DID

```kotlin
import org.trustweave.trust.TrustWeave
import org.trustweave.trust.dsl.credential.DidMethods
import org.trustweave.trust.dsl.credential.KeyAlgorithms
import org.trustweave.did.resolver.DidResolutionResult
import org.trustweave.did.resolver.errorMessage
import org.trustweave.testkit.services.*
import kotlinx.coroutines.runBlocking

fun main() = runBlocking {
    // Build TrustWeave instance (auto-discovered via SPI)
    val trustWeave = TrustWeave.build {
        keys { provider(IN_MEMORY); algorithm(KeyAlgorithms.ED25519) }  // Auto-discovered via SPI
        did { method(DidMethods.KEY) { algorithm(KeyAlgorithms.ED25519) } }  // Auto-discovered via SPI
        // KMS, DID methods, and CredentialService all auto-created!
    }

    // Create a DID using the modern DSL
    val did = trustWeave.createDid { method(DidMethods.KEY) }
    println("Created DID: ${did.value}")

    // Resolve the DID we just created
    val resolution = trustWeave.resolveDid(did)
    when (resolution) {
        is DidResolutionResult.Success -> {
            println("✅ Resolved DID: ${did.value}")
            println("   Methods: ${resolution.document.verificationMethod.size}")
        }
        is DidResolutionResult.Failure -> {
            println("❌ Failed to resolve: ${resolution.errorMessage}")
        }
    }
}
```

**What this does:** Resolves the DID to retrieve its document, demonstrating that DIDs are resolvable identifiers.

### Step 5: Handle Errors Properly

```kotlin
import org.trustweave.trust.TrustWeave
import org.trustweave.trust.dsl.credential.DidMethods
import org.trustweave.trust.dsl.credential.KeyAlgorithms
import org.trustweave.did.resolver.DidResolutionResult
import org.trustweave.did.resolver.errorMessage
import org.trustweave.testkit.services.*
import kotlinx.coroutines.runBlocking

fun main() = runBlocking {
    // Build TrustWeave instance (auto-discovered via SPI)
    val trustWeave = TrustWeave.build {
        keys { provider(IN_MEMORY); algorithm(KeyAlgorithms.ED25519) }  // Auto-discovered via SPI
        did { method(DidMethods.KEY) { algorithm(KeyAlgorithms.ED25519) } }  // Auto-discovered via SPI
        // KMS, DID methods, and CredentialService all auto-created!
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
                    println("❌ Error: ${resolution.errorMessage}")
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
import org.trustweave.trust.types.getOrThrowDid
import org.trustweave.testkit.services.*

fun main() = runBlocking {
    // Build TrustWeave instance (auto-discovered via SPI)
    val trustWeave = TrustWeave.build {
        keys { provider(IN_MEMORY); algorithm(KeyAlgorithms.ED25519) }  // Auto-discovered via SPI
        did { method(DidMethods.KEY) { algorithm(KeyAlgorithms.ED25519) } }  // Auto-discovered via SPI
        // KMS, DID methods, and CredentialService all auto-created!
    }

    // Create issuer DID (the organization issuing credentials)
    
    val issuerDid = trustWeave.createDid { method(DidMethods.KEY) }.getOrThrowDid()
    println("Issuer DID: ${issuerDid.value}")

    // Create holder DID (the person receiving the credential)
    val holderDid = trustWeave.createDid { method(DidMethods.KEY) }.getOrThrowDid()
    println("Holder DID: ${holderDid.value}")
}
```

**What this does:** Sets up the two parties needed for credential issuance: issuer and holder.

### Step 2: Issue a Credential

```kotlin
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.Clock
import org.trustweave.trust.TrustWeave
import org.trustweave.trust.dsl.credential.DidMethods
import org.trustweave.trust.dsl.credential.KeyAlgorithms
import org.trustweave.trust.types.getOrThrowDid
import org.trustweave.credential.results.getOrThrow
import org.trustweave.did.resolver.DidResolutionResult
import org.trustweave.did.identifiers.extractKeyId
import org.trustweave.testkit.services.*

fun main() = runBlocking {
    val trustWeave = TrustWeave.build {
        keys { provider(IN_MEMORY); algorithm(KeyAlgorithms.ED25519) }
        did { method(DidMethods.KEY) { algorithm(KeyAlgorithms.ED25519) } }
    }

    val issuerDid = trustWeave.createDid { method(DidMethods.KEY) }.getOrThrowDid()
    val holderDid = trustWeave.createDid { method(DidMethods.KEY) }.getOrThrowDid()

    val issuerDoc = when (val res = trustWeave.resolveDid(issuerDid)) {
        is DidResolutionResult.Success -> res.document
        else -> throw IllegalStateException("Failed to resolve issuer DID")
    }
    val issuerKeyId = issuerDoc.verificationMethod.firstOrNull()?.extractKeyId()
        ?: throw IllegalStateException("No verification method found")

    val credential = trustWeave.issue {
        credential {
            type("VerifiableCredential", "EducationalCredential")
            issuer(issuerDid)
            subject {
                id(holderDid)
                "name" to "Alice"
                "degree" to "Bachelor of Science"
                "university" to "Example University"
            }
            issued(Clock.System.now())
        }
        signedBy(issuerDid = issuerDid, keyId = issuerKeyId)
    }.getOrThrow()

    println("✅ Credential issued: ${credential.id}")
}
```

**What this does:** Issues a verifiable credential from the issuer to the holder, containing educational information.

**Result:** A `VerifiableCredential` with cryptographic proof that can be verified.

### Step 3: Verify the Credential

```kotlin
import org.trustweave.credential.results.VerificationResult
import org.trustweave.trust.types.proofValid
import org.trustweave.trust.types.notExpired
import org.trustweave.trust.types.notRevoked

fun main() = runBlocking {
    // ... trustWeave and credential from Step 2 ...

    val verificationResult = trustWeave.verify {
        credential(credential)
    }

    when (verificationResult) {
        is VerificationResult.Valid -> {
            println("✅ Credential is valid")
            println("   Proof valid: ${verificationResult.proofValid}")
            println("   Not expired: ${verificationResult.notExpired}")
            println("   Not revoked: ${verificationResult.notRevoked}")
            if (verificationResult.warnings.isNotEmpty()) {
                println("   Warnings: ${verificationResult.warnings.joinToString()}")
            }
        }
        is VerificationResult.Invalid -> {
            println("❌ Credential is invalid")
            println("   Errors: ${verificationResult.allErrors}")
        }
    }
}
```

**What this does:** Verifies the credential's proof, expiration, and revocation status.

### Step 4: Add Expiration Date

```kotlin
import kotlin.time.Duration.Companion.days
import kotlinx.datetime.Clock

fun main() = runBlocking {
    // ... trustWeave, issuerDid, holderDid, issuerKeyId ...

    trustWeave.issue {
        credential {
            type("VerifiableCredential", "EducationalCredential")
            issuer(issuerDid)
            subject {
                id(holderDid)
                "name" to "Alice"
                "degree" to "Bachelor of Science"
            }
            issued(Clock.System.now())
            expiresIn(365.days)
        }
        signedBy(issuerDid = issuerDid, keyId = issuerKeyId)
    }

    // ... verify with trustWeave.verify { credential(...) } ...
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
// Kotlin stdlib
import kotlinx.coroutines.runBlocking

// TrustWeave core
import org.trustweave.trust.TrustWeave
import org.trustweave.trust.dsl.credential.DidMethods
import org.trustweave.trust.dsl.credential.KeyAlgorithms
import org.trustweave.wallet.*
import org.trustweave.testkit.services.*

import org.trustweave.trust.types.getOrThrowDid
import org.trustweave.trust.types.getOrThrow

fun main() = runBlocking {
    val trustWeave = TrustWeave.build {
        keys { provider(IN_MEMORY); algorithm(KeyAlgorithms.ED25519) }
        did { method(DidMethods.KEY) { algorithm(KeyAlgorithms.ED25519) } }
    }

    val holderDid = trustWeave.createDid { method(DidMethods.KEY) }.getOrThrowDid()

    val wallet = trustWeave.wallet {
        holder(holderDid)
        provider("inMemory")
    }.getOrThrow()

    println("✅ Wallet created: ${wallet.walletId}")
    println("   Holder: ${holderDid.value}")
}
```

**What this does:** Creates a wallet for storing credentials. Wallets provide secure storage and management.

### Step 2: Store Credentials

```kotlin
import org.trustweave.credential.model.vc.VerifiableCredential
import org.trustweave.trust.types.getOrThrowDid
import org.trustweave.trust.types.getOrThrow
import org.trustweave.testkit.services.*

fun main() = runBlocking {
    val trustWeave = TrustWeave.build {
        keys { provider(IN_MEMORY); algorithm(KeyAlgorithms.ED25519) }
        did { method(DidMethods.KEY) { algorithm(KeyAlgorithms.ED25519) } }
    }

    val issuerDid = trustWeave.createDid { method(DidMethods.KEY) }.getOrThrowDid()
    val holderDid = trustWeave.createDid { method(DidMethods.KEY) }.getOrThrowDid()

    // Issue credential (simplified - see Tutorial 2 for full example)
    val credential: VerifiableCredential =
        /* ... trustWeave.issue { ... }.getOrThrow() ... */

    val wallet = trustWeave.wallet {
        holder(holderDid)
        provider("inMemory")
    }.getOrThrow()

    val storedId = wallet.store(credential)
    println("✅ Credential stored: $storedId")
}
```

**What this does:** Stores a credential in the wallet, making it available for later retrieval.

### Step 3: Query Credentials

```kotlin
import org.trustweave.did.identifiers.Did
import org.trustweave.wallet.Wallet
import org.trustweave.testkit.services.*

fun main() = runBlocking {
    val trustWeave = TrustWeave.build {
        keys { provider(IN_MEMORY); algorithm(KeyAlgorithms.ED25519) }
        did { method(DidMethods.KEY) { algorithm(KeyAlgorithms.ED25519) } }
    }

    // ... create wallet, store credentials (see Step 2) ...
    val wallet: Wallet = /* ... */
    val issuerDid: Did = /* ... */

    val allCredentials = wallet.query { }
    println("Total credentials: ${allCredentials.size}")

    val educationalCreds = wallet.query {
        byType("EducationalCredential")
    }
    println("Educational credentials: ${educationalCreds.size}")

    val issuerCreds = wallet.query {
        byIssuer(issuerDid.value)
    }
    println("Credentials from issuer: ${issuerCreds.size}")
}
```

**What this does:** Demonstrates querying credentials by various criteria.

### Step 4: Use Organization Features

```kotlin
import org.trustweave.trust.types.getOrThrow
import org.trustweave.testkit.services.*

fun main() = runBlocking {
    val trustWeave = TrustWeave.build {
        keys { provider(IN_MEMORY); algorithm(KeyAlgorithms.ED25519) }
        did { method(DidMethods.KEY) { algorithm(KeyAlgorithms.ED25519) } }
    }

    val wallet = trustWeave.wallet {
        holder(holderDid)
        provider("inMemory")
    }.getOrThrow()

    val credential = /* ... */
    val credentialId = wallet.store(credential)

    wallet.withOrganization { org ->
        val collectionId = org.createCollection("Education")
        org.addToCollection(credentialId, collectionId)

        org.tagCredential(credentialId, setOf("diploma", "bachelor"))

        val educationCreds = wallet.query {
            byCollection(collectionId)
        }
        println("Education collection: ${educationCreds.size} credentials")

        val diplomaCreds = wallet.query {
            byTag("diploma")
        }
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
import org.trustweave.trust.TrustWeave
import org.trustweave.trust.dsl.credential.DidMethods
import org.trustweave.trust.dsl.credential.KeyAlgorithms
import org.trustweave.trust.types.getOrThrowDid
import org.trustweave.trust.types.getOrThrow
import org.trustweave.did.resolver.DidResolutionResult
import org.trustweave.did.identifiers.extractKeyId
import org.trustweave.testkit.services.*
import kotlinx.coroutines.runBlocking

fun main() = runBlocking {
    val trustWeave = TrustWeave.build {
        keys { provider(IN_MEMORY); algorithm(KeyAlgorithms.ED25519) }
        did { method(DidMethods.KEY) { algorithm(KeyAlgorithms.ED25519) } }
    }

    val issuerDid = trustWeave.createDid { method(DidMethods.KEY) }.getOrThrowDid()
    val issuerDoc = when (val res = trustWeave.resolveDid(issuerDid)) {
        is DidResolutionResult.Success -> res.document
        else -> throw IllegalStateException("Failed to resolve issuer DID")
    }
    val issuerKeyId = issuerDoc.verificationMethod.firstOrNull()?.extractKeyId()
        ?: throw IllegalStateException("No verification method found")

    val holderDid = trustWeave.createDid { method(DidMethods.KEY) }.getOrThrowDid()
    val holderWallet = trustWeave.wallet {
        holder(holderDid)
        provider("inMemory")
    }.getOrThrow()

    val verifierDid = trustWeave.createDid { method(DidMethods.KEY) }.getOrThrowDid()

    println("✅ All parties set up")
    println("   Issuer: ${issuerDid.value}")
    println("   Holder: ${holderDid.value}")
    println("   Verifier: ${verifierDid.value}")
}
```

**What this does:** Sets up the three parties in a typical credential workflow.

### Step 2: Issue Credential (Issuer)

```kotlin
import org.trustweave.credential.results.getOrThrow
import kotlinx.datetime.Clock

fun main() = runBlocking {
    // ... setup parties (Step 1): trustWeave, issuerDid, issuerKeyId, holderDid ...

    val credential = trustWeave.issue {
        credential {
            type("VerifiableCredential", "EducationalCredential")
            issuer(issuerDid)
            subject {
                id(holderDid)
                "name" to "Alice"
                "degree" to "Bachelor of Science in Computer Science"
                "university" to "Example University"
                "graduationDate" to "2024-05-15"
            }
            issued(Clock.System.now())
        }
        signedBy(issuerDid = issuerDid, keyId = issuerKeyId)
    }.getOrThrow()

    println("✅ Credential issued by issuer")
}
```

### Step 3: Store Credential (Holder)

```kotlin
fun main() = runBlocking {
    // ... setup, issue (Steps 1–2), holderWallet, credential ...

    val storedId = holderWallet.store(credential)
    println("✅ Credential stored by holder: $storedId")
}
```

### Step 4: Create Presentation (Holder)

```kotlin
import org.trustweave.trust.dsl.credential.presentationResult
import org.trustweave.trust.types.PresentationResult

fun main() = runBlocking {
    // ... setup, issue, store; trustWeave, holderDid, credential ...

    val challenge = "verifier-challenge-123"

    val presentation = when (
        val pr = trustWeave.presentationResult {
            credentials(credential)
            holder(holderDid)
            challenge(challenge)
            domain("example-employer.com")
        }
    ) {
        is PresentationResult.Success -> pr.presentation
        is PresentationResult.Failure -> error(pr.allErrors.joinToString())
    }

    println("✅ Presentation created by holder")
}
```

**What this does:** Creates a verifiable presentation that the holder can share with the verifier.

### Step 5: Verify Presentation (Verifier)

```kotlin
import org.trustweave.credential.requests.VerificationOptions
import org.trustweave.credential.results.VerificationResult

fun main() = runBlocking {
    // ... complete workflow above; trustWeave, presentation ...

    val credentialService = trustWeave.configuration.credentialService
        ?: error("Configure a CredentialService in TrustWeave.build { ... }")

    when (
        val vr = credentialService.verifyPresentation(
            presentation = presentation,
            options = VerificationOptions(),
        )
    ) {
        is VerificationResult.Valid -> println("✅ Presentation verified by verifier")
        is VerificationResult.Invalid -> {
            println("❌ Presentation invalid: ${vr.allErrors.joinToString()}")
        }
    }
}
```

**What this does:** Verifies the presentation, ensuring credentials are valid and the presentation proof is correct.

### Step 6: Selective Disclosure (Advanced)

```kotlin
import org.trustweave.trust.dsl.credential.presentationResult
import org.trustweave.trust.types.PresentationResult

fun main() = runBlocking {
    // ... trustWeave, holderDid, credential ...

    val presentation = when (
        val pr = trustWeave.presentationResult {
            credentials(credential)
            holder(holderDid)
            challenge("challenge-123")
            domain("example.com")
            selectiveDisclosure {
                reveal("degree", "university")
            }
        }
    ) {
        is PresentationResult.Success -> pr.presentation
        is PresentationResult.Failure -> error(pr.allErrors.joinToString())
    }

    println("✅ Presentation with selective disclosure created")
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
import org.trustweave.testkit.services.*
// Kotlin stdlib
import kotlinx.coroutines.runBlocking

// TrustWeave core
import org.trustweave.trust.TrustWeave
import org.trustweave.trust.dsl.credential.DidMethods
import org.trustweave.trust.dsl.credential.KeyAlgorithms

fun main() = runBlocking {
    val trustWeave = TrustWeave.build {
        did {
            method(DidMethods.KEY) { algorithm(KeyAlgorithms.ED25519) }
        }
        anchor {
            chain("algorand:testnet") {
                provider("algorand")
                options {
                    "algodUrl" to "https://testnet-api.algonode.cloud"
                    "privateKey" to "your-private-key"
                }
            }
        }
    }

    println("Blockchain client registered for algorand:testnet")
}
```

**What this does:** Registers a blockchain client for anchoring operations.

### Step 2: Anchor Data

```kotlin
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import org.trustweave.testkit.services.*

@Serializable
data class ImportantData(
    val id: String,
    val timestamp: String,
    val value: String
)

fun main() = runBlocking {
    val trustWeave = TrustWeave.build {
        did { method(DidMethods.KEY) { algorithm(KeyAlgorithms.ED25519) } }
        anchor {
            chain("algorand:testnet") {
                provider("algorand")
                options {
                    "algodUrl" to "https://testnet-api.algonode.cloud"
                    "privateKey" to "your-private-key"
                }
            }
        }
    }

    val data = ImportantData(
        id = "data-123",
        timestamp = Instant.now().toString(),
        value = "Important information"
    )

    val anchorResult = trustWeave.blockchains.anchor(
        data = data,
        serializer = ImportantData.serializer(),
        chainId = "algorand:testnet"
    )

    println("Data anchored at tx ${anchorResult.ref.txHash} on ${anchorResult.ref.chainId}")
}
```

**What this does:** Anchors data to the blockchain, creating a tamper-evident timestamp.

### Step 3: Read Anchored Data

```kotlin
fun main() = runBlocking {
    val trustWeave = TrustWeave.build {
        // ... keys, did, anchor { chain("algorand:testnet") { provider("algorand") { ... } } } ...
    }

    // ... anchor data → AnchorResult ...
    val anchorRef = anchorResult.ref

    try {
        val data = trustWeave.blockchains.read<ImportantData>(
            ref = anchorRef,
            serializer = ImportantData.serializer()
        )
        println("✅ Read anchored data: $data")
    } catch (e: Exception) {
        println("❌ Failed to read: ${e.message}")
    }
}
```

**What this does:** Reads anchored data from the blockchain and verifies it matches the on-chain digest.

### Step 4: Anchor Credential Status List

```kotlin
fun main() = runBlocking {
    val trustWeave = TrustWeave.build {
        // ... (register blockchain client) ...
    }

    // Create status list for revocation
    val statusList = trustWeave.createStatusList(
        issuerDid = issuerDid.value,
        purpose = StatusPurpose.REVOCATION
    ).getOrThrow()

    // Anchor status list to blockchain (throws on chain/client errors)
    val anchored = trustWeave.blockchains.anchor(
        data = statusList,
        serializer = StatusListCredential.serializer(),
        chainId = "algorand:testnet"
    )
    println("✅ Status list anchored at ${anchored.ref.chainId} / ${anchored.ref.txHash}")
    println("   Revocation status is tamper-evident on-chain")
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
   - Key Rotation](../advanced/key-rotation.md)
   - Verification Policies](../advanced/verification-policies.md)
   - Error Recovery Patterns](../advanced/error-handling.md#error-recovery-patterns)
3. **Study Domain Scenarios**: See how TrustWeave is used in [real-world scenarios](../scenarios/README.md)
4. **Contribute**: Help improve TrustWeave by [creating plugins](../contributing/creating-plugins.md)

## Additional Resources

- API Reference](../api-reference/core-api.md) - Complete API documentation
- Core Concepts](../core-concepts/README.md) - Deep dives into concepts
- Common Patterns](../getting-started/common-patterns.md) - Production patterns
- Troubleshooting](../getting-started/troubleshooting.md) - Debugging guide
- FAQ](../faq.md) - Frequently asked questions

