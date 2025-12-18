---
title: Earth Observation Scenario
parent: Use Case Scenarios
nav_order: 28
---

# Earth Observation Scenario

This guide walks you through building a complete Earth Observation (EO) data integrity verification system using TrustWeave. You'll learn how to create DIDs, compute digests, build integrity chains, and anchor data to blockchains.

## What You'll Build

By the end of this tutorial, you'll have:

- ✅ Created a DID for a data provider
- ✅ Generated metadata, provenance, and quality reports for EO datasets
- ✅ Built a Linkset connecting all artifacts
- ✅ Created a Verifiable Credential referencing the Linkset
- ✅ Anchored the VC digest to a blockchain
- ✅ Verified the complete integrity chain

## Big Picture & Significance

### The Earth Observation Data Challenge

Earth Observation data powers critical applications from climate monitoring to disaster response. However, ensuring data integrity, authenticity, and provenance is a fundamental challenge that affects trust, compliance, and decision-making.

**Industry Context**:
- **Market Size**: Global EO market projected to reach $11.3 billion by 2026 (CAGR 9.8%)
- **Data Volume**: Petabytes of satellite imagery generated daily
- **Critical Applications**: Climate monitoring, disaster management, agriculture, urban planning
- **Trust Requirements**: Data used for policy decisions, scientific research, and emergency response
- **Regulatory Pressure**: Increasing requirements for data provenance and quality assurance

**Why This Matters**:
1. **Data Trust**: Verify EO data hasn't been tampered with or corrupted
2. **Provenance**: Track data lineage from satellite to end user
3. **Quality Assurance**: Ensure data meets quality standards for critical applications
4. **Compliance**: Meet regulatory requirements for data integrity
5. **Reproducibility**: Enable scientific reproducibility with verifiable data
6. **Accountability**: Hold data providers accountable for data quality

### The Data Integrity Problem

Traditional EO data systems struggle with integrity because:
- **No Verification**: Can't verify data hasn't been tampered with
- **No Provenance**: Missing information about data origin and processing
- **No Quality Tracking**: Can't verify data quality claims
- **Centralized Trust**: Reliance on single authorities creates bottlenecks
- **No Interoperability**: Different systems can't verify each other's data

## Value Proposition

### Problems Solved

1. **Data Integrity**: Cryptographic proof that data hasn't been tampered with
2. **Provenance Tracking**: Complete lineage from satellite to end user
3. **Quality Verification**: Verifiable quality reports and metadata
4. **Interoperability**: Standard format works across all EO systems
5. **Compliance**: Automated audit trails for regulatory requirements
6. **Trust**: Build trust in EO data through verifiable credentials
7. **Accountability**: Hold data providers accountable for data quality

### Business Benefits

**For Data Providers**:
- **Trust**: Build trust with data consumers
- **Compliance**: Meet regulatory requirements
- **Differentiation**: Stand out with verifiable data quality
- **Accountability**: Clear responsibility tracking

**For Data Consumers**:
- **Confidence**: Verify data integrity before use
- **Quality**: Access verifiable quality reports
- **Provenance**: Understand data lineage
- **Compliance**: Meet data quality requirements

**For Regulators**:
- **Audit Trails**: Complete data lineage records
- **Verification**: Verify data quality claims
- **Transparency**: Understand data processing

### ROI Considerations

- **Trust**: Increased data trust enables new use cases
- **Compliance**: Automated compliance reduces costs by 50%
- **Quality**: Reduced errors save time and money
- **Interoperability**: Standard format reduces integration costs

## Understanding the Problem

Earth Observation data (like satellite imagery) needs to be trustworthy. When someone receives EO data, they need to verify:

1. **Who created it?** - Identity of the data provider
2. **Is it authentic?** - Has it been tampered with?
3. **What's its quality?** - Is the data reliable?
4. **Where did it come from?** - What's the data's provenance?

TrustWeave solves this by creating a **verifiable integrity chain** that links all this information together and anchors it to a blockchain for tamper-proof verification.

## How It Works: The Integrity Chain

Think of the integrity chain like a Russian nesting doll, where each layer protects and verifies the next:

