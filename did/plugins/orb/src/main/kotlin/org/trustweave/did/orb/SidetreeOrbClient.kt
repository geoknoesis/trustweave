package org.trustweave.did.orb

import org.trustweave.did.model.DidDocument
import org.trustweave.did.representation.DidDocumentJsonProducer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.interfaces.ECPublicKey
import java.security.spec.ECGenParameterSpec
import java.util.Base64

/**
 * Sidetree REST client adapted for Orb.
 *
 * Implements the four Sidetree operation builders (create / update / recover /
 * deactivate) plus operation submission and DID resolution against an Orb node.
 *
 * Operation payloads follow the [Sidetree v1 spec](https://identity.foundation/sidetree/spec/):
 * - Commitments are `base64url(SHA-256(JCS(publicKeyJwk)))`.
 * - `deltaHash` is `base64url(SHA-256(JCS(delta)))`.
 * - DID suffix is `base64url(SHA-256(JCS(suffixData)))`.
 *
 * NOTE: This client intentionally duplicates the Sidetree primitives from the
 * `:did:plugins:ion` plugin because that code lives in an internal package of
 * the ION module. A future refactor should extract these primitives into
 * `:did:plugins:base` so all Sidetree-based methods (ION, Orb, ...) can share
 * a single canonical implementation.
 */
