package com.geoknoesis.vericore.godiddy.issuer

import com.geoknoesis.vericore.core.VeriCoreException
import com.geoknoesis.vericore.godiddy.GodiddyClient
import com.geoknoesis.vericore.godiddy.models.GodiddyIssueCredentialRequest
import com.geoknoesis.vericore.godiddy.models.GodiddyIssueCredentialResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*

/**
 * Client for godiddy Universal Issuer service.
 */
class GodiddyIssuer(
    private val client: GodiddyClient
) {
    /**
     * Issues a Verifiable Credential using Universal Issuer.
     *
     * @param credential The credential to issue (as JsonObject)
     * @param options Issuance options (format, proof type, etc.)
     * @return The issued credential with proof
     */
    suspend fun issueCredential(
        credential: JsonObject,
        options: Map<String, Any?> = emptyMap()
    ): JsonObject = withContext(Dispatchers.IO) {
        try {
            // Universal Issuer endpoint: POST /1.0/credentials/issue
            val path = "/1.0/credentials/issue"
            
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
            
            val request = GodiddyIssueCredentialRequest(
                credential = credential,
                options = jsonOptions
            )
            
            val response: GodiddyIssueCredentialResponse = client.post(path, request)
            
            if (response.credential == null) {
                throw VeriCoreException("Failed to issue credential: ${response.error ?: "unknown error"}")
            }
            
            response.credential.jsonObject
        } catch (e: VeriCoreException) {
            throw e
        } catch (e: Exception) {
            throw VeriCoreException("Failed to issue credential: ${e.message}", e)
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
}