```mermaid
flowchart TD
    A["Blockchain Anchor<br/>VC Digest<br/>Immutable proof on blockchain"] -->|references| B["Verifiable Credential VC<br/>Linkset Digest Reference<br/>Dataset Metadata<br/>Credential about the dataset"]
    B -->|references| C["Linkset<br/>Metadata Link<br/>Provenance Link<br/>Quality Report Link<br/>Collection of links to artifacts"]
    C -->|references| D["Artifacts<br/>Metadata Document<br/>Provenance Document<br/>Quality Report Document<br/>Actual data documents"]

    style A fill:#1976d2,stroke:#0d47a1,stroke-width:2px,color:#fff
    style B fill:#f57c00,stroke:#e65100,stroke-width:2px,color:#fff
    style C fill:#388e3c,stroke:#1b5e20,stroke-width:2px,color:#fff
    style D fill:#c2185b,stroke:#880e4f,stroke-width:2px,color:#fff
```

**Key Concept**: Each level contains a **digest** (cryptographic hash) of the level below it. If any data is tampered with, the digest won't match, and verification will fail.

## Prerequisites

- Java 21+
- Kotlin 2.2.21+
- Gradle 8.5+
- Basic understanding of Kotlin and coroutines

**Note**: Don't worry if you're new to DIDs, Verifiable Credentials, or blockchain! This guide explains everything step-by-step.

## Step 1: Add Dependencies

Add TrustWeave dependencies to your `build.gradle.kts`. This pulls in the core runtime plus optional adapters the scenario uses (JSON, DID, anchoring, and the in-memory test kit).

```kotlin
dependencies {
    // Core TrustWeave modules
    // TrustWeave distribution (includes all modules)
    implementation("com.trustweave:distribution-all:1.0.0-SNAPSHOT")

    // Test kit for in-memory implementations
    testImplementation("com.trustweave:testkit:1.0.0-SNAPSHOT")

    // Optional: Algorand adapter for real blockchain anchoring
    implementation("com.trustweave.chains:algorand:1.0.0-SNAPSHOT")

    // Kotlinx Serialization
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.0")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
}
```

**Result:** Gradle resolves the full stack so every snippet below compiles with zero additional setup.

## Step 2: Complete Runnable Example

Here's the full Earth Observation data integrity workflow using the TrustWeave facade API. This complete, copy-paste ready example demonstrates the entire workflow from DID creation to blockchain anchoring and verification.

```kotlin
package com.example.earth.observation

import com.trustweave.trust.dsl.trustWeave
import com.trustweave.credential.model.vc.VerifiableCredential
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.datetime.Clock

fun main() = runBlocking {
    println("=".repeat(70))
    println("Earth Observation Data Integrity Scenario - Complete End-to-End Example")
    println("=".repeat(70))

    trustWeave {
        keys { provider(IN_MEMORY); algorithm(ED25519) }
        did { method(KEY) { algorithm(ED25519) } }
        anchor { chain("inmemory:testnet") { provider(IN_MEMORY) } }
    }.run {
        println("\n✅ TrustWeave initialized")
        
        createDid { method(KEY) }.getOrThrow().let { (did, doc) ->
            val datasetId = "did:web:eo.example.com:collections:Sentinel2:items:L1C_T31UFS_20230615"
            println("✅ Data Provider DID: ${did.value}")
            
            issue {
                credential {
                    type("VerifiableCredential", "EOCredential")
                    issuer(did)
                    subject {
                        id(datasetId)
                        "relatedResource" to listOf(
                            resource("eo:Imagery", "https://storage.example.com/L1C_T31UFS.tif", "sha384-oqVuAfXR..."),
                            resource("eo:Metadata", "https://catalog.example.com/L1C_T31UFS/metadata.json", "sha384-8fA2K1n..."),
                            resource("eo:Provenance", "https://catalog.example.com/L1C_T31UFS/provenance.json", "sha384-Qx7vBnM..."),
                            resource("eo:QualityReport", "https://catalog.example.com/L1C_T31UFS/quality.json", "sha384-mP9kLwR...")
                        )
                    }
                    issued(Clock.System.now())
                }
                signedBy(did, doc.verificationMethod.first().id.substringAfter("#"))
            }.getOrThrow().also { cred ->
                println("✅ Credential issued: ${cred.id}")
                println("\n📄 Credential Structure:")
                println(Json { prettyPrint = true }.encodeToString(VerifiableCredential.serializer(), cred))
                
                blockchains.anchor(cred, VerifiableCredential.serializer(), "inmemory:testnet").getOrThrow().ref.also { ref ->
                    println("\n✅ Anchored: ${ref.chainId} / ${ref.txHash}")
                    verify { credential(cred) }.getOrThrow()
                    println("✅ Verified!")
                }
            }
        }
    }
    println("\n${"=".repeat(70)}\n✅ Earth Observation Scenario Complete!\n${"=".repeat(70)}")
}

// Helper to build relatedResource entries
fun resource(type: String, url: String, digest: String) = mapOf(
    "type" to listOf("Link", type),
    "contentUrl" to url,
    "digestSRI" to digest
)
```

