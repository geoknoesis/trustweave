package org.trustweave.credential.pex

import org.junit.jupiter.api.Assertions.assertTimeoutPreemptively
import java.time.Duration
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * A DIF Presentation Definition's `filter.pattern` is attacker-controlled (it comes from the
 * verifier's request), so matching it against a credential value must not be vulnerable to
 * catastrophic backtracking (ReDoS). [SafeRegex] bounds the match so a malicious pattern cannot
 * hang the matching thread.
 */
class SafeRegexTest {
    // Evil regex using alternation of unequal lengths, which defeats the JDK's nested-quantifier
    // optimization and backtracks ~Fibonacci(n) over a long run of 'a' ending in a non-match char.
    private val evilPattern = "(a|aa)+$"
    private val evilInput = "a".repeat(55) + "!"

    @Test
    fun `catastrophic pattern does not hang and reports no match`() {
        var matched = true
        assertTimeoutPreemptively(Duration.ofSeconds(5)) {
            matched = SafeRegex.containsMatch(evilPattern, evilInput)
        }
        assertFalse(matched, "evil pattern must not match (and must not hang)")
    }

    @Test
    fun `normal patterns still match correctly`() {
        assertTrue(SafeRegex.containsMatch("^abc", "abcdef"))
        assertTrue(SafeRegex.containsMatch("\\d{3}", "id-123"))
        assertFalse(SafeRegex.containsMatch("^abc", "xyzabc"))
    }

    @Test
    fun `oversized patterns are rejected rather than evaluated`() {
        // A 2000-char pattern that WOULD match if evaluated must instead be refused (returns false).
        assertFalse(SafeRegex.containsMatch("a".repeat(2000), "a".repeat(2000)))
    }

    @Test
    fun `a burst of hostile patterns stays bounded and still fails closed`() {
        val callers = 100
        val pool =
            java.util.concurrent.Executors
                .newFixedThreadPool(callers)
        try {
            val started = System.nanoTime()
            val results =
                (1..callers)
                    .map { pool.submit<Boolean> { SafeRegex.containsMatch(evilPattern, evilInput) } }
                    .map { it.get(30, java.util.concurrent.TimeUnit.SECONDS) }
            val elapsedMs = (System.nanoTime() - started) / 1_000_000
            assertTrue(results.none { it }, "every hostile match must fail closed")
            assertTrue(SafeRegex.guardThreadCount() <= 8, "guard threads must stay bounded: ${SafeRegex.guardThreadCount()}")
            assertTrue(elapsedMs < 10_000, "burst must be bounded by the deadline, took ${elapsedMs}ms")
        } finally {
            pool.shutdownNow()
        }
    }

    @Test
    fun `compiled patterns are cached and the cache is bounded`() {
        repeat(1000) { SafeRegex.containsMatch("x$it", "x$it") }
        assertTrue(SafeRegex.cachedPatternCount() <= 256, "cache size ${SafeRegex.cachedPatternCount()}")
        assertTrue(SafeRegex.containsMatch("x999", "x999"))
    }
}
