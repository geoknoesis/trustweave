package org.trustweave.kms.internal

import org.trustweave.kms.KmsCreationOptions
import java.security.MessageDigest
import java.util.HexFormat

/**
 * Creates stable cache keys from provider names and canonical configurations.
 *
 * Configuration values are hashed so secrets are not retained as plain text in cache keys.
 *
 * Stateless and safe for concurrent callers. Intended for internal use.
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
    ): String = create(providerName, options.toMap())

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
    private fun sha256Hex(input: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(input.toByteArray(Charsets.UTF_8))
        return HexFormat.of().formatHex(digest)
    }

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
    private fun canonical(value: Any?): String = buildString { encode(value, this) }

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
