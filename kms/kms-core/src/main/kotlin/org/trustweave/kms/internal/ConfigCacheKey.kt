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
 * 
 * **Thread Safety:**
 * Cache key creation is thread-safe and immutable once created.
 * 
 * **Internal Use Only:**
 * This is an internal class and should not be used directly by KMS plugins
 * or consumers of the KMS API.
 */

import org.trustweave.kms.KmsCreationOptions

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
    fun create(providerName: String, options: KmsCreationOptions): String {
        // Convert to map and use map-based key generation
        return create(providerName, options.toMap())
    }

    /**
     * Creates a cache key from provider name and map configuration.
     *
     * The key is based on a sorted, normalized representation of the configuration
     * to ensure that equivalent configurations produce the same key.
     *
     * @param providerName The provider name
     * @param options Map configuration options
     * @return Cache key string
     */
    fun create(providerName: String, options: Map<String, Any?>): String {
        // Normalize map: sort keys, handle nulls, normalize values
        val normalizedOptions = normalizeMap(options)
        
        // Create key string: provider:key1=value1:key2=value2:...
        val configPart = normalizedOptions.entries
            .sortedBy { it.key }
            .joinToString(":") { (key, value) ->
                "$key=${normalizeValue(value)}"
            }
        
        return "$providerName:$configPart"
    }

    /**
     * Normalizes a map by sorting keys and handling nested structures.
     */
    private fun normalizeMap(map: Map<String, Any?>): Map<String, Any?> {
        return map.entries
            .sortedBy { it.key }
            .associate { (key, value) ->
                key to normalizeValue(value)
            }
    }

    /**
     * Normalizes a value for consistent comparison.
     * Handles nulls, collections, and nested maps.
     */
    private fun normalizeValue(value: Any?): String {
        return when (value) {
            null -> "null"
            is Boolean -> value.toString()
            is Number -> value.toString()
            is String -> value
            is Map<*, *> -> {
                val normalized = normalizeMap(value.entries.associate { 
                    (k, v) -> (k?.toString() ?: "null") to v 
                })
                normalized.entries
                    .sortedBy { it.key }
                    .joinToString(",") { "${it.key}=${normalizeValue(it.value)}" }
            }
            is Collection<*> -> {
                value.map { normalizeValue(it) }
                    .sorted()
                    .joinToString(",")
            }
            else -> value.toString()
        }
    }
}

