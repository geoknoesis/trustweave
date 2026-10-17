package org.trustweave.kms.util

import java.math.BigInteger
import java.util.concurrent.TimeUnit

/**
 * Cached value with an optional TTL measured from [timestampMillis].
 *
 * Expiration follows the system wall clock. A null TTL never expires; future timestamps
 * extend the remaining lifetime. The containing cache must synchronize its own access.
 *
 * @param value The cached value
 * @param timestampMillis Creation time in milliseconds since the Unix epoch
 * @param ttlMillis Time-to-live in milliseconds, or null for no expiration
 */
data class CacheEntry<T>(
    val value: T,
    val timestampMillis: Long = System.currentTimeMillis(),
    val ttlMillis: Long? = null,
) {
    /** Whether the TTL has elapsed. Entries without a TTL never expire. */
    fun isExpired(): Boolean = getRemainingTtlMillis() == 0L

    /** Remaining lifetime, capped at [Long.MAX_VALUE], or null for no expiration. */
    fun getRemainingTtlMillis(): Long? {
        val ttl = ttlMillis ?: return null
        val now = System.currentTimeMillis()
        return try {
            Math.subtractExact(ttl, Math.subtractExact(now, timestampMillis)).coerceAtLeast(0)
        } catch (_: ArithmeticException) {
            // Extreme timestamps can overflow either subtraction. Compute the exact result
            // before clamping so intermediate overflow cannot reverse the expiry decision.
            BigInteger
                .valueOf(timestampMillis)
                .add(BigInteger.valueOf(ttl))
                .subtract(BigInteger.valueOf(now))
                .coerceIn(BigInteger.ZERO, BigInteger.valueOf(Long.MAX_VALUE))
                .toLong()
        }
    }

    companion object {
        /** Creates an entry with a TTL specified in seconds. */
        fun <T> withTtlSeconds(
            value: T,
            ttlSeconds: Long,
        ): CacheEntry<T> = CacheEntry(value, ttlMillis = TimeUnit.SECONDS.toMillis(ttlSeconds))

        /** Creates an entry with a TTL specified in minutes. */
        fun <T> withTtlMinutes(
            value: T,
            ttlMinutes: Long,
        ): CacheEntry<T> = CacheEntry(value, ttlMillis = TimeUnit.MINUTES.toMillis(ttlMinutes))

        /** Creates an entry that never expires. */
        fun <T> permanent(value: T): CacheEntry<T> = CacheEntry(value, ttlMillis = null)
    }
}
