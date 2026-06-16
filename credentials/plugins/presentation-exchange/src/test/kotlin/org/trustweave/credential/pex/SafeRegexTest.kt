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
}
