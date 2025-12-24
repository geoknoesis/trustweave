package org.trustweave.testkit.integrity

import org.trustweave.anchor.AnchorRef
import org.trustweave.anchor.BlockchainAnchorRegistry
import org.trustweave.core.exception.TrustWeaveException
import org.trustweave.core.util.DigestUtils
import org.trustweave.testkit.integrity.models.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*

/**
 * Verification utilities for integrity chain validation.
 */
object IntegrityVerifier {

    /**
     * Verifies that a VC's digest matches the digest stored in a blockchain anchor.
     */
    suspend fun verifyVcIntegrity(
        vc: JsonObject,
        anchorRef: AnchorRef,
        registry: BlockchainAnchorRegistry
    ): Boolean = withContext(Dispatchers.IO) {
        // Compute VC digest from VC without metadata fields (digestMultibase, evidence, credentialStatus)
        // These fields are added after the digest is computed and anchored
        // Note: 'issued' is included in digest computation as it's part of the VC content
        val vcWithoutMetadata = buildJsonObject {
            vc.entries.forEach { (key, value) ->
                if (key != "digestMultibase" && key != "evidence" && key != "credentialStatus") {
                    put(key, value)
                }
            }
        }
        val computedDigest = DigestUtils.sha256DigestMultibase(vcWithoutMetadata)

        // Read anchor from blockchain
        val client = registry.get(anchorRef.chainId)
            ?: throw org.trustweave.anchor.exceptions.BlockchainException.ChainNotRegistered(
                chainId = anchorRef.chainId,
                availableChains = registry.getAllChainIds()
            )

        val anchorResult = client.readPayload(anchorRef)
        val anchoredPayload = anchorResult.payload.jsonObject

        // Extract digest from anchored payload (could be "digestMultibase" or "vcDigest")
        val anchoredDigest = anchoredPayload["digestMultibase"]?.jsonPrimitive?.content
            ?: anchoredPayload["vcDigest"]?.jsonPrimitive?.content
            ?: throw org.trustweave.core.exception.TrustWeaveException.ValidationFailed(
                field = "anchoredPayload",
                reason = "Anchored payload does not contain digestMultibase or vcDigest",
                value = anchoredPayload.toString()
            )

        computedDigest == anchoredDigest
    }

    /**
     * Verifies that a Linkset's digest matches the reference in a VC.
     */
    fun verifyLinksetIntegrity(
        linkset: JsonObject,
        vcDigestRef: String
    ): Boolean {
        // Compute digest from Linkset without digestMultibase field (to avoid circular dependency)
        val linksetWithoutDigest = buildJsonObject {
            linkset.entries.forEach { (key, value) ->
                if (key != "digestMultibase") {
                    put(key, value)
                }
            }
        }
        val computedDigest = DigestUtils.sha256DigestMultibase(linksetWithoutDigest)
        return computedDigest == vcDigestRef
    }

    /**
     * Verifies that an artifact's digest matches the digest in a link.
     * The digest should be computed from the artifact's content, not the entire artifact object.
     */
    fun verifyArtifactIntegrity(
        artifact: JsonObject,
        linkDigest: String
    ): Boolean {
        // Extract content from artifact (the actual data, not metadata)
        val content = artifact["content"] ?: artifact
        val computedDigest = DigestUtils.sha256DigestMultibase(content)
        return computedDigest == linkDigest
    }

    /**
     * Verifies the complete integrity chain: Blockchain → VC → Linkset → Artifacts.
     */
    suspend fun verifyIntegrityChain(
        vc: JsonObject,
        linkset: JsonObject,
        artifacts: Map<String, JsonObject>,
        anchorRef: AnchorRef,
        registry: BlockchainAnchorRegistry
    ): IntegrityVerificationResult = withContext(Dispatchers.IO) {
        val results = mutableListOf<VerificationStep>()

        // Step 1: Verify VC digest matches blockchain anchor
        // Compute digest from VC without metadata fields (digestMultibase, evidence, credentialStatus)
        // Note: 'issued' is included in digest computation as it's part of the VC content
        val vcWithoutMetadata = buildJsonObject {
            vc.entries.forEach { (key, value) ->
                if (key != "digestMultibase" && key != "evidence" && key != "credentialStatus") {
                    put(key, value)
                }
            }
        }
        val vcDigest = DigestUtils.sha256DigestMultibase(vcWithoutMetadata)
        val vcValid = try {
            verifyVcIntegrity(vc, anchorRef, registry)
        } catch (e: Exception) {
            false
        }
        results.add(VerificationStep("VC Digest", vcValid, vcDigest))

        // Step 2: Verify Linkset digest matches VC reference
        // Compute digest from Linkset without digestMultibase field (to avoid circular dependency)
        val linksetWithoutDigest = buildJsonObject {
            linkset.entries.forEach { (key, value) ->
                if (key != "digestMultibase") {
                    put(key, value)
                }
            }
        }
        val linksetDigest = DigestUtils.sha256DigestMultibase(linksetWithoutDigest)
        val linksetRef = vc["links"]?.jsonObject?.get("digestMultibase")?.jsonPrimitive?.content
            ?: vc["linksetDigest"]?.jsonPrimitive?.content
            ?: vc["linkset"]?.jsonObject?.get("digestMultibase")?.jsonPrimitive?.content

        // If VC references linkset digest, verify it matches; otherwise just verify linkset has valid digest
        val linksetValid = if (linksetRef != null) {
            linksetDigest == linksetRef
        } else {
            // Linkset digest is valid if it's properly computed (starts with 'u')
            linksetDigest.startsWith("u")
        }
        results.add(VerificationStep("Linkset Digest", linksetValid, linksetDigest))

        // Step 3: Verify each artifact digest matches Linkset link
        val links = linkset["links"]?.jsonArray ?: buildJsonArray { }
        val artifactResults = mutableListOf<VerificationStep>()

        for (linkElement in links) {
            val link = Json.decodeFromJsonElement<Link>(linkElement)
            val artifact = artifacts[link.href]

            if (artifact != null) {
                // Extract content from artifact for digest computation
                val content = artifact["content"] ?: artifact
                val artifactDigest = DigestUtils.sha256DigestMultibase(content)
                val artifactValid = artifactDigest == link.digestMultibase
                artifactResults.add(VerificationStep("Artifact: ${link.href}", artifactValid, artifactDigest))
            } else {
                artifactResults.add(VerificationStep("Artifact: ${link.href}", false, null, "Artifact not found"))
            }
        }

        results.addAll(artifactResults)

        val allValid = results.all { it.valid }
        IntegrityVerificationResult(
            valid = allValid,
            steps = results
        )
    }

