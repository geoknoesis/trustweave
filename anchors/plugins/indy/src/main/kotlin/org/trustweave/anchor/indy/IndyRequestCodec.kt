package org.trustweave.anchor.indy

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.Json
import java.security.MessageDigest

/**
 * Builders and parsers for Hyperledger Indy ATTRIB / GET_ATTRIB ledger requests.
 *
 * The wire format mirrors what `indy-vdr` produces, so requests built here can be
 * submitted to any Indy pool (Sovrin, BCovrin, von-network, …) through `indy-vdr-proxy`
 * or a direct ZeroMQ client.
 *
 * See: https://github.com/hyperledger/indy-node/blob/main/docs/source/requests.md#attrib
 */
internal object IndyRequestCodec {

    /** Stable JSON used for both signing input and on-wire serialization. */
    val json: Json = Json {
        encodeDefaults = true
        prettyPrint = false
    }

    /**
     * Build an unsigned ATTRIB request that stores [rawPayload] under the configured
     * attribute name on [targetDid].
     *
     * @param submitterDid DID writing the transaction (`identifier` on the request).
     * @param targetDid DID the attribute is attached to (`operation.dest`). Typically
     *   equal to [submitterDid] for self-anchoring.
     * @param rawPayload The JSON object stored under `operation.raw`.
     * @param reqId Optional request id; defaults to epoch nanoseconds. Must be unique
     *   per submitter or the ledger will reject the request as a replay.
     */
    fun buildAttribRequest(
        submitterDid: String,
        targetDid: String,
        rawPayload: JsonObject,
        reqId: Long = nextReqId()
    ): JsonObject {
        require(submitterDid.isNotBlank()) { "submitterDid must not be blank" }
        require(targetDid.isNotBlank()) { "targetDid must not be blank" }

        val rawString = json.encodeToString(JsonObject.serializer(), rawPayload)
        return buildJsonObject {
            put("operation", buildJsonObject {
                put("type", JsonPrimitive(IndyTxnTypes.ATTRIB))
                put("dest", JsonPrimitive(targetDid))
                put("raw", JsonPrimitive(rawString))
            })
            put("identifier", JsonPrimitive(submitterDid))
            put("reqId", JsonPrimitive(reqId))
            put("protocolVersion", JsonPrimitive(IndyTxnTypes.PROTOCOL_VERSION))
        }
    }

    /**
     * Build a GET_ATTRIB request for the configured attribute name. GET_ATTRIB is a
     * read request and is sent unsigned (the ledger accepts unsigned reads).
     */
    fun buildGetAttribRequest(
        submitterDid: String,
        targetDid: String,
        attributeName: String = IndyAttribFields.ATTRIB_NAME,
        reqId: Long = nextReqId()
    ): JsonObject {
        require(submitterDid.isNotBlank()) { "submitterDid must not be blank" }
        require(targetDid.isNotBlank()) { "targetDid must not be blank" }
        require(attributeName.isNotBlank()) { "attributeName must not be blank" }

        return buildJsonObject {
            put("operation", buildJsonObject {
                put("type", JsonPrimitive(IndyTxnTypes.GET_ATTRIB))
                put("dest", JsonPrimitive(targetDid))
                put("raw", JsonPrimitive(attributeName))
            })
            put("identifier", JsonPrimitive(submitterDid))
            put("reqId", JsonPrimitive(reqId))
            put("protocolVersion", JsonPrimitive(IndyTxnTypes.PROTOCOL_VERSION))
        }
    }

    /**
     * Serialize a request to the canonical byte string Indy nodes expect as signing input.
     *
     * Indy uses a deterministic "ledger serializer" derived from the request fields. The
     * exact algorithm is documented in indy-node's ledger serializer: keys are sorted
     * lexicographically and nested objects recursively serialized as `key1:value|key2:value`
     * with primitives rendered as their string form.
     *
     * See: https://github.com/hyperledger/indy-plenum/blob/main/plenum/common/types.py
     */
    fun signingPayload(request: JsonObject): ByteArray {
        val builder = StringBuilder()
        appendSorted(builder, request)
        return builder.toString().toByteArray(Charsets.UTF_8)
    }

