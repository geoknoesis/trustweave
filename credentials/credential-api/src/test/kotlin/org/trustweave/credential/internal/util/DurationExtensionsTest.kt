package org.trustweave.credential.internal.util

import java.time.Duration
import kotlin.test.Test
import kotlin.test.assertEquals

class DurationExtensionsTest {
    @Test
    fun `units convert to the matching java durations`() {
        assertEquals(Duration.ofDays(1), 1.days)
        assertEquals(Duration.ofHours(36), 36.hours)
        assertEquals(Duration.ofMinutes(90), 90.minutes)
        assertEquals(Duration.ofDays(2 * 365L), 2.years)
        assertEquals(Duration.ZERO, 0.days)
    }

    @Test
    fun `a year is 365 days and large values do not overflow int arithmetic`() {
        assertEquals(Duration.ofDays(365), 1.years)
        // 10_000_000 * 365 overflows Int; the extension must widen to Long first.
        assertEquals(Duration.ofDays(3_650_000_000L), 10_000_000.years)
    }
}
