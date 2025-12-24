package org.trustweave.core.util

import org.trustweave.core.exception.TrustWeaveException
import org.trustweave.core.util.DigestUtils.maxCacheSize
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
            synchronized(cacheLock) {
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
     * Lock for synchronizing cache access.
     * LinkedHashMap is not thread-safe, so all access must be synchronized.
     */
    private val cacheLock = Any()

    /**
     * Cache for computed digests (optional, can be disabled for memory-constrained environments).
     * Key: canonical JSON string, Value: computed digest
     *
     * **Implementation:** Uses a size-limited cache with LRU eviction to prevent unbounded growth.
     * When the cache reaches maxCacheSize, the least recently used entries are evicted.
     *
     * **Thread Safety:** All access to this cache is synchronized via cacheLock.
     */
    private val digestCache = object : LinkedHashMap<String, String>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, String>?): Boolean {
            // Access maxCacheSize via field to avoid synchronization overhead in hot path
            // This is safe because maxCacheSize is @Volatile
            return size > this@DigestUtils.maxCacheSize
        }
    }

    /**
     * Whether digest caching is enabled.
     * Set to false to disable caching (useful for memory-constrained environments).
     *
     * **Memory Consideration:** When enabled, the cache is limited to [maxCacheSize] entries
     * with LRU eviction, preventing unbounded growth in long-running applications.
     */
    var enableDigestCache: Boolean = true
        set(value) {
            field = value
            if (!value) {
                synchronized(cacheLock) {
                    digestCache.clear()
                }
            }
        }

    /**
     * Canonicalizes a JSON string by parsing and re-serializing with stable key ordering.
     *
     * @param jsonString The JSON string to canonicalize
     * @return The canonical JSON string
     * @throws TrustWeaveException.InvalidJson if the input is not valid JSON
     */
    fun canonicalizeJson(jsonString: String): String {
        require(jsonString.isNotBlank()) { "JSON string cannot be blank" }
        return try {
            val element = Json.parseToJsonElement(jsonString)
            canonicalizeJson(element)
        } catch (e: SerializationException) {
            throw TrustWeaveException.InvalidJson(
                jsonString = jsonString,
                parseError = e.message ?: "Parse error",
                position = null
            )
        } catch (e: Exception) {
            throw TrustWeaveException.InvalidJson(
                jsonString = jsonString,
                parseError = e.message ?: "Unknown error",
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
            throw TrustWeaveException.JsonEncodeFailed(
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
            if (enableDigestCache && maxCacheSize > 0) {
                synchronized(cacheLock) {
                    digestCache[canonical]?.let { return it }
                }
            }
            val digest = computeDigest(canonical)
            if (enableDigestCache && maxCacheSize > 0) {
                synchronized(cacheLock) {
                    digestCache[canonical] = digest
                }
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
        if (enableDigestCache && maxCacheSize > 0) {
            synchronized(cacheLock) {
                digestCache[canonical]?.let { return it }
            }
        }

        // Compute digest
        val digest = computeDigest(canonical)

        // Cache if enabled
        if (enableDigestCache && maxCacheSize > 0) {
            synchronized(cacheLock) {
                digestCache[canonical] = digest
            }
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

            // Multibase encoding: base58btc uses prefix 'u' per multibase specification.
            // The prefix indicates the encoding format, allowing decoders to automatically
            // detect and use the correct decoding algorithm.
            val base58 = encodeBase58(digestBytes)
            "u$base58"
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
     * Encodes bytes to base58 (simplified implementation).
     * For production use, consider using a proper multibase library.
     *
     * Base58 encoding algorithm:
     * 1. Convert byte array to BigInteger (treating as unsigned big-endian)
     * 2. Repeatedly divide by 58, using remainder as index into alphabet
     * 3. Handle leading zeros specially (they encode as '1' characters)
     * 4. Reverse the result since we built it from least significant digit
     */
    private fun encodeBase58(bytes: ByteArray): String {
        val alphabet = "123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz"
        // BigInteger(1, bytes) treats bytes as unsigned big-endian integer.
        // The '1' parameter ensures positive sign (unsigned interpretation).
        var num = java.math.BigInteger(1, bytes)
        val sb = StringBuilder()

        // Base conversion: repeatedly divide by 58, using remainder as digit.
        // This builds the base58 representation from least significant to most significant.
        while (num > java.math.BigInteger.ZERO) {
            val remainder = num.mod(java.math.BigInteger.valueOf(58))
            sb.append(alphabet[remainder.toInt()])
            num = num.divide(java.math.BigInteger.valueOf(58))
        }

        // Base58 special case: Leading zero bytes are encoded as '1' characters.
        // This is because '0' is not in the base58 alphabet, so we use '1' (the first
        // character) as a placeholder. We must add these AFTER the division loop
        // because they represent the most significant digits.
        for (byte in bytes) {
            if (byte.toInt() == 0) {
                sb.append('1')
            } else {
                // Stop at first non-zero byte - only leading zeros need special handling
                break
            }
        }

        // Reverse because we built the string from least significant to most significant.
        return sb.reverse().toString()
    }

    /**
     * Clears the digest cache.
     * Useful for memory management or testing.
     */
    fun clearCache() {
        synchronized(cacheLock) {
            digestCache.clear()
        }
    }

    /**
     * Gets the current cache size.
     * Useful for monitoring cache usage.
     */
    fun getCacheSize(): Int = synchronized(cacheLock) { digestCache.size }
}