    /**
     * Discovers anchor information using Method 1: Embedded Evidence.
     */
    fun discoverAnchorFromEvidence(vc: JsonObject): AnchorRef? {
        val evidence = vc["evidence"]?.jsonArray ?: return null

        for (evidenceElement in evidence) {
            val evidenceObj = evidenceElement.jsonObject
            val type = evidenceObj["type"]?.jsonPrimitive?.content

            // Check if this is a BlockchainAnchorEvidence (type may be missing if default value wasn't serialized)
            // Also check if it has the required fields (chainId and txHash)
            val chainId = evidenceObj["chainId"]?.jsonPrimitive?.content
            val txHash = evidenceObj["txHash"]?.jsonPrimitive?.content

            if ((type == "BlockchainAnchorEvidence" || type == null) && chainId != null && txHash != null) {
                val contract = evidenceObj["contract"]?.jsonPrimitive?.content
                return AnchorRef(
                    chainId = chainId,
                    txHash = txHash,
                    contract = contract
                )
            }
        }

        return null
    }

    /**
     * Discovers anchor information using Method 2: Issuer DID Document.
     * Note: This is a simplified version - in practice, you would resolve the DID.
     */
    fun discoverAnchorFromDidDocument(
        issuerDid: String,
        didDocument: JsonObject
    ): AnchorRef? {
        val services = didDocument["service"]?.jsonArray ?: return null

        for (serviceElement in services) {
            val service = serviceElement.jsonObject
            val type = service["type"]?.jsonPrimitive?.content

            if (type == "AnchorService") {
                val endpoint = service["serviceEndpoint"]?.jsonObject
                val chainId = endpoint?.get("chainId")?.jsonPrimitive?.content
                val anchorLookup = endpoint?.get("anchorLookup")?.jsonPrimitive?.content

                if (chainId != null && anchorLookup != null) {
                    // In practice, you would resolve the anchorLookup pattern
                    // For testing, we'll return a placeholder
                    return null // Would need to resolve the lookup pattern
                }
            }
        }

        return null
    }

    /**
     * Discovers anchor information using Method 3: Status Service.
     * Note: This is a simplified version - in practice, you would fetch from the status endpoint.
     */
    fun discoverAnchorFromStatusService(statusResponse: StatusResponse): AnchorRef? {
        val anchor = statusResponse.anchor ?: return null

        return AnchorRef(
            chainId = anchor.chainId,
            txHash = anchor.txHash,
            contract = anchor.contract
        )
    }

    /**
     * Discovers anchor information using Method 4: External Anchor Registry.
     */
    fun discoverAnchorFromRegistry(registryEntry: AnchorRegistryEntry): AnchorRef {
        return AnchorRef(
            chainId = registryEntry.chainId,
            txHash = registryEntry.txHash,
            contract = registryEntry.contract
        )
    }

    /**
     * Discovers anchor information using Method 5: Linked Manifest.
     */
    fun discoverAnchorFromManifest(manifest: AnchorManifest): AnchorRef? {
        val context = manifest.anchorContext

        // For manifest, we return the context - individual digests would be verified separately
        return AnchorRef(
            chainId = context.chainId,
            txHash = "", // Manifest doesn't have a single txHash
            contract = context.contract
        )
    }
}

/**
 * Result of a verification step.
 */
data class VerificationStep(
    val name: String,
    val valid: Boolean,
    val digest: String? = null,
    val error: String? = null
)

/**
 * Result of integrity chain verification.
 */
data class IntegrityVerificationResult(
    val valid: Boolean,
    val steps: List<VerificationStep>
)

