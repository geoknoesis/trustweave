package org.trustweave.kms.util

import java.util.concurrent.TimeUnit

/**
 * Cache entry with timestamp for TTL-based expiration.
 * 
 * This utility class represents a cache entry that stores a value along with
 * metadata for cache management, including timestamp and optional time-to-live (TTL).
 * 
 * **Usage:**
 * Used by KMS plugins to cache key metadata with configurable time-to-live.
 * Also used internally by the [KeyManagementServices] factory for instance caching.
 * 
 * **Thread Safety:**
 * Cache entries are immutable data classes, making them inherently thread-safe.
 * When used in caches (e.g., ConcurrentHashMap), the cache implementation provides
 * thread-safety guarantees.
 * 
 * **Lifecycle:**
 * Cache entries can have optional TTL-based expiration. Use [isExpired] to check
 * if an entry has expired. The factory's cache entries are automatically cleared
 * when [KeyManagementServices.shutdown] is called.
 * 
 * @param value The cached value
 * @param timestampMillis Timestamp when the entry was created (in milliseconds since epoch)
 * @param ttlMillis Time-to-live in milliseconds (null means no expiration)
 */
data class CacheEntry<T>(
    val value: T,
    val timestampMillis: Long = System.currentTimeMillis(),
    val ttlMillis: Long? = null
) {
    /**
     * Checks if this cache entry has expired.
     * 
     * @return true if the entry has expired, false otherwise
     */
    fun isExpired(): Boolean {
        if (ttlMillis == null) return false
        val age = System.currentTimeMillis() - timestampMillis
        return age >= ttlMillis
    }
    
    /**
     * Gets the remaining time-to-live in milliseconds.
     * 
     * @return Remaining TTL in milliseconds, or null if no TTL is set
     */
    fun getRemainingTtlMillis(): Long? {
        if (ttlMillis == null) return null
        val age = System.currentTimeMillis() - timestampMillis
        return (ttlMillis - age).coerceAtLeast(0)
    }
    
    companion object {
        /**
         * Creates a cache entry with a TTL specified in seconds.
         */
        fun <T> withTtlSeconds(value: T, ttlSeconds: Long): CacheEntry<T> {
            return CacheEntry(value, ttlMillis = TimeUnit.SECONDS.toMillis(ttlSeconds))
        }
        
        /**
         * Creates a cache entry with a TTL specified in minutes.
         */
        fun <T> withTtlMinutes(value: T, ttlMinutes: Long): CacheEntry<T> {
            return CacheEntry(value, ttlMillis = TimeUnit.MINUTES.toMillis(ttlMinutes))
        }
        
        /**
         * Creates a cache entry with no expiration.
         */
        fun <T> permanent(value: T): CacheEntry<T> {
            return CacheEntry(value, ttlMillis = null)
        }
    }
}

