package com.trustweave.anchor.ganache

import com.trustweave.anchor.*
import com.trustweave.did.DidDocument
import com.trustweave.json.DigestUtils
import com.trustweave.testkit.did.DidKeyMockMethod
import com.trustweave.testkit.integrity.IntegrityVerifier
import com.trustweave.testkit.integrity.TestDataBuilders
import com.trustweave.testkit.kms.InMemoryKeyManagementService
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import kotlin.test.*
import com.trustweave.anchor.DefaultBlockchainAnchorRegistry

/**
 * Earth Observation (EO) integration test scenario for Ganache.
 *
 * This test demonstrates the complete EO workflow using:
 * - Ganache (local Ethereum) blockchain for anchoring via Testcontainers
 * - In-memory DID method (did:key) for DID operations
 * - Integrity chain verification (Blockchain → VC → Linkset → Artifacts)
 *
 * Ganache runs automatically in a Docker container via Testcontainers.
 * No manual setup required - Docker must be installed and running.
 */
class GanacheEoIntegrationTest {

    companion object {
        @JvmStatic
        lateinit var ganacheContainer: GanacheContainer
        
        @JvmStatic
        @BeforeAll
        fun startGanache() {
            ganacheContainer = GanacheContainer().apply {
                start()
            }
        }
        
        @JvmStatic
        @AfterAll
        fun stopGanache() {
            if (::ganacheContainer.isInitialized) {
                ganacheContainer.stop()
            }
        }
    }

    @AfterEach
    fun cleanup() {
    }

