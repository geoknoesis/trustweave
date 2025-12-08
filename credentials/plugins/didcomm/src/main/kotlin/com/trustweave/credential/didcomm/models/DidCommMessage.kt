package com.trustweave.credential.didcomm.models

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray

/**
 * DIDComm V2 message structure following JWM (JSON Web Message) format.
 *
 * DIDComm V2 messages consist of:
 * - Headers: Metadata about the message (id, type, from, to, etc.)
 * - Body: The actual message content
 *
 * Messages can be:
 * - Plain: Unencrypted, signed or unsigned
 * - Encrypted: Using AuthCrypt (ECDH-1PU) or AnonCrypt
 *
 * @param id Unique message identifier (typically UUID)
 * @param type Message type (e.g., "https://didcomm.org/issue-credential/3.0/offer-credential")
 * @param from Sender DID
 * @param to List of recipient DIDs
 * @param body Message body as JSON
 * @param created Optional timestamp when message was created
 * @param expiresTime Optional expiration time
 * @param attachments Optional attachments (e.g., credentials)
 */
@Serializable
data class DidCommMessage(
    val id: String,
    val type: String,
    val from: String? = null,
    val to: List<String> = emptyList(),
    val body: JsonObject = buildJsonObject { },
    val created: String? = null,
    val expiresTime: String? = null,
    val attachments: List<DidCommAttachment> = emptyList(),
    val thid: String? = null, // Thread ID for message threading
    val pthid: String? = null, // Parent thread ID
    val ack: List<String>? = null // Acknowledgment message IDs
) {
    /**
     * Converts the message to a JSON object for serialization.
     */
    fun toJsonObject(): JsonObject {
        return buildJsonObject {
            put("id", id)
            put("type", type)
            from?.let { put("from", it) }
            if (to.isNotEmpty()) {
                putJsonArray("to") { to.forEach { add(JsonPrimitive(it)) } }
            }
            put("body", body)
            created?.let { put("created_time", it) }
            expiresTime?.let { put("expires_time", it) }
            if (attachments.isNotEmpty()) {
                putJsonArray("attachments") { attachments.forEach { add(it.toJsonObject()) } }
            }
            thid?.let { put("thid", it) }
            pthid?.let { put("pthid", it) }
            ack?.let { putJsonArray("ack") { it.forEach { add(JsonPrimitive(it)) } } }
        }
    }
}

/**
 * DIDComm attachment for including credentials or other data.
 */
@Serializable
data class DidCommAttachment(
    val id: String? = null,
    val mediaType: String = "application/json",
    val data: DidCommAttachmentData
) {
    fun toJsonObject(): JsonObject {
        return buildJsonObject {
            id?.let { put("id", it) }
            put("media_type", mediaType)
            put("data", data.toJsonObject())
        }
    }
}

/**
 * Attachment data (can be base64-encoded or JSON).
 */
@Serializable
data class DidCommAttachmentData(
    val base64: String? = null,
    val json: JsonElement? = null,
    val links: List<String>? = null
) {
    fun toJsonObject(): JsonObject {
        return buildJsonObject {
            base64?.let { put("base64", it) }
            json?.let { put("json", it) }
            links?.let { putJsonArray("links") { it.forEach { add(JsonPrimitive(it)) } } }
        }
    }
}

/**
 * DIDComm V2 message types for common protocols.
 */
object DidCommMessageTypes {
    // Issue Credential Protocol
    const val CREDENTIAL_OFFER = "https://didcomm.org/issue-credential/3.0/offer-credential"
    const val CREDENTIAL_REQUEST = "https://didcomm.org/issue-credential/3.0/request-credential"
    const val CREDENTIAL_ISSUE = "https://didcomm.org/issue-credential/3.0/issue-credential"
    const val CREDENTIAL_ACK = "https://didcomm.org/issue-credential/3.0/ack"

    // Present Proof Protocol
    const val PROOF_REQUEST = "https://didcomm.org/present-proof/3.0/request-presentation"
    const val PROOF_PRESENTATION = "https://didcomm.org/present-proof/3.0/presentation"
    const val PROOF_ACK = "https://didcomm.org/present-proof/3.0/ack"

    // Basic Message
    const val BASIC_MESSAGE = "https://didcomm.org/basicmessage/2.0/message"

    // Trust Ping
    const val TRUST_PING = "https://didcomm.org/trust-ping/2.0/ping"
    const val TRUST_PING_RESPONSE = "https://didcomm.org/trust-ping/2.0/ping-response"

    // Out of Band
    const val OUT_OF_BAND_INVITATION = "https://didcomm.org/out-of-band/2.0/invitation"

    // Discover Features
    const val DISCOVER_FEATURES_QUERY = "https://didcomm.org/discover-features/2.0/queries"
    const val DISCOVER_FEATURES_DISCLOSE = "https://didcomm.org/discover-features/2.0/disclose"
}

