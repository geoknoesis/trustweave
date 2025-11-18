# Quick Start

Get started with VeriCore in 5 minutes! This guide will walk you through creating your first VeriCore application.

> **Version:** 1.0.0-SNAPSHOT  
> **Kotlin:** 2.2.0+ | **Java:** 21+  
> See [Installation](installation.md) for setup details.

## Complete Runnable Example

Here's a complete, copy-paste ready example that demonstrates the full VeriCore workflow:

```kotlin
package com.example.vericore.quickstart

import com.geoknoesis.vericore.VeriCore
import com.geoknoesis.vericore.anchor.BlockchainAnchorRegistry
import com.geoknoesis.vericore.credential.models.VerifiableCredential
import com.geoknoesis.vericore.json.DigestUtils
import com.geoknoesis.vericore.testkit.anchor.InMemoryBlockchainAnchorClient
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.put

fun main() = runBlocking {
    // Step 1: Create VeriCore instance with defaults
    val vericore = VeriCore.create()

    // Step 2: Compute a digest (demonstrates canonicalization)
    val credentialSubject = buildJsonObject {
        put("id", "did:key:holder-placeholder")
        put("name", "Alice Example")
        put("role", "Site Reliability Engineer")
    }
    val digest = DigestUtils.sha256DigestMultibase(credentialSubject)
    println("Canonical credential-subject digest: $digest")

    // Step 3: Create an issuer DID
    val issuerDocument = vericore.createDid().getOrThrow()
    val issuerDid = issuerDocument.id
    val issuerKeyId = issuerDocument.verificationMethod.firstOrNull()?.id
        ?: error("No verification method generated for $issuerDid")
    println("Issuer DID: $issuerDid (keyId=$issuerKeyId)")

    // Step 4: Issue a verifiable credential
    val credential = vericore.issueCredential(
        issuerDid = issuerDid,
        issuerKeyId = issuerKeyId,
        credentialSubject = credentialSubject,
        types = listOf("VerifiableCredential", "QuickStartCredential")
    ).getOrThrow()
    println("Issued credential id: ${credential.id}")

    // Step 5: Verify the credential
    vericore.verifyCredential(credential).fold(
        onSuccess = { verification ->
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
        },
        onFailure = { error ->
            println("Verification failed: ${error.message}")
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

**Why:** `vericore-all` bundles every public module (core APIs, DID support, KMS, anchoring, DSLs) so you can get going with one line.  
**How it works:** It's a convenience metapackage that re-exports the same artifacts you would otherwise add one-by-one.  
**How simple:** Drop one dependency and you're done.

```kotlin
dependencies {
    implementation("com.geoknoesis.vericore:vericore-all:1.0.0-SNAPSHOT")
    testImplementation("com.geoknoesis.vericore:vericore-testkit:1.0.0-SNAPSHOT")
}
```

**What this does**  
- Pulls in every public VeriCore module (core APIs, DID support, KMS, anchoring, DSLs) with a single coordinate so you never chase transitive dependencies.  
- Adds `vericore-testkit` for the in-memory DID/KMS/wallet implementations used in the tutorials and automated tests.

**Design significance**  
VeriCore promotes a “batteries included” experience for newcomers. The monolithic artifact keeps onboarding simple; when you graduate to production you can swap in individual modules without changing API usage.

## Step 2: Bootstrap VeriCore and compute a digest

**Why:** Most flows start by hashing JSON so signatures and anchors are stable.  
**How it works:** `DigestUtils.sha256DigestMultibase` canonicalises JSON and returns a multibase string.  
**How simple:** One helper call, no manual canonicalisation.

```kotlin
import com.geoknoesis.vericore.VeriCore
import com.geoknoesis.vericore.json.DigestUtils
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