    @Test
    fun `end-to-end EO integrity chain verification with Ganache`() = runBlocking {
        // Setup: Create DID for issuer and register Ganache blockchain anchor client
        val kms = InMemoryKeyManagementService()
        val didMethod = DidKeyMockMethod(kms)
        val chainId = GanacheBlockchainAnchorClient.LOCAL
        
        // Get RPC URL and private key from Testcontainers Ganache container
        val rpcUrl = ganacheContainer.getRpcUrl()
        val privateKey = ganacheContainer.getFirstAccountPrivateKey()
        
        // Create Ganache blockchain anchor client using Testcontainers
        val anchorClient = GanacheBlockchainAnchorClient(
            chainId = chainId,
            options = mapOf(
                "rpcUrl" to rpcUrl,
                "privateKey" to privateKey
            )
        )

        val blockchainRegistry = DefaultBlockchainAnchorRegistry().apply { register(chainId, anchorClient) }

        // Step 1: Create a DID for the issuer
        val issuerDoc = didMethod.createDid()
        val issuerDid = issuerDoc.id
        assertNotNull(issuerDid)

        // Log DID creation
        println("\n=== Created Issuer DID ===")
        println("DID: $issuerDid")
        println("Verification Methods: ${issuerDoc.verificationMethod.size}")
        issuerDoc.verificationMethod.forEachIndexed { index, vm ->
            println("  VM ${index + 1}: ${vm.id} (${vm.type})")
        }
        println("==========================\n")

        // Step 2: Create artifacts (metadata, provenance, quality report) with digests
        val (metadataArtifact, metadataDigest) = TestDataBuilders.createMetadataArtifact(
            "metadata-1",
            "Sample EO Dataset",
            "A test Earth Observation dataset for integrity verification"
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

        // Log created artifacts
        println("\n=== Created Artifacts ===")
        println("Metadata Artifact:")
        println("  ID: ${metadataArtifact["id"]?.jsonPrimitive?.content}")
        println("  Digest: $metadataDigest")
        val metadataContent = metadataArtifact["content"]?.jsonObject
        println("  Content Title: ${metadataContent?.get("title")?.jsonPrimitive?.content}")
        println("  Content Description: ${metadataContent?.get("description")?.jsonPrimitive?.content}")
        println("\nProvenance Artifact:")
        println("  ID: ${provenanceArtifact["id"]?.jsonPrimitive?.content}")
        println("  Digest: $provenanceDigest")
        val provenanceContent = provenanceArtifact["content"]?.jsonObject
        println("  Activity: ${provenanceContent?.get("activity")?.jsonPrimitive?.content}")
        println("  Agent: ${provenanceContent?.get("agent")?.jsonPrimitive?.content}")
        println("\nQuality Report Artifact:")
        println("  ID: ${qualityArtifact["id"]?.jsonPrimitive?.content}")
        println("  Digest: $qualityDigest")
        val qualityContent = qualityArtifact["content"]?.jsonObject
        println("  Overall Quality: ${qualityContent?.get("overallQuality")?.jsonPrimitive?.doubleOrNull}")
        val metrics = qualityContent?.get("metrics")?.jsonObject
        println("  Metrics: ${metrics?.entries?.joinToString(", ") { "${it.key}=${it.value.jsonPrimitive.content}" }}")
        println("========================\n")

        // Step 3: Create Linkset with digestMultibase and links to artifacts
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

        // Log Linkset structure
        println("=== Created Linkset ===")
        println("Linkset Digest: $linksetDigest")
        println("Number of Links: ${links.size}")
        links.forEachIndexed { index, link ->
            println("  Link ${index + 1}:")
            println("    Href: ${link.href}")
            println("    Digest: ${link.digestMultibase}")
            println("    Type: ${link.type}")
        }
        println("=======================\n")

        // Step 4: Create VC with digestMultibase and reference to Linkset
        val subject = buildJsonObject {
            put("id", "eo-dataset-123")
            put("type", "EarthObservationDataset")
            put("title", "Sample EO Dataset")
        }

        // Build VC with linkset digest reference (use fixed timestamp for consistent digest)
        val fixedTimestamp = "2024-01-01T00:00:00Z"
        val vc = buildJsonObject {
            put("id", "vc-eo-12345")
            put("type", buildJsonArray { add("VerifiableCredential") })
            put("issuer", issuerDid)
            put("credentialSubject", subject)
            put("linksetDigest", linksetDigest) // Reference to Linkset digest
            put("issued", fixedTimestamp)
        }
        val vcDigest = DigestUtils.sha256DigestMultibase(vc)
        val vcWithDigest = buildJsonObject {
            put("id", "vc-eo-12345")
            put("type", buildJsonArray { add("VerifiableCredential") })
            put("issuer", issuerDid)
            put("credentialSubject", subject)
            put("digestMultibase", vcDigest)
            put("linksetDigest", linksetDigest) // Reference to Linkset digest
            put("issued", fixedTimestamp)
        }

        // Log VC structure
        println("=== Created Verifiable Credential ===")
        println("VC ID: ${vcWithDigest["id"]?.jsonPrimitive?.content}")
        println("VC Digest: $vcDigest")
        println("Issuer: $issuerDid")
        println("Issued: $fixedTimestamp")
        println("Credential Subject:")
        println("  ID: ${subject["id"]?.jsonPrimitive?.content}")
        println("  Type: ${subject["type"]?.jsonPrimitive?.content}")
        println("  Title: ${subject["title"]?.jsonPrimitive?.content}")
        println("Linkset Digest Reference: $linksetDigest")
        println("====================================\n")

        // Step 5: Anchor VC digest to Ganache blockchain
        val digestPayload = buildJsonObject {
            put("vcId", "vc-eo-12345")
            put("vcDigest", vcDigest)
            put("issuer", issuerDid)
        }

        println("Anchoring VC digest to Ganache...")
        val anchorResult = anchorClient.writePayload(digestPayload)
        assertNotNull(anchorResult.ref)
        assertEquals(chainId, anchorResult.ref.chainId)

        // Log anchoring information
        println("\n=== Blockchain Anchoring ===")
        println("Chain ID: ${anchorResult.ref.chainId}")
        println("Transaction Hash: ${anchorResult.ref.txHash}")
        println("Anchored Payload:")
        println("  VC ID: ${digestPayload["vcId"]?.jsonPrimitive?.content}")
        println("  VC Digest: ${digestPayload["vcDigest"]?.jsonPrimitive?.content}")
        println("  Issuer: ${digestPayload["issuer"]?.jsonPrimitive?.content}")
        println("Timestamp: ${anchorResult.timestamp}")
        println("===========================\n")

        // Step 6: Verify integrity chain
        val artifacts = mapOf(
            "metadata-1" to metadataArtifact,
            "provenance-1" to provenanceArtifact,
            "quality-1" to qualityArtifact
        )

        // Log artifacts map for verification
        println("=== Artifacts Map for Verification ===")
        artifacts.forEach { (key, artifact) ->
            println("  $key:")
            println("    ID: ${artifact["id"]?.jsonPrimitive?.content}")
            println("    Type: ${artifact["type"]?.jsonPrimitive?.content}")
        }
        println("===================================\n")

        val verificationResult = IntegrityVerifier.verifyIntegrityChain(
            vc = vcWithDigest,
            linkset = linksetWithDigest,
            artifacts = artifacts,
            anchorRef = anchorResult.ref,
            registry = blockchainRegistry
        )

        // Verify all steps passed
        if (!verificationResult.valid) {
            println("Verification failed. Steps:")
            verificationResult.steps.forEach { step ->
                println("  ${step.name}: ${if (step.valid) "PASS" else "FAIL"} - ${step.digest ?: step.error}")
            }
        }
        assertTrue(verificationResult.valid, "Integrity chain verification failed")
        assertEquals(5, verificationResult.steps.size) // VC + Linkset + 3 artifacts

        // Verify each step
        val vcStep = verificationResult.steps.find { it.name == "VC Digest" }
        assertNotNull(vcStep)
        assertTrue(vcStep.valid, "VC digest verification failed")

        val linksetStep = verificationResult.steps.find { it.name == "Linkset Digest" }
        assertNotNull(linksetStep)

        println("EO Integrity chain verification successful with Ganache!")
        println("Issuer DID: $issuerDid")
        println("VC Digest: ${vcStep.digest}")
        println("Anchored at: ${anchorResult.ref.txHash}")
        println("Chain: ${anchorResult.ref.chainId}")

        // Log verification steps summary
        println("\n=== Verification Steps Summary ===")
        verificationResult.steps.forEachIndexed { index, step ->
            println("  Step ${index + 1}: ${step.name}")
            println("    Status: ${if (step.valid) "PASS" else "FAIL"}")
            if (step.digest != null) {
                println("    Digest: ${step.digest}")
            }
            if (step.error != null) {
                println("    Error: ${step.error}")
            }
        }
        println("===================================\n")
    }

    @Test
    fun `test Ganache blockchain anchoring for EO dataset`() = runBlocking {
        val chainId = GanacheBlockchainAnchorClient.LOCAL
        
        // Get RPC URL and private key from Testcontainers Ganache container
        val rpcUrl = ganacheContainer.getRpcUrl()
        val privateKey = ganacheContainer.getFirstAccountPrivateKey()
        
        val anchorClient = GanacheBlockchainAnchorClient(
            chainId = chainId,
            options = mapOf(
                "rpcUrl" to rpcUrl,
                "privateKey" to privateKey
            )
        )

        // Create a simple EO dataset digest payload
        val digestPayload = buildJsonObject {
            put("datasetId", "eo-dataset-test-123")
            put("digestMultibase", "uEiTestDigest123456789")
            put("metadata", buildJsonObject {
                put("title", "Test EO Dataset")
                put("type", "EarthObservationDataset")
            })
        }

        // Anchor to Ganache
        val anchorResult = anchorClient.writePayload(digestPayload)
        assertNotNull(anchorResult.ref)
        assertEquals(chainId, anchorResult.ref.chainId)
        assertTrue(anchorResult.ref.txHash.isNotEmpty(), "Transaction hash should not be empty")

        // Read back from blockchain
        val retrieved = anchorClient.readPayload(anchorResult.ref)
        assertNotNull(retrieved)
        assertEquals(digestPayload["datasetId"]?.jsonPrimitive?.content, retrieved.payload.jsonObject["datasetId"]?.jsonPrimitive?.content)

        println("Successfully anchored and retrieved EO dataset digest from Ganache")
        println("Transaction Hash: ${anchorResult.ref.txHash}")
    }
}

