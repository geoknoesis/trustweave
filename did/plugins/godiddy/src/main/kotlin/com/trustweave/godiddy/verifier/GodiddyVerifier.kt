package com.trustweave.godiddy.verifier

import com.trustweave.core.exception.TrustWeaveException
import com.trustweave.godiddy.GodiddyClient
import com.trustweave.godiddy.models.GodiddyVerifyCredentialRequest
import com.trustweave.godiddy.models.GodiddyVerifyCredentialResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*

/**
 * Result of credential verification.
 */
data class CredentialVerificationResult(
    val verified: Boolean,
    val error: String? = null,
    val checks: Map<String, Boolean>? = null
)

/**
 * Client for godiddy Universal Verifier service.
 */
class GodiddyVerifier(
    private val client: GodiddyClient
) {
    /**
     * Verifies a Verifiable Credential using Universal Verifier.
     *
     * @param credential The credential to verify (as JsonObject)
     * @param options Verification options (format, policies, etc.)
     * @return CredentialVerificationResult with verification status
     */
    suspend fun verifyCredential(
        credential: JsonObject,
        options: Map<String, Any?> = emptyMap()
    ): CredentialVerificationResult = withContext(Dispatchers.IO) {
        try {
            // Universal Verifier endpoint: POST /1.0/credentials/verify
            val path = "/1.0/credentials/verify"

            // Convert options to JsonElement map
            val jsonOptions = options.mapValues { (_, value) ->
                when (value) {
                    is String -> JsonPrimitive(value)
                    is Number -> JsonPrimitive(value.toDouble())
                    is Boolean -> JsonPrimitive(value)
                    is Map<*, *> -> JsonObject(value.mapKeys { it.key.toString() }
                        .mapValues { convertToJsonElement(it.value) })
                    is List<*> -> JsonArray(value.map { convertToJsonElement(it) })
                    else -> JsonNull
                }
            }

            val request = GodiddyVerifyCredentialRequest(
                credential = credential,
                options = jsonOptions
            )

            val response: GodiddyVerifyCredentialResponse = client.post(path, request)

            CredentialVerificationResult(
                verified = response.verified,
                error = response.error,
                checks = response.checks
            )
        } catch (e: Exception) {
            throw com.trustweave.credential.exception.CredentialException.CredentialInvalid(
                reason = "Verification failed: ${e.message ?: "Unknown error"}",
                credentialId = null,
                field = null
            )
        }
    }

    /**
     * Converts Any to JsonElement.
     */
    private fun convertToJsonElement(value: Any?): JsonElement {
        return when (value) {
            null -> JsonNull
            is String -> JsonPrimitive(value)
            is Number -> JsonPrimitive(value.toDouble())
            is Boolean -> JsonPrimitive(value)
            is Map<*, *> -> JsonObject(value.mapKeys { it.key.toString() }
                .mapValues { convertToJsonElement(it.value) })
            is List<*> -> JsonArray(value.map { convertToJsonElement(it) })
            else -> JsonPrimitive(value.toString())
        }
    }

    /**
     * Converts JsonElement to Any.
     */
    private fun convertJsonElement(element: JsonElement): Any? {
        return when (element) {
            is JsonPrimitive -> {
                when {
                    element.isString -> element.content
                    element.booleanOrNull != null -> element.boolean
                    element.longOrNull != null -> element.long
                    element.doubleOrNull != null -> element.double
                    else -> element.content
                }
            }
            is JsonArray -> element.map { convertJsonElement(it) }
            is JsonObject -> element.entries.associate { it.key to convertJsonElement(it.value) }
            is JsonNull -> null
        }
    }
}

