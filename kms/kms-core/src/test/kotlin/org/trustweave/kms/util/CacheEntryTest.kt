package org.trustweave.kms.util

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import java.util.concurrent.TimeUnit

class CacheEntryTest {
    @Test
    fun `test cache entry without TTL never expires`() {
        val entry = CacheEntry("test-value")
        assertFalse(entry.isExpired(), "Entry without TTL should never expire")
        assertNotNull(entry.value)
    }

    @Test
    fun `test cache entry with TTL not expired`() {
        val entry = CacheEntry("test-value", ttlMillis = 1000)
        assertFalse(entry.isExpired(), "Entry should not be expired immediately")
    }

    @Test
    fun `test cache entry with TTL expired`() {
        val oldTimestamp = System.currentTimeMillis() - 2000
        val entry = CacheEntry("test-value", timestampMillis = oldTimestamp, ttlMillis = 1000)
        assertTrue(entry.isExpired(), "Entry should be expired after TTL")
    }

    @Test
    fun `test getRemainingTtlMillis returns null when no TTL`() {
        val entry = CacheEntry("test-value")
        assertTrue(entry.getRemainingTtlMillis() == null, "Entry without TTL should return null")
    }

    @Test
    fun `test getRemainingTtlMillis returns positive value`() {
        val entry = CacheEntry("test-value", ttlMillis = 1000)
        val remaining = entry.getRemainingTtlMillis()
        assertNotNull(remaining, "Remaining TTL should not be null")
        assertTrue(remaining!! > 0, "Remaining TTL should be positive")
        assertTrue(remaining <= 1000, "Remaining TTL should be less than or equal to total TTL")
    }

    @Test
    fun `test getRemainingTtlMillis returns zero for expired entry`() {
        val oldTimestamp = System.currentTimeMillis() - 2000
        val entry = CacheEntry("test-value", timestampMillis = oldTimestamp, ttlMillis = 1000)
        val remaining = entry.getRemainingTtlMillis()
        assertNotNull(remaining, "Remaining TTL should not be null")
        assertTrue(remaining!! == 0L, "Remaining TTL should be zero for expired entry")
    }

    @Test
    fun `test withTtlSeconds creates entry with correct TTL`() {
        val entry = CacheEntry.withTtlSeconds("test-value", 5)
        assertFalse(entry.isExpired(), "Entry should not be expired immediately")
        val remaining = entry.getRemainingTtlMillis()
        assertNotNull(remaining)
        assertTrue(remaining!! > 0)
        assertTrue(remaining <= TimeUnit.SECONDS.toMillis(5))
    }

    @Test
    fun `test withTtlMinutes creates entry with correct TTL`() {
        val entry = CacheEntry.withTtlMinutes("test-value", 1)
        assertFalse(entry.isExpired(), "Entry should not be expired immediately")
        val remaining = entry.getRemainingTtlMillis()
        assertNotNull(remaining)
        assertTrue(remaining!! > 0)
        assertTrue(remaining <= TimeUnit.MINUTES.toMillis(1))
    }

    @Test
    fun `test permanent creates entry without TTL`() {
        val entry = CacheEntry.permanent("test-value")
        assertFalse(entry.isExpired(), "Permanent entry should never expire")
        assertTrue(entry.getRemainingTtlMillis() == null, "Permanent entry should have no TTL")
    }

    @Test
    fun `test cache entry preserves value`() {
        val value = "test-value"
        val entry = CacheEntry(value)
        assertTrue(entry.value == value, "Entry should preserve value")
    }

    @Test
    fun `test cache entry with generic type`() {
        val mapValue = mapOf("key" to "value")
        val entry = CacheEntry(mapValue)
        assertTrue(entry.value == mapValue, "Entry should preserve generic value")
    }
}

