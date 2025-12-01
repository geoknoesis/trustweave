package com.trustweave.examples.eo

import com.trustweave.trust.TrustWeave
import com.trustweave.trust.types.VerificationResult
import com.trustweave.trust.types.*
import com.trustweave.core.*
import com.trustweave.credential.models.VerifiableCredential
import com.trustweave.testkit.anchor.InMemoryBlockchainAnchorClient
import com.trustweave.testkit.integrity.IntegrityVerifier
import com.trustweave.testkit.integrity.TestDataBuilders
import com.trustweave.testkit.services.TestkitDidMethodFactory
import com.trustweave.anchor.DefaultBlockchainAnchorRegistry
import com.trustweave.core.util.DigestUtils
import com.trustweave.did.exception.DidException
import com.trustweave.anchor.exceptions.BlockchainException
import com.trustweave.core.exception.TrustWeaveException
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import java.time.Instant

/**
 * Earth Observation (EO) Data Integrity Example - Complete Scenario
 *
 * This example demonstrates a comprehensive EO data integrity workflow using TrustWeave:
 * 1. Setup TrustWeave with blockchain anchoring
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
 * - DID creation and resolution
 * - Artifact creation with cryptographic digests
 * - Linkset creation for artifact linking
 * - Verifiable Credential issuance
 * - Blockchain anchoring
 * - Integrity chain verification
 * - Error handling with Result types and TrustWeaveException
 *
 * Run: `./gradlew :TrustWeave-examples:runEarthObservation`
 */
