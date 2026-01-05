package org.trustweave.examples.eo

import org.trustweave.trust.TrustWeave
import org.trustweave.trust.types.VerificationResult
import org.trustweave.trust.dsl.credential.DidMethods.KEY
import org.trustweave.credential.model.ProofType
import org.trustweave.testkit.integrity.IntegrityVerifier
import org.trustweave.testkit.integrity.TestDataBuilders
import org.trustweave.testkit.kms.InMemoryKeyManagementService
import org.trustweave.core.util.DigestUtils
import org.trustweave.did.identifiers.extractKeyId
import org.trustweave.testkit.getOrFail
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import kotlinx.datetime.Clock
import org.trustweave.did.identifiers.Did
import org.trustweave.credential.model.vc.VerifiableCredential
import org.trustweave.testkit.integrity.models.Link
import org.trustweave.anchor.AnchorRef

/**
 * Extension functions for improved API ergonomics
 */

/**
 * Get the first key ID from a DID document.
 * Simplifies the common pattern of resolving a DID and extracting its key ID.
 */
suspend fun TrustWeave.getKeyId(did: Did): String {
    val resolutionResult = resolveDid(did)
    val document = when (resolutionResult) {
        is org.trustweave.did.resolver.DidResolutionResult.Success -> resolutionResult.document
        else -> throw IllegalStateException("Failed to resolve DID: ${did.value}")
    }
    return document.verificationMethod.firstOrNull()?.extractKeyId()
        ?: throw IllegalStateException("No verification method found for DID: ${did.value}")
}

/**
 * Compute credential digest excluding metadata fields.
 * This is the digest that should be anchored to the blockchain.
 */
fun computeCredentialDigest(
    credential: VerifiableCredential,
    linksetDigest: String? = null
): String {
    val credentialJson = Json { ignoreUnknownKeys = true }
        .encodeToJsonElement(credential)
    val vcWithoutMetadata = buildJsonObject {
        credentialJson.jsonObject.entries.forEach { (key, value) ->
            if (key !in listOf("digestMultibase", "evidence", "credentialStatus")) {
                put(key, value)
            }
        }
        linksetDigest?.let { put("linksetDigest", it) }
    }
    return DigestUtils.sha256DigestMultibase(vcWithoutMetadata)
}

/**
 * Create a linkset with automatically computed digest.
 */
fun createLinksetWithDigest(
    links: List<Link>,
    linksetId: String
): Pair<JsonObject, String> {
    val linksetWithoutDigest = buildJsonObject {
        put("id", linksetId)
        put("@context", "https://www.w3.org/ns/json-ld#")
        put("links", Json.encodeToJsonElement(links))
    }
    val digest = DigestUtils.sha256DigestMultibase(linksetWithoutDigest)
    val linksetWithDigest = TestDataBuilders.buildLinkset(digest, links, linksetId)
    return linksetWithDigest to digest
}

/**
 * Anchor a credential digest to blockchain with simplified API.
 */
suspend fun TrustWeave.anchorCredentialDigest(
    credential: VerifiableCredential,
    vcDigest: String,
    linksetDigest: String,
    chainId: String
): AnchorRef {
    val payload = buildJsonObject {
        put("vcId", credential.id?.value ?: "")
        put("vcDigest", vcDigest)
        put("digestMultibase", vcDigest)
        put("issuer", credential.issuer.id.value)
        put("linksetDigest", linksetDigest)
        put("timestamp", Clock.System.now().toString())
    }
    return blockchains.anchor(
        data = payload,
        serializer = JsonElement.serializer(),
        chainId = chainId
    ).ref
}

/**
 * Verify integrity chain with simplified API using TrustWeave's registry.
 */
suspend fun TrustWeave.verifyIntegrityChain(
    credential: VerifiableCredential,
    credentialDigest: String,
    linkset: JsonObject,
    linksetDigest: String,
    artifacts: Map<String, JsonObject>,
    anchorRef: AnchorRef
): Boolean {
    val vcWithDigest = buildJsonObject {
        val credentialJson = Json { ignoreUnknownKeys = true }
            .encodeToJsonElement(credential)
        credentialJson.jsonObject.entries.forEach { put(it.key, it.value) }
        put("linksetDigest", linksetDigest)
        put("digestMultibase", credentialDigest)
    }
    val result = IntegrityVerifier.verifyIntegrityChain(
        vc = vcWithDigest,
        linkset = linkset,
        artifacts = artifacts,
        anchorRef = anchorRef,
        registry = this@verifyIntegrityChain.configuration.blockchainRegistry
    )
    return result.valid
}

/**
 * Earth Observation (EO) Data Integrity Example - Simplified
 *
 * This example demonstrates a comprehensive EO data integrity workflow using TrustWeave:
 * 1. Setup TrustWeave with blockchain anchoring (SPI auto-discovery)
 * 2. Create DID for data provider
 * 3. Generate metadata, provenance, and quality report artifacts
 * 4. Create Linkset connecting all artifacts
 * 5. Issue Verifiable Credential referencing the Linkset
 * 6. Anchor VC digest to blockchain
 * 7. Read back anchored data
 * 8. Verify the complete integrity chain
 * 9. Verify the Verifiable Credential
 *
 * This scenario demonstrates:
 * - SPI-based auto-discovery of KMS, DID methods, and blockchain anchors
 * - DID creation and resolution
 * - Artifact creation with cryptographic digests
 * - Linkset creation for artifact linking
 * - Verifiable Credential issuance
 * - Blockchain anchoring
 * - Integrity chain verification
 *
 * Run: `./gradlew :TrustWeave-examples:runEarthObservation`
 */
