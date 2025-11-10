package io.geoknoesis.vericore.examples.eo

import io.geoknoesis.vericore.anchor.AnchorRef
import io.geoknoesis.vericore.anchor.AnchorResult
import io.geoknoesis.vericore.anchor.BlockchainAnchorClient
import io.geoknoesis.vericore.anchor.DefaultBlockchainAnchorRegistry
import io.geoknoesis.vericore.did.DefaultDidMethodRegistry
import io.geoknoesis.vericore.did.DidMethod
import io.geoknoesis.vericore.json.DigestUtils
import io.geoknoesis.vericore.testkit.anchor.InMemoryBlockchainAnchorClient
import io.geoknoesis.vericore.testkit.did.DidKeyMockMethod
import io.geoknoesis.vericore.testkit.integrity.IntegrityVerifier
import io.geoknoesis.vericore.testkit.integrity.TestDataBuilders
import io.geoknoesis.vericore.testkit.kms.InMemoryKeyManagementService
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*

/**
 * Earth Observation (EO) Data Integrity Example
 * 
 * This example demonstrates how to:
 * 1. Create a DID for a data provider
 * 2. Generate metadata, provenance, and quality reports
 * 3. Create a Linkset connecting all artifacts
 * 4. Issue a Verifiable Credential referencing the Linkset
 * 5. Anchor the VC digest to a blockchain
 * 6. Verify the complete integrity chain
 */
