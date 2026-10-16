package org.trustweave.credential.oidc4vci.server

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class Oidc4VciProtocolRateLimitOverflowTest {
    private var now = 0L

    private fun limit(
        permits: Int = 1,
        maxTracked: Int = 2,
    ) = Oidc4VciProtocolRateLimit(permits = permits, windowMillis = 1_000, maxTrackedCallers = maxTracked, clock = { now })

    @Test
    fun `an attacker exhausting its overflow bucket does not lock out callers in other buckets`() {
        val rl = limit()
        assertTrue(rl.admit("a", "/token"))
        assertTrue(rl.admit("b", "/token")) // table now full
        assertTrue(rl.admit("evil", "/token"))
        assertFalse(rl.admit("evil", "/token"))
        val victim = (0..1000).map { "legit-$it" }.first { rl.overflowBucket(it) != rl.overflowBucket("evil") }
        assertTrue(rl.admit(victim, "/token"), "a caller in another overflow bucket must still be admitted")
    }

    @Test
    fun `a full table is swept at most once per sweep interval, not on every new caller`() {
        val rl = limit(maxTracked = 3)
        repeat(3) { assertTrue(rl.admit("c$it", "/token")) }
        repeat(500) { rl.admit("spray-$it", "/token") }
        assertEquals(1, rl.sweepCount)
        now += 1_000
        repeat(5) { rl.admit("later-$it", "/token") }
        assertEquals(2, rl.sweepCount)
    }

    @Test
    fun `expired windows are reclaimed so new callers are tracked again`() {
        val rl = limit()
        assertTrue(rl.admit("a", "/token"))
        assertTrue(rl.admit("b", "/token"))
        now += 1_000
        assertTrue(rl.admit("c", "/token"))
        assertFalse(rl.admit("c", "/token"))
    }
}
