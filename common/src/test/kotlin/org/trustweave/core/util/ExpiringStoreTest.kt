package org.trustweave.core.util

import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class ExpiringStoreTest {
    private class TestClock(
        var now: Instant = Instant.EPOCH,
    ) : Clock() {
        var stepMillis: Long = 0

        override fun instant(): Instant {
            val current = now
            now = now.plusMillis(stepMillis)
            return current
        }

        override fun getZone(): ZoneId = ZoneOffset.UTC

        override fun withZone(zone: ZoneId): Clock = this
    }

    @Test
    fun `capacity expiry and close bound retained secrets`() {
        val clock = TestClock()
        val store = ExpiringStore<String>(clock, 1000, 1)
        store["one"] = "secret"
        assertFailsWith<SessionCapacityExceededException> { store["two"] = "another" }
        clock.now = Instant.EPOCH.plusMillis(999)
        assertEquals("secret", store["one"])
        store["one"] = "updated secret" // Updating must not extend the original expiry.
        clock.now = Instant.EPOCH.plusMillis(1000)
        assertNull(store["one"])
        assertEquals(0, store.size())
        store["two"] = "another"
        store.close()
        assertNull(store["two"])
        assertFailsWith<IllegalStateException> { store["three"] = "secret" }
    }

    @Test
    fun `purge treats entries with the same deadline consistently`() {
        val clock = TestClock()
        ExpiringStore<String>(clock, 1000).use { store ->
            store["one"] = "first"
            store["two"] = "second"
            clock.now = Instant.EPOCH.plusMillis(999)
            clock.stepMillis = 1

            store.purge()

            // Inspect at the sweep time to distinguish removal from subsequent expiration.
            clock.stepMillis = 0
            clock.now = Instant.EPOCH.plusMillis(999)
            assertEquals("first", store["one"])
            assertEquals("second", store["two"])
            clock.now = Instant.EPOCH.plusMillis(1000)
            assertEquals(0, store.size())
        }
    }

    @Test
    fun `large positive TTL does not overflow into immediate expiry`() {
        val clock = TestClock(Instant.ofEpochMilli(1))
        ExpiringStore<String>(clock, Long.MAX_VALUE).use { store ->
            store["one"] = "secret"
            assertEquals("secret", store["one"])
            clock.now = Instant.ofEpochMilli(Long.MAX_VALUE - 1)
            assertEquals("secret", store["one"])
            clock.now = Instant.ofEpochMilli(Long.MAX_VALUE)
            assertNull(store["one"])
        }
    }
}