fun main() = runBlocking {
    println("=== Earth Observation Data Integrity Workflow ===\n")
    
    // ============================================================
    // Step 1: Setup Services
    // ============================================================
    println("Step 1: Setting up services...")
    
    // Key Management Service (KMS): Manages cryptographic keys
    // Think of it as a secure keychain for your application
    val kms = InMemoryKeyManagementService()
    
    // DID Method: Defines how to create and resolve DIDs
    // We're using did:key, which is simple and self-contained
    // (no external registration needed - perfect for testing)
    val didMethod = DidKeyMockMethod(kms)
    
    // Blockchain Client: Handles anchoring data to blockchains
    // We're using an in-memory client for testing (no real blockchain needed)
    // In production, you'd use AlgorandBlockchainAnchorClient for real anchoring
    val chainId = "algorand:testnet"
    val anchorClient = InMemoryBlockchainAnchorClient(chainId)
    
    // Register services in scoped registries so VeriCore can find them
    val didRegistry = DefaultDidMethodRegistry().apply { register(didMethod) }
    val blockchainRegistry = DefaultBlockchainAnchorRegistry().apply { register(chainId, anchorClient) }
    
    println("✓ Services configured")
    println("  - Key Management: In-memory")
    println("  - DID Method: did:key")
    println("  - Blockchain: In-memory (testnet)\n")
    
    // ============================================================
    // Step 2: Create DID for Data Provider
    // ============================================================
    println("Step 2: Creating DID for data provider...")
    
    // A DID (Decentralized Identifier) is like a digital identity card
    // It uniquely identifies the data provider without relying on a central authority
    // Example: did:key:zABC123... (self-contained, no external registration)
    
    // Create a DID using Ed25519 algorithm (modern, efficient cryptographic algorithm)
    val issuerDoc = didMethod.createDid()
    val issuerDid = issuerDoc.id
    
    // The DID Document contains:
    // - The DID itself (unique identifier)
    // - Verification methods (public keys for signing/verification)
    // - Authentication capabilities
    
    println("✓ Created issuer DID: $issuerDid")
    println("  Verification Methods: ${issuerDoc.verificationMethod.size}")
    println("  This DID represents the data provider's identity\n")
    
    // ============================================================
    // Step 3: Create Artifacts (Metadata, Provenance, Quality)
    // ============================================================
    println("Step 3: Creating EO dataset artifacts...")
    
    // Artifacts are the actual data documents that describe the EO dataset.
    // Each artifact gets a cryptographic digest (hash) that acts as a fingerprint.
    // If the artifact changes, the digest changes - making tampering detectable.
    
    // 1. Metadata Artifact: Describes WHAT the data is
    //    - Title, description, spatial/temporal coverage
    //    - Follows ISO 19115 / DCAT standards for geospatial metadata
    val (metadataArtifact, metadataDigest) = TestDataBuilders.createMetadataArtifact(
        id = "metadata-1",
        title = "Sentinel-2 L2A Dataset",
        description = "Atmospherically corrected Sentinel-2 Level 2A product covering area XYZ"
    )
    // metadataDigest is a hash like "uABC123..." - a unique fingerprint of the metadata
    
    // 2. Provenance Artifact: Describes WHERE the data came from
    //    - Who collected it, when, how
    //    - Follows PROV (Provenance) standard
    val (provenanceArtifact, provenanceDigest) = TestDataBuilders.createProvenanceArtifact(
        id = "provenance-1",
        activity = "EO Data Collection",
        agent = issuerDid  // Links back to the DID we created in Step 2
    )
    // provenanceDigest is a hash of the provenance information
    
    // 3. Quality Report Artifact: Describes HOW GOOD the data is
    //    - Quality scores, metrics, assessments
    //    - Follows DQV (Data Quality Vocabulary) standard
    val (qualityArtifact, qualityDigest) = TestDataBuilders.createQualityReportArtifact(
        id = "quality-1",
        qualityScore = 0.95,  // 95% quality score
        metrics = mapOf(
            "completeness" to 0.98,      // 98% complete
            "accuracy" to 0.92,           // 92% accurate
            "temporalConsistency" to 0.96 // 96% temporally consistent
        )
    )
    // qualityDigest is a hash of the quality information
    
    println("✓ Created artifacts:")
    println("  - Metadata: $metadataDigest")
    println("  - Provenance: $provenanceDigest")
    println("  - Quality Report: $qualityDigest")
    println("  Each artifact has a unique digest (fingerprint) that proves its integrity\n")
    
    // ============================================================
    // Step 4: Create Linkset
    // ============================================================
    println("Step 4: Creating Linkset...")
    
    // A Linkset is like a table of contents that links all artifacts together.
    // It contains references (links) to each artifact along with its digest.
    // This allows us to verify that all artifacts are present and untampered.
    
    // Create links to each artifact
    // Each link contains:
    // - href: Reference to the artifact (like a filename or URL)
    // - digestMultibase: The digest (fingerprint) of the artifact
    // - type: What kind of artifact it is
    val links = listOf(
        TestDataBuilders.buildLink(
            href = "metadata-1",
            digestMultibase = metadataDigest,  // Reference to metadata digest from Step 3
            type = "Metadata"
        ),
        TestDataBuilders.buildLink(
            href = "provenance-1",
            digestMultibase = provenanceDigest,  // Reference to provenance digest
            type = "Provenance"
        ),
        TestDataBuilders.buildLink(
            href = "quality-1",
            digestMultibase = qualityDigest,  // Reference to quality digest
            type = "QualityReport"
        )
    )
    
    // IMPORTANT: We compute the Linkset digest BEFORE adding it to the Linkset
    // This avoids a circular dependency (digest depends on Linkset, but Linkset contains digest)
    // We build the Linkset without the digest field, compute the digest, then add it
    val linksetWithoutDigest = buildJsonObject {
        put("@context", "https://www.w3.org/ns/json-ld#")  // JSON-LD context
        put("links", Json.encodeToJsonElement(links))
    }
    
    // Compute the digest of the Linkset (this becomes the Linkset's fingerprint)
    val linksetDigest = DigestUtils.sha256DigestMultibase(linksetWithoutDigest)
    
    // Now build the complete Linkset WITH the digest
    val linksetWithDigest = TestDataBuilders.buildLinkset(
        digestMultibase = linksetDigest,
        links = links
    )
    
    println("✓ Created Linkset:")
    println("  - Digest: $linksetDigest")
    println("  - Links: ${links.size}")
    println("  The Linkset connects all artifacts and has its own digest\n")
    
    // ============================================================
    // Step 5: Create Verifiable Credential
    // ============================================================
    println("Step 5: Creating Verifiable Credential...")
    
    // A Verifiable Credential (VC) is like a digital certificate that attests to something.
    // In our case, it attests that:
    // - A specific EO dataset exists
    // - It has associated metadata, provenance, and quality reports
    // - These are linked together via the Linkset
    
    // The "subject" is what the credential is about - our EO dataset
    val subject = buildJsonObject {
        put("id", "eo-dataset-sentinel2-l2a-xyz")
        put("type", "EarthObservationDataset")
        put("title", "Sentinel-2 L2A Dataset")
        put("spatialCoverage", buildJsonObject {
            put("type", "BoundingBox")
            put("coordinates", buildJsonArray {
                add(buildJsonArray { add(10.0); add(45.0) }) // Southwest corner (lon, lat)
                add(buildJsonArray { add(11.0); add(46.0) }) // Northeast corner (lon, lat)
            })
        })
        put("temporalCoverage", buildJsonObject {
            put("start", "2024-01-15T10:00:00Z")
            put("end", "2024-01-15T10:20:00Z")
        })
    }
    
    // IMPORTANT: We compute the VC digest BEFORE adding it to the VC
    // Similar to the Linkset, we avoid circular dependency by computing digest first
    // Use a fixed timestamp for consistent digest computation (in real apps, use actual timestamp)
    val fixedTimestamp = "2024-01-15T10:30:00Z"
    val vcWithoutDigest = buildJsonObject {
        put("id", "vc-eo-sentinel2-l2a-xyz")
        put("type", buildJsonArray { add("VerifiableCredential") })
        put("issuer", issuerDid)  // Who issued this credential (the DID from Step 2)
        put("credentialSubject", subject)  // What it's about (the EO dataset)
        put("linksetDigest", linksetDigest)  // Reference to the Linkset digest from Step 4
        put("issued", fixedTimestamp)  // When it was issued
    }
    
    // Compute the digest of the VC (this becomes the VC's fingerprint)
    val vcDigest = DigestUtils.sha256DigestMultibase(vcWithoutDigest)
    
    // Now build the complete VC WITH the digest
    val vcWithDigest = buildJsonObject {
        put("id", "vc-eo-sentinel2-l2a-xyz")
        put("type", buildJsonArray { add("VerifiableCredential") })
        put("issuer", issuerDid)
        put("credentialSubject", subject)
        put("digestMultibase", vcDigest)  // The VC's own digest
        put("linksetDigest", linksetDigest)  // Reference to Linkset (for verification)
        put("issued", fixedTimestamp)
    }
    
    println("✓ Created Verifiable Credential:")
    println("  - VC ID: vc-eo-sentinel2-l2a-xyz")
    println("  - VC Digest: $vcDigest")
    println("  - Linkset Digest Reference: $linksetDigest")
    println("  The VC attests to the dataset and references the Linkset\n")
    
    // ============================================================
    // Step 6: Anchor VC Digest to Blockchain
    // ============================================================
    println("Step 6: Anchoring VC digest to blockchain...")
    
    // Blockchain anchoring provides tamper-proof, timestamped proof that the VC existed
    // at a specific point in time. We anchor the VC digest (not the full VC) to save space
    // and costs on the blockchain.
    
    // Create a payload containing the essential information
    val digestPayload = buildJsonObject {
        put("vcId", "vc-eo-sentinel2-l2a-xyz")  // Which VC this is
        put("vcDigest", vcDigest)  // The fingerprint of the VC (from Step 5)
        put("issuer", issuerDid)  // Who issued it
        put("timestamp", fixedTimestamp)  // When it was anchored
    }
    
    // Write the payload to the blockchain
    // This creates an immutable record that proves the VC existed at this time
    val anchorResult = anchorClient.writePayload(digestPayload)
    
    // The anchor result contains:
    // - ref: A reference to find this data on the blockchain
    // - payload: What was stored
    // - timestamp: When it was stored
    
    println("✓ Anchored to blockchain:")
    println("  - Chain ID: ${anchorResult.ref.chainId}")
    println("  - Transaction Hash: ${anchorResult.ref.txHash}")
    println("  - Timestamp: ${anchorResult.timestamp}")
    println("  The VC digest is now immutably stored on the blockchain\n")
    
    // ============================================================
    // Step 7: Verify Integrity Chain
    // ============================================================
    println("Step 7: Verifying integrity chain...")
    
    // Verification checks the entire integrity chain from bottom to top:
    // 1. Verify each artifact's digest matches its link in the Linkset
    // 2. Verify the Linkset digest matches the reference in the VC
    // 3. Verify the VC digest matches what's stored on the blockchain
    
    // Create a map of artifacts (keyed by their IDs for easy lookup)
    val artifacts = mapOf(
        "metadata-1" to metadataArtifact,
        "provenance-1" to provenanceArtifact,
        "quality-1" to qualityArtifact
    )
    
    // Perform the complete integrity verification
    // This checks:
    // - Blockchain anchor matches VC digest
    // - VC references Linkset correctly
    // - Linkset references artifacts correctly
    // - Each artifact's content matches its digest
    val verificationResult = IntegrityVerifier.verifyIntegrityChain(
        vc = vcWithDigest,
        linkset = linksetWithDigest,
        artifacts = artifacts,
        anchorRef = anchorResult.ref,
        registry = blockchainRegistry
    )
    
    // Display verification results
    if (verificationResult.valid) {
        println("✓ Integrity chain verification PASSED!")
        println("\nVerification Steps:")
        verificationResult.steps.forEachIndexed { index, step ->
            println("  ${index + 1}. ${step.name}: ${if (step.valid) "✓ PASS" else "✗ FAIL"}")
            if (step.digest != null) {
                println("     Digest: ${step.digest}")
            }
        }
        println("\nAll checks passed! The data integrity chain is valid.")
    } else {
        println("✗ Integrity chain verification FAILED!")
        verificationResult.steps.forEach { step ->
            if (!step.valid) {
                println("  ✗ ${step.name}: ${step.error}")
            }
        }
        println("\nOne or more checks failed. The data may have been tampered with.")
    }
    
    println("\n=== Workflow Complete ===")
    println("Summary:")
    println("  - Issuer DID: $issuerDid")
    println("  - VC Digest: $vcDigest")
    println("  - Blockchain Anchor: ${anchorResult.ref.txHash}")
    println("  - Integrity Status: ${if (verificationResult.valid) "VERIFIED" else "FAILED"}")
}

