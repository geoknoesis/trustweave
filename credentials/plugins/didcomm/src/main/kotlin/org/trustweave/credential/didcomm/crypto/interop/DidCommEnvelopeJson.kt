package org.trustweave.credential.didcomm.crypto.interop

import org.trustweave.credential.didcomm.models.DidCommEnvelope
import org.trustweave.credential.didcomm.models.DidCommRecipient
import org.trustweave.credential.didcomm.models.DidCommRecipientHeader
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/**
 * Converts between TrustWeave [DidCommEnvelope] and the JSON string produced/consumed by didcomm-java.
 */
object DidCommEnvelopeJson {

    fun envelopeToPackedJson(envelope: DidCommEnvelope): String {
        val json = buildJsonObject {
            put("protected", envelope.protected)
            put(
                "recipients",
                JsonArray(
                    envelope.recipients.map { r ->
                        buildJsonObject {
                            put(
                                "header",
                                buildJsonObject {
                                    put("kid", r.header.kid)
                                    put("alg", r.header.alg)
                                    r.header.epk?.let { put("epk", it) }
                                },
                            )
                            put("encrypted_key", r.encrypted_key)
                        }
                    },
                ),
            )
            put("iv", envelope.iv)
            put("ciphertext", envelope.ciphertext)
            put("tag", envelope.tag)
        }
        return kotlinx.serialization.json.Json.encodeToString(JsonObject.serializer(), json)
    }

    fun packedJsonToEnvelope(packed: String): DidCommEnvelope {
        val root = kotlinx.serialization.json.Json.parseToJsonElement(packed).jsonObject
        val protected = root["protected"]?.jsonPrimitive?.content
            ?: throw IllegalArgumentException("Missing 'protected' in packed DIDComm message")
        val recipients = root["recipients"]?.jsonArray?.map { recipientJson ->
            val recipientObj = recipientJson.jsonObject
            val headerObj = recipientObj["header"]?.jsonObject
                ?: throw IllegalArgumentException("Missing 'header' in recipient")
            DidCommRecipient(
                header = DidCommRecipientHeader(
                    kid = headerObj["kid"]?.jsonPrimitive?.content
                        ?: throw IllegalArgumentException("Missing 'kid' in header"),
                    alg = headerObj["alg"]?.jsonPrimitive?.content ?: "ECDH-1PU+A256KW",
                    epk = headerObj["epk"]?.jsonObject,
                ),
                encrypted_key = recipientObj["encrypted_key"]?.jsonPrimitive?.content
                    ?: throw IllegalArgumentException("Missing 'encrypted_key' in recipient"),
            )
        } ?: throw IllegalArgumentException("Missing 'recipients' in packed DIDComm message")
        val iv = root["iv"]?.jsonPrimitive?.content
            ?: throw IllegalArgumentException("Missing 'iv' in packed DIDComm message")
        val ciphertext = root["ciphertext"]?.jsonPrimitive?.content
            ?: throw IllegalArgumentException("Missing 'ciphertext' in packed DIDComm message")
        val tag = root["tag"]?.jsonPrimitive?.content
            ?: throw IllegalArgumentException("Missing 'tag' in packed DIDComm message")
        return DidCommEnvelope(
            protected = protected,
            recipients = recipients,
            iv = iv,
            ciphertext = ciphertext,
            tag = tag,
        )
    }
}
