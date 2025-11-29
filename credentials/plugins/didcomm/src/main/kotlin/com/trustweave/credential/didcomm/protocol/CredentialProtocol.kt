package com.trustweave.credential.didcomm.protocol

import com.trustweave.credential.didcomm.models.DidCommMessage
import com.trustweave.credential.didcomm.models.DidCommMessageTypes
import com.trustweave.credential.didcomm.models.DidCommAttachment
import com.trustweave.credential.didcomm.models.DidCommAttachmentData
import com.trustweave.credential.models.VerifiableCredential
import kotlinx.serialization.json.*
import java.time.Instant
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
        credentialPreview: com.trustweave.credential.exchange.CredentialPreview,
        thid: String? = null
    ): DidCommMessage {
        val body = buildJsonObject {
            put("goal_code", credentialPreview.goalCode ?: "issue-vc")
            put("replacement_id", credentialPreview.replacementId)
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
            created = Instant.now().toString(),
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
            put("goal_code", "request-credential")
        }

        return DidCommMessage(
            id = UUID.randomUUID().toString(),
            type = DidCommMessageTypes.CREDENTIAL_REQUEST,
            from = fromDid,
            to = listOf(toDid),
            body = body,
            created = Instant.now().toString(),
            thid = thid
        )
    }

    /**
     * Creates a credential issue message.
     *
     * @param fromDid Issuer DID
     * @param toDid Holder DID
     * @param credential The verifiable credential to issue
     * @param thid Thread ID (from the request)
     * @return Credential issue message
     */
    fun createCredentialIssue(
        fromDid: String,
        toDid: String,
        credential: VerifiableCredential,
        thid: String
    ): DidCommMessage {
        // Serialize credential to JSON
        val json = Json { prettyPrint = false; encodeDefaults = false }
        val credentialJson = json.encodeToJsonElement(
            VerifiableCredential.serializer(),
            credential
        )

        val attachment = DidCommAttachment(
            id = UUID.randomUUID().toString(),
            mediaType = "application/json",
            data = DidCommAttachmentData(
                json = credentialJson
            )
        )

        val body = buildJsonObject {
            put("goal_code", "issue-credential")
        }

        return DidCommMessage(
            id = UUID.randomUUID().toString(),
            type = DidCommMessageTypes.CREDENTIAL_ISSUE,
            from = fromDid,
            to = listOf(toDid),
            body = body,
            created = Instant.now().toString(),
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
            put("goal_code", "ack-credential")
        }

        return DidCommMessage(
            id = UUID.randomUUID().toString(),
            type = DidCommMessageTypes.CREDENTIAL_ACK,
            from = fromDid,
            to = listOf(toDid),
            body = body,
            created = Instant.now().toString(),
            thid = thid
        )
    }

    /**
     * Extracts credential from a credential issue message.
     *
     * @param message The credential issue message
     * @return The verifiable credential, or null if not found
     */
    fun extractCredential(message: DidCommMessage): VerifiableCredential? {
        val attachment = message.attachments.firstOrNull()
            ?: return null

        val credentialJson = attachment.data.json
            ?: return null

        val json = Json { prettyPrint = false; ignoreUnknownKeys = true }
        return try {
            json.decodeFromJsonElement(
                VerifiableCredential.serializer(),
                credentialJson
            )
        } catch (e: Exception) {
            null
        }
    }
}