fun main() = runBlocking {
    // Create VeriCore with sensible defaults (in-memory KMS, did:key method)
    val vericore = VeriCore.create()

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
- Instantiates VeriCore with sensible defaults (in-memory registries) suitable for playground and unit tests.  
- Builds a credential payload using Kotlinx Serialization builders so the structure is type-safe.  
- Canonicalises and hashes the payload, returning a multibase-encoded digest you can anchor or sign.

**Result**  
`DigestUtils.sha256DigestMultibase` prints a deterministic digest (for example `u5v...`) that becomes the integrity reference for later steps.

**Design significance**  
Everything in VeriCore assumes deterministic canonicalization, so the very first code sample reinforces the pattern: serialize → canonicalize → hash → sign/anchor. This is the backbone of interoperability.

## Step 3: Create a DID with typed options

**Why:** You need an issuer DID before issuing credentials.  
**How it works:** `VeriCore.createDid` uses the bundled DID method registry and typed `DidCreationOptions`.  
**How simple:** Configure only what you need using a fluent builder—defaults cover the rest.

```kotlin
// Simple: use defaults (did:key method, ED25519 algorithm)
val issuerDocument = vericore.createDid().getOrThrow()
val issuerDid = issuerDocument.id
val issuerKeyId = issuerDocument.verificationMethod.firstOrNull()?.id
    ?: error("No verification method generated")
println("Issuer DID: $issuerDid (keyId=$issuerKeyId)")

// Advanced: customize with builder
val customDid = vericore.createDid("key") {
    algorithm = com.geoknoesis.vericore.did.DidCreationOptions.KeyAlgorithm.ED25519
    purpose(com.geoknoesis.vericore.did.DidCreationOptions.KeyPurpose.AUTHENTICATION)
}.getOrThrow()
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

**Why:** Credential issuance is the heart of most VeriCore solutions.  
**How it works:** The facade orchestrates KMS, proofs, and registries, returning a `Result<VerifiableCredential>`.  
**How simple:** Provide the issuer DID/key and credential subject JSON; the API handles proof generation and validation.

```kotlin
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

// Issue credential using the issuer DID and key ID from Step 3
val credential = vericore.issueCredential(
    issuerDid = issuerDid,
    issuerKeyId = issuerKeyId,  // From issuerDocument.verificationMethod
    credentialSubject = buildJsonObject {
        put("id", "did:key:holder-placeholder")
        put("name", "Alice Example")
        put("role", "Site Reliability Engineer")
    },
    types = listOf("VerifiableCredential", "QuickStartCredential")
).getOrThrow()

println("Issued credential id: ${credential.id}")
```

**What this does**  
- Invokes the credential issuance facade which orchestrates key lookup/generation, proof creation, and credential assembly.  
- Configures the credential subject payload and credential types.  
- Returns a signed `VerifiableCredential` wrapped in `Result`.

**Result**  
The printed ID corresponds to a tamper-evident credential JSON object that you can store, present, or anchor.

**Design significance**  
Facades embrace VeriCore’s “everything returns `Result<T>`” philosophy. By forcing the caller to handle success and failure explicitly, flows stay predictable in production and testable in unit harnesses.

> ✅ **Run the sample**  
> The full quick-start flow lives in `vericore-examples/src/main/kotlin/com/geoknoesis/vericore/examples/quickstart/QuickStartSample.kt`.  
> Execute it locally with `./gradlew :vericore-examples:runQuickStartSample`.

## Step 5: Verify the credential

**Why:** Consumers must trust the credential; verification validates proofs and checks revocation.  
**How it works:** `verifyCredential` rebuilds proofs, resolves issuer DIDs, and performs validity checks.  
**How simple:** One call returns a structured result with validation details.

```kotlin
import com.geoknoesis.vericore.core.*

// Verify credential with full error handling
vericore.verifyCredential(credential).fold(
    onSuccess = { verification ->
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
    },
    onFailure = { error ->
        println("Verification failed: ${error.message}")
    }
)

// Simple usage (throws on error)
val verification = vericore.verifyCredential(credential).getOrThrow()
if (verification.valid) {
    println("Credential is valid")
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
import com.geoknoesis.vericore.anchor.BlockchainAnchorRegistry
import com.geoknoesis.vericore.credential.models.VerifiableCredential
import com.geoknoesis.vericore.testkit.anchor.InMemoryBlockchainAnchorClient
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

VeriCore provides structured error handling with `Result<T>` and `VeriCoreError` types. Understanding when to use different patterns is important for production code.

### When to Use `getOrThrow()`

Use `getOrThrow()` for:
- ✅ Quick start examples and prototypes
- ✅ Simple scripts where you can let errors bubble up
- ✅ Test code where exceptions are acceptable
- ✅ When you want exceptions for errors

```kotlin
// Simple usage (throws on error)
val did = vericore.createDid().getOrThrow()
val credential = vericore.issueCredential(...).getOrThrow()
```

### When to Use `fold()`

Use `fold()` for:
- ✅ Production code
- ✅ When you need to handle specific error types
- ✅ When you want to provide user-friendly error messages
- ✅ When you need to log errors before handling

```kotlin
// Production pattern with specific error handling
val result = vericore.createDid()
result.fold(
    onSuccess = { did -> 
        // Handle success
        processDid(did)
    },
    onFailure = { error ->
        // Handle specific errors
        when (error) {
            is VeriCoreError.DidMethodNotRegistered -> {
                logger.warn("DID method not registered: ${error.method}")
                // Register method and retry, or use fallback
            }
            else -> {
                logger.error("Unexpected error: ${error.message}", error)
                // Handle generic error
            }
        }
    }
)
```

## Handling errors and verification failures

VeriCore provides structured error handling with `Result<T>` and `VeriCoreError` types:

```kotlin
import com.geoknoesis.vericore.core.*

// Verify credential with error handling
vericore.verifyCredential(credential).fold(
    onSuccess = { result ->
        if (result.valid) {
            println("Credential is valid")
        } else {
            println("Credential invalid: ${result.errors.joinToString()}")
            result.warnings.forEach { println("Warning: $it") }
        }
    },
    onFailure = { error ->
        when (error) {
            is VeriCoreError.CredentialInvalid -> {
                println("Credential validation failed: ${error.reason}")
                println("Field: ${error.field}")
            }
            else -> {
                println("Verification error: ${error.message}")
                error.context.forEach { (key, value) ->
                    println("  $key: $value")
                }
            }
        }
    }
)

// Anchoring with error handling
val anchorResult = vericore.anchor(data, serializer, "algorand:testnet")
anchorResult.fold(
    onSuccess = { anchor ->
        println("Anchored at: ${anchor.ref.txHash}")
    },
    onFailure = { error ->
        when (error) {
            is VeriCoreError.ChainNotRegistered -> {
                println("Chain not registered: ${error.chainId}")
                println("Available chains: ${error.availableChains}")
            }
            else -> {
                println("Anchoring error: ${error.message}")
            }
        }
    }
)
```

For simple cases, you can use `getOrThrow()`:

```kotlin
// Simple usage (throws on error)
val result = vericore.verifyCredential(credential).getOrThrow()
if (result.valid) {
    println("Credential is valid")
}

// Or use getOrThrowError for VeriCoreError
val anchor = vericore.anchor(data, serializer, chainId).getOrThrowError()
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

## What's Next?

- [Your First Application](your-first-application.md) - Build a more complete example
- [Common Patterns](common-patterns.md) - Learn common usage patterns
- [Core Concepts](../core-concepts/README.md) - Learn the fundamentals
- [API Reference](../api-reference/core-api.md) - Complete API documentation
- [Troubleshooting](troubleshooting.md) - Common issues and solutions
- [Error Handling Guide](../advanced/error-handling.md) - Detailed error handling patterns

