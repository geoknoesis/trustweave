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
        override fun instant(): Instant = now

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
}
