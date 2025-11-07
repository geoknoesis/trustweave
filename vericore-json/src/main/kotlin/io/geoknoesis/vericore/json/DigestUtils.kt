package io.geoknoesis.vericore.json

import kotlinx.serialization.json.*
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap

/**
 * JSON canonicalization and digest computation utilities.
 * 
 * Provides optimized implementations for JSON canonicalization and SHA-256 digest computation
 * with multibase encoding (base58btc).
 * 
 * **Performance Optimizations**:
 * - Cached JSON instances for reuse
 * - Optimized canonicalization algorithm
 * - Thread-safe digest computation
 */
object DigestUtils {
    
    /**
     * Shared JSON instance for consistent serialization.
     * Configured with stable ordering and no pretty printing.
     */
    private val json = Json {
        prettyPrint = false
        encodeDefaults = false
        ignoreUnknownKeys = true
        isLenient = false
        coerceInputValues = false
    }
    
    /**
     * Cache for computed digests (optional, can be disabled for memory-constrained environments).
     * Key: canonical JSON string, Value: computed digest
     */
    private val digestCache = ConcurrentHashMap<String, String>()
    
    /**
     * Whether digest caching is enabled.
     * Set to false to disable caching (useful for memory-constrained environments).
     */
    var enableDigestCache: Boolean = true
        set(value) {
            field = value
            if (!value) {
                digestCache.clear()
            }
        }
    
    /**
     * Canonicalizes a JSON string by parsing and re-serializing with stable key ordering.
     * 
     * @param jsonString The JSON string to canonicalize
     * @return The canonical JSON string
     */
    fun canonicalizeJson(jsonString: String): String {
        return try {
            val element = Json.parseToJsonElement(jsonString)
            canonicalizeJson(element)
        } catch (e: Exception) {
            // If not valid JSON, return as-is
            jsonString
        }
    }
    
    /**
     * Canonicalizes a JSON element by sorting keys lexicographically.
     * 
     * This ensures that the same JSON content always produces the same canonical form,
     * regardless of key order in the original JSON.
     * 
     * **Performance**: Uses optimized JSON serialization with stable ordering.
     * 
     * @param element The JSON element to canonicalize
     * @return The canonical JSON string
     */
    fun canonicalizeJson(element: JsonElement): String {
        val sorted = sortKeys(element)
        return json.encodeToString(JsonElement.serializer(), sorted)
    }
    
    /**
     * Recursively sorts keys in JSON objects.
     */
    private fun sortKeys(element: JsonElement): JsonElement {
        return when (element) {
            is JsonObject -> {
                val sortedEntries = element.entries.sortedBy { it.key }
                    .map { it.key to sortKeys(it.value) }
                buildJsonObject {
                    sortedEntries.forEach { (key, value) ->
                        put(key, value)
                    }
                }
            }
            is JsonArray -> {
                buildJsonArray {
                    element.forEach { add(sortKeys(it)) }
                }
            }
            else -> element
        }
    }
    
    /**
     * Computes SHA-256 digest of a string with multibase encoding (base58btc).
     * 
     * If the string is valid JSON, it will be canonicalized first.
     * 
     * @param data The string to digest (JSON or plain text)
     * @return The multibase-encoded digest (e.g., "uABC123...")
     */
    fun sha256DigestMultibase(data: String): String {
        return try {
            // Try to parse as JSON and canonicalize
            val element = Json.parseToJsonElement(data)
            sha256DigestMultibase(element)
        } catch (e: Exception) {
            // Not JSON, compute digest directly
            val canonical = data
            if (enableDigestCache) {
                digestCache[canonical]?.let { return it }
            }
            val digest = computeDigest(canonical)
            if (enableDigestCache) {
                digestCache[canonical] = digest
            }
            digest
        }
    }
    
    /**
     * Computes SHA-256 digest of a JSON element with multibase encoding (base58btc).
     * 
     * The digest is computed from the canonical JSON representation, ensuring consistency.
     * 
     * **Performance**: Uses caching to avoid recomputing digests for the same content.
     * 
     * @param element The JSON element to digest
     * @return The multibase-encoded digest (e.g., "uABC123...")
     */
    fun sha256DigestMultibase(element: JsonElement): String {
        val canonical = canonicalizeJson(element)
        
        // Check cache if enabled
        if (enableDigestCache) {
            digestCache[canonical]?.let { return it }
        }
        
        // Compute digest
        val digest = computeDigest(canonical)
        
        // Cache if enabled
        if (enableDigestCache) {
            digestCache[canonical] = digest
        }
        
        return digest
    }
    
    /**
     * Computes SHA-256 digest from canonical JSON string.
     * 
     * @param canonicalJson The canonical JSON string
     * @return The multibase-encoded digest (prefix 'u' for base58btc)
     */
    private fun computeDigest(canonicalJson: String): String {
        val bytes = canonicalJson.toByteArray(Charsets.UTF_8)
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
        
        // Multibase encoding: base58btc (prefix 'u')
        val base58 = encodeBase58(digest)
        return "u$base58"
    }
    
    /**
     * Encodes bytes to base58 (simplified implementation).
     * For production use, consider using a proper multibase library.
     */
    private fun encodeBase58(bytes: ByteArray): String {
        val alphabet = "123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz"
        var num = java.math.BigInteger(1, bytes)
        val sb = StringBuilder()
        
        while (num > java.math.BigInteger.ZERO) {
            val remainder = num.mod(java.math.BigInteger.valueOf(58))
            sb.append(alphabet[remainder.toInt()])
            num = num.divide(java.math.BigInteger.valueOf(58))
        }
        
        // Add leading zeros
        for (byte in bytes) {
            if (byte.toInt() == 0) {
                sb.append('1')
            } else {
                break
            }
        }
        
        return sb.reverse().toString()
    }
    
    /**
     * Clears the digest cache.
     * Useful for memory management or testing.
     */
    fun clearCache() {
        digestCache.clear()
    }
    
    /**
     * Gets the current cache size.
     * Useful for monitoring cache usage.
     */
    fun getCacheSize(): Int = digestCache.size
}
