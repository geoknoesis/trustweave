package com.trustweave.credential.didcomm.packing

import com.trustweave.credential.didcomm.crypto.DidCommCryptoInterface
import com.trustweave.credential.didcomm.models.DidCommEnvelope
import com.trustweave.credential.didcomm.models.DidCommMessage
import com.trustweave.did.DidDocument
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import java.time.Instant
import java.util.*
import java.util.Base64

/**
 * Packs and unpacks DIDComm V2 messages.
 * 
 * Supports:
 * - Plain messages (signed or unsigned)
 * - Encrypted messages (AuthCrypt)
 * 
 * Following DIDComm V2 specification:
 * - Messages use JWM (JSON Web Message) format
 * - Encryption uses ECDH-1PU (AuthCrypt) or ECDH-ES (AnonCrypt)
 * - Messages can be signed using JWS
 */
class DidCommPacker(
    private val crypto: DidCommCryptoInterface,
    private val resolveDid: suspend (String) -> DidDocument?,
    private val signer: suspend (ByteArray, String) -> ByteArray // Signer for plain messages
) {
    /**
     * Packs a message for sending.
     * 
     * @param message The message to pack
     * @param fromDid Sender DID
     * @param fromKeyId Sender key ID for encryption/signing
     * @param toDid Recipient DID
     * @param toKeyId Recipient key ID (from their DID document)
     * @param encrypt Whether to encrypt the message (default: true)
     * @param sign Whether to sign the message (default: true)
     * @return Packed message as JSON string
     */
    suspend fun pack(
        message: DidCommMessage,
        fromDid: String,
        fromKeyId: String,
        toDid: String,
        toKeyId: String,
        encrypt: Boolean = true,
        sign: Boolean = true
    ): String = withContext(Dispatchers.IO) {
        val messageJson = message.toJsonObject()
        
        if (encrypt) {
            // Encrypt the message
            val envelope = crypto.encrypt(
                message = messageJson,
                fromDid = fromDid,
                fromKeyId = fromKeyId,
                toDid = toDid,
                toKeyId = toKeyId
            )
            
            // Serialize envelope to JSON
            val envelopeJson = buildJsonObject {
                put("protected", envelope.protected)
                put("recipients", JsonArray(
                    envelope.recipients.map { recipient ->
                        buildJsonObject {
                            put("header", buildJsonObject {
                                put("kid", recipient.header.kid)
                                put("alg", recipient.header.alg)
                                recipient.header.epk?.let { put("epk", it) }
                            })
                            put("encrypted_key", recipient.encrypted_key)
                        }
                    }
                ))
                put("iv", envelope.iv)
                put("ciphertext", envelope.ciphertext)
                put("tag", envelope.tag)
            }
            
            Json.encodeToString(JsonObject.serializer(), envelopeJson)
        } else {
            // Plain message (may be signed)
            if (sign) {
                // Sign the message using JWS
                val signedMessage = signMessage(messageJson, fromDid, fromKeyId)
                Json.encodeToString(JsonObject.serializer(), signedMessage)
            } else {
                Json.encodeToString(JsonObject.serializer(), messageJson)
            }
        }
    }

    /**
     * Unpacks a received message.
     * 
     * @param packedMessage The packed message (JSON string)
     * @param recipientDid Recipient DID
     * @param recipientKeyId Recipient key ID
     * @param senderDid Expected sender DID (for verification)
     * @return Unpacked message
     */
    suspend fun unpack(
        packedMessage: String,
        recipientDid: String,
        recipientKeyId: String,
        senderDid: String? = null
    ): DidCommMessage = withContext(Dispatchers.IO) {
        val json = Json.parseToJsonElement(packedMessage)
        
        // Check if it's an encrypted envelope
        if (json.jsonObject.containsKey("ciphertext")) {
            // Encrypted message
            val envelope = parseEnvelope(json.jsonObject)
            
            val resolvedSenderDid = senderDid ?: extractSenderDid(envelope)
                ?: throw IllegalArgumentException("Cannot determine sender DID")
            
            val decryptedJson = crypto.decrypt(
                envelope = envelope,
                recipientDid = recipientDid,
                recipientKeyId = recipientKeyId,
                senderDid = resolvedSenderDid
            )
            
            // Parse decrypted message
            parseMessage(decryptedJson)
        } else {
            // Plain message
            parseMessage(json.jsonObject)
        }
    }

    private fun parseEnvelope(json: JsonObject): DidCommEnvelope {
        val protected = json["protected"]?.jsonPrimitive?.content
            ?: throw IllegalArgumentException("Missing 'protected' in envelope")
        
        val recipients = json["recipients"]?.jsonArray?.map { recipientJson ->
            val recipientObj = recipientJson.jsonObject
            val headerObj = recipientObj["header"]?.jsonObject
                ?: throw IllegalArgumentException("Missing 'header' in recipient")
            
            com.trustweave.credential.didcomm.models.DidCommRecipient(
                header = com.trustweave.credential.didcomm.models.DidCommRecipientHeader(
                    kid = headerObj["kid"]?.jsonPrimitive?.content
                        ?: throw IllegalArgumentException("Missing 'kid' in header"),
                    alg = headerObj["alg"]?.jsonPrimitive?.content ?: "ECDH-1PU+A256KW",
                    epk = headerObj["epk"]?.jsonObject
                ),
                encrypted_key = recipientObj["encrypted_key"]?.jsonPrimitive?.content
                    ?: throw IllegalArgumentException("Missing 'encrypted_key' in recipient")
            )
        } ?: throw IllegalArgumentException("Missing 'recipients' in envelope")
        
        val iv = json["iv"]?.jsonPrimitive?.content
            ?: throw IllegalArgumentException("Missing 'iv' in envelope")
        val ciphertext = json["ciphertext"]?.jsonPrimitive?.content
            ?: throw IllegalArgumentException("Missing 'ciphertext' in envelope")
        val tag = json["tag"]?.jsonPrimitive?.content
            ?: throw IllegalArgumentException("Missing 'tag' in envelope")
        
        return DidCommEnvelope(
            protected = protected,
            recipients = recipients,
            iv = iv,
            ciphertext = ciphertext,
            tag = tag
        )
    }

    private fun parseMessage(json: JsonObject): DidCommMessage {
        val id = json["id"]?.jsonPrimitive?.content
            ?: throw IllegalArgumentException("Missing 'id' in message")
        val type = json["type"]?.jsonPrimitive?.content
            ?: throw IllegalArgumentException("Missing 'type' in message")
        
        val from = json["from"]?.jsonPrimitive?.content
        val to = json["to"]?.jsonArray?.mapNotNull { it.jsonPrimitive?.content }
            ?: json["to"]?.jsonPrimitive?.content?.let { listOf(it) }
            ?: emptyList()
        
        val body = json["body"]?.jsonObject ?: buildJsonObject { }
        val created = json["created_time"]?.jsonPrimitive?.content
        val expiresTime = json["expires_time"]?.jsonPrimitive?.content
        
        val attachments = json["attachments"]?.jsonArray?.mapNotNull { attachmentJson ->
            val attachmentObj = attachmentJson.jsonObject
            val mediaType = attachmentObj["media_type"]?.jsonPrimitive?.content ?: "application/json"
            val dataObj = attachmentObj["data"]?.jsonObject
                ?: throw IllegalArgumentException("Missing 'data' in attachment")
            
            com.trustweave.credential.didcomm.models.DidCommAttachment(
                id = attachmentObj["id"]?.jsonPrimitive?.content,
                mediaType = mediaType,
                data = com.trustweave.credential.didcomm.models.DidCommAttachmentData(
                    base64 = dataObj["base64"]?.jsonPrimitive?.content,
                    json = dataObj["json"],
                    links = dataObj["links"]?.jsonArray?.mapNotNull { it.jsonPrimitive?.content }
                )
            )
        } ?: emptyList()
        
        val thid = json["thid"]?.jsonPrimitive?.content
        val pthid = json["pthid"]?.jsonPrimitive?.content
        val ack = json["ack"]?.jsonArray?.mapNotNull { it.jsonPrimitive?.content }
        
        return DidCommMessage(
            id = id,
            type = type,
            from = from,
            to = to,
            body = body,
            created = created,
            expiresTime = expiresTime,
            attachments = attachments,
            thid = thid,
            pthid = pthid,
            ack = ack
        )
    }

    private fun extractSenderDid(envelope: DidCommEnvelope): String? {
        // Extract sender DID from protected headers
        return try {
            val protectedJson = Json.parseToJsonElement(
                String(Base64.getUrlDecoder().decode(envelope.protected))
            ).jsonObject
            
            val skid = protectedJson["skid"]?.jsonPrimitive?.content
            // Extract DID from key ID (format: did:method:id#key-id)
            skid?.substringBefore("#")
        } catch (e: Exception) {
            null
        }
    }
    
    /**
     * Signs a plain message using JWS (JSON Web Signature).
     * 
     * Creates a compact JWS format: header.payload.signature
     */
    private suspend fun signMessage(
        messageJson: JsonObject,
        fromDid: String,
        fromKeyId: String
    ): JsonObject {
        val json = Json { prettyPrint = false; encodeDefaults = false }
        val messageBytes = json.encodeToString(JsonObject.serializer(), messageJson).toByteArray(Charsets.UTF_8)
        
        // Sign using provided signer
        val signature = signer(messageBytes, fromKeyId)
        
        // Create JWS compact format
        val header = buildJsonObject {
            put("alg", "Ed25519") // Default algorithm, should be determined from key
            put("typ", "JWS")
            put("kid", fromKeyId)
        }
        
        val headerBase64 = Base64.getUrlEncoder().withoutPadding()
            .encodeToString(json.encodeToString(JsonObject.serializer(), header).toByteArray(Charsets.UTF_8))
        val payloadBase64 = Base64.getUrlEncoder().withoutPadding()
            .encodeToString(messageBytes)
        val signatureBase64 = Base64.getUrlEncoder().withoutPadding()
            .encodeToString(signature)
        
        // Return signed message with JWS
        return buildJsonObject {
            putAll(messageJson)
            put("signatures", JsonArray(listOf(
                buildJsonObject {
                    put("protected", headerBase64)
                    put("signature", signatureBase64)
                }
            )))
        }
    }
}

