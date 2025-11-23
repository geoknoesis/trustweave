# Quick Start

Get started with TrustWeave in 5 minutes! This guide will walk you through creating your first TrustWeave application.

> **Version:** 1.0.0-SNAPSHOT  
> **Kotlin:** 2.2.0+ | **Java:** 21+  
> See [Installation](installation.md) for setup details.

## Complete Runnable Example

Here's a complete, copy-paste ready example that demonstrates the full TrustWeave workflow. This example uses `getOrThrow()` for simplicity in quick-start scenarios. For production code, see the [Production Pattern](#production-pattern-with-error-handling) section below.

> **Note:** This example uses `getOrThrow()` which is acceptable for quick starts, tests, and prototypes. For production code, always use `fold()` for explicit error handling. See [Error Handling Patterns](#error-handling-patterns) below.

```kotlin
package com.example.TrustWeave.quickstart

import com.trustweave.TrustWeave
import com.trustweave.anchor.BlockchainAnchorRegistry
import com.trustweave.credential.models.VerifiableCredential
import com.trustweave.json.DigestUtils
import com.trustweave.testkit.anchor.InMemoryBlockchainAnchorClient
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.put

fun main() = runBlocking {
    // Step 1: Create TrustWeave instance with defaults
    val TrustWeave = TrustWeave.create()

    // Step 2: Compute a digest (demonstrates canonicalization)
    val credentialSubject = buildJsonObject {
        put("id", "did:key:holder-placeholder")
        put("name", "Alice Example")
        put("role", "Site Reliability Engineer")
    }
    val digest = DigestUtils.sha256DigestMultibase(credentialSubject)
    println("Canonical credential-subject digest: $digest")

    // Step 3: Create an issuer DID
    // Note: getOrThrow() is used here for quick-start simplicity
    // In production, use fold() for error handling (see below)
    val issuerDocument = TrustWeave.dids.create()
    val issuerDid = issuerDocument.id
    val issuerKeyId = issuerDocument.verificationMethod.firstOrNull()?.id
        ?: error("No verification method generated for $issuerDid")
    println("Issuer DID: $issuerDid (keyId=$issuerKeyId)")

    // Step 4: Issue a verifiable credential
    val credential = TrustWeave.credentials.issue(
        issuer = issuerDid,
        subject = credentialSubject,
        config = IssuanceConfig(
            proofType = ProofType.Ed25519Signature2020,
            keyId = issuerKeyId,
            issuerDid = issuerDid
        ),
        types = listOf("VerifiableCredential", "QuickStartCredential")
    )
    println("Issued credential id: ${credential.id}")

    // Step 5: Verify the credential
    val verification = TrustWeave.credentials.verify(credential)
    if (verification.valid) {
        println(
            "Verification succeeded (proof=${verification.proofValid}, " +
            "issuer=${verification.issuerValid}, revocation=${verification.notRevoked})"
        )
        if (verification.warnings.isNotEmpty()) {
            println("Warnings: ${verification.warnings}")
        }
    } else {
        println("Verification returned errors: ${verification.errors}")
    }
    )

    // Step 6: Anchor credential to blockchain (optional)
    val anchorRegistry = BlockchainAnchorRegistry().apply {
        register("inmemory:anchor", InMemoryBlockchainAnchorClient("inmemory:anchor"))
    }
    val anchorClient = requireNotNull(anchorRegistry.get("inmemory:anchor")) {
        "inmemory anchor client not registered"
    }

    runCatching {
        val payload = Json.encodeToJsonElement(VerifiableCredential.serializer(), credential)
        val anchorResult = anchorClient.writePayload(payload)
        println("Anchored credential on ${anchorResult.ref.chainId}: ${anchorResult.ref.txHash}")
    }.onFailure { error ->
        println("Anchoring failed: ${error.message}")
    }
}
```

### Production Pattern with Error Handling

For production code, always use `fold()` for explicit error handling:

```kotlin
fun main() = runBlocking {
    val TrustWeave = TrustWeave.create()

    // Production pattern: Use fold() for all operations
    val issuerDocument = Result.success(TrustWeave.dids.create()).fold(
        onSuccess = { it },
        onFailure = { error ->
            when (error) {
                is TrustWeaveError.DidMethodNotRegistered -> {
                    println("❌ DID method not registered: ${error.method}")
                    println("   Available methods: ${error.availableMethods}")
                }
                else -> {
                    println("❌ Failed to create DID: ${error.message}")
                }
            }
            return@runBlocking
        }
    )
    
    val issuerDid = issuerDocument.id
    val issuerKeyId = issuerDocument.verificationMethod.firstOrNull()?.id
        ?: error("No verification method found")
    
    val credential = TrustWeave.credentials.issue(
        issuerDid = issuerDid,
        issuerKeyId = issuerKeyId,
        credentialSubject = buildJsonObject {
            put("id", "did:key:holder")
            put("name", "Alice")
        },
        types = listOf("VerifiableCredential", "QuickStartCredential")
    ).fold(
        onSuccess = { it },
        onFailure = { error ->
            when (error) {
                is TrustWeaveError.CredentialIssuanceFailed -> {
                    println("❌ Credential issuance failed: ${error.reason}")
                }
                else -> {
                    println("❌ Failed to issue credential: ${error.message}")
                }
            }
            return@runBlocking
        }
    )
    
    println("✅ Credential issued: ${credential.id}")
}
```

**Expected Output:**
```
Canonical credential-subject digest: u5v...
Issuer DID: did:key:z6Mk... (keyId=did:key:z6Mk...#key-1)
Issued credential id: urn:uuid:...
Verification succeeded (proof=true, issuer=true, revocation=true)
Anchored credential on inmemory:anchor: tx_...
```

**To run this example:**
1. Add the dependency (see Step 1 below)
2. Copy the code above into `src/main/kotlin/QuickStart.kt`
3. Run with `./gradlew run` or execute in your IDE

---

## Step-by-Step Guide

The sections below explain each step in detail.

## Step 1: Add a single dependency

**Why:** `TrustWeave-all` bundles every public module (core APIs, DID support, KMS, anchoring, DSLs) so you can get going with one line.  
**How it works:** It's a convenience metapackage that re-exports the same artifacts you would otherwise add one-by-one.  
**How simple:** Drop one dependency and you're done.

> **Note:** For production deployments, consider using individual modules instead of `TrustWeave-all` to minimize bundle size. See [Installation Guide](installation.md) for details.

```kotlin
dependencies {
    implementation("com.trustweave:trustweave-all:1.0.0-SNAPSHOT")
    testImplementation("com.trustweave:trustweave-testkit:1.0.0-SNAPSHOT")
}
```

**What this does**  
- Pulls in every public TrustWeave module (core APIs, DID support, KMS, anchoring, DSLs) with a single coordinate so you never chase transitive dependencies.  
- Adds `TrustWeave-testkit` for the in-memory DID/KMS/wallet implementations used in the tutorials and automated tests.

**Design significance**  
TrustWeave promotes a “batteries included” experience for newcomers. The monolithic artifact keeps onboarding simple; when you graduate to production you can swap in individual modules without changing API usage.

## Step 2: Bootstrap TrustWeave and compute a digest

**Why:** Most flows start by hashing JSON so signatures and anchors are stable.  
**How it works:** `DigestUtils.sha256DigestMultibase` canonicalises JSON and returns a multibase string.  
**How simple:** One helper call, no manual canonicalisation.

```kotlin
import com.trustweave.TrustWeave
import com.trustweave.json.DigestUtils
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

fun main() = runBlocking {
    // Create TrustWeave with sensible defaults (in-memory KMS, did:key method)
    val TrustWeave = TrustWeave.create()

    // Build credential subject payload
    val credentialSubject = buildJsonObject {
        put("id", "did:key:holder-placeholder")
        put("name", "Alice Example")
        put("role", "Site Reliability Engineer")
    }

    // Compute deterministic digest (canonicalizes JSON first)
    val digest = DigestUtils.sha256DigestMultibase(credentialSubject)
    println("Digest: $digest")
}
```

**What this does**  
- Instantiates TrustWeave with sensible defaults (in-memory registries) suitable for playground and unit tests.  
- Builds a credential payload using Kotlinx Serialization builders so the structure is type-safe.  
- Canonicalises and hashes the payload, returning a multibase-encoded digest you can anchor or sign.

> **Important:** The defaults use in-memory components (KMS, wallets, DID methods) suitable for testing only. For production, configure your own KMS, DID methods, and storage backends. See [Default Configuration](../configuration/defaults.md) and [Production Deployment](../deployment/production-checklist.md) for details.

**Result**  
`DigestUtils.sha256DigestMultibase` prints a deterministic digest (for example `u5v...`) that becomes the integrity reference for later steps.

**Design significance**  
Everything in TrustWeave assumes deterministic canonicalization, so the very first code sample reinforces the pattern: serialize → canonicalize → hash → sign/anchor. This is the backbone of interoperability.

## Step 3: Create a DID with typed options

**Why:** You need an issuer DID before issuing credentials.  
**How it works:** `TrustWeave.dids.create()` uses the bundled DID method registry and typed `DidCreationOptions`.  
**How simple:** Configure only what you need using a fluent builder—defaults cover the rest.

```kotlin
// Simple: use defaults (did:key method, ED25519 algorithm)
val issuerDocument = TrustWeave.dids.create()
val issuerDid = issuerDocument.id
val issuerKeyId = issuerDocument.verificationMethod.firstOrNull()?.id
    ?: error("No verification method generated")
println("Issuer DID: $issuerDid (keyId=$issuerKeyId)")

// Advanced: customize with builder
val customDid = TrustWeave.dids.create("key") {
    algorithm = com.trustweave.did.DidCreationOptions.KeyAlgorithm.ED25519
    purpose(com.trustweave.did.DidCreationOptions.KeyPurpose.AUTHENTICATION)
}
```

**What this does**  
- Calls the facade to provision a DID using the default registry (in this case `did:key`).  
- Returns the fully materialised DID document with verification methods.  
- Extracts the DID identifier and key ID for use in credential issuance.

**Result**  
`issuerDid` now holds a resolvable DID such as `did:key:z6M...` that acts as the issuer for credentials. The `issuerKeyId` is needed for signing credentials.

**Design significance**  
Typed builders (`DidCreationOptions`) are a core design choice: they prevent misconfigured DID creation at compile time and make IDE autocompletion an onboarding tool rather than documentation guesswork.

## Step 4: Issue a credential and store it

**Why:** Credential issuance is the heart of most TrustWeave solutions.  
**How it works:** The facade orchestrates KMS, proofs, and registries, returning a `Result<VerifiableCredential>`.  
**How simple:** Provide the issuer DID/key and credential subject JSON; the API handles proof generation and validation.

```kotlin
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

// Issue credential using the issuer DID and key ID from Step 3
val credential = TrustWeave.credentials.issue(
    issuer = issuerDid,
    subject = buildJsonObject {
        put("id", "did:key:holder-placeholder")
        put("name", "Alice Example")
        put("role", "Site Reliability Engineer")
    },
    config = IssuanceConfig(
        proofType = ProofType.Ed25519Signature2020,
        keyId = issuerKeyId,  // From issuerDocument.verificationMethod
        issuerDid = issuerDid
    ),
    types = listOf("VerifiableCredential", "QuickStartCredential")
)

println("Issued credential id: ${credential.id}")
```

**What this does**  
- Invokes the credential issuance facade which orchestrates key lookup/generation, proof creation, and credential assembly.  
- Configures the credential subject payload and credential types.  
- Returns a signed `VerifiableCredential` wrapped in `Result`.

**Result**  
The printed ID corresponds to a tamper-evident credential JSON object that you can store, present, or anchor.

**Design significance**  
Facades embrace TrustWeave's "everything returns `Result<T>`" philosophy. By forcing the caller to handle success and failure explicitly, flows stay predictable in production and testable in unit harnesses.

> ✅ **Run the sample**  
> The full quick-start flow lives in `TrustWeave-examples/src/main/kotlin/com/geoknoesis/TrustWeave/examples/quickstart/QuickStartSample.kt`.  
> Execute it locally with `./gradlew :TrustWeave-examples:runQuickStartSample`.

## Step 5: Verify the credential

**Why:** Consumers must trust the credential; verification validates proofs and checks revocation.  
**How it works:** `verifyCredential` rebuilds proofs, resolves issuer DIDs, and performs validity checks.  
**How simple:** One call returns a structured result with validation details.

```kotlin
import com.trustweave.core.*

// Verify credential
val verification = TrustWeave.credentials.verify(credential)
if (verification.valid) {
    println(
        "Verification succeeded (proof=${verification.proofValid}, " +
        "issuer=${verification.issuerValid}, revocation=${verification.notRevoked})"
    )
    if (verification.warnings.isNotEmpty()) {
        println("Warnings: ${verification.warnings}")
    }
} else {
    println("Verification returned errors: ${verification.errors}")
}
```

**What this does**  
- Verifies the credential by rebuilding proofs and performing validity checks.  
- Checks issuer DID resolution, proof validity, and revocation status.  
- Returns structured results with detailed validation information.

**Result**  
You get a `CredentialVerificationResult` with `valid`, `proofValid`, `issuerValid`, `notRevoked` flags, plus lists of `errors` and `warnings`.

## Step 6: Anchor to blockchain (optional)

**Why:** Anchoring provides tamper evidence and timestamping on a blockchain.  
**How it works:** Register a blockchain client and use it to anchor credential data.  
**How simple:** Register client, serialize credential, write to chain.

```kotlin
import com.trustweave.anchor.BlockchainAnchorRegistry
import com.trustweave.credential.models.VerifiableCredential
import com.trustweave.testkit.anchor.InMemoryBlockchainAnchorClient
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.encodeToJsonElement

// Register in-memory blockchain client (for testing)
val anchorRegistry = BlockchainAnchorRegistry().apply {
    register("inmemory:anchor", InMemoryBlockchainAnchorClient("inmemory:anchor"))
}
val anchorClient = requireNotNull(anchorRegistry.get("inmemory:anchor")) {
    "inmemory anchor client not registered"
}

// Anchor the credential
runCatching {
    val payload = Json.encodeToJsonElement(VerifiableCredential.serializer(), credential)
    val anchorResult = anchorClient.writePayload(payload)
    println("Anchored credential on ${anchorResult.ref.chainId}: ${anchorResult.ref.txHash}")
}.onFailure { error ->
    println("Anchoring failed: ${error.message}")
}
```

**What this does**  
- Registers an in-memory blockchain client (for testing; use real clients in production).  
- Serializes the credential to JSON and writes it to the anchor client.  
- Returns an `AnchorRef` with chain ID and transaction hash.

**Result**  
You get an `AnchorRef` representing the write operation. In production you'd persist this for audits and use a real chain adapter (Algorand, Polygon, etc.).

**Design significance**  
Anchoring is abstracted behind the same interface regardless of provider. The sample sticks to the in-memory implementation, but the code path is identical for Algorand, Polygon, Indy, or future adapters—making environment swaps low risk.

## Error Handling Patterns

TrustWeave provides structured error handling with `Result<T>` and `TrustWeaveError` types. Understanding when to use different patterns is important for production code.

> **Best Practice:** Always use `fold()` for production code. Use `getOrThrow()` only for quick starts, tests, and prototypes.

### When to Use `getOrThrow()` (Testing/Prototyping Only)

Use `getOrThrow()` **only** for:
- ✅ Quick start examples and prototypes
- ✅ Simple scripts where you can let errors bubble up
- ✅ Test code where exceptions are acceptable
- ✅ Learning and experimentation

```kotlin
// ⚠️ Simple usage (throws on error) - Testing/Prototyping Only
// For production, always use fold() instead
val did = TrustWeave.dids.create()
val credential = TrustWeave.credentials.issue(...)
```

**Why not in production?** `getOrThrow()` throws exceptions that can crash your application. Production code should handle errors gracefully.

### When to Use `fold()` (Production Pattern)

Use `fold()` **always** for:
- ✅ Production code
- ✅ When you need to handle specific error types
- ✅ When you want to provide user-friendly error messages
- ✅ When you need to log errors before handling
- ✅ When you need to recover from errors

```kotlin
// ✅ Production pattern with specific error handling
val did = TrustWeave.dids.create()
// Note: dids.create() returns DidDocument directly (not Result)
// For error handling, wrap in try-catch or use extension functions
try {
    processDid(did)
} catch (e: TrustWeaveError.DidMethodNotRegistered) {
    // Handle error
}
    onFailure = { error ->
        // Handle specific errors
        when (error) {
            is TrustWeaveError.DidMethodNotRegistered -> {
                logger.warn("DID method not registered: ${error.method}")
                logger.info("Available methods: ${error.availableMethods}")
                // Register method and retry, or use fallback
            }
            is TrustWeaveError.InvalidDidFormat -> {
                logger.error("Invalid DID format: ${error.reason}")
                // Show format requirements to user
            }
            else -> {
                logger.error("Unexpected error: ${error.message}", error)
                // Handle generic error
            }
        }
    }
)
```

**Why use `fold()`?** It forces explicit error handling, prevents crashes, and provides structured error information for recovery.

## Handling errors and verification failures

TrustWeave provides structured error handling with `Result<T>` and `TrustWeaveError` types:

```kotlin
import com.trustweave.core.*

// Verify credential with error handling
val verification = TrustWeave.credentials.verify(credential)
if (verification.valid) {
    println("Credential is valid")
} else {
    println("Credential invalid: ${verification.errors.joinToString()}")
    verification.warnings.forEach { println("Warning: $it") }
}

// Anchoring with error handling
try {
    val anchor = TrustWeave.blockchains.anchor(
        data = data,
        serializer = serializer,
        chainId = "algorand:testnet"
    )
    println("Anchored at: ${anchor.ref.txHash}")
} catch (error: TrustWeaveError) {
    when (error) {
        is TrustWeaveError.ChainNotRegistered -> {
            println("Chain not registered: ${error.chainId}")
            println("Available chains: ${error.availableChains}")
        }
        else -> {
            println("Anchoring error: ${error.message}")
        }
    }
}
```

For simple cases, you can use `getOrThrow()`:

```kotlin
// Simple usage
val verification = TrustWeave.credentials.verify(credential)
if (verification.valid) {
    println("Credential is valid")
}

// Or use getOrThrowError for TrustWeaveError
val anchor = TrustWeave.blockchains.anchor(
    data = data,
    serializer = serializer,
    chainId = chainId
)
```

**Best Practice:** In production code, prefer `fold()` for explicit error handling. Use `getOrThrow()` for quick prototypes and tests.

See [Error Handling](../advanced/error-handling.md) for more details on error handling patterns.

## Scenario Playbook

Ready to explore real-world workflows? Each guide below walks through an end-to-end scenario using the same APIs you just touched:

- **[View All Scenarios](../scenarios/README.md)** – Complete list of all available scenarios

**Popular Scenarios:**
- [Academic Credentials](../scenarios/academic-credentials-scenario.md) – issue diplomas, validate transcripts, and manage revocation.
- [Employee Onboarding](../scenarios/employee-onboarding-scenario.md) – complete onboarding with education, work history, and background checks.
- [Vaccination Health Passports](../scenarios/vaccination-health-passport-scenario.md) – privacy-preserving health credentials for travel and access.
- [Event Ticketing](../scenarios/event-ticketing-scenario.md) – verifiable tickets with transfer control and fraud prevention.
- [Age Verification](../scenarios/age-verification-scenario.md) – verify age without revealing personal information.
- [Insurance Claims](../scenarios/insurance-claims-scenario.md) – complete claims verification with fraud prevention.
- [Financial Services (KYC)](../scenarios/financial-services-kyc-scenario.md) – streamline onboarding and reuse credentials across institutions.
- [Government Digital Identity](../scenarios/government-digital-identity-scenario.md) – citizens receive, store, and present official IDs.
- [Healthcare Records](../scenarios/healthcare-medical-records-scenario.md) – share consented medical data across providers with audit trails.
- [Supply Chain Traceability](../scenarios/supply-chain-traceability-scenario.md) – follow goods from origin to shelf with verifiable checkpoints.

## Troubleshooting

If you encounter issues:
- See [Troubleshooting Guide](troubleshooting.md) for common problems and solutions
- Check [Error Handling](../advanced/error-handling.md) for error handling patterns
- Review [FAQ](../faq.md) for frequently asked questions

## Learning Path

Follow this structured path to master TrustWeave:

### 1. Get Started (You are here!)
- ✅ Complete this Quick Start guide
- ✅ Run the example code
- ✅ Understand basic concepts

### 2. Learn the Fundamentals
- **[Beginner Tutorial Series](../tutorials/beginner-tutorial-series.md)** - Structured 5-tutorial series (2+ hours)
  - Tutorial 1: Your First DID (15-20 min)
  - Tutorial 2: Issuing Your First Credential (20-25 min)
  - Tutorial 3: Managing Credentials with Wallets (25-30 min)
  - Tutorial 4: Building a Complete Workflow (30-35 min)
  - Tutorial 5: Adding Blockchain Anchoring (25-30 min)

### 3. Build Real Applications
- **[Your First Application](your-first-application.md)** - Build a complete example
- **[Common Patterns](common-patterns.md)** - Production-ready patterns
- **[Scenarios](../scenarios/README.md)** - Real-world use cases

### 4. Deepen Your Knowledge
- **[Core Concepts](../core-concepts/README.md)** - Deep dives into fundamentals
- **[API Reference](../api-reference/core-api.md)** - Complete API documentation
- **[Advanced Topics](../advanced/README.md)** - Key rotation, verification policies, etc.

### 5. Production Deployment
- **[Error Handling Guide](../advanced/error-handling.md)** - Production error handling
- **[Troubleshooting](troubleshooting.md)** - Debugging and solutions
- **[Security Best Practices](../security/README.md)** - Security guidelines

## What's Next?

**New to TrustWeave?**
1. Start with [Beginner Tutorial Series](../tutorials/beginner-tutorial-series.md) - Tutorial 1
2. Complete all 5 tutorials in order
3. Move to [Common Patterns](common-patterns.md) for production patterns

**Already familiar with DIDs/VCs?**
1. Review [Common Patterns](common-patterns.md) for TrustWeave-specific patterns
2. Explore [Scenarios](../scenarios/README.md) for your use case
3. Reference [API Reference](../api-reference/core-api.md) as needed

**Building a specific application?**
1. Check [Scenarios](../scenarios/README.md) for similar use cases
2. Review [Common Patterns](common-patterns.md) for reusable patterns
3. Consult [API Reference](../api-reference/core-api.md) for details

## Additional Resources

- [Core Concepts](../core-concepts/README.md) - Learn the fundamentals
- [API Reference](../api-reference/core-api.md) - Complete API documentation
- [Troubleshooting](troubleshooting.md) - Common issues and solutions
- [Error Handling Guide](../advanced/error-handling.md) - Detailed error handling patterns
- [FAQ](../faq.md) - Frequently asked questions