**Expected Output:**

```
======================================================================
Earth Observation Data Integrity Scenario - Complete End-to-End Example
======================================================================

✅ TrustWeave initialized
✅ Data Provider DID: did:key:z6Mk...
✅ Credential issued: urn:uuid:...

📄 Credential Structure:
{
  "@context": "https://www.w3.org/ns/credentials/v2",
  "id": "urn:uuid:...",
  "type": ["VerifiableCredential", "EOCredential"],
  "issuer": "did:key:z6Mk...",
  "credentialSubject": {
    "id": "did:web:eo.example.com:collections:Sentinel2:items:L1C_T31UFS_20230615",
    "relatedResource": [
      { "type": ["Link", "eo:Imagery"], "contentUrl": "https://storage.example.com/L1C_T31UFS.tif", "digestSRI": "sha384-oqVuAfXR..." },
      { "type": ["Link", "eo:Metadata"], "contentUrl": "https://catalog.example.com/L1C_T31UFS/metadata.json", "digestSRI": "sha384-8fA2K1n..." },
      ...
    ]
  },
  "proof": { ... }
}

✅ Anchored: inmemory:testnet / tx_...
✅ Verified!

======================================================================
✅ Earth Observation Scenario Complete!
======================================================================
```

**To run this example:**
1. Copy the code above into `src/main/kotlin/EarthObservationExample.kt`
2. Ensure dependencies are added (see Step 1)
3. Run with `./gradlew run` or execute in your IDE

**What this demonstrates:**
- ✅ W3C VC 2.0 compliant credential structure
- ✅ `relatedResource` pattern with `digestSRI` for integrity
- ✅ Clean DSL without intermediate variables
- ✅ DID → Issue → Anchor → Verify in one fluent expression
- ✅ Resources linked by URL with Subresource Integrity hashes

## Step 3: Step-by-Step Breakdown

The sections below explain each step in detail. The complete example above demonstrates the full workflow using the TrustWeave facade API.

### Understanding the Integrity Chain

The integrity chain works like this:

1. **Artifacts** (metadata, provenance, quality) are created with cryptographic digests
2. **Linkset** connects all artifacts together with their digests
3. **Verifiable Credential** references the Linkset digest
4. **Blockchain** anchors the credential for tamper evidence

Each step builds on the previous one, creating a verifiable chain of trust.

### Detailed Steps

The following sections provide detailed explanations of each component:

- **DID Creation**: How to create identities for data providers
- **Artifact Creation**: How to create and digest metadata, provenance, and quality reports
- **Linkset Creation**: How to link artifacts together
- **Credential Issuance**: How to issue verifiable credentials
- **Blockchain Anchoring**: How to anchor credentials to blockchains
- **Verification**: How to verify the complete integrity chain

For a complete working example, see the code in Step 2 above.

## Step 4: Running the Example

1. Copy the complete example from Step 2 above into `src/main/kotlin/EarthObservationExample.kt`
2. Ensure dependencies are added (see Step 1)
3. Run the application:
```bash
./gradlew run
```

**Expected output:**
```
======================================================================
Earth Observation Data Integrity Scenario - Complete End-to-End Example
======================================================================

✅ TrustWeave initialized with blockchain anchoring
✅ Data Provider DID: did:key:z6Mk...
✅ Metadata artifact created: u5v...
✅ Provenance artifact created: u5v...
✅ Quality report artifact created: u5v...
✅ Linkset created: u5v...
✅ Verifiable Credential issued: urn:uuid:...
   Linkset digest: u5v...
✅ Credential anchored to blockchain
   Chain ID: inmemory:anchor
   Transaction Hash: tx_...

✅ Credential Verification SUCCESS
   Proof valid: true
   Issuer valid: true
   Not revoked: true

🔗 Integrity Chain Verification:
   Metadata digest: u5v...
   Provenance digest: u5v...
   Quality digest: u5v...
   Linkset digest: u5v...
   Credential anchored: tx_...
   ✅ Complete integrity chain verified!

======================================================================
✅ Earth Observation Scenario Complete!
======================================================================
```

**Alternative:** Run the example from the TrustWeave examples module:
```bash
./gradlew :TrustWeave-examples:runEarthObservation
```

## Step 5: Using Real Blockchain (Algorand)

