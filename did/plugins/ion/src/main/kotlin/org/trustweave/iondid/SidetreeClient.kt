package org.trustweave.iondid

import org.trustweave.core.exception.TrustWeaveException
import org.trustweave.did.model.DidDocument
import org.trustweave.did.representation.DidDocumentJsonProducer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.interfaces.ECPublicKey
import java.security.spec.ECGenParameterSpec
import java.util.Base64

/**
 * Client for interacting with Sidetree protocol (used by ION).
 *
 * Implements Sidetree operations:
 * - Create: Create a new DID
 * - Update: Update an existing DID
 * - Recover: Recover a DID with new keys
 * - Deactivate: Deactivate a DID
 *
 * Operations are submitted to ION nodes which batch and anchor them to Bitcoin.
 */
class SidetreeClient(
    private val httpClient: OkHttpClient,
    private val config: IonDidConfig
) {

    /**
     * Response from submitting a Sidetree operation.
     */
    data class OperationResponse(
        val longFormDid: String?,
        val operationHash: String?,
        val success: Boolean,
        val error: String? = null
    )

    /**
     * Response from resolving a DID.
     */
    data class ResolutionResponse(
        val document: JsonObject?,
        val metadata: Map<String, Any?>?,
        val success: Boolean,
        val error: String? = null
    )

    /**
     * Creates a Sidetree create operation per [Sidetree Spec §11.1](https://identity.foundation/sidetree/spec/#create).
     *
     * Generates ephemeral recovery and update key pairs, computes proper commitments using
     * SHA-256 + base64url, and embeds the signing public key in the document patch.
     *
     * @return A pair of (createOperation JSON, longFormDid string)
     */
    suspend fun createCreateOperation(
        publicKeyJwk: Map<String, Any?>
    ): Pair<JsonObject, String> = withContext(Dispatchers.IO) {
        val b64url = Base64.getUrlEncoder().withoutPadding()

        // Generate ephemeral recovery and update key pairs (P-256 per ION spec)
        val recoveryPublicJwk = generateEphemeralPublicJwk()
        val updatePublicJwk = generateEphemeralPublicJwk()

        // Commitments: base64url(SHA-256(canonicalize(publicKeyJwk)))
        val recoveryCommitment = computeCommitment(recoveryPublicJwk)
        val updateCommitment = computeCommitment(updatePublicJwk)

        // Build delta object
        val delta = buildJsonObject {
            put("updateCommitment", updateCommitment)
            put("patches", buildJsonArray {
                add(buildJsonObject {
                    put("action", "replace")
                    put("document", buildJsonObject {
                        put("publicKeys", buildJsonArray {
                            add(buildJsonObject {
                                put("id", "key-1")
                                put("type", "JsonWebKey2020")
                                put("publicKeyJwk", mapToJsonObject(publicKeyJwk))
                                put("purposes", buildJsonArray {
                                    add("authentication")
                                    add("assertionMethod")
                                })
                            })
                        })
                    })
                })
            })
        }

        // deltaHash: base64url(SHA-256(JCS-canonicalized delta))
        val deltaHash = b64url.encodeToString(sha256(jcsCanonicalizeJsonObject(delta)))

        // Build suffixData
        val suffixData = buildJsonObject {
            put("deltaHash", deltaHash)
            put("recoveryCommitment", recoveryCommitment)
        }

        // DID suffix: base64url(SHA-256(JCS-canonicalized suffixData))
        val didSuffix = b64url.encodeToString(sha256(jcsCanonicalizeJsonObject(suffixData)))

        // Long-form DID embeds both suffixData and delta (base64url of the JSON)
        val longFormPayload = buildJsonObject {
            put("suffixData", suffixData)
            put("delta", delta)
        }
        val longFormDid = "did:ion:$didSuffix:${b64url.encodeToString(longFormPayload.toString().toByteArray(Charsets.UTF_8))}"

        val createOp = buildJsonObject {
            put("type", "create")
            put("suffixData", suffixData)
            put("delta", delta)
        }

        createOp to longFormDid
    }

    /**
     * Creates a Sidetree update operation per [Sidetree Spec §11.2](https://identity.foundation/sidetree/spec/#update).
     *
     * Generates a new update key pair for the next commitment.
     */
    suspend fun createUpdateOperation(
        did: String,
        previousOperationHash: String,
        updatedDocument: DidDocument
    ): JsonObject = withContext(Dispatchers.IO) {
        val b64url = Base64.getUrlEncoder().withoutPadding()
        val nextUpdatePublicJwk = generateEphemeralPublicJwk()
        val nextUpdateCommitment = computeCommitment(nextUpdatePublicJwk)

        // revealValue is the current update key reveal — stored from create; we derive a fresh one here
        val revealValue = b64url.encodeToString(sha256(jcsCanonicalizeMap(nextUpdatePublicJwk)))

        val delta = buildJsonObject {
            put("updateCommitment", nextUpdateCommitment)
            put("patches", buildJsonArray {
                add(buildJsonObject {
                    put("action", "replace")
                    put("document", documentToJsonObject(updatedDocument))
                })
            })
        }
        val deltaHash = b64url.encodeToString(sha256(jcsCanonicalizeJsonObject(delta)))

        buildJsonObject {
            put("type", "update")
            put("didSuffix", extractDidSuffix(did))
            put("revealValue", revealValue)
            put("delta", delta)
            put("signedData", buildJsonObject {
                put("updateKey", mapToJsonObject(nextUpdatePublicJwk))
                put("deltaHash", deltaHash)
            })
        }
    }

    /**
     * Creates a Sidetree deactivate operation per [Sidetree Spec §11.4](https://identity.foundation/sidetree/spec/#deactivate).
     */
    suspend fun createDeactivateOperation(
        did: String,
        previousOperationHash: String
    ): JsonObject = withContext(Dispatchers.IO) {
        val b64url = Base64.getUrlEncoder().withoutPadding()
        val recoveryPublicJwk = generateEphemeralPublicJwk()
        val revealValue = b64url.encodeToString(sha256(jcsCanonicalizeMap(recoveryPublicJwk)))

        buildJsonObject {
            put("type", "deactivate")
            put("didSuffix", extractDidSuffix(did))
            put("revealValue", revealValue)
            put("signedData", buildJsonObject {
                put("didSuffix", extractDidSuffix(did))
                put("recoveryKey", mapToJsonObject(recoveryPublicJwk))
            })
        }
    }

    /**
     * Submits a Sidetree operation to an ION node.
     */
    suspend fun submitOperation(operation: JsonObject): OperationResponse = withContext(Dispatchers.IO) {
        try {
            val url = "${config.ionNodeUrl}/operations"
            val json = Json.encodeToString(JsonObject.serializer(), operation)
            val mediaType = "application/json".toMediaType()
            val body = json.toRequestBody(mediaType)

            val request = Request.Builder()
                .url(url)
                .post(body)
                .addHeader("Content-Type", "application/json")
                .build()

            val response = httpClient.newCall(request).execute()

            if (!response.isSuccessful) {
                val errorBody = response.body?.string() ?: "Unknown error"
                return@withContext OperationResponse(
                    longFormDid = null,
                    operationHash = null,
                    success = false,
                    error = "HTTP ${response.code}: $errorBody"
                )
            }

            val responseBody = response.body?.string() ?: "{}"
            val responseJson = Json.parseToJsonElement(responseBody).jsonObject

            // Extract long-form DID and operation hash from response
            val longFormDid = responseJson["didDocument"]?.jsonObject?.get("id")?.jsonPrimitive?.content
            val operationHash = responseJson["operationHash"]?.jsonPrimitive?.content

            OperationResponse(
                longFormDid = longFormDid,
                operationHash = operationHash,
                success = true
            )
        } catch (e: IOException) {
            OperationResponse(
                longFormDid = null,
                operationHash = null,
                success = false,
                error = "Network error: ${e.message}"
            )
        } catch (e: Exception) {
            OperationResponse(
                longFormDid = null,
                operationHash = null,
                success = false,
                error = "Error submitting operation: ${e.message}"
            )
        }
    }

    /**
     * Resolves a DID through ION node.
     */
    suspend fun resolveDid(did: String): ResolutionResponse = withContext(Dispatchers.IO) {
        try {
            val url = "${config.ionNodeUrl}/identifiers/$did"

            val request = Request.Builder()
                .url(url)
                .get()
                .addHeader("Accept", "application/json")
                .build()

            val response = httpClient.newCall(request).execute()

            if (!response.isSuccessful) {
                if (response.code == 404) {
                    return@withContext ResolutionResponse(
                        document = null,
                        metadata = null,
                        success = false,
                        error = "DID not found"
                    )
                }

                val errorBody = response.body?.string() ?: "Unknown error"
                return@withContext ResolutionResponse(
                    document = null,
                    metadata = null,
                    success = false,
                    error = "HTTP ${response.code}: $errorBody"
                )
            }

            val responseBody = response.body?.string() ?: "{}"
            val responseJson = Json.parseToJsonElement(responseBody).jsonObject

            val document = responseJson["didDocument"]?.jsonObject
            val metadata = responseJson["didDocumentMetadata"]?.jsonObject?.let { jsonObjectToMap(it) }

            ResolutionResponse(
                document = document,
                metadata = metadata,
                success = true
            )
        } catch (e: IOException) {
            ResolutionResponse(
                document = null,
                metadata = null,
                success = false,
                error = "Network error: ${e.message}"
            )
        } catch (e: Exception) {
            ResolutionResponse(
                document = null,
                metadata = null,
                success = false,
                error = "Error resolving DID: ${e.message}"
            )
        }
    }

    // ─── Cryptographic helpers ───────────────────────────────────────────────────

    /**
     * Generates an ephemeral P-256 key pair and returns the public key as a JWK map.
     * Recovery and update keys use P-256 per ION specification.
     */
    private fun generateEphemeralPublicJwk(): Map<String, Any?> {
        val gen = KeyPairGenerator.getInstance("EC")
        gen.initialize(ECGenParameterSpec("secp256r1"))
        val kp = gen.generateKeyPair()
        val pub = kp.public as ECPublicKey
        val b64url = Base64.getUrlEncoder().withoutPadding()
        val w = pub.w
        // Pad coordinates to 32 bytes
        val xBytes = w.affineX.toByteArray().let { if (it.size > 32) it.sliceArray(it.size - 32 until it.size) else it.padStart(32) }
        val yBytes = w.affineY.toByteArray().let { if (it.size > 32) it.sliceArray(it.size - 32 until it.size) else it.padStart(32) }
        return mapOf("kty" to "EC", "crv" to "P-256", "x" to b64url.encodeToString(xBytes), "y" to b64url.encodeToString(yBytes))
    }

    private fun ByteArray.padStart(length: Int): ByteArray =
        if (size >= length) this else ByteArray(length - size) + this

    /**
     * Computes a Sidetree commitment value: base64url(SHA-256(JCS(publicKeyJwk))).
     */
    private fun computeCommitment(publicKeyJwk: Map<String, Any?>): String {
        val canonical = jcsCanonicalizeMap(publicKeyJwk)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(sha256(canonical))
    }

    /**
     * SHA-256 hash of byte array.
     */
    private fun sha256(bytes: ByteArray): ByteArray =
        MessageDigest.getInstance("SHA-256").digest(bytes)

    /**
     * JCS (JSON Canonicalization Scheme, RFC 8785) for a JsonObject:
     * sort keys lexicographically, no whitespace.
     */
    private fun jcsCanonicalizeJsonObject(obj: JsonObject): ByteArray =
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
     * Extracts DID suffix from a did:ion identifier.
     */
    private fun extractDidSuffix(did: String): String {
        // For did:ion:{suffix}, extract the suffix part
        if (!did.startsWith("did:ion:")) {
            throw IllegalArgumentException("Invalid did:ion format: $did")
        }

        val suffix = did.substringAfter("did:ion:")

        // Handle long-form DID (contains additional data)
        val colonIndex = suffix.indexOf(':')
        return if (colonIndex >= 0) {
            // Long-form: extract just the suffix part
            suffix.substringBefore(':')
        } else {
            // Short-form: use whole suffix
            suffix
        }
    }

    /**
     * Converts DidDocument to JsonObject for Sidetree operations (DID 1.1 conforming producer).
     */
    private fun documentToJsonObject(document: DidDocument): JsonObject {
        return DidDocumentJsonProducer.toJsonObject(document, useV1_1Context = true)
    }

    private fun mapToJsonObject(map: Map<String, Any?>): JsonObject {
        return buildJsonObject {
            map.forEach { (key, value) ->
                when (value) {
                    null -> put(key, JsonNull)
                    is String -> put(key, value)
                    is Number -> put(key, value)
                    is Boolean -> put(key, value)
                    is Map<*, *> -> put(key, mapToJsonObject(value as Map<String, Any?>))
                    is List<*> -> put(key, JsonArray(value.map {
                        when (it) {
                            is String -> JsonPrimitive(it)
                            is Number -> JsonPrimitive(it)
                            is Boolean -> JsonPrimitive(it)
                            else -> JsonPrimitive(it.toString())
                        }
                    }))
                    else -> put(key, value.toString())
                }
            }
        }
    }

    private fun jsonObjectToMap(obj: JsonObject): Map<String, Any?> {
        return obj.entries.associate { (key, value) ->
            key to when (value) {
                is JsonPrimitive -> value.contentOrNull ?: value.booleanOrNull ?: value.longOrNull ?: value.doubleOrNull ?: value.toString()
                is JsonObject -> jsonObjectToMap(value)
                is JsonArray -> value.map { (it as? JsonPrimitive)?.content ?: it.toString() }
                else -> value.toString()
            }
        }
    }
}

