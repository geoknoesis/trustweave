package io.geoknoesis.vericore.examples.eo

import io.geoknoesis.vericore.anchor.BlockchainRegistry
import io.geoknoesis.vericore.did.DidRegistry
import io.geoknoesis.vericore.testkit.anchor.InMemoryBlockchainAnchorClient
import io.geoknoesis.vericore.testkit.did.DidKeyMockMethod
import io.geoknoesis.vericore.testkit.integrity.IntegrityVerifier
import io.geoknoesis.vericore.testkit.integrity.TestDataBuilders
import io.geoknoesis.vericore.testkit.kms.InMemoryKeyManagementService
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import org.junit.jupiter.api.Test
import kotlin.test.*

/**
 * Unit tests for Earth Observation scenario.
 * 
 * Verifies that the EO data integrity workflow behaves correctly:
 * - DID creation for data provider
 * - Artifact creation (metadata, provenance, quality)
 * - Linkset creation
 * - Credential issuance with Linkset reference
 * - Blockchain anchoring
 * - Integrity verification
 */
class EarthObservationExampleTest {

    @Test
    fun `test main function executes successfully`() = runBlocking {
        // Capture output to verify execution
        val output = java.io.ByteArrayOutputStream()
        val originalOut = System.out
        try {
            System.setOut(java.io.PrintStream(output))
            
            // Execute main function
            main()
            
            // Verify output contains expected content
            val outputString = output.toString()
            assertTrue(outputString.contains("Earth Observation Data Integrity Workflow"), "Should print scenario title")
            assertTrue(outputString.contains("Setting up services") || outputString.contains("services"), "Should print setup step")
            assertTrue(outputString.contains("DID") || outputString.contains("did:"), "Should print DID creation")
            assertTrue(outputString.contains("Linkset") || outputString.contains("linkset"), "Should print linkset creation")
            assertTrue(outputString.contains("Anchored") || outputString.contains("blockchain") || outputString.contains("Transaction"), "Should print anchoring")
            assertTrue(outputString.contains("Verification") || outputString.contains("verified") || outputString.contains("integrity"), "Should print verification")
        } finally {
            System.setOut(originalOut)
        }
    }

    @Test
    fun `test DID creation for data provider`() = runBlocking {
        // Setup
        DidRegistry.clear()
        BlockchainRegistry.clear()
        val kms = InMemoryKeyManagementService()
        val didMethod = DidKeyMockMethod(kms)
        DidRegistry.register(didMethod)
        
        val issuerDoc = didMethod.createDid(mapOf("algorithm" to "Ed25519"))
        val issuerDid = issuerDoc.id

        assertNotNull(issuerDid)
        assertTrue(issuerDid.startsWith("did:key:"))
        assertTrue(issuerDoc.verificationMethod.isNotEmpty())
    }

    @Test
    fun `test artifact creation`() = runBlocking {
        // Setup
        DidRegistry.clear()
        val kms = InMemoryKeyManagementService()
        val didMethod = DidKeyMockMethod(kms)
        DidRegistry.register(didMethod)
        val issuerDoc = didMethod.createDid(mapOf("algorithm" to "Ed25519"))
        val issuerDid = issuerDoc.id

        // Create metadata artifact
        val (metadataArtifact, metadataDigest) = TestDataBuilders.createMetadataArtifact(
            id = "metadata-1",
            title = "Sentinel-2 L2A Dataset",
            description = "Test dataset"
        )

        assertNotNull(metadataArtifact)
        assertNotNull(metadataDigest)
        assertTrue(metadataDigest.startsWith("u"))

        // Create provenance artifact
        val (provenanceArtifact, provenanceDigest) = TestDataBuilders.createProvenanceArtifact(
            id = "provenance-1",
            activity = "EO Data Collection",
            agent = issuerDid
        )

        assertNotNull(provenanceArtifact)
        assertNotNull(provenanceDigest)

        // Create quality artifact
        val (qualityArtifact, qualityDigest) = TestDataBuilders.createQualityReportArtifact(
            id = "quality-1",
            qualityScore = 0.95,
            metrics = mapOf(
                "completeness" to 0.98,
                "accuracy" to 0.92
            )
        )

        assertNotNull(qualityArtifact)
        assertNotNull(qualityDigest)
    }

