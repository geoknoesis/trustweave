package org.trustweave.kms.internal

/**
 * Internal utility for creating cache keys from provider name and configuration.
 *
 * This class is used internally by [KeyManagementServices] to create unique
 * cache keys for KMS instances. The cache key is based on the provider name
 * and the configuration options, ensuring that instances with the same
 * configuration are reused from the cache.
 *
 * **Cache Key Strategy:**
 * - Provider name and configuration map are combined to create a unique key
 * - Same provider + same configuration = same cache key = same instance
 * - Different configurations for the same provider create different cache entries
 * - The configuration part of the key is a SHA-256 hash of the normalized
 *   options, so secrets passed as option values (access keys, passwords,
 *   tokens) never appear in the key string retained by the instance cache
 *
 * **Thread Safety:**
 * Cache key creation is thread-safe and immutable once created.
 *
 * **Internal Use Only:**
 * This is an internal class and should not be used directly by KMS plugins
 * or consumers of the KMS API.
 */

import org.trustweave.kms.KmsCreationOptions
import java.security.MessageDigest

/**
 * Internal utility for creating cache keys from configurations.
 *
 * Generates stable, comparable keys from both Map and typed configurations.
 *
 * Note: Made public for testing purposes.
 */
object ConfigCacheKey {
    /**
     * Creates a cache key from provider name and configuration options.
     *
     * @param providerName The provider name
     * @param options Typed configuration options
     * @return Cache key string
     */
    fun create(
        providerName: String,
        options: KmsCreationOptions,
    ): String {
        // Convert to map and use map-based key generation
        return create(providerName, options.toMap())
    }

    /**
     * Creates a cache key from provider name and map configuration.
     *
     * The key is based on a sorted, normalized representation of the configuration
     * to ensure that equivalent configurations produce the same key.
     *
     * The normalized configuration is hashed (SHA-256, hex) before being embedded
     * in the key so that secret option values (credentials, tokens, passwords)
     * are never retained in plain text by the instance cache. The provider name
     * is kept as a readable prefix for debugging.
     *
     * @param providerName The provider name
     * @param options Map configuration options
     * @return Cache key string of the form `provider:<sha256-hex>`
     */
    fun create(
        providerName: String,
        options: Map<String, Any?>,
    ): String {
        // Hash the canonical form so the key never contains raw secret values.
        return "$providerName:${sha256Hex(canonical(options))}"
    }

    /**
     * Computes the lowercase hex SHA-256 digest of the given string.
     */
    private fun sha256Hex(input: String): String =
        MessageDigest
            .getInstance("SHA-256")
            .digest(input.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }

    /** Appends a type tag and a length-prefixed text, so no value can run into its neighbour. */
    private fun StringBuilder.text(
        tag: Char,
        text: String,
    ) {
        append(tag).append(text.length).append(':').append(text)
    }

    /**
     * Unambiguous, deterministic encoding of a configuration value.
     *
     * Every scalar carries a type tag and a length prefix, so a delimiter inside a value
     * cannot shift the boundary of the next one, `null` cannot collide with the string
     * `"null"`, and a number cannot collide with its string form. Maps are ordered by key,
     * sets by their encoded elements (they have no order), and lists, arrays and other ordered
     * collections keep their order.
     */
    private fun canonical(value: Any?): String {
        val out = StringBuilder()
        encode(value, out)
        return out.toString()
    }

    private fun encode(
        value: Any?,
        out: StringBuilder,
    ) {
        when (value) {
            null -> out.append('n')
            is Boolean -> out.append(if (value) 'T' else 'F')
            is Number -> out.text('d', value.toString())
            is String -> out.text('s', value)
            is CharSequence -> out.text('s', value.toString())
            is Enum<*> -> out.text('e', value.javaClass.name + "." + value.name)
            is Map<*, *> -> {
                val entries =
                    value.entries
                        .map { (k, v) -> canonical(k) to v }
                        .sortedBy { it.first }
                out.append('m').append(entries.size).append('{')
                for ((k, v) in entries) {
                    out.append(k)
                    encode(v, out)
                }
                out.append('}')
            }
            is Set<*> -> {
                val elements = value.map { canonical(it) }.sorted()
                out.append('S').append(elements.size).append('[')
                elements.forEach { out.append(it) }
                out.append(']')
            }
            is Iterable<*> -> {
                val elements = value.toList()
                out.append('l').append(elements.size).append('[')
                elements.forEach { encode(it, out) }
                out.append(']')
            }
            is Array<*> -> encode(value.toList(), out)
            else -> out.text('o', value.javaClass.name + "|" + value.toString())
        }
    }
}
