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
    val vericore = VeriCore.create()

    val credentialPayload = buildJsonObject {
        put("id", "urn:vc:quick-start")
        put("issuer", "did:key:alice")
    }

    val digest = DigestUtils.sha256DigestMultibase(credentialPayload)
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
import com.geoknoesis.vericore.did.didCreationOptions

val issuerDid = vericore.createDid {
    algorithm = com.geoknoesis.vericore.did.DidCreationOptions.KeyAlgorithm.ED25519
}.getOrThrow().id

println("Issuer DID: $issuerDid")
```

**What this does**  
- Calls the facade to provision a DID using the default registry (in this case `did:key`).  
- Supplies typed options instead of raw maps, so the compiler validates algorithm names and properties.  
- Returns the fully materialised DID document and extracts its identifier.

**Result**  
`issuerDid` now holds a resolvable DID such as `did:key:z6M...` that acts as the issuer for credentials.

**Design significance**  
Typed builders (`DidCreationOptions`) are a core design choice: they prevent misconfigured DID creation at compile time and make IDE autocompletion an onboarding tool rather than documentation guesswork.

## Step 4: Issue a credential and store it

**Why:** Credential issuance is the heart of most VeriCore solutions.  
**How it works:** The facade orchestrates KMS, proofs, and registries, returning a `Result<VerifiableCredential>`.  
**How simple:** Provide the issuer DID/key and credential subject JSON; the API handles proof generation and validation.

```kotlin
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
    types = listOf("PersonCredential")
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

## Step 5: Verify and (optionally) anchor

**Why:** Consumers must trust the credential; anchoring provides tamper evidence.  
**How it works:** `verifyCredential` rebuilds proofs and checks revocation; anchoring reuses the digest from Step 2 with the in-memory test anchor.  
**How simple:** One call to verify, one helper to anchor when you need persistence.

```kotlin
import com.geoknoesis.vericore.anchor.BlockchainAnchorRegistry
import com.geoknoesis.vericore.testkit.anchor.InMemoryBlockchainAnchorClient
import kotlinx.serialization.json.Json

val verification = vericore.verifyCredential(credential).getOrThrow()
println("Credential valid: ${verification.valid} (proof=${verification.proofValid})")

val anchorRegistry = BlockchainAnchorRegistry().apply {
    register("inmemory:anchor", InMemoryBlockchainAnchorClient("inmemory:anchor"))
}

val anchorClient = requireNotNull(anchorRegistry.get("inmemory:anchor"))
val payload = Json.encodeToJsonElement(
    com.geoknoesis.vericore.credential.models.VerifiableCredential.serializer(),
    credential
)
val anchorResult = anchorClient.writePayload(payload)
println("Anchored digest tx: ${anchorResult.ref.txHash}")
```

**What this does**  
- Verifies the credential by rebuilding proofs and performing validity checks (`verification.valid`).  
- Registers an in-memory blockchain client and writes the credential JSON to the anchor client, converting it to a `JsonElement` first.  
- Prints the synthetic transaction hash returned by the anchor client (useful for unit assertions).

**Result**  
You get both a verification summary and an `AnchorRef` representing the write operation. In production you’d persist `AnchorRef` for audits and use a real chain adapter.

**Design significance**  
Anchoring is abstracted behind the same interface regardless of provider. The sample sticks to the in-memory implementation, but the code path is identical for Algorand, Polygon, Indy, or future adapters—making environment swaps low risk.

## Handling errors and verification failures

Use `Result.fold` or `runCatching` so you surface validation errors early:

```kotlin
vericore.verifyCredential(credential).fold(
    onSuccess = { result ->
        require(result.valid) { "Credential invalid: ${result.errors}" }
    },
    onFailure = { error ->
        logger.warn("Verification failed", error)
    }
)
```

Anchoring can also fail (network hiccups, missing credentials). Wrap calls in `runCatching` and add retries when targeting public chains. The runnable sample demonstrates both patterns end-to-end.

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

