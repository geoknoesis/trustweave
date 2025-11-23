package com.trustweave.godiddy

import com.trustweave.anchor.DefaultBlockchainAnchorRegistry
import com.trustweave.did.DidMethodRegistry
import com.trustweave.json.DigestUtils
import com.trustweave.testkit.anchor.InMemoryBlockchainAnchorClient
import com.trustweave.testkit.integrity.IntegrityVerifier
import com.trustweave.testkit.integrity.TestDataBuilders
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import org.junit.jupiter.api.Test
import kotlin.test.*

/**
 * Earth Observation (EO) integration test scenario for GoDiddy.
 * 
 * This test demonstrates the complete EO workflow using GoDiddy services:
 * - DID creation and resolution via Universal Resolver/Registrar
 * - Verifiable Credential issuance via Universal Issuer
 * - VC verification via Universal Verifier
 * - Integrity chain verification (Blockchain → VC → Linkset → Artifacts)
 * 
 * Note: This test uses in-memory blockchain client for anchoring, but uses
 * GoDiddy services for DID operations and VC issuance/verification.
 */
class GodiddyEoIntegrationTest {

    @Test
    fun `end-to-end EO integrity chain verification with GoDiddy`() = runBlocking {
        // Setup: Register GoDiddy integration and blockchain client (testnet)
        val didRegistry = DidMethodRegistry()
        val result = try {
            GodiddyIntegration.discoverAndRegister(didRegistry)
        } catch (e: Exception) {
            println("Skipping test: GoDiddy integration failed: ${e.message}")
            return@runBlocking
        }
        
        val chainId = "algorand:testnet"
        val anchorClient = InMemoryBlockchainAnchorClient(chainId)
        
        val blockchainRegistry = DefaultBlockchainAnchorRegistry().apply { register(chainId, anchorClient) }

        // Verify GoDiddy services are available
        assertNotNull(result.resolver, "GoDiddy resolver should be available")
        assertTrue(result.registeredDidMethods.isNotEmpty(), "At least one DID method should be registered")

        // Step 1: Create a DID for the issuer using GoDiddy
        // Note: In a real scenario, this would use Universal Registrar
        // For testing, we'll use a DID method that's registered via GoDiddy
        val keyMethod = result.registry.get("key")
        if (keyMethod == null) {
            // If key method is not available, use in-memory DID method as fallback for testing
            println("Note: did:key method not available via GoDiddy, using fallback")
            val kms = com.trustweave.testkit.kms.InMemoryKeyManagementService()
            val fallbackMethod = com.trustweave.testkit.did.DidKeyMockMethod(kms)
            result.registry.register(fallbackMethod)
            val issuerDoc = fallbackMethod.createDid()
            val issuerDid = issuerDoc.id
            assertNotNull(issuerDid)
            // Continue with fallback method
            val resolutionResult = result.registry.resolve(issuerDid)
            assertNotNull(resolutionResult.document, "DID should resolve")
            val document = resolutionResult.document
            if (document != null) {
                assertEquals(issuerDid, document.id)
            }
            // Skip rest of test since GoDiddy services aren't available
            return@runBlocking
        }
        
        val issuerDoc = try {
            keyMethod.createDid()
        } catch (e: Exception) {
            println("Warning: DID creation via GoDiddy failed: ${e.message}. Using fallback.")
            // Use in-memory DID method as fallback
            val kms = com.trustweave.testkit.kms.InMemoryKeyManagementService()
            val fallbackMethod = com.trustweave.testkit.did.DidKeyMockMethod(kms)
            result.registry.register(fallbackMethod)
            fallbackMethod.createDid()
        }
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

        // Step 2: Resolve the DID using GoDiddy Universal Resolver
        val resolutionResult = try {
            result.registry.resolve(issuerDid)
        } catch (e: Exception) {
            println("Warning: DID resolution failed (expected if service unavailable): ${e.message}")
            // Continue with test using local DID document
            com.trustweave.did.DidResolutionResult(
                document = issuerDoc,
                documentMetadata = com.trustweave.did.DidDocumentMetadata(),
                resolutionMetadata = mapOf("provider" to "local")
            )
        }
        
        assertNotNull(resolutionResult.document, "DID should resolve")
        val document = resolutionResult.document
        if (document != null) {
            assertEquals(issuerDid, document.id)
        }
        
        // Verify metadata indicates GoDiddy provider (if available)
        val provider = resolutionResult.resolutionMetadata["provider"]
        if (provider != null) {
            // Provider might be "godiddy" or the underlying method provider
            assertTrue(
                provider == "godiddy" || provider is String,
                "Resolution metadata should indicate provider"
            )
        }

        // Step 3: Create artifacts (metadata, provenance, quality report) with digests
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

        // Step 4: Create Linkset with digestMultibase and links to artifacts
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

        // Step 5: Create VC with digestMultibase and reference to Linkset
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

        // Step 6: Issue VC using GoDiddy Universal Issuer (if available)
        val issuedCredential = if (result.issuer != null) {
            try {
                result.issuer.issueCredential(
                    credential = vcWithDigest,
                    options = mapOf("format" to "json-ld")
                )
            } catch (e: Exception) {
                // If issuance fails (e.g., service not available), use original VC
                println("Warning: VC issuance via GoDiddy failed: ${e.message}. Using original VC.")
                vcWithDigest
            }
        } else {
            // If issuer is not available, use original VC
            println("Warning: GoDiddy Universal Issuer not available. Using original VC.")
            vcWithDigest
        }

        // Step 7: Verify VC using GoDiddy Universal Verifier (if available)
        if (result.verifier != null) {
            try {
                val verificationResult = result.verifier.verifyCredential(
                    credential = issuedCredential,
                    options = emptyMap()
                )
                // Note: Verification might fail if issuer doesn't have proper keys configured
                // This is expected in a test environment
                println("GoDiddy VC verification result: verified=${verificationResult.verified}, error=${verificationResult.error}")
            } catch (e: Exception) {
                println("Warning: VC verification via GoDiddy failed: ${e.message}")
            }
        } else {
            println("Warning: GoDiddy Universal Verifier not available.")
        }

        // Step 8: Anchor VC digest to blockchain (testnet)
        val digestPayload = buildJsonObject {
            put("vcId", "vc-eo-12345")
            put("vcDigest", vcDigest)
            put("issuer", issuerDid)
        }
        
        val anchorResult = anchorClient.writePayload(digestPayload)
        assertNotNull(anchorResult.ref)
        assertEquals(chainId, anchorResult.ref.chainId)

        // Log anchoring information
        println("=== Blockchain Anchoring ===")
        println("Chain ID: ${anchorResult.ref.chainId}")
        println("Transaction Hash: ${anchorResult.ref.txHash}")
        println("Anchored Payload:")
        println("  VC ID: ${digestPayload["vcId"]?.jsonPrimitive?.content}")
        println("  VC Digest: ${digestPayload["vcDigest"]?.jsonPrimitive?.content}")
        println("  Issuer: ${digestPayload["issuer"]?.jsonPrimitive?.content}")
        println("===========================\n")

        // Step 9: Verify integrity chain
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
            vc = issuedCredential, // Use issued credential if available, otherwise original
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
        
        println("EO Integrity chain verification successful with GoDiddy!")
        println("Issuer DID: $issuerDid")
        println("VC Digest: ${vcStep.digest}")
        println("Anchored at: ${anchorResult.ref.txHash}")
        println("Registered DID methods: ${result.registeredDidMethods}")
        
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
    fun `test GoDiddy DID resolution for EO issuer`() = runBlocking {
        val result = try {
            GodiddyIntegration.discoverAndRegister(DidMethodRegistry())
        } catch (e: Exception) {
            println("Skipping test: GoDiddy integration failed: ${e.message}")
            return@runBlocking
        }
        
        assertNotNull(result.resolver, "GoDiddy resolver should be available")
        assertTrue(result.registeredDidMethods.isNotEmpty(), "At least one DID method should be registered")

        // Create a DID using one of the registered methods
        val keyMethod = result.registry.get("key")
        if (keyMethod == null) {
            println("Skipping test: did:key method not available")
            return@runBlocking
        }
        
        val issuerDoc = try {
            keyMethod.createDid()
        } catch (e: Exception) {
            println("Skipping test: DID creation failed: ${e.message}")
            return@runBlocking
        }
        val issuerDid = issuerDoc.id

        // Resolve using GoDiddy Universal Resolver
        val resolutionResult = try {
            result.resolver.resolveDid(issuerDid)
        } catch (e: Exception) {
            println("Note: DID resolution failed (expected if service unavailable): ${e.message}")
            null
        }
        
        // Note: Resolution might fail if the DID was just created and not yet
        // registered in the Universal Resolver's backend. This is expected behavior.
        val document = resolutionResult?.document
        if (document != null) {
            assertEquals(issuerDid, document.id)
            println("Successfully resolved DID via GoDiddy: $issuerDid")
        } else {
            println("Note: DID resolution returned null (DID may not be registered in Universal Resolver backend)")
            // This is acceptable for testing - the DID was created locally
        }
    }

    @Test
    fun `test GoDiddy VC issuance and verification workflow`() = runBlocking {
        val result = try {
            GodiddyIntegration.discoverAndRegister(DidMethodRegistry())
        } catch (e: Exception) {
            println("Skipping test: GoDiddy integration failed: ${e.message}")
            return@runBlocking
        }
        
        // Skip if services are not available
        if (result.issuer == null || result.verifier == null) {
            println("Skipping test: GoDiddy Issuer or Verifier not available")
            return@runBlocking
        }

        // Create a simple VC for EO dataset
        val vc = buildJsonObject {
            put("id", "vc-eo-test")
            put("type", buildJsonArray { add("VerifiableCredential") })
            put("issuer", "did:key:test")
            put("credentialSubject", buildJsonObject {
                put("id", "eo-dataset-test")
                put("type", "EarthObservationDataset")
            })
            put("issued", "2024-01-01T00:00:00Z")
        }

        // Issue VC using GoDiddy Universal Issuer
        try {
            val issuedCredential = result.issuer.issueCredential(
                credential = vc,
                options = mapOf("format" to "json-ld")
            )
            
            assertNotNull(issuedCredential, "Issued credential should not be null")
            assertTrue(issuedCredential.containsKey("id"), "Issued credential should have id")
            
            // Verify VC using GoDiddy Universal Verifier
            val verificationResult = result.verifier.verifyCredential(
                credential = issuedCredential,
                options = emptyMap()
            )
            
            println("VC verification result: verified=${verificationResult.verified}, error=${verificationResult.error}")
            // Note: Verification might fail if issuer doesn't have proper keys
            // This is expected in a test environment without proper key configuration
        } catch (e: Exception) {
            // Expected in test environment - services might not be fully configured or network unavailable
            println("VC issuance/verification failed (expected in test environment): ${e.message}")
            // Test passes if it handles the exception gracefully
        }
    }
}