    @Test
    fun `test linkset creation`() = runBlocking {
        // Setup
        DidRegistry.clear()
        val kms = InMemoryKeyManagementService()
        val didMethod = DidKeyMockMethod(kms)
        DidRegistry.register(didMethod)
        val issuerDoc = didMethod.createDid(mapOf("algorithm" to "Ed25519"))
        val issuerDid = issuerDoc.id

        // Create artifacts
        val (_, metadataDigest) = TestDataBuilders.createMetadataArtifact(
            id = "metadata-1",
            title = "Test Dataset",
            description = "Test"
        )

        val (_, provenanceDigest) = TestDataBuilders.createProvenanceArtifact(
            id = "provenance-1",
            activity = "Collection",
            agent = issuerDid
        )

        val (_, qualityDigest) = TestDataBuilders.createQualityReportArtifact(
            id = "quality-1",
            qualityScore = 0.95,
            metrics = emptyMap()
        )

        // Create linkset
        val links = listOf(
            TestDataBuilders.buildLink(
                href = "metadata-1",
                digestMultibase = metadataDigest,
                type = "Metadata"
            ),
            TestDataBuilders.buildLink(
                href = "provenance-1",
                digestMultibase = provenanceDigest,
                type = "Provenance"
            ),
            TestDataBuilders.buildLink(
                href = "quality-1",
                digestMultibase = qualityDigest,
                type = "QualityReport"
            )
        )

        val linksetWithoutDigest = buildJsonObject {
            put("@context", "https://www.w3.org/ns/json-ld#")
            put("links", Json.encodeToJsonElement(links))
        }
        val linksetDigest = io.geoknoesis.vericore.json.DigestUtils.sha256DigestMultibase(linksetWithoutDigest as JsonElement)
        val linkset = TestDataBuilders.buildLinkset(
            digestMultibase = linksetDigest,
            links = links,
            linksetId = "linkset-1"
        )

        assertNotNull(linkset)
        val linksElement = linkset["links"]
        assertNotNull(linksElement)
        val linksArray = linksElement.jsonArray
        assertEquals(3, linksArray.size)
    }

    @Test
    fun `test blockchain anchoring`() = runBlocking {
        // Setup
        DidRegistry.clear()
        BlockchainRegistry.clear()
        val kms = InMemoryKeyManagementService()
        val didMethod = DidKeyMockMethod(kms)
        val chainId = "algorand:testnet"
        val anchorClient = InMemoryBlockchainAnchorClient(chainId)
        DidRegistry.register(didMethod)
        BlockchainRegistry.register(chainId, anchorClient)
        
        val issuerDoc = didMethod.createDid(mapOf("algorithm" to "Ed25519"))
        val issuerDid = issuerDoc.id

        // Create linkset
        val (_, metadataDigest) = TestDataBuilders.createMetadataArtifact(
            id = "metadata-1",
            title = "Test Dataset",
            description = "Test"
        )

        val links = listOf(
            TestDataBuilders.buildLink(
                href = "metadata-1",
                digestMultibase = metadataDigest,
                type = "Metadata"
            )
        )

        val linksetWithoutDigest = buildJsonObject {
            put("@context", "https://www.w3.org/ns/json-ld#")
            put("links", Json.encodeToJsonElement(links))
        }
        val linksetDigest = io.geoknoesis.vericore.json.DigestUtils.sha256DigestMultibase(linksetWithoutDigest as JsonElement)
        val linkset = TestDataBuilders.buildLinkset(
            digestMultibase = linksetDigest,
            links = links,
            linksetId = "linkset-1"
        )

        // Create credential referencing linkset
        val vcSubject = buildJsonObject {
            put("linkset", linkset)
        }
        val credential = TestDataBuilders.buildVc(
            issuerDid = issuerDid,
            subject = vcSubject,
            digestMultibase = ""
        )

        // Anchor credential digest to blockchain
        val anchorResult = anchorClient.writePayload(
            payload = credential,
            mediaType = "application/json"
        )

        assertNotNull(anchorResult)
        assertNotNull(anchorResult.ref.txHash)
        assertEquals(chainId, anchorResult.ref.chainId)
    }

