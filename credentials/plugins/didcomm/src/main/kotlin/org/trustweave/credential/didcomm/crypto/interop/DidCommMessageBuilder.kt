package org.trustweave.credential.didcomm.crypto.interop

import org.didcommx.didcomm.message.Message
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.longOrNull

/**
 * Builds a didcomm-java [Message] from a TrustWeave JSON payload (DIDComm message shape).
 */
object DidCommMessageBuilder {

    fun fromJsonObject(
        message: JsonObject,
        fromDid: String,
        toDid: String,
    ): Message {
        val id = message["id"]?.jsonPrimitive?.content
            ?: throw IllegalArgumentException("DIDComm message missing 'id'")
        val type = message["type"]?.jsonPrimitive?.content
            ?: throw IllegalArgumentException("DIDComm message missing 'type'")
        val bodyEl = message["body"]
        val bodyMap: Map<String, Any?> = when (bodyEl) {
            is JsonObject -> jsonObjectToKotlinMap(bodyEl)
            null -> emptyMap()
            else -> throw IllegalArgumentException("DIDComm message 'body' must be a JSON object")
        }
        val from = message["from"]?.jsonPrimitive?.content ?: fromDid
        val toList = when (val toEl = message["to"]) {
            is JsonArray -> toEl.mapNotNull { el ->
                (el as? JsonPrimitive)?.takeIf { it.isString }?.content
            }
            is JsonPrimitive -> listOf(toEl.content)
            null -> listOf(toDid)
            else -> throw IllegalArgumentException("DIDComm message 'to' must be a string or array of strings")
        }.ifEmpty { listOf(toDid) }

        val builder = Message.builder(id, bodyMap, type)
            .from(from)
            .to(toList)

        message["created_time"]?.jsonPrimitive?.longOrNull?.let { builder.createdTime(it) }
        message["expires_time"]?.jsonPrimitive?.let { prim ->
            prim.longOrNull?.let { builder.expiresTime(it) }
        }

        return builder.build()
    }

    private fun jsonObjectToKotlinMap(obj: JsonObject): Map<String, Any?> =
        obj.entries.associate { (k, v) -> k to jsonElementToKotlin(v) }

    private fun jsonElementToKotlin(el: JsonElement): Any? = when (el) {
        is JsonPrimitive -> when {
            el.isString -> el.content
            el.booleanOrNull != null -> el.booleanOrNull!!
            el.longOrNull != null -> el.longOrNull!!
            el.doubleOrNull != null -> el.doubleOrNull!!
            else -> el.contentOrNull
        }
        is JsonObject -> jsonObjectToKotlinMap(el)
        is JsonArray -> el.map { jsonElementToKotlin(it) }
        JsonNull -> null
        else -> el.toString()
    }
}