fun main() = runBlocking {
    println("=".repeat(70))
    println("Earth Observation - Complete Data Integrity Scenario")
    println("=".repeat(70))
    println()

    // Step 1: Setup TrustWeave with blockchain anchoring
    println("Step 1: Setting up TrustWeave with blockchain anchoring...")
    val chainId = "algorand:testnet"

    // Create TrustWeave instance with in-memory blockchain client for testing
    // In production, use AlgorandBlockchainAnchorClient or other blockchain clients
    // IMPORTANT: Store the client reference so we can reuse it for verification
    val anchorClient = InMemoryBlockchainAnchorClient(chainId)
    
    // Create KMS instance and capture reference for signer
    val kms = com.trustweave.testkit.kms.InMemoryKeyManagementService()
    val kmsRef = kms
    
    val trustweave = TrustWeave.build {
        factories(
            didMethodFactory = TestkitDidMethodFactory()
        )
        keys {
            custom(kmsRef)
            signer { data, keyId ->
                kmsRef.sign(com.trustweave.core.types.KeyId(keyId), data)
            }
            algorithm("Ed25519")
        }
        did {
            method("key") {
                algorithm("Ed25519")
            }
        }
        // Note: Chain is registered manually below, not via DSL
    }.also {
        it.configuration.registries.blockchainRegistry.register(chainId, anchorClient)
    }
    println("✓ TrustWeave instance created")
    println("✓ Blockchain client registered: $chainId")
    println("  - Mode: In-memory (for testing)")
    println("  - Note: In production, use real blockchain clients (Algorand, Ethereum, etc.)")
    println()

    // Step 2: Create DID for data provider
    println("Step 2: Creating DID for data provider...")
    println("\n📤 REQUEST: Create DID")
    println("  Purpose: Generate a decentralized identifier for the data provider")
    println("  Method: key (default)")
    println("  Parameters: Using default DID creation options")

    val issuerDid = try {
        trustweave.createDid()
    } catch (error: DidException.DidMethodNotRegistered) {
        println("\n📥 RESPONSE: DID Creation Failed")
        println("  ✗ Error Type: DidMethodNotRegistered")
        println("  ✗ Method: ${error.method}")
        println("  ✗ Available methods: ${error.availableMethods.joinToString(", ")}")
        return@runBlocking
    } catch (error: Throwable) {
        println("\n📥 RESPONSE: DID Creation Failed")
        println("  ✗ Error: ${error.message}")
        println("  ✗ Error Type: ${error::class.simpleName}")
        return@runBlocking
    }

    // Resolve the DID to get the document with verification methods
    println("\n📤 REQUEST: Resolve DID")
    println("  Purpose: Get DID document with verification methods")
    println("  DID: ${issuerDid.value}")

    val issuerDidResolution = try {
        trustweave.resolveDid(issuerDid)
    } catch (error: Throwable) {
        println("\n📥 RESPONSE: DID Resolution Failed")
        println("  ✗ Error: ${error.message}")
        return@runBlocking
    }

    val issuerDidDoc = when (issuerDidResolution) {
        is com.trustweave.did.resolver.DidResolutionResult.Success -> issuerDidResolution.document
        else -> {
            println("\n📥 RESPONSE: DID Resolution Failed")
            println("  ⚠ Status: No document found (may be in-memory)")
            return@runBlocking
        }
    }

    println("\n📥 RESPONSE: DID Created and Resolved Successfully")
    println("  ✓ DID Document ID: ${issuerDidDoc.id}")
    println("  ✓ Verification Methods Count: ${issuerDidDoc.verificationMethod.size}")
    println("\n  DID Document Details:")
    println("    - ID: ${issuerDidDoc.id}")
    println("    - Context: ${issuerDidDoc.context.joinToString(", ")}")
    println("    - Verification Methods: ${issuerDidDoc.verificationMethod.size}")
    println("    - Authentication: ${issuerDidDoc.authentication.size}")
    println("    - Services: ${issuerDidDoc.service.size}")
    println("\n  Verification Methods:")
    issuerDidDoc.verificationMethod.forEachIndexed { index, vm ->
        println("    ${index + 1}. ID: ${vm.id}")
        println("       Type: ${vm.type}")
        println("       Controller: ${vm.controller}")
    }
    val issuerKeyId = issuerDidDoc.verificationMethod.first().id.substringAfter("#")
    println("\n  ✓ Selected Issuer Key ID: $issuerKeyId")
    println()

    // Step 3: Create Artifacts (Metadata, Provenance, Quality)
    println("Step 3: Creating EO dataset artifacts...")
    println("\n" + "─".repeat(70))
    println("ARTIFACT TRACEABILITY - Complete Request/Response Logging")
    println("─".repeat(70))

    // Artifacts are the actual data documents that describe the EO dataset.
    // Each artifact gets a cryptographic digest (hash) that acts as a fingerprint.
    // If the artifact changes, the digest changes - making tampering detectable.

    // 1. Metadata Artifact: Describes WHAT the data is
    println("\n📤 REQUEST: Create Metadata Artifact")
    println("  Purpose: Create metadata document describing WHAT the EO dataset is")
    println("  Standard: ISO 19115 / DCAT (geospatial metadata)")
    println("  Parameters:")
    println("    - ID: metadata-1")
    println("    - Title: Sentinel-2 L2A Dataset - Central Europe")
    println("    - Description: Atmospherically corrected Sentinel-2 Level 2A product covering Central Europe region")

    val (metadataArtifact, metadataDigest) = TestDataBuilders.createMetadataArtifact(
        id = "metadata-1",
        title = "Sentinel-2 L2A Dataset - Central Europe",
        description = "Atmospherically corrected Sentinel-2 Level 2A product covering Central Europe region"
    )

    println("\n📥 RESPONSE: Metadata Artifact Created")
    println("  ✓ Artifact ID: ${metadataArtifact["id"]?.jsonPrimitive?.content}")
    println("  ✓ Artifact Type: ${metadataArtifact["type"]?.jsonPrimitive?.content}")
    println("  ✓ Digest (Multibase): $metadataDigest")
    println("  ✓ Media Type: ${metadataArtifact["mediaType"]?.jsonPrimitive?.content ?: "application/json"}")
    println("\n  Full Artifact Document:")
    val artifactJson = Json { prettyPrint = true; ignoreUnknownKeys = true }
    println(artifactJson.encodeToString(JsonObject.serializer(), metadataArtifact))

    // 2. Provenance Artifact: Describes WHERE the data came from
    println("\n" + "─".repeat(70))
    println("\n📤 REQUEST: Create Provenance Artifact")
    println("  Purpose: Create provenance document describing WHERE the data came from")
    println("  Standard: PROV (Provenance Ontology)")
    println("  Parameters:")
    println("    - ID: provenance-1")
    println("    - Activity: EO Data Collection")
    println("    - Agent: ${issuerDid.value}")

    val (provenanceArtifact, provenanceDigest) = TestDataBuilders.createProvenanceArtifact(
        id = "provenance-1",
        activity = "EO Data Collection",
        agent = issuerDid.value  // Links back to the DID we created in Step 2
    )

    println("\n📥 RESPONSE: Provenance Artifact Created")
    println("  ✓ Artifact ID: ${provenanceArtifact["id"]?.jsonPrimitive?.content}")
    println("  ✓ Artifact Type: ${provenanceArtifact["type"]?.jsonPrimitive?.content}")
    println("  ✓ Digest (Multibase): $provenanceDigest")
    println("  ✓ Media Type: ${provenanceArtifact["mediaType"]?.jsonPrimitive?.content ?: "application/json"}")
    println("\n  Full Artifact Document:")
    println(artifactJson.encodeToString(JsonObject.serializer(), provenanceArtifact))

    // 3. Quality Report Artifact: Describes HOW GOOD the data is
    println("\n" + "─".repeat(70))
    println("\n📤 REQUEST: Create Quality Report Artifact")
    println("  Purpose: Create quality report describing HOW GOOD the data is")
    println("  Standard: DQV (Data Quality Vocabulary)")
    println("  Parameters:")
    println("    - ID: quality-1")
    println("    - Quality Score: 0.95 (95%)")
    println("    - Metrics:")
    val qualityMetrics = mapOf(
        "completeness" to 0.98,      // 98% complete
        "accuracy" to 0.92,           // 92% accurate
        "temporalConsistency" to 0.96, // 96% temporally consistent
        "spatialAccuracy" to 0.94,    // 94% spatially accurate
        "cloudCoverage" to 0.05       // 5% cloud coverage
    )
    qualityMetrics.forEach { (key, value) ->
        println("      - $key: $value")
    }

    val (qualityArtifact, qualityDigest) = TestDataBuilders.createQualityReportArtifact(
        id = "quality-1",
        qualityScore = 0.95,  // 95% quality score
        metrics = qualityMetrics
    )

    println("\n📥 RESPONSE: Quality Report Artifact Created")
    println("  ✓ Artifact ID: ${qualityArtifact["id"]?.jsonPrimitive?.content}")
    println("  ✓ Artifact Type: ${qualityArtifact["type"]?.jsonPrimitive?.content}")
    println("  ✓ Digest (Multibase): $qualityDigest")
    println("  ✓ Media Type: ${qualityArtifact["mediaType"]?.jsonPrimitive?.content ?: "application/json"}")
    println("\n  Full Artifact Document:")
    println(artifactJson.encodeToString(JsonObject.serializer(), qualityArtifact))

    println("\n" + "─".repeat(70))
    println("✓ All artifacts created successfully")
    println("  Summary:")
    println("    - Metadata Artifact: $metadataDigest")
    println("    - Provenance Artifact: $provenanceDigest")
    println("    - Quality Report Artifact: $qualityDigest")
    println("  Each artifact has a unique digest (fingerprint) that proves its integrity")
    println("─".repeat(70))
    println()

    // Step 4: Create Linkset
    println("Step 4: Creating Linkset...")
    println("\n📤 REQUEST: Create Linkset")
    println("  Purpose: Create a Linkset that connects all artifacts together")
    println("  Standard: JSON-LD Linkset (W3C)")
    println("  Linkset ID: linkset-eo-sentinel2-l2a-xyz")
    println("  Links to create:")
    println("    1. metadata-1 (Digest: $metadataDigest)")
    println("    2. provenance-1 (Digest: $provenanceDigest)")
    println("    3. quality-1 (Digest: $qualityDigest)")

    // A Linkset is like a table of contents that links all artifacts together.
    // It contains references (links) to each artifact along with its digest.
    // This allows us to verify that all artifacts are present and untampered.

    // Create links to each artifact
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

    // IMPORTANT: We compute the Linkset digest BEFORE adding it to the Linkset
    // This avoids a circular dependency (digest depends on Linkset, but Linkset contains digest)
    // CRITICAL: The digest must be computed from the same structure that will be verified
    // The verifier removes only "digestMultibase", so we must include "id" and "@context" and "links"
    val linksetId = "linkset-eo-sentinel2-l2a-xyz"
    val linksetWithoutDigest = buildJsonObject {
        put("id", linksetId)  // Must include id field - verifier will include it when recomputing
        put("@context", "https://www.w3.org/ns/json-ld#")
        put("links", Json.encodeToJsonElement(links))
    }

    // Compute the digest of the Linkset (this becomes the Linkset's fingerprint)
    val linksetDigest = DigestUtils.sha256DigestMultibase(linksetWithoutDigest)

    // Now build the complete Linkset WITH the digest
    val linksetWithDigest = TestDataBuilders.buildLinkset(
        digestMultibase = linksetDigest,
        links = links,
        linksetId = linksetId
    )

    println("\n📥 RESPONSE: Linkset Created")
    println("  ✓ Linkset ID: $linksetId")
    println("  ✓ Digest (Multibase): $linksetDigest")
    println("  ✓ Number of Links: ${links.size}")
    println("\n  Full Linkset Document:")
    println(artifactJson.encodeToString(JsonObject.serializer(), linksetWithDigest))
    println("\n  Link Details:")
    links.forEachIndexed { index, link ->
        println("    ${index + 1}. HREF: ${link.href}")
        println("       Type: ${link.type}")
        println("       Digest: ${link.digestMultibase}")
    }
    println()

    // Step 5: Issue Verifiable Credential
    println("Step 5: Issuing Verifiable Credential...")
    println("\n📤 REQUEST: Issue Verifiable Credential")
    println("  Purpose: Issue a verifiable credential attesting to the EO dataset")
    println("  What it attests:")
    println("    - A specific EO dataset exists")
    println("    - It has associated metadata, provenance, and quality reports")
    println("    - These are linked together via the Linkset")
    println("  Parameters:")
    println("    - Issuer DID: ${issuerDid.value}")
    println("    - Issuer Key ID: $issuerKeyId")
    println("    - Credential Types: EarthObservationCredential, VerifiableCredential")

    // A Verifiable Credential (VC) is like a digital certificate that attests to something.
    // In our case, it attests that:
    // - A specific EO dataset exists
    // - It has associated metadata, provenance, and quality reports
    // - These are linked together via the Linkset

    // The "subject" is what the credential is about - our EO dataset
    // We include the Linkset digest in the credential subject
    // Note: Subject ID must be an IRI (URI/URL/DID/URN) per W3C VC spec
    val credentialSubject = buildJsonObject {
        put("id", "https://example.com/datasets/eo-dataset-sentinel2-l2a-xyz")
        put("type", "EarthObservationDataset")
        put("title", "Sentinel-2 L2A Dataset - Central Europe")
        put("description", "Atmospherically corrected Sentinel-2 Level 2A product")
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
        put("linksetDigest", linksetDigest)  // Reference to the Linkset digest
        put("datasetProvider", issuerDid.value)  // Reference to the data provider
    }
    println("  Credential Subject:")
    val subjectJson = Json { prettyPrint = true; ignoreUnknownKeys = true }
    println(subjectJson.encodeToString(JsonObject.serializer(), credentialSubject))

    // Issue the credential using TrustWeave facade
    val credential = trustweave.issue {
        credential {
            type("EarthObservationCredential")
            issuer(issuerDid.value)
            subject {
                val subjectId = credentialSubject["id"]?.jsonPrimitive?.content
                if (subjectId != null) {
                    id(subjectId)
                }
                credentialSubject.forEach { (key, value) ->
                    if (key != "id") {
                        // JsonElement values (JsonObject, JsonArray, JsonPrimitive) are supported directly
                        key to value
                    }
                }
            }
            issued(java.time.Instant.now())
        }
        signedBy(issuerDid = issuerDid.value, keyId = issuerKeyId)
    }

    println("\n📥 RESPONSE: Credential Issued Successfully")
    println("  ✓ Credential ID: ${credential.id}")
    println("  ✓ Issuer: ${credential.issuer}")
    println("  ✓ Types: ${credential.type.joinToString(", ")}")
    println("  ✓ Issuance Date: ${credential.issuanceDate}")
    println("  ✓ Has Proof: ${credential.proof != null}")
    val proof = credential.proof
    if (proof != null) {
        println("  ✓ Proof Type: ${proof.type}")
        println("  ✓ Proof Purpose: ${proof.proofPurpose}")
    }
    println("  ✓ Linkset Digest Reference: $linksetDigest")
    println("\n  Full Credential Document:")
    val credentialJsonFormatter = Json { prettyPrint = true; ignoreUnknownKeys = true }
    println(credentialJsonFormatter.encodeToString(VerifiableCredential.serializer(), credential))

    println()

    // Step 6: Verify the credential
    println("Step 6: Verifying credential...")
    println("\n📤 REQUEST: Verify Verifiable Credential")
    println("  Purpose: Verify the cryptographic proof and validity of the credential")
    println("  Credential ID: ${credential.id}")
    println("  Checks performed:")
    println("    - Cryptographic proof verification")
    println("    - Issuer DID resolution and validation")
    println("    - Expiration check")
    println("    - Revocation status check")

    val verification = trustweave.verifyCredential(credential)

    println("\n📥 RESPONSE: Credential Verification Result")
    if (verification.valid) {
        println("  ✓ Overall Status: VALID")
        println("  ✓ Proof Valid: ${verification.proofValid}")
        println("  ✓ Issuer Valid: ${verification.issuerValid}")
        println("  ✓ Not Expired: ${verification.notExpired}")
        println("  ✓ Not Revoked: ${verification.notRevoked}")
        if (verification.allWarnings.isNotEmpty()) {
            println("  ⚠ Warnings:")
            verification.allWarnings.forEach { warning ->
                println("    - $warning")
            }
        }
    } else {
        println("  ✗ Overall Status: INVALID")
        println("  ✗ Errors:")
        verification.allErrors.forEach { error ->
            println("    - $error")
        }
        if (verification.allWarnings.isNotEmpty()) {
            println("  ⚠ Warnings:")
            verification.allWarnings.forEach { warning ->
                println("    - $warning")
            }
        }
    }
    println()

    // Step 7: Anchor VC digest to blockchain
    println("Step 7: Anchoring VC digest to blockchain...")
    println("\n📤 REQUEST: Anchor Data to Blockchain")
    println("  Purpose: Store VC digest immutably on blockchain for timestamping and integrity")
    println("  Chain ID: $chainId")
    println("  Mode: In-memory (for testing)")
    println("  Note: In production, this would write to a real blockchain")

    // Convert credential to JsonElement for anchoring
    val anchorJson = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }
    val credentialJson = anchorJson.encodeToJsonElement(VerifiableCredential.serializer(), credential)

    // Add linksetDigest at top level for integrity verification
    // This must be done BEFORE computing the VC digest
    val credentialWithLinksetRef = buildJsonObject {
        credentialJson.jsonObject.entries.forEach { (key, value) ->
            put(key, value)
        }
        // Add linkset digest reference at top level for verification
        put("linksetDigest", linksetDigest)
    }

    // Compute VC digest for anchoring (without metadata fields)
    val vcWithoutMetadata = buildJsonObject {
        credentialWithLinksetRef.entries.forEach { (key, value) ->
            if (key != "digestMultibase" && key != "evidence" && key != "credentialStatus") {
                put(key, value)
            }
        }
    }
    val vcDigest = DigestUtils.sha256DigestMultibase(vcWithoutMetadata)

    // Compute VC digest for anchoring
    // In production, you might want to anchor just the digest to save space
    val vcDigestPayload = buildJsonObject {
        put("vcId", credential.id)
        put("vcDigest", vcDigest)
        put("digestMultibase", vcDigest) // Also include as digestMultibase for verifier
        put("issuer", issuerDid.value)
        put("linksetDigest", linksetDigest)
        put("timestamp", Instant.now().toString())
    }
    println("  Payload to anchor:")
    println(anchorJson.encodeToString(JsonObject.serializer(), vcDigestPayload))

    val anchor = try {
        trustweave.blockchains.anchor(
            data = vcDigestPayload,
            serializer = JsonElement.serializer(),
            chainId = chainId
        )
    } catch (error: BlockchainException.ChainNotRegistered) {
        println("\n📥 RESPONSE: Anchoring Failed")
        println("  ✗ Error Type: ChainNotRegistered")
        println("  ✗ Chain ID: ${error.chainId}")
        println("  ✗ Available chains: ${error.availableChains.joinToString(", ")}")
        return@runBlocking
    } catch (error: TrustWeaveException.ValidationFailed) {
        println("\n📥 RESPONSE: Anchoring Failed")
        println("  ✗ Error Type: ValidationFailed")
        println("  ✗ Reason: ${error.reason}")
        println("  ✗ Field: ${error.field}")
        println("  ✗ Value: ${error.value}")
        return@runBlocking
    } catch (error: Throwable) {
        println("\n📥 RESPONSE: Anchoring Failed")
        println("  ✗ Error: ${error.message}")
        println("  ✗ Error Type: ${error::class.simpleName}")
        if (error is TrustWeaveException && error.context.isNotEmpty()) {
            println("  Context:")
            error.context.forEach { (key, value) ->
                println("    - $key: $value")
            }
        }
        return@runBlocking
    }

    println("\n📥 RESPONSE: Data Anchored Successfully")
    println("  ✓ Chain ID: ${anchor.ref.chainId}")
    println("  ✓ Transaction Hash: ${anchor.ref.txHash}")
    println("  ✓ Anchor Reference:")
    println("    - Chain ID: ${anchor.ref.chainId}")
    println("    - Transaction Hash: ${anchor.ref.txHash}")
    anchor.ref.contract?.let {
        println("    - Contract: $it")
    }
    println()

    // Step 8: Read back anchored data
    println("Step 8: Reading anchored data from blockchain...")
    println("\n📤 REQUEST: Read Anchored Data from Blockchain")
    println("  Purpose: Retrieve and verify the anchored data from blockchain")
    println("  Anchor Reference:")
    println("    - Chain ID: ${anchor.ref.chainId}")
    println("    - Transaction Hash: ${anchor.ref.txHash}")

    val readJson = try {
        trustweave.blockchains.read<JsonElement>(
            ref = anchor.ref,
            serializer = JsonElement.serializer()
        )
    } catch (error: Throwable) {
        println("\n📥 RESPONSE: Reading Anchored Data Failed")
        println("  ✗ Error: ${error.message}")
        return@runBlocking
    }

    println("\n📥 RESPONSE: Anchored Data Retrieved")
    println("  ✓ Status: Successfully read from blockchain")
    println("  ✓ VC ID: ${readJson.jsonObject["vcId"]?.jsonPrimitive?.content}")
    println("  ✓ VC Digest: ${readJson.jsonObject["vcDigest"]?.jsonPrimitive?.content}")
    println("  ✓ Issuer: ${readJson.jsonObject["issuer"]?.jsonPrimitive?.content}")
    println("  ✓ Linkset Digest: ${readJson.jsonObject["linksetDigest"]?.jsonPrimitive?.content}")
    println("  ✓ Timestamp: ${readJson.jsonObject["timestamp"]?.jsonPrimitive?.content}")
    println("\n  Full Anchored Payload:")
    println(anchorJson.encodeToString(JsonObject.serializer(), readJson.jsonObject))

    // Verify data integrity
    val readVcDigest = readJson.jsonObject["vcDigest"]?.jsonPrimitive?.content
    println("\n  Integrity Verification:")
    println("    Expected VC Digest: $vcDigest")
    println("    Retrieved VC Digest: $readVcDigest")
    if (readVcDigest == vcDigest) {
        println("    ✓ Status: MATCH - Data integrity verified")
    } else {
        println("    ✗ Status: MISMATCH - Data integrity check failed")
        return@runBlocking
    }
    println()

    // Step 9: Verify integrity chain
    println("Step 9: Verifying integrity chain...")
    println("\n📤 REQUEST: Verify Complete Integrity Chain")
    println("  Purpose: Verify the complete integrity chain from blockchain → VC → Linkset → Artifacts")
    println("  Verification Flow:")
    println("    1. Verify VC digest matches what's stored on the blockchain")
    println("    2. Verify Linkset digest matches the reference in the VC")
    println("    3. Verify each artifact's digest matches its link in the Linkset")
    println("  Inputs:")
    println("    - VC with digest: ${credential.id}")
    println("    - Linkset ID: linkset-eo-sentinel2-l2a-xyz")
    println("    - Artifacts: metadata-1, provenance-1, quality-1")
    println("    - Anchor Reference: ${anchor.ref.txHash}")

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
    println("  Artifacts Map:")
    artifacts.forEach { (id, artifact) ->
        val artifactDigest = artifact["digestMultibase"]?.jsonPrimitive?.content ?: "N/A"
        println("    - $id: $artifactDigest")
    }

    // Use the same VC structure that was used for anchoring (with linksetDigest at top level)
    // Add VC digest to the VC object for verification
    val vcWithDigest = buildJsonObject {
        credentialWithLinksetRef.entries.forEach { (key, value) ->
            put(key, value)
        }
        put("digestMultibase", vcDigest)
    }

    // Debug: Show what we're verifying
    println("  Debug Information:")
    println("    - VC Digest (computed for anchoring): $vcDigest")
    println("    - Linkset Digest (expected in VC): $linksetDigest")
    println("    - VC has linksetDigest at top level: ${vcWithDigest["linksetDigest"] != null}")
    val vcLinksetRef = vcWithDigest["linksetDigest"]?.jsonPrimitive?.content
    println("    - VC linksetDigest value: $vcLinksetRef")
    if (vcLinksetRef != linksetDigest) {
        println("    ⚠ WARNING: VC linksetDigest ($vcLinksetRef) does not match expected ($linksetDigest)")
    }

    // CRITICAL: The blockchain registry must use the SAME client instance that was used for anchoring
    // Since InMemoryBlockchainAnchorClient stores data in memory, each new instance is empty
    // We stored the client reference at the beginning, so we can reuse it here
    // In production with real blockchains, this wouldn't be an issue as the blockchain is shared
    val verificationRegistry = DefaultBlockchainAnchorRegistry().apply {
        register(chainId, anchorClient)  // Use the SAME client instance
    }

    // Perform the complete integrity verification
    val integrityResult = IntegrityVerifier.verifyIntegrityChain(
        vc = vcWithDigest,
        linkset = linksetWithDigest,
        artifacts = artifacts,
        anchorRef = anchor.ref,
        registry = verificationRegistry
    )

    // Display verification results
    println("\n📥 RESPONSE: Integrity Chain Verification Result")
    if (integrityResult.valid) {
        println("  ✓ Overall Status: PASSED")
        println("  ✓ All verification steps completed successfully")
        println("\n  Detailed Verification Steps:")
        integrityResult.steps.forEachIndexed { index, step ->
            println("    Step ${index + 1}: ${step.name}")
            println("      Status: ${if (step.valid) "✓ PASS" else "✗ FAIL"}")
            if (step.digest != null) {
                println("      Digest: ${step.digest}")
            }
            if (!step.valid && step.error != null) {
                println("      Error: ${step.error}")
            }
        }
        println("\n  ✓ Conclusion: All checks passed! The data integrity chain is valid.")
    } else {
        println("  ✗ Overall Status: FAILED")
        println("  ✗ One or more verification steps failed")
        println("\n  Detailed Verification Steps:")
        integrityResult.steps.forEachIndexed { index, step ->
            println("    Step ${index + 1}: ${step.name}")
            println("      Status: ${if (step.valid) "✓ PASS" else "✗ FAIL"}")
            if (step.digest != null) {
                println("      Digest: ${step.digest}")
            }
            if (!step.valid && step.error != null) {
                println("      Error: ${step.error}")
            }
        }
        println("\n  ✗ Conclusion: One or more checks failed. The data may have been tampered with.")
        return@runBlocking
    }
    println()

    // Step 10: Demonstrate error handling scenarios
    println("Step 10: Demonstrating error handling...")

    // Test invalid chain ID
    println("  Testing invalid chain ID...")
    try {
        trustweave.blockchains.anchor(
            data = buildJsonObject { put("test", "data") },
            serializer = JsonElement.serializer(),
            chainId = "invalid:chain:id"
        )
        println("  ⚠ Unexpected success with invalid chain ID")
    } catch (error: BlockchainException.ChainNotRegistered) {
        println("  ✓ Correctly rejected invalid chain ID: ${error.chainId}")
        println("    Available chains: ${error.availableChains.joinToString(", ")}")
    } catch (error: TrustWeaveException.ValidationFailed) {
        println("  ✓ Correctly rejected invalid chain ID format: ${error.reason}")
    } catch (error: Throwable) {
        println("  ✓ Error handling works: ${error.message}")
    }
    println()

    // Summary
    println("=".repeat(70))
    println("Scenario Summary")
    println("=".repeat(70))
    println("✓ TrustWeave instance created with blockchain integration")
    println("✓ Issuer DID: ${issuerDid.value}")
    println("✓ Issuer Key ID: $issuerKeyId")
    println("✓ Artifacts created: 3")
    println("  - Metadata: $metadataDigest")
    println("  - Provenance: $provenanceDigest")
    println("  - Quality Report: $qualityDigest")
    println("✓ Linkset created: $linksetDigest")
    println("  - Links: ${links.size}")
    println("✓ Credential issued: ${credential.id}")
    println("  - Types: ${credential.type.joinToString(", ")}")
    println("  - Has proof: ${credential.proof != null}")
    println("✓ Credential verified: ${verification.valid}")
    println("  - Proof valid: ${verification.proofValid}")
    println("  - Issuer valid: ${verification.issuerValid}")
    println("✓ Credential digest anchored to blockchain: ${anchor.ref.txHash}")
    println("  - Chain ID: ${anchor.ref.chainId}")
    println("  - Transaction Hash: ${anchor.ref.txHash}")
    println("✓ Data integrity verified: VC digest matches anchored data")
    println("✓ Integrity chain verified: ${integrityResult.valid}")
    println("  - Verification steps: ${integrityResult.steps.size}")
    println("  - All steps passed: ${integrityResult.steps.all { it.valid }}")
    println()
    println("=".repeat(70))
    println("✅ Complete Earth Observation Scenario Successful!")
    println("=".repeat(70))
    println()
    println("Next Steps:")
    println("  - In production, use real blockchain clients (Algorand, Ethereum, etc.)")
    println("  - Configure proper DID methods for your use case")
    println("  - Implement artifact storage and retrieval mechanisms")
    println("  - Add monitoring and logging for production deployments")
    println("  - Implement proper error handling and retry logic")
    println("=".repeat(70))
}
