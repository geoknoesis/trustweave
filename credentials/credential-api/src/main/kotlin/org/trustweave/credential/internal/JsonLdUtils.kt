package org.trustweave.credential.internal

import com.github.jsonldjava.core.JsonLdOptions
import com.github.jsonldjava.core.JsonLdProcessor
import kotlinx.serialization.json.*

/**
 * JSON-LD utility functions for canonicalization and document conversion.
 * 
 * This utility object centralizes JSON-LD operations used throughout the credential API,
 * particularly for VC-LD (Verifiable Credentials Linked Data) proof generation and verification.
 * 
 * **Key Operations:**
 * - JSON object to Map conversion for jsonld-java library compatibility
 * - JSON-LD canonicalization to N-Quads format
 * - Document normalization for signature verification
 * 
 * **Usage:**
 * ```kotlin
 * // Convert JsonObject to Map for jsonld-java
 * val map = JsonLdUtils.jsonObjectToMap(jsonObject)
 * 
 * // Canonicalize document for signing
 * val canonical = JsonLdUtils.canonicalizeDocument(map)
 * ```
 * 
 * **Note:** This is an internal utility and should not be used directly by API consumers.
 * It is used by proof engines for VC-LD operations.
 */
internal object JsonLdUtils {
    /**
     * Convert a kotlinx.serialization.json.JsonObject to a Map for jsonld-java library.
     * 
     * Recursively converts all JSON elements to their Java equivalents.
     * This conversion is necessary because the jsonld-java library expects Java Map structures
     * rather than Kotlin JsonObject/JsonElement types.
     * 
     * **Conversion Algorithm:**
     * 1. JsonPrimitive values are converted to their Java equivalents:
     *    - Strings: remain as String
     *    - Booleans: converted to Boolean
     *    - Numbers: converted to Long (integers) or Double (floating-point)
     *    - Other primitives: converted to String via content
     * 2. JsonArray values are converted to List<Any> by recursively processing each element
     * 3. JsonObject values are recursively converted using this same function
     * 4. JsonNull values are converted to String representation
     * 
     * **Performance Considerations:**
     * - This is a recursive operation that processes the entire JSON tree
     * - For large documents, this conversion may be memory-intensive
     * - The recursive nature means deeply nested structures may impact performance
     * 
     * **Example:**
     * ```kotlin
     * val jsonObject = buildJsonObject {
     *     put("name", "John")
     *     put("age", 30)
     *     put("active", true)
     * }
     * val map = JsonLdUtils.jsonObjectToMap(jsonObject)
     * // Result: mapOf("name" to "John", "age" to 30L, "active" to true)
     * ```
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
     * Uses JSON-LD normalization (RFC 8785) to create a canonical form of the document.
     * This canonicalization is essential for signature generation and verification, as it
     * ensures that semantically equivalent JSON-LD documents produce identical byte sequences.
     * 
     * **Canonicalization Algorithm:**
     * 1. Convert JsonObject to Map format (required by jsonld-java library)
     * 2. Configure JsonLdOptions with N-Quads format output
     * 3. Perform normalization using JsonLdProcessor.normalize()
     * 4. If normalization fails or returns empty, fall back to JSON serialization
     * 5. Validate that the canonicalized document does not exceed size limits
     * 
     * **Security Considerations:**
     * - Size validation prevents DoS attacks from extremely large canonicalized documents
     * - The canonicalization process may expand the document size (N-Quads format)
     * - Maximum size limit is enforced to prevent memory exhaustion
     * 
     * **Error Handling:**
     * - Validation exceptions (size limit exceeded) are re-thrown immediately
     * - Other exceptions trigger fallback to JSON serialization
     * - Fallback output is also validated for size limits
     * 
     * **Performance Notes:**
     * - JSON-LD canonicalization can be CPU-intensive for complex documents
     * - The recursive Map conversion adds overhead
     * - Size validation occurs after canonicalization to catch all cases
     * 
     * @param document The JSON object to canonicalize
     * @param json Json instance for fallback serialization
     * @return Canonicalized document as N-Quads string
     * @throws IllegalArgumentException if canonicalized document exceeds maximum size
     */
    fun canonicalizeDocument(document: JsonObject, json: Json): String {
        return try {
            // Step 1: Convert JsonObject to Map (required by jsonld-java)
            val documentMap = jsonObjectToMap(document)
            
            // Step 2: Configure JSON-LD normalization options
            val options = JsonLdOptions()
            options.format = CredentialConstants.JsonLdFormats.N_QUADS
            
            // Step 3: Perform JSON-LD canonicalization to N-Quads format
            val canonical = JsonLdProcessor.normalize(documentMap, options)
            
            // Step 4: Use canonicalized form if valid, otherwise fall back to JSON serialization
            val canonicalString = canonical?.toString()?.takeIf { it.isNotBlank() }
                ?: json.encodeToString(JsonObject.serializer(), document)
            
            // Step 5: Validate canonicalized document size to prevent DoS attacks
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
            // Re-throw validation exceptions (size limit exceeded)
            throw e
        } catch (e: Exception) {
            // Fallback: use JSON serialization if canonicalization fails
            val fallback = json.encodeToString(JsonObject.serializer(), document)
            val fallbackBytes = fallback.toByteArray(Charsets.UTF_8)
            
            // Validate fallback size as well
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