internal class SidetreeOrbClient(
    private val httpClient: OkHttpClient,
    private val config: OrbDidConfig,
) {

    data class OperationResponse(
        val did: String?,
        val rawBody: String,
        val success: Boolean,
        val httpStatus: Int,
        val error: String? = null,
    )

    data class ResolutionResponse(
        val document: JsonObject?,
        val metadata: JsonObject?,
        val success: Boolean,
        val httpStatus: Int,
        val error: String? = null,
    )

    // ─── Operation builders ──────────────────────────────────────────────────────

    /**
     * Builds a Sidetree create operation and the corresponding long-form DID.
     *
     * @param publicKeyJwk JWK of the signing key (must contain `kty` and the
     *                     curve parameters). Embedded in the document patch.
     * @return Pair of (create-operation JSON, longFormDid).
     */
    suspend fun buildCreateOperation(
        publicKeyJwk: Map<String, Any?>,
    ): Pair<JsonObject, String> = withContext(Dispatchers.IO) {
        val b64url = Base64.getUrlEncoder().withoutPadding()

        val recoveryPublicJwk = generateEphemeralP256PublicJwk()
        val updatePublicJwk = generateEphemeralP256PublicJwk()

        val recoveryCommitment = computeCommitment(recoveryPublicJwk)
        val updateCommitment = computeCommitment(updatePublicJwk)

        val delta = buildJsonObject {
            put("updateCommitment", updateCommitment)
            put(
                "patches",
                buildJsonArray {
                    add(
                        buildJsonObject {
                            put("action", "replace")
                            put(
                                "document",
                                buildJsonObject {
                                    put(
                                        "publicKeys",
                                        buildJsonArray {
                                            add(
                                                buildJsonObject {
                                                    put("id", "key-1")
                                                    put("type", "JsonWebKey2020")
                                                    put("publicKeyJwk", mapToJsonObject(publicKeyJwk))
                                                    put(
                                                        "purposes",
                                                        buildJsonArray {
                                                            add("authentication")
                                                            add("assertionMethod")
                                                        },
                                                    )
                                                },
                                            )
                                        },
                                    )
                                },
                            )
                        },
                    )
                },
            )
        }

        val deltaHash = b64url.encodeToString(sha256(jcsCanonicalize(delta)))

        val suffixData = buildJsonObject {
            put("deltaHash", deltaHash)
            put("recoveryCommitment", recoveryCommitment)
        }

        val didSuffix = b64url.encodeToString(sha256(jcsCanonicalize(suffixData)))

        val longFormPayload = buildJsonObject {
            put("suffixData", suffixData)
            put("delta", delta)
        }
        val longFormDid =
            "${config.namespace}:$didSuffix:${b64url.encodeToString(longFormPayload.toString().toByteArray(Charsets.UTF_8))}"

        val createOp = buildJsonObject {
            put("type", "create")
            put("suffixData", suffixData)
            put("delta", delta)
        }

        createOp to longFormDid
    }

    suspend fun buildUpdateOperation(
        did: String,
        updatedDocument: DidDocument,
    ): JsonObject = withContext(Dispatchers.IO) {
        val b64url = Base64.getUrlEncoder().withoutPadding()
        val nextUpdatePublicJwk = generateEphemeralP256PublicJwk()
        val nextUpdateCommitment = computeCommitment(nextUpdatePublicJwk)
        val revealValue = b64url.encodeToString(sha256(jcsCanonicalizeMap(nextUpdatePublicJwk)))

        val delta = buildJsonObject {
            put("updateCommitment", nextUpdateCommitment)
            put(
                "patches",
                buildJsonArray {
                    add(
                        buildJsonObject {
                            put("action", "replace")
                            put("document", DidDocumentJsonProducer.toJsonObject(updatedDocument, useV1_1Context = true))
                        },
                    )
                },
            )
        }
        val deltaHash = b64url.encodeToString(sha256(jcsCanonicalize(delta)))

        buildJsonObject {
            put("type", "update")
            put("didSuffix", extractDidSuffix(did))
            put("revealValue", revealValue)
            put("delta", delta)
            put(
                "signedData",
                buildJsonObject {
                    put("updateKey", mapToJsonObject(nextUpdatePublicJwk))
                    put("deltaHash", deltaHash)
                },
            )
        }
    }

    suspend fun buildDeactivateOperation(did: String): JsonObject = withContext(Dispatchers.IO) {
        val b64url = Base64.getUrlEncoder().withoutPadding()
        val recoveryPublicJwk = generateEphemeralP256PublicJwk()
        val revealValue = b64url.encodeToString(sha256(jcsCanonicalizeMap(recoveryPublicJwk)))

        buildJsonObject {
            put("type", "deactivate")
            put("didSuffix", extractDidSuffix(did))
            put("revealValue", revealValue)
            put(
                "signedData",
                buildJsonObject {
                    put("didSuffix", extractDidSuffix(did))
                    put("recoveryKey", mapToJsonObject(recoveryPublicJwk))
                },
            )
        }
    }

    // ─── Network operations ──────────────────────────────────────────────────────

    /**
     * Submits a Sidetree operation to the Orb node.
     *
     * Orb returns a DID resolution result (`{ didDocument, didDocumentMetadata }`)
     * for create operations. For update/recover/deactivate operations Orb typically
     * acknowledges with 200 and an empty body, since the operation is queued for
     * batching.
     */
    suspend fun submitOperation(operation: JsonObject): OperationResponse = withContext(Dispatchers.IO) {
        val body = Json.encodeToString(JsonObject.serializer(), operation)
            .toRequestBody(JSON_MEDIA_TYPE)
        val builder = Request.Builder()
            .url(config.operationsUrl)
            .post(body)
            .addHeader("Content-Type", "application/json")
            .addHeader("Accept", "application/json")
        config.authHeader?.let { (name, value) -> builder.addHeader(name, value) }

        try {
            httpClient.newCall(builder.build()).execute().use { response ->
                val responseBody = response.body?.string() ?: ""
                if (!response.isSuccessful) {
                    return@withContext OperationResponse(
                        did = null,
                        rawBody = responseBody,
                        success = false,
                        httpStatus = response.code,
                        error = "HTTP ${response.code}: $responseBody",
                    )
                }
                val didFromBody = runCatching {
                    val element = if (responseBody.isBlank()) JsonNull else Json.parseToJsonElement(responseBody)
                    (element as? JsonObject)?.get("didDocument")?.jsonObject
                        ?.get("id")?.jsonPrimitive?.contentOrNull
                }.getOrNull()
                OperationResponse(
                    did = didFromBody,
                    rawBody = responseBody,
                    success = true,
                    httpStatus = response.code,
                )
            }
        } catch (e: IOException) {
            OperationResponse(
                did = null,
                rawBody = "",
                success = false,
                httpStatus = -1,
                error = "Network error: ${e.message}",
            )
        }
    }

    /**
     * Resolves a DID through the Orb node.
     */
    suspend fun resolveDid(did: String): ResolutionResponse = withContext(Dispatchers.IO) {
        val url = "${config.identifiersUrl}/$did"
        val builder = Request.Builder()
            .url(url)
            .get()
            .addHeader("Accept", "application/json")
        config.authHeader?.let { (name, value) -> builder.addHeader(name, value) }

        try {
            httpClient.newCall(builder.build()).execute().use { response ->
                val responseBody = response.body?.string() ?: ""
                if (response.code == 404) {
                    return@withContext ResolutionResponse(
                        document = null,
                        metadata = null,
                        success = false,
                        httpStatus = 404,
                        error = "DID not found",
                    )
                }
                if (!response.isSuccessful) {
                    return@withContext ResolutionResponse(
                        document = null,
                        metadata = null,
                        success = false,
                        httpStatus = response.code,
                        error = "HTTP ${response.code}: $responseBody",
                    )
                }
                val parsed = runCatching { Json.parseToJsonElement(responseBody) as? JsonObject }.getOrNull()
                    ?: return@withContext ResolutionResponse(
                        document = null,
                        metadata = null,
                        success = false,
                        httpStatus = response.code,
                        error = "Invalid JSON response from Orb",
                    )
                ResolutionResponse(
                    document = parsed["didDocument"] as? JsonObject,
                    metadata = parsed["didDocumentMetadata"] as? JsonObject,
                    success = parsed["didDocument"] is JsonObject,
                    httpStatus = response.code,
                )
            }
        } catch (e: IOException) {
            ResolutionResponse(
                document = null,
                metadata = null,
                success = false,
                httpStatus = -1,
                error = "Network error: ${e.message}",
            )
        }
    }

    // ─── Cryptographic helpers ───────────────────────────────────────────────────

    private fun generateEphemeralP256PublicJwk(): Map<String, Any?> {
        val gen = KeyPairGenerator.getInstance("EC")
        gen.initialize(ECGenParameterSpec("secp256r1"))
        val kp = gen.generateKeyPair()
        val pub = kp.public as ECPublicKey
        val b64url = Base64.getUrlEncoder().withoutPadding()
        val w = pub.w
        val xBytes = w.affineX.toByteArray().padCoord()
        val yBytes = w.affineY.toByteArray().padCoord()
        return mapOf("kty" to "EC", "crv" to "P-256", "x" to b64url.encodeToString(xBytes), "y" to b64url.encodeToString(yBytes))
    }

    private fun ByteArray.padCoord(): ByteArray = when {
        size > 32 -> sliceArray(size - 32 until size)
        size < 32 -> ByteArray(32 - size) + this
        else -> this
    }

    private fun computeCommitment(publicKeyJwk: Map<String, Any?>): String {
        val canonical = jcsCanonicalizeMap(publicKeyJwk)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(sha256(canonical))
    }

    private fun sha256(bytes: ByteArray): ByteArray =
        MessageDigest.getInstance("SHA-256").digest(bytes)

    private fun jcsCanonicalize(obj: JsonObject): ByteArray =
        jcsBuildString(obj).toByteArray(Charsets.UTF_8)

    private fun jcsCanonicalizeMap(map: Map<String, Any?>): ByteArray =
        jcsBuildString(mapToJsonObject(map)).toByteArray(Charsets.UTF_8)

    private fun jcsBuildString(element: JsonElement): String = when (element) {
        is JsonObject -> element.entries
            .sortedBy { it.key }
            .joinToString(",", "{", "}") { (k, v) -> "\"$k\":${jcsBuildString(v)}" }
        is JsonArray -> element.joinToString(",", "[", "]") { jcsBuildString(it) }
        is JsonPrimitive -> element.toString()
        else -> element.toString()
    }

    /**
     * Extracts the suffix of an Orb / Sidetree DID.
     *
     * For `did:orb:<suffix>` returns `<suffix>`. For a long-form DID
     * `did:orb:<suffix>:<long-form-payload>` returns `<suffix>`. The optional
     * anchor-origin segment in Orb's hosted form (`did:orb:<cid>:<suffix>`) is
     * preserved by config: callers should strip non-suffix segments before
     * calling this method, which only handles the prefix.
     */
    private fun extractDidSuffix(did: String): String {
        val prefix = "${config.namespace}:"
        require(did.startsWith(prefix)) { "DID does not match Orb namespace '$prefix': $did" }
        val rest = did.removePrefix(prefix)
        val colonIndex = rest.indexOf(':')
        return if (colonIndex >= 0) rest.substring(0, colonIndex) else rest
    }

    private fun mapToJsonObject(map: Map<String, Any?>): JsonObject = buildJsonObject {
        map.forEach { (key, value) ->
            when (value) {
                null -> put(key, JsonNull)
                is String -> put(key, value)
                is Number -> put(key, value)
                is Boolean -> put(key, value)
                is Map<*, *> -> {
                    @Suppress("UNCHECKED_CAST")
                    put(key, mapToJsonObject(value as Map<String, Any?>))
                }
                is List<*> -> put(
                    key,
                    JsonArray(
                        value.map { v ->
                            when (v) {
                                is String -> JsonPrimitive(v)
                                is Number -> JsonPrimitive(v)
                                is Boolean -> JsonPrimitive(v)
                                null -> JsonNull
                                else -> JsonPrimitive(v.toString())
                            }
                        },
                    ),
                )
                else -> put(key, value.toString())
            }
        }
    }

    @Suppress("unused")
    private fun jsonObjectToMap(obj: JsonObject): Map<String, Any?> = obj.entries.associate { (key, value) ->
        key to when (value) {
            is JsonPrimitive ->
                value.contentOrNull
                    ?: value.booleanOrNull
                    ?: value.longOrNull
                    ?: value.doubleOrNull
                    ?: value.toString()
            is JsonObject -> jsonObjectToMap(value)
            is JsonArray -> value.map { (it as? JsonPrimitive)?.content ?: it.toString() }
            else -> value.toString()
        }
    }

    companion object {
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }
}
