package com.trustweave.credential.didcomm.protocol

import com.trustweave.credential.didcomm.models.DidCommMessage
import com.trustweave.credential.didcomm.models.DidCommMessageTypes
import com.trustweave.credential.didcomm.models.DidCommAttachment
import com.trustweave.credential.didcomm.models.DidCommAttachmentData
import com.trustweave.credential.didcomm.protocol.models.DidCommGoalCode
import com.trustweave.credential.didcomm.protocol.util.toJsonObject
import com.trustweave.credential.model.vc.VerifiablePresentation
import kotlinx.serialization.json.*
import kotlinx.datetime.Clock
import java.util.*

/**
 * Helper functions for Present Proof Protocol messages.
 */
object ProofProtocol {
    /**
     * Creates a proof request message.
     *
     * @param fromDid Verifier DID
     * @param toDid Prover DID
     * @param proofRequest The proof request details
     * @param thid Optional thread ID
     * @return Proof request message
     */
    fun createProofRequest(
        fromDid: String,
        toDid: String,
        proofRequest: ProofRequest,
        thid: String? = null
    ): DidCommMessage {
        val body = buildJsonObject {
            put("goal_code", proofRequest.goalCode ?: DidCommGoalCode.RequestProof.value)
            put("will_confirm", proofRequest.willConfirm)
            put("proof_request", buildJsonObject {
                put("name", proofRequest.name)
                put("version", proofRequest.version)
                put("requested_attributes", JsonObject(
                    proofRequest.requestedAttributes.mapValues { (_, attr) ->
                        buildJsonObject {
                            put("name", attr.name)
                            put("restrictions", JsonArray(
                                attr.restrictions.map { restriction ->
                                    buildJsonObject {
                                        restriction.issuerDid?.let { put("issuer", it) }
                                        restriction.schemaId?.let { put("schema_id", it) }
                                        restriction.credentialDefinitionId?.let { put("cred_def_id", it) }
                                    }
                                }
                            ))
                        }
                    }
                ))
                put("requested_predicates", JsonObject(
                    proofRequest.requestedPredicates.mapValues { (_, pred) ->
                        buildJsonObject {
                            put("name", pred.name)
                            put("p_type", pred.pType)
                            put("p_value", pred.pValue)
                            put("restrictions", JsonArray(
                                pred.restrictions.map { restriction: AttributeRestriction ->
                                    buildJsonObject {
                                        restriction.issuerDid?.let { put("issuer", it) }
                                        restriction.schemaId?.let { put("schema_id", it) }
                                        restriction.credentialDefinitionId?.let { put("cred_def_id", it) }
                                    }
                                }
                            ))
                        }
                    }
                ))
            })
        }

        return DidCommMessage(
            id = UUID.randomUUID().toString(),
            type = DidCommMessageTypes.PROOF_REQUEST,
            from = fromDid,
            to = listOf(toDid),
            body = body,
            created = Clock.System.now().toString(),
            thid = thid
        )
    }

    /**
     * Creates a proof presentation message.
     *
     * @param fromDid Prover DID
     * @param toDid Verifier DID
     * @param presentation The VerifiablePresentation to present
     * @param thid Thread ID (from the request)
     * @return Proof presentation message
     */
    fun createProofPresentation(
        fromDid: String,
        toDid: String,
        presentation: com.trustweave.credential.model.vc.VerifiablePresentation,
        thid: String
    ): DidCommMessage {
        // Serialize VerifiablePresentation to JSON for DIDComm attachment
        val presentationJson = presentation.toJsonObject()

        val attachment = DidCommAttachment(
            id = UUID.randomUUID().toString(),
            mediaType = "application/json",
            data = DidCommAttachmentData(
                json = presentationJson
            )
        )

        val body = buildJsonObject {
            put("goal_code", DidCommGoalCode.PresentProof.value)
        }

        return DidCommMessage(
            id = UUID.randomUUID().toString(),
            type = DidCommMessageTypes.PROOF_PRESENTATION,
            from = fromDid,
            to = listOf(toDid),
            body = body,
            created = Clock.System.now().toString(),
            attachments = listOf(attachment),
            thid = thid
        )
    }

    /**
     * Creates a proof acknowledgment message.
     *
     * @param fromDid Verifier DID
     * @param toDid Prover DID
     * @param thid Thread ID
     * @return Proof acknowledgment message
     */
    fun createProofAck(
        fromDid: String,
        toDid: String,
        thid: String
    ): DidCommMessage {
        val body = buildJsonObject {
            put("goal_code", DidCommGoalCode.AckProof.value)
        }

        return DidCommMessage(
            id = UUID.randomUUID().toString(),
            type = DidCommMessageTypes.PROOF_ACK,
            from = fromDid,
            to = listOf(toDid),
            body = body,
            created = Clock.System.now().toString(),
            thid = thid
        )
    }

}

/**
 * Proof request details.
 */
data class ProofRequest(
    val name: String,
    val version: String = "1.0",
    val requestedAttributes: Map<String, RequestedAttribute>,
    val requestedPredicates: Map<String, RequestedPredicate> = emptyMap(),
    val goalCode: String? = null,
    val willConfirm: Boolean = true
)

/**
 * Requested attribute in proof request.
 */
data class RequestedAttribute(
    val name: String,
    val restrictions: List<AttributeRestriction> = emptyList()
)

/**
 * Requested predicate in proof request.
 */
data class RequestedPredicate(
    val name: String,
    val pType: String, // e.g., ">=", "<=", "=="
    val pValue: Int,
    val restrictions: List<AttributeRestriction> = emptyList()
)

/**
 * Attribute restriction for proof requests.
 */
data class AttributeRestriction(
    val issuerDid: String? = null,
    val schemaId: String? = null,
    val credentialDefinitionId: String? = null
)