    @Test
    fun `test integrity verification`() = runBlocking {
        // Setup
        DidRegistry.clear()
        BlockchainRegistry.clear()
        val kms = InMemoryKeyManagementService()
        val didMethod = DidKeyMockMethod(kms)
        val chainId = "algorand:testnet"
        val anchorClient = InMemoryBlockchainAnchorClient(chainId)
        DidRegistry.register(didMethod)
        BlockchainRegistry.register(chainId, anchorClient)
        
        val issuerDoc = didMethod.createDid(mapOf("algorithm" to "Ed25519"))
        val issuerDid = issuerDoc.id

        // Create artifacts
        val (metadataArtifact, metadataDigest) = TestDataBuilders.createMetadataArtifact(
            id = "metadata-1",
            title = "Test Dataset",
            description = "Test"
        )

        val (provenanceArtifact, provenanceDigest) = TestDataBuilders.createProvenanceArtifact(
            id = "provenance-1",
            activity = "Collection",
            agent = issuerDid
        )

        // Create linkset
        val links = listOf(
            TestDataBuilders.buildLink(
                href = "metadata-1",
                digestMultibase = metadataDigest,
                type = "Metadata"
            ),
            TestDataBuilders.buildLink(
                href = "provenance-1",
                digestMultibase = provenanceDigest,
                type = "Provenance"
            )
        )

        // Build linkset first to get the final structure (including id field)
        val linksetTemp = TestDataBuilders.buildLinkset(
            digestMultibase = "", // Temporary empty digest
            links = links,
            linksetId = "linkset-1"
        )
        // Compute digest from linkset without digestMultibase (matching verifier logic)
        val linksetWithoutDigest = buildJsonObject {
            linksetTemp.entries.forEach { (key, value) ->
                if (key != "digestMultibase") {
                    put(key, value)
                }
            }
        }
        val linksetDigest = io.geoknoesis.vericore.json.DigestUtils.sha256DigestMultibase(linksetWithoutDigest as JsonElement)
        // Build final linkset with computed digest
        val linkset = TestDataBuilders.buildLinkset(
            digestMultibase = linksetDigest,
            links = links,
            linksetId = "linkset-1"
        )

        // Create credential
        val vcSubject = buildJsonObject {
            put("linkset", linkset)
        }
        val credential = TestDataBuilders.buildVc(
            issuerDid = issuerDid,
            subject = vcSubject,
            digestMultibase = ""
        )
        
        // Add linkset digest reference at top level BEFORE computing digest
        // Use "linksetDigest" as a top-level string field (not nested object)
        val credentialWithLinksetRef = buildJsonObject {
            credential.entries.forEach { (key, value) ->
                put(key, value)
            }
            // Add linkset digest reference at top level for verification
            put("linksetDigest", linksetDigest)
        }
        
        // Compute VC digest (without metadata fields) - linkset reference is included
        val vcWithoutMetadata = buildJsonObject {
            credentialWithLinksetRef.entries.forEach { (key, value) ->
                if (key != "digestMultibase" && key != "evidence" && key != "credentialStatus") {
                    put(key, value)
                }
            }
        }
        val vcDigest = io.geoknoesis.vericore.json.DigestUtils.sha256DigestMultibase(vcWithoutMetadata as JsonElement)
        
        // Add VC digest to credential
        val credentialWithDigest = buildJsonObject {
            credentialWithLinksetRef.entries.forEach { (key, value) ->
                put(key, value)
            }
            // Add VC digest
            put("digestMultibase", vcDigest)
        }

        // Anchor to blockchain - anchor a digest payload (like in working tests)
        val digestPayload = buildJsonObject {
            put("vcDigest", vcDigest)
            put("digestMultibase", vcDigest) // Also include as digestMultibase for verifier
            put("issuer", issuerDid)
        }
        val anchorResult = anchorClient.writePayload(
            payload = digestPayload,
            mediaType = "application/json"
        )

        // Verify integrity
        val verificationResult = IntegrityVerifier.verifyIntegrityChain(
            vc = credentialWithDigest,
            linkset = linkset,
            artifacts = mapOf(
                "metadata-1" to metadataArtifact,
                "provenance-1" to provenanceArtifact
            ),
            anchorRef = anchorResult.ref
        )

        // Check individual verification steps
        val vcStep = verificationResult.steps.find { it.name == "VC Digest" }
        assertNotNull(vcStep, "VC Digest step should exist")
        assertTrue(vcStep?.valid == true, "VC Digest verification failed: ${vcStep?.error}")
        
        val linksetStep = verificationResult.steps.find { it.name == "Linkset Digest" }
        assertNotNull(linksetStep, "Linkset Digest step should exist")
        assertTrue(linksetStep?.valid == true, "Linkset Digest verification failed: ${linksetStep?.error}")
        
        val artifactSteps = verificationResult.steps.filter { it.name.contains("Artifact") }
        assertTrue(artifactSteps.isNotEmpty(), "Should have artifact verification steps")
        artifactSteps.forEach { step ->
            assertTrue(step.valid, "Artifact verification failed for ${step.name}: ${step.error}")
        }
        
        assertTrue(verificationResult.valid, "All verification steps should pass")
    }

