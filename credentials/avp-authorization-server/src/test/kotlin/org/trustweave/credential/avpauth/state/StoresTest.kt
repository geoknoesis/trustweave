package org.trustweave.credential.avpauth.state

import java.math.BigDecimal
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class StoresTest {
    @Test fun `nonce store detects reuse`() {
        val s = NonceStore()
        assertFalse(s.seen("k", "n1"))
        s.record("k", "n1")
        assertTrue(s.seen("k", "n1"))
        assertFalse(s.seen("k", "n2"))
        assertFalse(s.seen("k2", "n1")) // same nonce, different credential -> not seen
    }

    @Test fun `consumption ledger is single-use`() {
        val c = ConsumptionLedger()
        assertFalse(c.consumed("auth-1"))
        c.consume("auth-1")
        assertTrue(c.consumed("auth-1"))
    }

    @Test fun `daily ledger accumulates per agent per day and resets on rollover`() {
        val d = DailyBudgetLedger()
        val t1 = Instant.parse("2026-03-25T10:00:00Z")
        val t2 = Instant.parse("2026-03-26T10:00:00Z")
        assertEquals(BigDecimal("0"), d.spentToday("agent", "cred", t1))
        d.add("agent", "cred", BigDecimal("3.00"), t1)
        assertEquals(BigDecimal("3.00"), d.spentToday("agent", "cred", t1))
        assertEquals(BigDecimal("0"), d.spentToday("agent", "cred", t2))
    }
}
