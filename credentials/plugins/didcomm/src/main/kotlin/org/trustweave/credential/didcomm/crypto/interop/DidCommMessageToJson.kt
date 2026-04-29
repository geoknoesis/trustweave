package org.trustweave.credential.didcomm.crypto.interop

import org.didcommx.didcomm.message.Message
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject

/**
 * Converts a didcomm-java [Message] to a [JsonObject] matching TrustWeave DIDComm message JSON.
 */
fun Message.toJsonObject(): JsonObject = buildJsonObject {
    put("id", JsonPrimitive(id))
    put("type", JsonPrimitive(type))
    put("typ", JsonPrimitive(typ.typ))
    from?.let { put("from", JsonPrimitive(it)) }
    to?.let { list ->
        if (list.size == 1) put("to", JsonPrimitive(list.first()))
        else put("to", JsonArray(list.map { JsonPrimitive(it) }))
    }
    createdTime?.let { put("created_time", JsonPrimitive(it)) }
    expiresTime?.let { put("expires_time", JsonPrimitive(it)) }
    put("body", kotlinMapToJsonElement(body))
    thid?.let { put("thid", JsonPrimitive(it)) }
    pthid?.let { put("pthid", JsonPrimitive(it)) }
    ack?.let { put("ack", JsonPrimitive(it)) }
}

private fun kotlinMapToJsonElement(map: Map<String, Any?>): JsonObject =
    buildJsonObject {
        map.forEach { (k, v) -> put(k, anyToJsonElement(v)) }
    }

@Suppress("UNCHECKED_CAST")
private fun anyToJsonElement(v: Any?): JsonElement = when (v) {
    null -> JsonNull
    is String -> JsonPrimitive(v)
    is Number -> JsonPrimitive(v.toString())
    is Boolean -> JsonPrimitive(v)
    is Map<*, *> -> kotlinMapToJsonElement(v as Map<String, Any?>)
    is List<*> -> buildJsonArray { v.forEach { add(anyToJsonElement(it)) } }
    else -> JsonPrimitive(v.toString())
}