    @Test
    fun `test reading anchored payload`() = runBlocking {
        // Setup
        DidRegistry.clear()
        BlockchainRegistry.clear()
        val kms = InMemoryKeyManagementService()
        val didMethod = DidKeyMockMethod(kms)
        val chainId = "algorand:testnet"
        val anchorClient = InMemoryBlockchainAnchorClient(chainId)
        DidRegistry.register(didMethod)
        BlockchainRegistry.register(chainId, anchorClient)
        
        val issuerDoc = didMethod.createDid(mapOf("algorithm" to "Ed25519"))
        val issuerDid = issuerDoc.id

        // Create linkset
        val (_, metadataDigest) = TestDataBuilders.createMetadataArtifact(
            id = "metadata-1",
            title = "Test Dataset",
            description = "Test"
        )

        val links = listOf(
            TestDataBuilders.buildLink(
                href = "metadata-1",
                digestMultibase = metadataDigest,
                type = "Metadata"
            )
        )

        val linksetWithoutDigest = buildJsonObject {
            put("@context", "https://www.w3.org/ns/json-ld#")
            put("links", Json.encodeToJsonElement(links))
        }
        val linksetDigest = io.geoknoesis.vericore.json.DigestUtils.sha256DigestMultibase(linksetWithoutDigest as JsonElement)
        val linkset = TestDataBuilders.buildLinkset(
            digestMultibase = linksetDigest,
            links = links
        )

        // Create credential
        val vcSubject = buildJsonObject {
            put("linkset", linkset)
        }
        val credential = TestDataBuilders.buildVc(
            issuerDid = issuerDid,
            subject = vcSubject,
            digestMultibase = ""
        )

        // Anchor to blockchain
        val anchorResult = anchorClient.writePayload(
            payload = credential,
            mediaType = "application/json"
        )

        // Read back from blockchain
        val readResult = anchorClient.readPayload(anchorResult.ref)

        assertNotNull(readResult)
        assertEquals(anchorResult.ref.txHash, readResult.ref.txHash)
        assertEquals(chainId, readResult.ref.chainId)
    }
}
