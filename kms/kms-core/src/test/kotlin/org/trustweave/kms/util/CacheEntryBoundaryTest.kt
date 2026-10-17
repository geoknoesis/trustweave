package org.trustweave.kms.util

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CacheEntryBoundaryTest {
    @Test
    fun `ancient timestamp expires even when age exceeds Long range`() {
        val entry = CacheEntry("value", timestampMillis = Long.MIN_VALUE, ttlMillis = 1)
        assertTrue(entry.isExpired())
        assertEquals(0L, entry.getRemainingTtlMillis())
    }

    @Test
    fun `future timestamp with maximum TTL remains valid with saturated remaining time`() {
        val entry = CacheEntry("value", timestampMillis = Long.MAX_VALUE, ttlMillis = Long.MAX_VALUE)
        assertFalse(entry.isExpired())
        assertEquals(Long.MAX_VALUE, entry.getRemainingTtlMillis())
    }

    @Test
    fun `expired negative TTL cannot overflow into positive remaining time`() {
        val entry = CacheEntry("value", timestampMillis = 0, ttlMillis = Long.MIN_VALUE)
        assertTrue(entry.isExpired())
        assertEquals(0L, entry.getRemainingTtlMillis())
    }

    @Test
    fun `future timestamp preserves its additional lifetime`() {
        val future = System.currentTimeMillis() + 60_000
        val entry = CacheEntry("value", timestampMillis = future, ttlMillis = 60_000)
        assertFalse(entry.isExpired())
        val remaining = requireNotNull(entry.getRemainingTtlMillis())
        assertTrue(remaining in 60_001L..120_000L)
    }

    @Test
    fun `TTL factories saturate large units without changing expiration semantics`() {
        assertEquals(Long.MAX_VALUE, CacheEntry.withTtlSeconds("value", Long.MAX_VALUE).ttlMillis)
        assertEquals(Long.MAX_VALUE, CacheEntry.withTtlMinutes("value", Long.MAX_VALUE).ttlMillis)
        assertEquals(Long.MIN_VALUE, CacheEntry.withTtlSeconds("value", Long.MIN_VALUE).ttlMillis)
        assertEquals(Long.MIN_VALUE, CacheEntry.withTtlMinutes("value", Long.MIN_VALUE).ttlMillis)
    }
}
