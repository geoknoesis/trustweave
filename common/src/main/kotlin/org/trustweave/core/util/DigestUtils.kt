package org.trustweave.core.util

import org.trustweave.core.exception.TrustWeaveException
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.*
import java.security.MessageDigest

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
     * Thread-local MessageDigest instance for SHA-256 computation.
     *
     * MessageDigest is not thread-safe - concurrent calls to digest() on the same
     * instance can corrupt the internal state. ThreadLocal ensures each thread
     * has its own MessageDigest instance, eliminating contention and race conditions.
     *
     * Performance note: ThreadLocal has minimal overhead and is more efficient than
     * creating new MessageDigest instances for each operation, as it reuses instances
     * per thread while maintaining thread safety.
     */
    private val sha256Digest: ThreadLocal<MessageDigest> = ThreadLocal.withInitial {
        MessageDigest.getInstance("SHA-256")
    }

    /**
     * Maximum number of entries in the digest cache.
     * When this limit is reached, least recently used entries are evicted.
     *
     * Default: 1000 entries. Set to 0 to disable caching entirely.
     */
    @Volatile
    var maxCacheSize: Int = 1000
        set(value) {
            require(value >= 0) { "Cache size must be non-negative" }
            synchronized(digestCache) {
                field = value
                // Trim cache if new size is smaller
                if (digestCache.size > value) {
                    val entriesToRemove = digestCache.size - value
                    val iterator = digestCache.entries.iterator()
                    repeat(entriesToRemove) {
                        if (iterator.hasNext()) {
                            iterator.next()
                            iterator.remove()
                        }
                    }
                }
            }
        }

    /**
     * Cache for computed digests (optional, can be disabled for memory-constrained environments).
     * Key: canonical JSON string, Value: computed digest
     *
     * **Implementation:** Uses a size-limited cache with LRU eviction to prevent unbounded growth.
     * When the cache reaches maxCacheSize, the least recently used entries are evicted.
     * Access is synchronized for thread safety.
     */
    private val digestCache = object : LinkedHashMap<String, String>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, String>?): Boolean {
            // Access maxCacheSize via field to avoid synchronization overhead in hot path
            // This is safe because maxCacheSize is @Volatile
            return size > this@DigestUtils.maxCacheSize
        }
    }
    
    /**
     * Synchronized wrapper for cache access to ensure thread safety.
     */
    @Synchronized
    private fun getCachedDigest(canonical: String): String? {
        return digestCache[canonical]
    }
    
    /**
     * Synchronized wrapper for cache storage to ensure thread safety.
     */
    @Synchronized
    private fun putCachedDigest(canonical: String, digest: String) {
        digestCache[canonical] = digest
    }

    /**
     * Whether digest caching is enabled.
     * Set to false to disable caching (useful for memory-constrained environments).
     *
     * **Memory Consideration:** When enabled, the cache is limited to [maxCacheSize] entries
     * with LRU eviction, preventing unbounded growth in long-running applications.
     */
    @Volatile
    var isDigestCacheEnabled: Boolean = true
        set(value) {
            field = value
            if (!value) {
                synchronized(digestCache) {
                    digestCache.clear()
                }
            }
        }

    /**
     * Canonicalizes a JSON string by parsing and re-serializing with stable key ordering.
     *
     * @param jsonString The JSON string to canonicalize
     * @return The canonical JSON string
     * @throws org.trustweave.core.exception.SerializationException.InvalidJson if the input is not valid JSON
     */
    fun canonicalizeJson(jsonString: String): String {
        require(jsonString.isNotBlank()) { "JSON string cannot be blank" }
        return try {
            val element = Json.parseToJsonElement(jsonString)
            canonicalizeJson(element)
        } catch (e: SerializationException) {
            throw org.trustweave.core.exception.SerializationException.InvalidJson(
                jsonString = jsonString,
                parseError = e.message ?: "Parse error",
                position = null
            )
        } catch (e: Exception) {
            // Catch any other unexpected exceptions (e.g., OutOfMemoryError, etc.)
            throw org.trustweave.core.exception.SerializationException.InvalidJson(
                jsonString = jsonString,
                parseError = e.message ?: "Unknown error: ${e::class.simpleName}",
                position = null
            )
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
        return try {
            json.encodeToString(JsonElement.serializer(), sorted)
        } catch (e: Exception) {
            throw org.trustweave.core.exception.SerializationException.EncodeFailed(
                element = element.toString().take(200),
                reason = e.message ?: "Unknown encoding error"
            )
        }
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
            // Not valid JSON or parsing failed, compute digest directly on original string
            val canonical = data
            // Check cache
            if (isDigestCacheEnabled && maxCacheSize > 0) {
                getCachedDigest(canonical)?.let { return it }
            }
            val digest = computeDigest(canonical)
            if (isDigestCacheEnabled && maxCacheSize > 0) {
                putCachedDigest(canonical, digest)
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

        // Check cache
        if (isDigestCacheEnabled && maxCacheSize > 0) {
            getCachedDigest(canonical)?.let { return it }
        }

        // Compute digest
        val digest = computeDigest(canonical)

        // Cache if enabled
        if (isDigestCacheEnabled && maxCacheSize > 0) {
            putCachedDigest(canonical, digest)
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
        return try {
            // Get thread-local MessageDigest instance (thread-safe).
            val messageDigest = sha256Digest.get()

            // Compute SHA-256 hash. Note: digest() automatically resets the
            // MessageDigest instance after computation, so it's ready for reuse
            // in the same thread without manual reset() call.
            val digestBytes = messageDigest.digest(bytes)

            // Multibase encoding: base58btc uses prefix 'z' per multibase specification
            // (https://github.com/multiformats/multibase). The prefix 'u' is base64url —
            // using 'z' here is correct because encodeBase58() produces base58btc output.
            val base58 = digestBytes.encodeBase58()
            "z$base58"
        } catch (e: java.security.NoSuchAlgorithmException) {
            // SHA-256 should always be available in standard JVMs, but handle gracefully
            // for environments with restricted security providers.
            throw TrustWeaveException.DigestFailed(
                algorithm = "SHA-256",
                reason = "Algorithm not available: ${e.message}"
            )
        } catch (e: Exception) {
            // Catch any other encoding/digest errors and wrap in TrustWeaveException
            throw TrustWeaveException.EncodeFailed(
                operation = "base58-encoding",
                reason = e.message ?: "Unknown encoding error"
            )
        }
    }

    /**
     * Clears the digest cache.
     * Useful for memory management or testing.
     */
    @Synchronized
    fun clearCache() {
        digestCache.clear()
    }

    /**
     * The current cache size.
     * Useful for monitoring cache usage.
     */
    val cacheSize: Int
        @Synchronized get() = digestCache.size
}