fun main() = runBlocking {
    println("=".repeat(70))
    println("Earth Observation - Data Integrity Scenario")
    println("=".repeat(70))
    println()

    // Step 1: Setup TrustWeave (SPI auto-discovers everything)
    println("Step 1: Setting up TrustWeave...")
    val chainId = "algorand:testnet"
    val kms = InMemoryKeyManagementService()
    
    val trustweave = TrustWeave.build {
        keys { custom(kms) }  // ED25519 is default
        did { method(KEY) {} }   // Algorithm defaults to ED25519, auto-discovered via SPI
        anchor { chain(chainId) { inMemory() } }  // Auto-discovered via SPI
        credentials { defaultProofType(ProofType.Ed25519Signature2020) }
    }
    println("✓ TrustWeave configured\n")

    // Step 2: Create DID (simplified API)
    println("Step 2: Creating DID...")
    val issuerDid = trustweave.createDid().getOrFail()
    println("✓ DID created: ${issuerDid.value}\n")

    // Step 3: Create Artifacts
    println("Step 3: Creating artifacts...")
    val (metadataArtifact, metadataDigest) = TestDataBuilders.createMetadataArtifact(
        id = "metadata-1",
        title = "Sentinel-2 L2A Dataset - Central Europe",
        description = "Atmospherically corrected Sentinel-2 Level 2A product"
    )
    val (provenanceArtifact, provenanceDigest) = TestDataBuilders.createProvenanceArtifact(
        id = "provenance-1",
        activity = "EO Data Collection",
        agent = issuerDid
    )
    val qualityMetrics = mapOf(
        "completeness" to 0.98,
        "accuracy" to 0.92,
        "temporalConsistency" to 0.96,
        "spatialAccuracy" to 0.94,
        "cloudCoverage" to 0.05
    )
    val (qualityArtifact, qualityDigest) = TestDataBuilders.createQualityReportArtifact(
        id = "quality-1",
        qualityScore = 0.95,
        metrics = qualityMetrics
    )
    println("✓ Created 3 artifacts (Metadata, Provenance, Quality)\n")

    // Step 4: Create Linkset (improved API)
    println("Step 4: Creating Linkset...")
    val links = listOf(
        TestDataBuilders.buildLink("metadata-1", metadataDigest, "Metadata"),
        TestDataBuilders.buildLink("provenance-1", provenanceDigest, "Provenance"),
        TestDataBuilders.buildLink("quality-1", qualityDigest, "QualityReport")
    )
    val (linksetWithDigest, linksetDigest) = createLinksetWithDigest(
        links = links,
        linksetId = "linkset-eo-sentinel2-l2a-xyz"
    )
    println("✓ Linkset created: $linksetDigest\n")

    // Step 5: Issue Verifiable Credential
    println("Step 5: Issuing credential...")
    val credential = trustweave.issue {
        credential {
            type("EarthObservationCredential")
            issuer(issuerDid)
            subject {
                id("https://example.com/datasets/eo-dataset-sentinel2-l2a-xyz")
                "type" to "EarthObservationDataset"
                "title" to "Sentinel-2 L2A Dataset - Central Europe"
                "spatialCoverage" {
                    "type" to "BoundingBox"
                    "coordinates" to listOf(listOf(10.0, 45.0), listOf(11.0, 46.0))
                }
                "temporalCoverage" {
                    "start" to "2024-01-15T10:00:00Z"
                    "end" to "2024-01-15T10:20:00Z"
                }
                "linksetDigest" to linksetDigest
                "datasetProvider" to issuerDid.value
            }
            issued(Clock.System.now())
        }
        signedBy(issuerDid)
    }.getOrFail()
    println("✓ Credential issued: ${credential.id?.value}\n")

    // Step 6: Verify credential
    println("Step 6: Verifying credential...")
    val isValid = (trustweave.verify { credential(credential) } is VerificationResult.Valid)
    println("✓ Credential verification: ${if (isValid) "VALID" else "INVALID"}\n")

    // Step 7: Anchor VC digest to blockchain (improved API)
    println("Step 7: Anchoring to blockchain...")
    val vcDigest = computeCredentialDigest(credential, linksetDigest)
    val anchorRef = trustweave.anchorCredentialDigest(
        credential = credential,
        vcDigest = vcDigest,
        linksetDigest = linksetDigest,
        chainId = chainId
    )
    println("✓ Anchored to blockchain: ${anchorRef.txHash}\n")

    // Step 8: Read back and verify anchored data
    println("Step 8: Reading anchored data...")
    val readJson = trustweave.blockchains.read<JsonElement>(anchorRef, JsonElement.serializer())
    val readVcDigest = readJson.jsonObject["vcDigest"]?.jsonPrimitive?.content
    require(readVcDigest == vcDigest) { "VC digest mismatch" }
    println("✓ Data integrity verified\n")

    // Step 9: Verify integrity chain (improved API)
    println("Step 9: Verifying integrity chain...")
    val artifacts = mapOf(
        "metadata-1" to metadataArtifact,
        "provenance-1" to provenanceArtifact,
        "quality-1" to qualityArtifact
    )
    val integrityValid = trustweave.verifyIntegrityChain(
        credential = credential,
        credentialDigest = vcDigest,
        linkset = linksetWithDigest,
        linksetDigest = linksetDigest,
        artifacts = artifacts,
        anchorRef = anchorRef
    )
    require(integrityValid) { "Integrity chain verification failed" }
    println("✓ Integrity chain verified\n")

    // Summary
    println("=".repeat(70))
    println("✅ Scenario Complete")
    println("=".repeat(70))
    println("DID: ${issuerDid.value}")
    println("Credential: ${credential.id?.value}")
    println("Anchored: ${anchorRef.txHash}")
    println("Integrity: ${if (integrityValid) "VALID" else "INVALID"}")
    println("=".repeat(70))
}
