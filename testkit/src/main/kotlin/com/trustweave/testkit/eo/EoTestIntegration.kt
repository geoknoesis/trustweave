package com.trustweave.testkit.eo

import com.trustweave.anchor.AnchorResult
import com.trustweave.anchor.BlockchainAnchorClient
import com.trustweave.anchor.BlockchainAnchorRegistry
import com.trustweave.core.util.DigestUtils
import com.trustweave.testkit.integrity.IntegrityVerifier
import com.trustweave.testkit.integrity.IntegrityVerificationResult
import com.trustweave.testkit.integrity.TestDataBuilders
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*

/**
 * Earth Observation (EO) test integration utility.
 * 
 * Provides reusable test scenarios for EO data integrity verification workflows.
 * Works with any blockchain adapter via TestContainers or in-memory implementations.
 * 
 * Usage example:
 * ```kotlin
 * val scenario = EoTestIntegration.createScenario(
 *     issuerDid = issuerDoc.id,
 *     anchorClient = anchorClient,
 *     chainId = chainId
 * )
 * 
 * val result = scenario.execute()
 * assertTrue(result.verificationResult.valid)
 * ```
 */
object EoTestIntegration {

    /**
     * Creates a complete EO test scenario with all artifacts, linkset, VC, and anchoring.
     * 
     * @param issuerDid The DID of the issuer
     * @param anchorClient The blockchain anchor client to use
     * @param chainId The chain ID for anchoring
     * @param datasetId Optional dataset ID (defaults to "eo-dataset-test")
     * @param metadataTitle Optional metadata title (defaults to "Test EO Dataset")
     * @param metadataDescription Optional metadata description
     * @return An EoTestScenario ready to execute
     */
    suspend fun createScenario(
        issuerDid: String,
        anchorClient: BlockchainAnchorClient,
        chainId: String,
        datasetId: String = "eo-dataset-test",
        metadataTitle: String = "Test EO Dataset",
        metadataDescription: String = "A test Earth Observation dataset for integrity verification"
    ): EoTestScenario {
        // Step 1: Create artifacts (metadata, provenance, quality report) with digests
        val (metadataArtifact, metadataDigest) = TestDataBuilders.createMetadataArtifact(
            "metadata-1",
            metadataTitle,
            metadataDescription
        )

        val (provenanceArtifact, provenanceDigest) = TestDataBuilders.createProvenanceArtifact(
            "provenance-1",
            "EO Data Collection",
            issuerDid
        )

        val (qualityArtifact, qualityDigest) = TestDataBuilders.createQualityReportArtifact(
            "quality-1",
            0.95,
            mapOf("completeness" to 0.98, "accuracy" to 0.92)
        )

        // Step 2: Create Linkset with digestMultibase and links to artifacts
        val links = listOf(
            TestDataBuilders.buildLink("metadata-1", metadataDigest, "Metadata"),
            TestDataBuilders.buildLink("provenance-1", provenanceDigest, "Provenance"),
            TestDataBuilders.buildLink("quality-1", qualityDigest, "QualityReport")
        )

        val linkset = TestDataBuilders.buildLinkset(
            digestMultibase = "", // Will compute after building
            links = links
        )
        // Compute digest from Linkset without digestMultibase field (to avoid circular dependency)
        val linksetWithoutDigest = buildJsonObject {
            linkset.entries.forEach { (key, value) ->
                if (key != "digestMultibase") {
                    put(key, value)
                }
            }
        }
        val linksetDigest = DigestUtils.sha256DigestMultibase(linksetWithoutDigest)
        val linksetWithDigest = TestDataBuilders.buildLinkset(linksetDigest, links)

        // Step 3: Create VC with digestMultibase and reference to Linkset
        val subject = buildJsonObject {
            put("id", datasetId)
            put("type", "EarthObservationDataset")
            put("title", metadataTitle)
        }

        // Build VC with linkset digest reference (use fixed timestamp for consistent digest)
        val fixedTimestamp = "2024-01-01T00:00:00Z"
        val vc = buildJsonObject {
            put("id", "vc-eo-$datasetId")
            put("type", buildJsonArray { add("VerifiableCredential") })
            put("issuer", issuerDid)
            put("credentialSubject", subject)
            put("linksetDigest", linksetDigest) // Reference to Linkset digest
            put("issued", fixedTimestamp)
        }
        val vcDigest = DigestUtils.sha256DigestMultibase(vc)
        val vcWithDigest = buildJsonObject {
            put("id", "vc-eo-$datasetId")
            put("type", buildJsonArray { add("VerifiableCredential") })
            put("issuer", issuerDid)
            put("credentialSubject", subject)
            put("digestMultibase", vcDigest)
            put("linksetDigest", linksetDigest) // Reference to Linkset digest
            put("issued", fixedTimestamp)
        }

        // Step 4: Prepare anchor payload
        val digestPayload = buildJsonObject {
            put("vcId", "vc-eo-$datasetId")
            put("vcDigest", vcDigest)
            put("issuer", issuerDid)
        }

        return EoTestScenario(
            issuerDid = issuerDid,
            chainId = chainId,
            anchorClient = anchorClient,
            artifacts = mapOf(
                "metadata-1" to metadataArtifact,
                "provenance-1" to provenanceArtifact,
                "quality-1" to qualityArtifact
            ),
            linkset = linksetWithDigest,
            vc = vcWithDigest,
            digestPayload = digestPayload,
            vcDigest = vcDigest,
            linksetDigest = linksetDigest
        )
    }

