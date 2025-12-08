package com.trustweave.credential.didcomm.protocol

import com.trustweave.credential.didcomm.models.DidCommMessage
import com.trustweave.credential.didcomm.models.DidCommMessageTypes
import com.trustweave.credential.didcomm.models.DidCommAttachment
import com.trustweave.credential.didcomm.models.DidCommAttachmentData
import com.trustweave.credential.didcomm.protocol.models.DidCommGoalCode
import com.trustweave.credential.didcomm.protocol.util.toJsonObject
import com.trustweave.credential.model.vc.VerifiableCredential
import kotlinx.serialization.json.*
import kotlinx.datetime.Clock
import java.util.*

/**
 * Helper functions for creating DIDComm protocol messages.
 *
 * Implements common DIDComm protocols:
 * - Issue Credential Protocol
 * - Present Proof Protocol
 * - Basic Message Protocol
 */
object CredentialProtocol {
    /**
     * Creates a credential offer message.
     *
     * @param fromDid Issuer DID
     * @param toDid Holder DID
     * @param credentialPreview Preview of the credential to be issued
     * @param thid Optional thread ID
     * @return Credential offer message
     */
    fun createCredentialOffer(
        fromDid: String,
        toDid: String,
        credentialPreview: com.trustweave.credential.exchange.model.CredentialPreview,
        thid: String? = null
    ): DidCommMessage {
        val goalCode = credentialPreview.options.metadata["goalCode"]?.jsonPrimitive?.content
        val replacementId = credentialPreview.options.metadata["replacementId"]?.jsonPrimitive?.content
        val body = buildJsonObject {
            put("goal_code", goalCode ?: DidCommGoalCode.IssueVc.value)
            put("replacement_id", replacementId)
            put("credential_preview", buildJsonObject {
                put("@type", "https://didcomm.org/issue-credential/3.0/credential-preview")
                put("attributes", JsonArray(
                    credentialPreview.attributes.map { attr ->
                        buildJsonObject {
                            put("name", attr.name)
                            put("mime-type", attr.mimeType ?: "text/plain")
                            put("value", attr.value)
                        }
                    }
                ))
            })
        }

        return DidCommMessage(
            id = UUID.randomUUID().toString(),
            type = DidCommMessageTypes.CREDENTIAL_OFFER,
            from = fromDid,
            to = listOf(toDid),
            body = body,
            created = Clock.System.now().toString(),
            thid = thid
        )
    }

    /**
     * Creates a credential request message.
     *
     * @param fromDid Holder DID
     * @param toDid Issuer DID
     * @param thid Thread ID (from the offer)
     * @return Credential request message
     */
    fun createCredentialRequest(
        fromDid: String,
        toDid: String,
        thid: String
    ): DidCommMessage {
        val body = buildJsonObject {
            put("goal_code", DidCommGoalCode.RequestCredential.value)
        }

        return DidCommMessage(
            id = UUID.randomUUID().toString(),
            type = DidCommMessageTypes.CREDENTIAL_REQUEST,
            from = fromDid,
            to = listOf(toDid),
            body = body,
            created = Clock.System.now().toString(),
            thid = thid
        )
    }

    /**
     * Creates a credential issue message.
     *
     * @param fromDid Issuer DID
     * @param toDid Holder DID
     * @param credential The VerifiableCredential to issue
     * @param thid Thread ID (from the request)
     * @return Credential issue message
     */
    fun createCredentialIssue(
        fromDid: String,
        toDid: String,
        credential: com.trustweave.credential.model.vc.VerifiableCredential,
        thid: String
    ): DidCommMessage {
        // Serialize VerifiableCredential to JSON for DIDComm attachment
        val credentialJson = credential.toJsonObject()

        val attachment = DidCommAttachment(
            id = UUID.randomUUID().toString(),
            mediaType = "application/json",
            data = DidCommAttachmentData(
                json = credentialJson
            )
        )

        val body = buildJsonObject {
            put("goal_code", DidCommGoalCode.IssueVc.value) // "issue-credential" in DIDComm
        }

        return DidCommMessage(
            id = UUID.randomUUID().toString(),
            type = DidCommMessageTypes.CREDENTIAL_ISSUE,
            from = fromDid,
            to = listOf(toDid),
            body = body,
            created = Clock.System.now().toString(),
            attachments = listOf(attachment),
            thid = thid
        )
    }

    /**
     * Creates a credential acknowledgment message.
     *
     * @param fromDid Sender DID
     * @param toDid Recipient DID
     * @param thid Thread ID
     * @return Credential acknowledgment message
     */
    fun createCredentialAck(
        fromDid: String,
        toDid: String,
        thid: String
    ): DidCommMessage {
        val body = buildJsonObject {
            put("goal_code", DidCommGoalCode.AckCredential.value)
        }

        return DidCommMessage(
            id = UUID.randomUUID().toString(),
            type = DidCommMessageTypes.CREDENTIAL_ACK,
            from = fromDid,
            to = listOf(toDid),
            body = body,
            created = Clock.System.now().toString(),
            thid = thid
        )
    }
}


