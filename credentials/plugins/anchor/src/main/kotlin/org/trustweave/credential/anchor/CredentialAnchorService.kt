package org.trustweave.credential.anchor

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.trustweave.anchor.AnchorRef
import org.trustweave.anchor.BlockchainAnchorClient
import org.trustweave.core.util.DigestUtils
import org.trustweave.credential.model.Evidence
import org.trustweave.credential.model.vc.VerifiableCredential

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
    private val anchorClient: BlockchainAnchorClient,
) {
    private val json =
        Json {
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
        options: AnchorOptions = AnchorOptions(),
    ): CredentialAnchorResult =
        withContext(Dispatchers.IO) {
            // Prepare credential for anchoring
            val credentialToAnchor =
                credential.copy(
                    proof = if (options.includeProof) credential.proof else null,
                    evidence = credential.evidence?.takeIf { it.isNotEmpty() },
                )

            // Serialize credential to JSON
            val credentialJson = json.encodeToJsonElement(credentialToAnchor)

            // Compute digest
            val credentialJsonString = json.encodeToString(JsonElement.serializer(), credentialJson)
            val digest = DigestUtils.sha256DigestMultibase(credentialJsonString)

            // Anchor to blockchain
            val anchorResult =
                anchorClient.writePayload(
                    payload = credentialJson,
                    mediaType = "application/vc+json",
                )

            require(anchorResult.ref.chainId == chainId) { "Anchor provider returned a different chain" }
            require(anchorResult.payload == credentialJson) { "Anchor provider returned a different payload" }

            // Add evidence to credential if requested
            val updatedCredential =
                if (options.addEvidenceToCredential) {
                    val evidence =
                        createBlockchainAnchorEvidence(
                            chainId = chainId,
                            anchorRef = anchorResult.ref,
                            digest = digest,
                            includeProof = options.includeProof,
                        )
                    val existingEvidence = credential.evidence ?: emptyList()
                    credential.copy(evidence = existingEvidence + evidence)
                } else {
                    credential
                }

            CredentialAnchorResult(
                anchorRef = anchorResult.ref,
                credential = updatedCredential,
                digest = digest,
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
        chainId: String,
    ): Boolean =
        withContext(Dispatchers.IO) {
            // Find anchor evidence
            val anchorEvidence =
                credential.evidence?.find { evidence ->
                    evidence.type.contains("BlockchainAnchorEvidence") &&
                        evidence.evidenceDocument
                            ?.jsonObject
                            ?.get("chainId")
                            ?.jsonPrimitive
                            ?.content == chainId
                } ?: return@withContext false

            // Extract anchor reference from evidence
            val evidenceDoc = anchorEvidence.evidenceDocument?.jsonObject ?: return@withContext false
            val txHash = evidenceDoc["txHash"]?.jsonPrimitive?.content ?: return@withContext false
            val contract = evidenceDoc["contract"]?.jsonPrimitive?.content

            // Build anchor reference
            val anchorRef =
                AnchorRef(
                    chainId = chainId,
                    txHash = txHash,
                    contract = contract,
                    extra = emptyMap(),
                )

            // Compare the actual ledger payload, not just a digest supplied in the evidence.
            return@withContext try {
                val anchored = anchorClient.readPayload(anchorRef)
                val includeProof = evidenceDoc["includeProof"]?.jsonPrimitive?.booleanOrNull ?: false
                val candidate =
                    credential.copy(
                        proof = if (includeProof) credential.proof else null,
                        evidence = credential.evidence?.filterNot { it === anchorEvidence }?.takeIf { it.isNotEmpty() },
                    )
                val expected = json.encodeToJsonElement(candidate)
                val actualDigest = DigestUtils.sha256DigestMultibase(json.encodeToString(JsonElement.serializer(), anchored.payload))
                anchored.ref.chainId == chainId &&
                    anchored.ref.txHash == txHash &&
                    anchored.ref.contract == contract &&
                    anchored.payload == expected &&
                    actualDigest == evidenceDoc["digest"]?.jsonPrimitive?.content
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
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
        chainId: String,
    ): AnchorRef? =
        withContext(Dispatchers.IO) {
            val anchorEvidence =
                credential.evidence?.find { evidence ->
                    evidence.type.contains("BlockchainAnchorEvidence") &&
                        evidence.evidenceDocument
                            ?.jsonObject
                            ?.get("chainId")
                            ?.jsonPrimitive
                            ?.content == chainId
                } ?: return@withContext null

            val evidenceDoc = anchorEvidence.evidenceDocument?.jsonObject ?: return@withContext null
            val txHash = evidenceDoc["txHash"]?.jsonPrimitive?.content ?: return@withContext null
            val contract = evidenceDoc["contract"]?.jsonPrimitive?.content

            AnchorRef(
                chainId = chainId,
                txHash = txHash,
                contract = contract,
                extra = emptyMap(),
            )
        }

    /**
     * Create blockchain anchor evidence for credential.
     */
    private fun createBlockchainAnchorEvidence(
        chainId: String,
        anchorRef: AnchorRef,
        digest: String,
        includeProof: Boolean,
    ): Evidence {
        val evidenceDocument =
            buildJsonObject {
                put("chainId", chainId)
                put("txHash", anchorRef.txHash)
                anchorRef.contract?.let { put("contract", it) }
                put("digest", digest)
                put("includeProof", includeProof)
                put("timestamp", Clock.System.now().epochSeconds)
            }

        return Evidence(
            type = listOf("BlockchainAnchorEvidence"),
            evidenceDocument = evidenceDocument,
        )
    }
}

/**
 * Anchor options for credential anchoring.
 */
data class AnchorOptions(
    val includeProof: Boolean = false,
    val addEvidenceToCredential: Boolean = true,
)

/**
 * Anchor result containing anchor reference and updated credential.
 */
data class CredentialAnchorResult(
    val anchorRef: AnchorRef,
    val credential: VerifiableCredential,
    val digest: String,
)
