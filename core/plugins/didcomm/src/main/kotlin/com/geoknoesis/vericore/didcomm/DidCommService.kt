package com.geoknoesis.vericore.didcomm

import com.geoknoesis.vericore.credential.models.VerifiableCredential
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * DIDComm message types.
 */
object DidCommMessageTypes {
    const val CREDENTIAL_OFFER = "https://didcomm.org/issue-credential/3.0/offer-credential"
    const val CREDENTIAL_REQUEST = "https://didcomm.org/issue-credential/3.0/request-credential"
    const val CREDENTIAL_ISSUE = "https://didcomm.org/issue-credential/3.0/issue-credential"
    const val PRESENTATION_REQUEST = "https://didcomm.org/present-proof/3.0/request-presentation"
    const val PRESENTATION = "https://didcomm.org/present-proof/3.0/presentation"
}

/**
 * DIDComm message.
 */
@Serializable
data class DidCommMessage(
    val id: String,
    val type: String,
    val from: String? = null, // DID
    val to: List<String> = emptyList(), // DIDs
    val body: JsonObject,
    val attachments: List<DidCommAttachment> = emptyList()
)

/**
 * DIDComm attachment.
 */
@Serializable
data class DidCommAttachment(
    val id: String,
    val mediaType: String,
    val data: DidCommAttachmentData
)

/**
 * DIDComm attachment data.
 */
@Serializable
data class DidCommAttachmentData(
    val json: JsonObject? = null,
    val base64: String? = null,
    val links: List<String> = emptyList()
)

/**
 * Credential offer message.
 */
@Serializable
data class CredentialOfferMessage(
    val credentialPreview: CredentialPreview,
    val formats: List<CredentialFormat>,
    val offersAttach: List<DidCommAttachment>
)

/**
 * Credential preview.
 */
@Serializable
data class CredentialPreview(
    val type: String,
    val attributes: List<CredentialAttribute>
)

/**
 * Credential attribute.
 */
@Serializable
data class CredentialAttribute(
    val name: String,
    val value: String
)

/**
 * Credential format.
 */
@Serializable
data class CredentialFormat(
    val attachId: String,
    val format: String
)

/**
 * DIDComm v2 credential exchange service.
 * 
 * Implements DIDComm v2 protocol for credential exchange.
 * 
 * **Example Usage:**
 * ```kotlin
 * val service = DidCommService()
 * 
 * // Create credential offer
 * val offer = service.createCredentialOffer(
 *     from = "did:key:issuer",
 *     to = listOf("did:key:holder"),
 *     credential = credential
 * )
 * 
 * // Send message
 * service.sendMessage(offer, recipientEndpoint)
 * ```
 */
interface DidCommService {
    /**
     * Create a credential offer message.
     * 
     * @param from Issuer DID
     * @param to List of recipient DIDs
     * @param credential Credential to offer
     * @return DIDComm message
     */
    suspend fun createCredentialOffer(
        from: String,
        to: List<String>,
        credential: VerifiableCredential
    ): DidCommMessage
    
    /**
     * Create a credential request message.
     * 
     * @param from Holder DID
     * @param to Issuer DID
     * @param offerId Offer message ID
     * @return DIDComm message
     */
    suspend fun createCredentialRequest(
        from: String,
        to: String,
        offerId: String
    ): DidCommMessage
    
    /**
     * Create a credential issue message.
     * 
     * @param from Issuer DID
     * @param to Holder DID
     * @param credential Issued credential
     * @param requestId Request message ID
     * @return DIDComm message
     */
    suspend fun createCredentialIssue(
        from: String,
        to: String,
        credential: VerifiableCredential,
        requestId: String
    ): DidCommMessage
    
    /**
     * Parse DIDComm message.
     * 
     * @param messageJson Message JSON
     * @return Parsed message
     */
    suspend fun parseMessage(messageJson: String): DidCommMessage
    
    /**
     * Extract credential from message.
     * 
     * @param message DIDComm message
     * @return Credential, or null if not found
     */
    suspend fun extractCredential(message: DidCommMessage): VerifiableCredential?
}

/**
 * Simple DIDComm service implementation.
 */
class SimpleDidCommService(
    private val json: Json = Json { prettyPrint = false; ignoreUnknownKeys = true }
) : DidCommService {
    
    override suspend fun createCredentialOffer(
        from: String,
        to: List<String>,
        credential: VerifiableCredential
    ): DidCommMessage = withContext(Dispatchers.IO) {
        val credentialJson = json.encodeToJsonElement(VerifiableCredential.serializer(), credential)
        
        val preview = CredentialPreview(
            type = credential.type.firstOrNull() ?: "VerifiableCredential",
            attributes = extractAttributes(credential)
        )
        
        val attachment = DidCommAttachment(
            id = UUID.randomUUID().toString(),
            mediaType = "application/json",
            data = DidCommAttachmentData(json = credentialJson.jsonObject)
        )
        
        DidCommMessage(
            id = UUID.randomUUID().toString(),
            type = DidCommMessageTypes.CREDENTIAL_OFFER,
            from = from,
            to = to,
            body = json.encodeToJsonElement(CredentialOfferMessage(
                credentialPreview = preview,
                formats = listOf(CredentialFormat(attachId = attachment.id, format = "ldp_vc")),
                offersAttach = listOf(attachment)
            )).jsonObject,
            attachments = listOf(attachment)
        )
    }
    
    override suspend fun createCredentialRequest(
        from: String,
        to: String,
        offerId: String
    ): DidCommMessage = withContext(Dispatchers.IO) {
        DidCommMessage(
            id = UUID.randomUUID().toString(),
            type = DidCommMessageTypes.CREDENTIAL_REQUEST,
            from = from,
            to = listOf(to),
            body = buildJsonObject {
                put("credential_request", buildJsonObject {
                    put("offer_id", offerId)
                })
            }
        )
    }
    
    override suspend fun createCredentialIssue(
        from: String,
        to: String,
        credential: VerifiableCredential,
        requestId: String
    ): DidCommMessage = withContext(Dispatchers.IO) {
        val credentialJson = json.encodeToJsonElement(VerifiableCredential.serializer(), credential)
        
        val attachment = DidCommAttachment(
            id = UUID.randomUUID().toString(),
            mediaType = "application/json",
            data = DidCommAttachmentData(json = credentialJson.jsonObject)
        )
        
        DidCommMessage(
            id = UUID.randomUUID().toString(),
            type = DidCommMessageTypes.CREDENTIAL_ISSUE,
            from = from,
            to = listOf(to),
            body = buildJsonObject {
                put("request_id", requestId)
            },
            attachments = listOf(attachment)
        )
    }
    
    override suspend fun parseMessage(messageJson: String): DidCommMessage = withContext(Dispatchers.IO) {
        json.decodeFromString(DidCommMessage.serializer(), messageJson)
    }
    
    override suspend fun extractCredential(message: DidCommMessage): VerifiableCredential? = withContext(Dispatchers.IO) {
        message.attachments.firstOrNull()?.data?.json?.let { jsonObject ->
            json.decodeFromJsonElement(VerifiableCredential.serializer(), jsonObject)
        }
    }
    
    private fun extractAttributes(credential: VerifiableCredential): List<CredentialAttribute> {
        val subject = credential.credentialSubject.jsonObject
        return subject.entries.map { (name, value) ->
            CredentialAttribute(
                name = name,
                value = value.jsonPrimitive.content
            )
        }
    }
}

