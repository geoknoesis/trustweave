package org.trustweave.credential.internal

import com.github.jsonldjava.core.JsonLdOptions
import com.github.jsonldjava.core.JsonLdProcessor
import kotlinx.serialization.json.*

/**
 * JSON-LD utility functions for canonicalization and document conversion.
 * 
 * Centralizes JSON-LD operations to reduce code duplication and improve maintainability.
 */
internal object JsonLdUtils {
    /**
     * Convert a kotlinx.serialization.json.JsonObject to a Map for jsonld-java library.
     * 
     * Recursively converts all JSON elements to their Java equivalents.
     * 
     * @param jsonObject The JSON object to convert
     * @return Map representation suitable for jsonld-java
     */
    fun jsonObjectToMap(jsonObject: JsonObject): Map<String, Any> {
        return jsonObject.entries.associate { (key, value) ->
            key to when (value) {
                is JsonPrimitive -> {
                    when {
                        value.isString -> value.content
                        value.booleanOrNull != null -> value.boolean
                        value.longOrNull != null -> value.long
                        value.doubleOrNull != null -> value.double
                        else -> value.content
                    }
                }
                is JsonArray -> value.map { element ->
                    when (element) {
                        is JsonPrimitive -> element.content
                        is JsonObject -> jsonObjectToMap(element)
                        is JsonArray -> element.toString() // Handle nested arrays
                        is JsonNull -> element.toString()
                    }
                }
                is JsonObject -> jsonObjectToMap(value)
                is JsonNull -> value.toString()
            }
        }
    }
    
    /**
     * Canonicalize a JSON-LD document to N-Quads format.
     * 
     * Uses JSON-LD normalization for canonical form. Falls back to JSON serialization
     * if canonicalization fails.
     * 
     * Includes size validation to prevent denial-of-service attacks from extremely
     * large canonicalized documents.
     * 
     * @param document The JSON object to canonicalize
     * @param json Json instance for fallback serialization
     * @return Canonicalized document as N-Quads string
     * @throws IllegalArgumentException if canonicalized document exceeds maximum size
     */
    fun canonicalizeDocument(document: JsonObject, json: Json): String {
        return try {
            val documentMap = jsonObjectToMap(document)
            val options = JsonLdOptions()
            options.format = CredentialConstants.JsonLdFormats.N_QUADS
            val canonical = JsonLdProcessor.normalize(documentMap, options)
            // If canonicalization returns null or empty string, use JSON fallback
            val canonicalString = canonical?.toString()?.takeIf { it.isNotBlank() }
                ?: json.encodeToString(JsonObject.serializer(), document)
            
            // Validate canonicalized document size
            val canonicalBytes = canonicalString.toByteArray(Charsets.UTF_8)
            if (canonicalBytes.size > SecurityConstants.MAX_CANONICALIZED_DOCUMENT_SIZE_BYTES) {
                throw IllegalArgumentException(
                    "Canonicalized document exceeds maximum size of " +
                    "${SecurityConstants.MAX_CANONICALIZED_DOCUMENT_SIZE_BYTES} bytes: " +
                    "${canonicalBytes.size} bytes"
                )
            }
            
            canonicalString
        } catch (e: IllegalArgumentException) {
            // Re-throw validation exceptions
            throw e
        } catch (e: Exception) {
            // Fallback: use JSON serialization if canonicalization fails
            val fallback = json.encodeToString(JsonObject.serializer(), document)
            val fallbackBytes = fallback.toByteArray(Charsets.UTF_8)
            if (fallbackBytes.size > SecurityConstants.MAX_CANONICALIZED_DOCUMENT_SIZE_BYTES) {
                throw IllegalArgumentException(
                    "Document (fallback serialization) exceeds maximum size of " +
                    "${SecurityConstants.MAX_CANONICALIZED_DOCUMENT_SIZE_BYTES} bytes: " +
                    "${fallbackBytes.size} bytes"
                )
            }
            fallback
        }
    }
}