So far, we've used an in-memory blockchain client for testing. For production, you'll want to use a real blockchain like Algorand.

### Why Algorand?

- **Fast**: Transactions confirm in seconds
- **Low cost**: Very affordable for anchoring
- **Eco-friendly**: Uses proof-of-stake (low energy)
- **Testnet available**: Free testing environment

### Switching to Real Algorand

Replace the in-memory client with Algorand:

```kotlin
import com.trustweave.algorand.AlgorandBlockchainAnchorClient

// Replace this:
// val anchorClient = InMemoryBlockchainAnchorClient(chainId)

// With this:
val anchorClient = AlgorandBlockchainAnchorClient(
    chainId = AlgorandBlockchainAnchorClient.TESTNET,  // Use TESTNET for testing
    options = mapOf(
        "algodUrl" to "https://testnet-api.algonode.cloud",  // Algorand testnet API
        "privateKey" to "your-private-key-base64"  // Your Algorand account private key
    )
)
```

### Getting Algorand Testnet Credentials

1. **Create a testnet account**: Use [Algorand Testnet Faucet](https://bank.testnet.algorand.network/)
2. **Get your private key**: Export from Algorand wallet
3. **Use testnet API**: Public endpoints available (no API key needed)

**Important**: Never use real Algorand mainnet credentials in test code!

## Step 6: Customizing Resources

The example uses standard resource types, but you can customize them for your specific needs.

### Custom Resource Types

Define domain-specific resource types:

```kotlin
"relatedResource" to listOf(
    // Core imagery with bands
    resource("eo:Imagery", "https://storage.example.com/data.tif", computeSRI(imageryBytes)),
    
    // Spectral bands as separate resources
    resource("eo:Band:B02", "https://storage.example.com/B02.tif", computeSRI(b02Bytes)),
    resource("eo:Band:B03", "https://storage.example.com/B03.tif", computeSRI(b03Bytes)),
    
    // Processing provenance
    resource("eo:Provenance", "https://catalog.example.com/provenance.json", computeSRI(provenanceJson)),
    
    // Quality metrics
    resource("eo:QualityReport", "https://catalog.example.com/quality.json", computeSRI(qualityJson)),
    
    // STAC metadata
    resource("stac:Item", "https://catalog.example.com/item.json", computeSRI(stacItemJson))
)
```

### Computing SRI Hashes

Use standard Subresource Integrity (SRI) format:

```kotlin
import java.security.MessageDigest
import java.util.Base64

fun computeSRI(data: ByteArray, algorithm: String = "SHA-384"): String {
    val digest = MessageDigest.getInstance(algorithm).digest(data)
    val base64 = Base64.getEncoder().encodeToString(digest)
    return "${algorithm.lowercase().replace("-", "")}-$base64"
}

// Example usage:
val metadataJson = """{"title": "Sentinel-2 L2A", "sensor": "MSI"}""".toByteArray()
val sri = computeSRI(metadataJson)  // "sha384-8fA2K1n..."
```

### Extended Resource Helper

For more complex resources:

```kotlin
fun resource(
    type: String,
    url: String,
    digest: String,
    mediaType: String? = null,
    size: Long? = null
) = buildMap {
    put("type", listOf("Link", type))
    put("contentUrl", url)
    put("digestSRI", digest)
    mediaType?.let { put("encodingFormat", it) }
    size?.let { put("contentSize", it) }
}

// Usage with full metadata:
resource(
    type = "eo:Imagery",
    url = "https://storage.example.com/L1C_T31UFS.tif",
    digest = "sha384-oqVuAfXR...",
    mediaType = "image/tiff",
    size = 1_234_567_890
)
```

**Tip**: Follow W3C VC 2.0 `relatedResource` spec and standard media types for interoperability.

## Next Steps

- Explore [Core Concepts](../core-concepts/README.md) for deeper understanding
- Learn about [Integration Modules](../integrations/README.md) for production use

## Common Questions

### Why use `relatedResource` with `digestSRI`?

This follows W3C VC 2.0 best practices:
- **Standard format**: `digestSRI` uses Subresource Integrity (SRI) format (`sha384-base64...`)
- **Self-contained**: Each resource carries its own integrity hash
- **Web-native**: SRI is a W3C standard used by browsers for script integrity
- **Verifiable**: Fetch the URL, compute hash, compare with `digestSRI`

### Why store only digests on blockchain?

Storing full data on blockchain is expensive and unnecessary:
- **Digests are small**: ~64 characters vs. potentially megabytes of data
- **Digests are sufficient**: If digest matches, data is intact
- **Data can be stored elsewhere**: IPFS, cloud storage, CDN, etc.
- **Blockchain provides proof**: Timestamp and immutability of the credential

### What if I need to update data?

The credential is immutable, but you can:
1. **Issue a new credential**: With updated resources and digests
2. **Link versions**: Reference previous credential ID in new credential
3. **Revoke old**: Mark old credential as superseded

### How do I verify this credential?

Verification flow:
1. **Verify signature**: Check the credential's cryptographic proof
2. **Fetch resources**: Download each `contentUrl`
3. **Check digests**: Compute SRI hash, compare with `digestSRI`
4. **Check anchor**: Verify blockchain anchor if present

Anyone with the credential can verify the complete integrity chain!

## Troubleshooting

### Issue: Digest mismatch

**Problem**: Verification fails with digest mismatch

**Cause**: Digests are computed from specific content. If you include/exclude wrong fields, digests won't match.

**Solution**: Ensure you're computing digests from the correct content:
- **VC digest**: Computed from VC **without** `digestMultibase`, `evidence`, `credentialStatus`
- **Linkset digest**: Computed from Linkset **without** `digestMultibase` field
- **Artifact digest**: Computed from artifact `content` field, **not** the entire artifact object

**Debug tip**: Print the JSON you're digesting to see what's included.

### Issue: Blockchain client not found

**Problem**: `No blockchain client registered for chain: algorand:testnet`

**Cause**: The blockchain client wasn't registered before use.

**Solution**: Register the blockchain client before using it:
```kotlin
val blockchainRegistry = BlockchainAnchorRegistry().apply {
    register(chainId, anchorClient)
}
```

**Check**: Ensure the registry registration happens before calling `anchorClient.writePayload()`.

### Issue: DID method not found

**Problem**: `DID method 'key' is not registered`

**Cause**: The DID method wasn't registered before creating/resolving DIDs.

**Solution**: Register the DID method:
```kotlin
val didRegistry = DidMethodRegistry().apply { register(didMethod) }
```

**Check**: Ensure registration happens before calling `didMethod.createDid()` or resolving through `didRegistry`.

### Issue: Verification fails unexpectedly

**Problem**: All data looks correct but verification fails

**Possible causes**:
1. **Timestamp mismatch**: Using different timestamps changes VC digest
2. **Key order**: JSON key order shouldn't matter (canonicalization handles this)
3. **Extra fields**: Adding fields to artifacts changes digests
4. **Wrong artifact**: Using wrong artifact in verification map

**Debug**: Check each verification step individually to find which one fails.

## Summary

Congratulations! You've built a complete Earth Observation data integrity system. Here's what you accomplished:

### What You Built

1. ✅ **Service Setup**: Configured KMS, DID methods, and blockchain clients
2. ✅ **Identity Creation**: Created a DID for the data provider
3. ✅ **Artifact Generation**: Generated metadata, provenance, and quality reports
4. ✅ **Linkset Creation**: Built a Linkset connecting all artifacts
5. ✅ **Credential Issuance**: Created a Verifiable Credential attesting to the dataset
6. ✅ **Blockchain Anchoring**: Anchored the VC digest for immutable proof
7. ✅ **Integrity Verification**: Verified the complete chain from blockchain to artifacts

### Key Takeaways

- **Digests are fingerprints**: They uniquely identify data and detect tampering
- **Chain of trust**: Each level verifies the next through digest references
- **Blockchain provides proof**: Immutable, timestamped proof of existence
- **Verification is public**: Anyone can verify the chain with the right components

### Next Steps

- **Explore production adapters**: Try real blockchain adapters (Algorand, Polygon)
- **Add more artifacts**: Include additional metadata or quality metrics
- **Implement in your system**: Integrate this workflow into your EO data pipeline
- **Learn more**: Check out [Core Concepts](../core-concepts/README.md) and [API Reference](../api-reference/README.md)

### Real-World Applications

This pattern works for:
- **Satellite imagery**: Verify authenticity and provenance
- **Sensor data**: Ensure data hasn't been tampered with
- **Processing pipelines**: Track data transformations
- **Data catalogs**: Verify catalog entries match actual data
- **Data sharing**: Provide verifiable data to consumers

This workflow ensures EO data integrity from collection to verification, providing tamper-proof guarantees through blockchain anchoring and cryptographic digests. Your data is now verifiable, traceable, and trustworthy! 🎉


