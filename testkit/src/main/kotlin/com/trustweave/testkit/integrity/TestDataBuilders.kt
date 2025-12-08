package com.trustweave.testkit.integrity

import com.trustweave.core.util.DigestUtils
import com.trustweave.testkit.integrity.models.*
import kotlinx.serialization.json.*
import kotlinx.datetime.Clock

/**
 * Test data builders for creating VC, Linkset, and artifact structures for integrity verification tests.
 *
 * Provides fluent builders for creating test data structures commonly used in TrustWeave tests.
 * All builders follow consistent patterns and include proper digest computation.
 *
 * **Example Usage**:
 * ```
 * // Create artifacts
 * val (metadata, metadataDigest) = TestDataBuilders.createMetadataArtifact("id", "Title", "Desc")
 *
 * // Build VC
 * val vc = TestDataBuilders.buildVc(issuerDid, subject, digestMultibase)
 *
 * // Build Linkset
 * val linkset = TestDataBuilders.buildLinkset(digest, links)
 * ```
 */
object TestDataBuilders {

    /**
     * Builds a Verifiable Credential JSON object.
     */
    fun buildVc(
        issuerDid: String,
        subject: JsonElement,
        digestMultibase: String,
        evidence: List<BlockchainAnchorEvidence>? = null,
        credentialStatus: CredentialStatus? = null,
        vcId: String? = null,
        issued: String? = null
    ): JsonObject {
        return buildJsonObject {
            if (vcId != null) {
                put("id", vcId)
            }
            put("type", buildJsonArray {
                add("VerifiableCredential")
            })
            put("issuer", issuerDid)
            put("credentialSubject", subject)
            // Only add digestMultibase if it's not empty
            if (digestMultibase.isNotEmpty()) {
                put("digestMultibase", digestMultibase)
            }
            if (evidence != null && evidence.isNotEmpty()) {
                put("evidence", Json.encodeToJsonElement(evidence))
            }
            if (credentialStatus != null) {
                put("credentialStatus", Json.encodeToJsonElement(credentialStatus))
            }
            put("issued", issued ?: Clock.System.now().toString())
        }
    }

    /**
     * Builds a Linkset JSON object.
     */
    fun buildLinkset(
        digestMultibase: String,
        links: List<Link>,
        linksetId: String? = null
    ): JsonObject {
        return buildJsonObject {
            if (linksetId != null) {
                put("id", linksetId)
            }
            put("@context", "https://www.w3.org/ns/json-ld#")
            put("digestMultibase", digestMultibase)
            put("links", Json.encodeToJsonElement(links))
        }
    }

    /**
     * Builds a Link object.
     */
    fun buildLink(
        href: String,
        digestMultibase: String,
        type: String? = null,
        rel: String? = null
    ): Link {
        return Link(
            href = href,
            digestMultibase = digestMultibase,
            type = type,
            rel = rel
        )
    }

    /**
     * Builds an artifact representation with digest.
     */
    fun buildArtifact(
        id: String,
        type: String,
        content: JsonElement,
        digestMultibase: String,
        mediaType: String = "application/json"
    ): JsonObject {
        return buildJsonObject {
            put("id", id)
            put("type", type)
            put("content", content)
            put("digestMultibase", digestMultibase)
            put("mediaType", mediaType)
        }
    }

    /**
     * Builds blockchain anchor evidence.
     */
    fun buildAnchorEvidence(
        chainId: String,
        txHash: String,
        digestMultibase: String,
        network: String? = null,
        contract: String? = null
    ): BlockchainAnchorEvidence {
        return BlockchainAnchorEvidence(
            chainId = chainId,
            network = network,
            txHash = txHash,
            digestMultibase = digestMultibase,
            contract = contract
        )
    }

    /**
     * Builds credential status pointing to a status service.
     */
    fun buildCredentialStatus(
        statusServiceUrl: String,
        statusListIndex: String? = null
    ): CredentialStatus {
        return CredentialStatus(
            id = statusServiceUrl,
            type = "StatusList2021Entry",
            statusPurpose = "revocation",
            statusListIndex = statusListIndex
        )
    }

    /**
     * Builds an anchor service endpoint for DID document.
     */
    fun buildAnchorService(
        chainId: String,
        anchorLookupPattern: String? = null,
        baseUrl: String? = null
    ): AnchorService {
        return AnchorService(
            id = "#anchor-service",
            type = "AnchorService",
            serviceEndpoint = AnchorServiceEndpoint(
                chainId = chainId,
                anchorLookup = anchorLookupPattern,
                baseUrl = baseUrl
            )
        )
    }

    /**
     * Builds a status response with anchor information.
     */
    fun buildStatusResponse(
        revocationStatus: String,
        anchorInfo: AnchorInfo? = null
    ): StatusResponse {
        return StatusResponse(
            revocationStatus = revocationStatus,
            anchor = anchorInfo
        )
    }

    /**
     * Builds anchor information.
     */
    fun buildAnchorInfo(
        chainId: String,
        txHash: String,
        digestMultibase: String,
        timestamp: Long? = null,
        contract: String? = null
    ): AnchorInfo {
        return AnchorInfo(
            chainId = chainId,
            txHash = txHash,
            digestMultibase = digestMultibase,
            timestamp = timestamp,
            contract = contract
        )
    }

    /**
     * Builds an anchor manifest for batch anchoring.
     */
    fun buildAnchorManifest(
        manifestId: String,
        chainId: String,
        anchoredDigests: List<String>,
        contract: String? = null,
        issuer: String? = null
    ): AnchorManifest {
        return AnchorManifest(
            id = manifestId,
            anchorContext = AnchorContext(
                chainId = chainId,
                contract = contract
            ),
            anchoredDigests = anchoredDigests,
            issuer = issuer,
            timestamp = System.currentTimeMillis() / 1000
        )
    }

    /**
     * Creates sample metadata artifact (ISO 19115 / DCAT style).
     */
    fun createMetadataArtifact(id: String, title: String, description: String): Pair<JsonObject, String> {
        val metadata = buildJsonObject {
            put("title", title)
            put("description", description)
            put("type", "Dataset")
            put("created", Clock.System.now().toString())
        }
        val digest = DigestUtils.sha256DigestMultibase(metadata)
        val artifact = buildArtifact(id, "Metadata", metadata, digest)
        return Pair(artifact, digest)
    }

    /**
     * Creates sample provenance artifact (PROV style).
     */
    fun createProvenanceArtifact(id: String, activity: String, agent: String): Pair<JsonObject, String> {
        val provenance = buildJsonObject {
            put("activity", activity)
            put("agent", agent)
            put("timestamp", Clock.System.now().toString())
        }
        val digest = DigestUtils.sha256DigestMultibase(provenance)
        val artifact = buildArtifact(id, "Provenance", provenance, digest)
        return Pair(artifact, digest)
    }

    /**
     * Creates sample quality report artifact (DQV style).
     */
    fun createQualityReportArtifact(id: String, qualityScore: Double, metrics: Map<String, Any>): Pair<JsonObject, String> {
        val qualityReport = buildJsonObject {
            put("qualityScore", qualityScore)
            put("metrics", buildJsonObject {
                metrics.forEach { (key, value) ->
                    put(key, value.toString())
                }
            })
            put("timestamp", Clock.System.now().toString())
        }
        val digest = DigestUtils.sha256DigestMultibase(qualityReport)
        val artifact = buildArtifact(id, "QualityReport", qualityReport, digest)
        return Pair(artifact, digest)
    }
}

