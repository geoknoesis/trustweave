package com.trustweave.credential.didcomm.crypto

import com.trustweave.credential.didcomm.models.DidCommEnvelope
import com.trustweave.did.DidDocument
import com.trustweave.kms.KeyManagementService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*

/**
 * Adapter that chooses between placeholder and production crypto implementations.
 * 
 * This allows the codebase to work with placeholder crypto during development,
 * and automatically use production crypto when didcomm-java is available.
 */
class DidCommCryptoAdapter(
    private val kms: KeyManagementService,
    private val resolveDid: suspend (String) -> DidDocument?,
    val useProduction: Boolean = true, // Default to production crypto
    private val secretResolver: org.didcommx.didcomm.secret.SecretResolver? = null // Optional custom resolver
) : DidCommCryptoInterface {
    private val placeholderCrypto = DidCommCrypto(kms, resolveDid)
    private val productionCrypto = try {
        DidCommCryptoProduction(kms, resolveDid, secretResolver)
    } catch (e: NoClassDefFoundError) {
        null // didcomm-java not available
    }
    
    /**
     * Encrypts a message.
     * 
     * Uses production crypto if available and enabled, otherwise uses placeholder.
     */
    suspend fun encrypt(
        message: JsonObject,
        fromDid: String,
        fromKeyId: String,
        toDid: String,
        toKeyId: String
    ): DidCommEnvelope = withContext(Dispatchers.IO) {
        if (useProduction && productionCrypto != null) {
            // Use production crypto
            try {
                productionCrypto.encrypt(message, fromDid, fromKeyId, toDid, toKeyId)
            } catch (e: Exception) {
                // Fall back to placeholder if production fails
                placeholderCrypto.encrypt(message, fromDid, fromKeyId, toDid, toKeyId)
            }
        } else {
            // Use placeholder implementation
            placeholderCrypto.encrypt(message, fromDid, fromKeyId, toDid, toKeyId)
        }
    }
    
    /**
     * Decrypts an encrypted envelope.
     */
    suspend fun decrypt(
        envelope: DidCommEnvelope,
        recipientDid: String,
        recipientKeyId: String,
        senderDid: String
    ): JsonObject = withContext(Dispatchers.IO) {
        if (useProduction && productionCrypto != null) {
            // Use production crypto
            try {
                productionCrypto.decrypt(envelope, recipientDid, recipientKeyId, senderDid)
            } catch (e: Exception) {
                // Fall back to placeholder if production fails
                placeholderCrypto.decrypt(envelope, recipientDid, recipientKeyId, senderDid)
            }
        } else {
            // Use placeholder implementation
            placeholderCrypto.decrypt(envelope, recipientDid, recipientKeyId, senderDid)
        }
    }
    
    /**
     * Decrypts from packed string format (used by didcomm-java).
     */
    suspend fun decryptFromPacked(
        packedMessage: String,
        recipientDid: String,
        recipientKeyId: String,
        senderDid: String
    ): JsonObject = withContext(Dispatchers.IO) {
        if (useProduction && productionCrypto != null) {
            // Use production crypto
            try {
                val envelope = parsePackedToEnvelope(packedMessage)
                productionCrypto.decrypt(envelope, recipientDid, recipientKeyId, senderDid)
            } catch (e: Exception) {
                // Fall back to placeholder if production fails
                val envelope = parsePackedToEnvelope(packedMessage)
                placeholderCrypto.decrypt(envelope, recipientDid, recipientKeyId, senderDid)
            }
        } else {
            // Parse packed string to envelope and use placeholder
            val envelope = parsePackedToEnvelope(packedMessage)
            placeholderCrypto.decrypt(envelope, recipientDid, recipientKeyId, senderDid)
        }
    }
    
    private fun parsePackedToEnvelope(packed: String): DidCommEnvelope {
        // Parse didcomm-java packed format to our envelope format
        val json = Json.parseToJsonElement(packed).jsonObject
        
        val protected = json["protected"]?.jsonPrimitive?.content
            ?: throw IllegalArgumentException("Missing 'protected' in packed message")
        
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
        } ?: throw IllegalArgumentException("Missing 'recipients' in packed message")
        
        val iv = json["iv"]?.jsonPrimitive?.content
            ?: throw IllegalArgumentException("Missing 'iv' in packed message")
        val ciphertext = json["ciphertext"]?.jsonPrimitive?.content
            ?: throw IllegalArgumentException("Missing 'ciphertext' in packed message")
        val tag = json["tag"]?.jsonPrimitive?.content
            ?: throw IllegalArgumentException("Missing 'tag' in packed message")
        
        return DidCommEnvelope(
            protected = protected,
            recipients = recipients,
            iv = iv,
            ciphertext = ciphertext,
            tag = tag
        )
    }
    
    private fun envelopeToPackedString(envelope: DidCommEnvelope): String {
        // Convert our envelope format to didcomm-java packed format
        val json = buildJsonObject {
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
        return Json.encodeToString(JsonObject.serializer(), json)
    }
}