    private fun appendSorted(out: StringBuilder, element: JsonElement) {
        when (element) {
            is JsonObject -> {
                val keys = element.keys.sorted()
                keys.forEachIndexed { idx, key ->
                    if (idx > 0) out.append('|')
                    out.append(key).append(':')
                    appendSorted(out, element[key]!!)
                }
            }
            is kotlinx.serialization.json.JsonArray -> {
                element.forEachIndexed { idx, item ->
                    if (idx > 0) out.append(',')
                    appendSorted(out, item)
                }
            }
            is JsonPrimitive -> {
                // Indy's canonical serializer renders primitives via their string form
                // (booleans as `True`/`False`, null as `None`, numbers unquoted). We mirror
                // the subset that ATTRIB/GET_ATTRIB use — only strings and longs occur.
                out.append(element.contentOrNull ?: "None")
            }
        }
    }

    /**
     * Attach a Base58-encoded signature to a request in-place. Returns the signed request.
     */
    fun attachSignature(request: JsonObject, signatureBase58: String): JsonObject {
        require(signatureBase58.isNotBlank()) { "signatureBase58 must not be blank" }
        return buildJsonObject {
            request.forEach { (k, v) -> put(k, v) }
            put("signature", JsonPrimitive(signatureBase58))
        }
    }

    /**
     * Extract the JSON object stored under `operation.raw` from a GET_ATTRIB ledger reply.
     *
     * Indy reply envelopes look like:
     * ```
     * { "op": "REPLY",
     *   "result": {
     *     "type": "104",
     *     "dest": "...",
     *     "data": "{\"digest\":\"...\"}",
     *     "seqNo": 42,
     *     "txnTime": 1700000000
     *   }
     * }
     * ```
     *
     * @return Parsed object stored under `data`, or `null` if the attribute is absent
     *   (Indy returns `"data": null` for never-set attributes).
     */
    fun parseGetAttribResponse(responseJson: JsonObject): GetAttribReply {
        val result = responseJson["result"]?.jsonObject
            ?: throw IllegalArgumentException("Missing 'result' field in Indy reply: $responseJson")

        val dataField = result["data"]
        val rawString = when {
            dataField == null -> null
            dataField is JsonPrimitive && !dataField.isString -> null
            dataField is JsonPrimitive -> dataField.contentOrNull
            else -> dataField.toString()
        }

        val parsedRaw = rawString?.takeIf { it.isNotBlank() && it != "null" }?.let {
            json.parseToJsonElement(it).jsonObject
        }

        val seqNo = result["seqNo"]?.jsonPrimitive?.contentOrNull?.toLongOrNull()
        val txnTime = result["txnTime"]?.jsonPrimitive?.contentOrNull?.toLongOrNull()

        return GetAttribReply(
            raw = parsedRaw,
            seqNo = seqNo,
            txnTime = txnTime
        )
    }

    /**
     * Extract the sequence number / transaction id from an ATTRIB write reply.
     *
     * Indy replies use either a numeric `seqNo` (newer pools) or a hex `txnId` (older).
     * We prefer `seqNo` because it is shorter and is what most explorers index against.
     */
    fun parseWriteReply(responseJson: JsonObject): String {
        val result = responseJson["result"]?.jsonObject
            ?: throw IllegalArgumentException("Missing 'result' field in Indy write reply: $responseJson")

        result["seqNo"]?.jsonPrimitive?.contentOrNull?.let { return it }
        result["txnId"]?.jsonPrimitive?.contentOrNull?.let { return it }
        result["txnMetadata"]?.jsonObject?.get("seqNo")?.jsonPrimitive?.contentOrNull?.let { return it }
        result["txnMetadata"]?.jsonObject?.get("txnId")?.jsonPrimitive?.contentOrNull?.let { return it }

        throw IllegalArgumentException("Indy write reply has no seqNo / txnId: $responseJson")
    }

    /**
     * SHA-256 helper used to compute digests for the anchored payload.
     */
    fun sha256Hex(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
        return digest.joinToString(separator = "") { "%02x".format(it) }
    }

    /**
     * Monotonic, second-precision request id. Indy nodes reject duplicate reqIds so we
     * combine epoch millis with a per-VM counter to stay unique under high request rates.
     */
    fun nextReqId(): Long {
        val now = System.currentTimeMillis()
        val ctr = counter.incrementAndGet() and 0xFFFF
        return now * 1_000 + ctr
    }

    private val counter = java.util.concurrent.atomic.AtomicLong(0)
}

/** Parsed GET_ATTRIB reply with the most relevant fields surfaced for the anchor layer. */
internal data class GetAttribReply(
    val raw: JsonObject?,
    val seqNo: Long?,
    val txnTime: Long?
)
