package com.trustweave.credential.anchor

import com.trustweave.anchor.BlockchainAnchorClient
import com.trustweave.anchor.AnchorRef
import com.trustweave.anchor.AnchorResult
import com.trustweave.credential.model.vc.VerifiableCredential
import com.trustweave.credential.model.Evidence
import com.trustweave.core.util.DigestUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import kotlinx.datetime.Clock

/**
 * Credential anchoring service.
 *
 * Integrates credential management with blockchain anchoring.
 * Computes credential digests and anchors them to blockchain for
 * tamper-proof verification.
 *
 * **Example Usage**:
 * ```kotlin
 * val anchorService = CredentialAnchorService(
 *     anchorClient = anchorClient
 * )
 *
 * val result = anchorService.anchorCredential(
 *     credential = credential,
 *     chainId = "algorand:testnet",
 *     options = AnchorOptions(includeProof = true)
 * )
 * ```
 */
class CredentialAnchorService(
    private val anchorClient: BlockchainAnchorClient
) {
    private val json = Json {
        prettyPrint = false
        encodeDefaults = false
        ignoreUnknownKeys = true
    }

    /**
     * Anchor a credential to blockchain.
     *
     * @param credential Credential to anchor
     * @param chainId Chain identifier (e.g., "algorand:testnet")
     * @param options Anchor options
     * @return Anchor result containing anchor reference and updated credential
     */
    suspend fun anchorCredential(
        credential: VerifiableCredential,
        chainId: String,
        options: AnchorOptions = AnchorOptions()
    ): CredentialAnchorResult = withContext(Dispatchers.IO) {
        // Prepare credential for anchoring
        val credentialToAnchor = if (options.includeProof) {
            credential
        } else {
            credential.copy(proof = null)
        }

        // Serialize credential to JSON
        val credentialJson = json.encodeToJsonElement(VerifiableCredential.serializer(), credentialToAnchor)
        
        // Compute digest
        val credentialJsonString = json.encodeToString(JsonElement.serializer(), credentialJson)
        val digest = DigestUtils.sha256DigestMultibase(credentialJsonString)

        // Anchor to blockchain
        val anchorResult = anchorClient.writePayload(
            payload = credentialJson,
            mediaType = "application/vc+json"
        )

        // Add evidence to credential if requested
        val updatedCredential = if (options.addEvidenceToCredential) {
            val evidence = createBlockchainAnchorEvidence(
                chainId = chainId,
                anchorRef = anchorResult.ref,
                digest = digest
            )
            val existingEvidence = credential.evidence ?: emptyList()
            credential.copy(evidence = existingEvidence + evidence)
        } else {
            credential
        }

        CredentialAnchorResult(
            anchorRef = anchorResult.ref,
            credential = updatedCredential,
            digest = digest
        )
    }

    /**
     * Verify that a credential is anchored on blockchain.
     *
     * @param credential Credential to verify
     * @param chainId Chain identifier
     * @return true if credential is anchored and verified
     */
    suspend fun verifyAnchoredCredential(
        credential: VerifiableCredential,
        chainId: String
    ): Boolean = withContext(Dispatchers.IO) {
        // Find anchor evidence
        val anchorEvidence = credential.evidence?.find { evidence ->
            evidence.type.contains("BlockchainAnchorEvidence") &&
            evidence.evidenceDocument?.jsonObject?.get("chainId")?.jsonPrimitive?.content == chainId
        } ?: return@withContext false

        // Extract anchor reference from evidence
        val evidenceDoc = anchorEvidence.evidenceDocument?.jsonObject ?: return@withContext false
        val txHash = evidenceDoc["txHash"]?.jsonPrimitive?.content ?: return@withContext false
        val contract = evidenceDoc["contract"]?.jsonPrimitive?.content

        // Build anchor reference
        val anchorRef = AnchorRef(
            chainId = chainId,
            txHash = txHash,
            contract = contract,
            extra = emptyMap()
        )

        // Read from blockchain and verify
        return@withContext try {
            val anchorResult = anchorClient.readPayload(anchorRef)
            
            // Verify digest matches
            val credentialJson = json.encodeToJsonElement(VerifiableCredential.serializer(), credential)
            val credentialJsonString = json.encodeToString(JsonElement.serializer(), credentialJson)
            val expectedDigest = DigestUtils.sha256DigestMultibase(credentialJsonString)
            val storedDigest = evidenceDoc["digest"]?.jsonPrimitive?.content
            
            expectedDigest == storedDigest
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Get anchor reference for a credential.
     *
     * @param credential Credential to get anchor reference for
     * @param chainId Chain identifier
     * @return Anchor reference, or null if not found
     */
    suspend fun getAnchorReference(
        credential: VerifiableCredential,
        chainId: String
    ): AnchorRef? = withContext(Dispatchers.IO) {
        val anchorEvidence = credential.evidence?.find { evidence ->
            evidence.type.contains("BlockchainAnchorEvidence") &&
            evidence.evidenceDocument?.jsonObject?.get("chainId")?.jsonPrimitive?.content == chainId
        } ?: return@withContext null

        val evidenceDoc = anchorEvidence.evidenceDocument?.jsonObject ?: return@withContext null
        val txHash = evidenceDoc["txHash"]?.jsonPrimitive?.content ?: return@withContext null
        val contract = evidenceDoc["contract"]?.jsonPrimitive?.content

        AnchorRef(
            chainId = chainId,
            txHash = txHash,
            contract = contract,
            extra = emptyMap()
        )
    }

    /**
     * Create blockchain anchor evidence for credential.
     */
    private fun createBlockchainAnchorEvidence(
        chainId: String,
        anchorRef: AnchorRef,
        digest: String
    ): Evidence {
        val evidenceDocument = buildJsonObject {
            put("chainId", chainId)
            put("txHash", anchorRef.txHash)
            anchorRef.contract?.let { put("contract", it) }
            put("digest", digest)
            put("timestamp", Clock.System.now().epochSeconds)
            anchorRef.extra.forEach { (key, value) ->
                put(key, value)
            }
        }

        return Evidence(
            type = listOf("BlockchainAnchorEvidence"),
            evidenceDocument = evidenceDocument
        )
    }
}

/**
 * Anchor options for credential anchoring.
 */
data class AnchorOptions(
    val includeProof: Boolean = false,
    val addEvidenceToCredential: Boolean = true
)

/**
 * Anchor result containing anchor reference and updated credential.
 */
data class CredentialAnchorResult(
    val anchorRef: AnchorRef,
    val credential: VerifiableCredential,
    val digest: String
)

