# Quick Start

Get started with VeriCore in 5 minutes! This guide will walk you through creating your first VeriCore application.

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
    val vericore = VeriCore.create()

    val credentialPayload = buildJsonObject {
        put("id", "urn:vc:quick-start")
        put("issuer", "did:key:alice")
    }

    val digest = DigestUtils.sha256DigestMultibase(credentialPayload)
    println("Digest: $digest")
}
```

## Step 3: Create a DID with typed options

**Why:** You need an issuer DID before issuing credentials.  
**How it works:** `VeriCore.createDid` uses the bundled DID method registry and typed `DidCreationOptions`.  
**How simple:** Configure only what you need using a fluent builder—defaults cover the rest.

```kotlin
import com.geoknoesis.vericore.did.didCreationOptions

val issuerDid = vericore.createDid {
    algorithm = com.geoknoesis.vericore.did.DidCreationOptions.KeyAlgorithm.ED25519
}.getOrThrow().id

println("Issuer DID: $issuerDid")
```

## Step 4: Issue a credential and store it

**Why:** Credential issuance is the heart of most VeriCore solutions.  
**How it works:** The facade orchestrates KMS, proofs, and registries, returning a `Result<VerifiableCredential>`.  
**How simple:** Provide typed issuance options; the API handles proof generation and validation.

```kotlin
import com.geoknoesis.vericore.credential.issueCredentialOptions
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

val credential = vericore.issueCredential(
    issuerDid = issuerDid,
    issuerKeyId = "$issuerDid#key-1",
    credentialSubject = buildJsonObject {
        put("id", "did:key:holder-123")
        put("name", "Alice")
    },
    types = listOf("PersonCredential"),
    options = issueCredentialOptions { validityDays = 365 }
).getOrThrow()

println("Issued credential id: ${credential.id}")
```

## Step 5: Verify and (optionally) anchor

**Why:** Consumers must trust the credential; anchoring provides tamper evidence.  
**How it works:** `verifyCredential` rebuilds proofs and checks revocation; anchoring reuses the digest from Step 2 with the in-memory test anchor.  
**How simple:** One call to verify, one helper to anchor when you need persistence.

```kotlin
import com.geoknoesis.vericore.anchor.BlockchainAnchorRegistry
import com.geoknoesis.vericore.testkit.anchor.InMemoryBlockchainAnchorClient
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

val verification = vericore.verifyCredential(credential).getOrThrow()
println("Credential valid: ${verification.valid}")

val anchorRegistry = BlockchainAnchorRegistry().apply {
    register("inmemory:anchor", InMemoryBlockchainAnchorClient("inmemory:anchor"))
}

val digestPayload = Json.encodeToString(credential)
val anchorClient = anchorRegistry.get("inmemory:anchor")
val anchorResult = anchorClient?.writePayload(Json.parseToJsonElement(digestPayload))
println("Anchored digest tx: ${anchorResult?.ref?.txHash}")
```

## Scenario Playbook

Ready to explore real-world workflows? Each guide below walks through an end-to-end scenario using the same APIs you just touched:

- [Academic Credentials](academic-credentials-scenario.md) – issue diplomas, validate transcripts, and manage revocation.
- [Financial Services (KYC)](financial-services-kyc-scenario.md) – streamline onboarding and reuse credentials across institutions.
- [Government Digital Identity](government-digital-identity-scenario.md) – citizens receive, store, and present official IDs.
- [Healthcare Records](healthcare-medical-records-scenario.md) – share consented medical data across providers with audit trails.
- [IoT Device Identity](iot-device-identity-scenario.md) – provision devices, rotate keys, and secure telemetry feeds.
- [Newsroom Provenance](news-industry-scenario.md) – anchor articles, trace updates, and verify journalistic sources.
- [Proof of Location](proof-of-location-scenario.md) – capture sensor evidence, notarise location, and present proofs.
- [Supply Chain Traceability](supply-chain-traceability-scenario.md) – follow goods from origin to shelf with verifiable checkpoints.
- [Spatial Web Authorization](spatial-web-authorization-scenario.md) – grant AR/VR permissions using verifiable policies.
- [Earth Observation](earth-observation-scenario.md) – manage satellite imagery provenance with digest anchoring.

## What's Next?

- [Your First Application](your-first-application.md) - Build a more complete example
- [Core Concepts](core-concepts/README.md) - Learn the fundamentals
- [Examples](examples/README.md) - Explore more examples