    /**
     * Executes a complete EO test scenario: anchors VC digest and verifies integrity chain.
     * 
     * @param scenario The EO test scenario to execute
     * @return EoTestResult containing anchor result and verification result
     */
    suspend fun executeScenario(
        scenario: EoTestScenario,
        blockchainRegistry: BlockchainAnchorRegistry
    ): EoTestResult {
        blockchainRegistry.register(scenario.chainId, scenario.anchorClient)

        // Anchor VC digest to blockchain
        val anchorResult = scenario.anchorClient.writePayload(scenario.digestPayload)

        // Verify integrity chain
        val verificationResult = IntegrityVerifier.verifyIntegrityChain(
            vc = scenario.vc,
            linkset = scenario.linkset,
            artifacts = scenario.artifacts,
            anchorRef = anchorResult.ref,
            registry = blockchainRegistry
        )

        return EoTestResult(
            anchorResult = anchorResult,
            verificationResult = verificationResult,
            scenario = scenario
        )
    }

    /**
     * Creates a complete EO test scenario and executes it in one call.
     * Convenience method for simple test cases.
     * 
     * @param issuerDid The DID of the issuer
     * @param anchorClient The blockchain anchor client to use
     * @param chainId The chain ID for anchoring
     * @param datasetId Optional dataset ID
     * @param metadataTitle Optional metadata title
     * @param metadataDescription Optional metadata description
     * @return EoTestResult containing anchor result and verification result
     */
    suspend fun runCompleteScenario(
        issuerDid: String,
        anchorClient: BlockchainAnchorClient,
        chainId: String,
        datasetId: String = "eo-dataset-test",
        metadataTitle: String = "Test EO Dataset",
        metadataDescription: String = "A test Earth Observation dataset for integrity verification",
        blockchainRegistry: BlockchainAnchorRegistry
    ): EoTestResult {
        val scenario = createScenario(
            issuerDid = issuerDid,
            anchorClient = anchorClient,
            chainId = chainId,
            datasetId = datasetId,
            metadataTitle = metadataTitle,
            metadataDescription = metadataDescription
        )
        return executeScenario(scenario, blockchainRegistry)
    }
}

/**
 * Represents a complete EO test scenario with all components.
 */
data class EoTestScenario(
    val issuerDid: String,
    val chainId: String,
    val anchorClient: BlockchainAnchorClient,
    val artifacts: Map<String, JsonObject>,
    val linkset: JsonObject,
    val vc: JsonObject,
    val digestPayload: JsonObject,
    val vcDigest: String,
    val linksetDigest: String
)

/**
 * Result of executing an EO test scenario.
 */
data class EoTestResult(
    val anchorResult: AnchorResult,
    val verificationResult: IntegrityVerificationResult,
    val scenario: EoTestScenario
)

