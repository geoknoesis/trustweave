package org.trustweave.did.resolver

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import kotlin.math.pow
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Performance characteristics of [RetryConfig]: that retrying costs what it says it costs.
 *
 * ## Why these assertions are shaped the way they are
 *
 * This file used to assert a *ratio between two measured wall-clock delays*:
 * `delays[1] >= delays[0] * 0.5`. That measures the machine, not the code. The first delay in a
 * run also carries class loading and JIT warm-up for the coroutine machinery, so on a loaded
 * runner it inflates — observed at 66ms against a configured 10ms — while the second delay is a
 * normal 28ms, and the assertion fails on a build where nothing is wrong. It failed on CI and
 * reproduced 3/3 locally against an unmodified tree.
 *
 * So the assertions here are **lower bounds and counts**, never ratios and never tight ceilings:
 *
 * - A lower bound is robust. Scheduling noise, a loaded runner and a cold JIT can only make an
 *   operation take *longer*, so "at least the configured delay elapsed" cannot be broken by them.
 * - A count is robust. How many times the block ran does not depend on the clock at all.
 * - The ceilings that remain are deliberately an order of magnitude clear of anything observed,
 *   so they still catch a real regression while staying silent about a slow afternoon.
 */
class RetryConfigPerformanceTest {
    @Test
    fun `an operation that succeeds first time is never delayed`() =
        runBlocking<Unit> {
            val config = RetryConfig.default()
            var invocations = 0

            val startedAt = System.nanoTime()
            repeat(OPERATIONS) {
                config.executeWithRetry<String> {
                    invocations++
                    "success"
                }
            }
            val elapsedMs = (System.nanoTime() - startedAt) / 1_000_000.0

            // The property that matters: success costs exactly one call and no backoff at all.
            assertEquals(OPERATIONS, invocations, "a successful operation must not be retried")

            // RetryConfig.default() has an initial delay of 100ms. If the retry path were ever
            // taken here, even once, this would be at least 100ms. Ten seconds for 1000 no-op
            // operations is an order of magnitude clear of any machine this runs on, so it only
            // fires for a real regression.
            assertTrue(elapsedMs < 10_000, "1000 immediate successes should not take ${elapsedMs}ms")
        }

    @Test
    fun `a retried operation actually waits, and only for the attempts it needed`() =
        runBlocking<Unit> {
            val config = RetryConfig(maxRetries = 1, initialDelayMs = 10, maxDelayMs = 100)

            var attempts = 0
            val startedAt = System.nanoTime()
            repeat(RETRY_OPERATIONS) {
                config.executeWithRetry<String> {
                    attempts++
                    if (attempts % 2 == 1) throw java.net.ConnectException("Retry")
                    "success"
                }
            }
            val elapsedMs = (System.nanoTime() - startedAt) / 1_000_000.0

            // Every operation failed once and succeeded once: two calls each, no more.
            assertEquals(RETRY_OPERATIONS * 2, attempts, "each operation should need exactly one retry")

            // A lower bound proves the backoff is real rather than skipped. Each of the 100
            // operations retries once at >= 10ms, so >= 1000ms must have elapsed. A test that only
            // set a ceiling here would still pass if the delay were removed entirely.
            val minimumDelayMs = RETRY_OPERATIONS * 10.0
            assertTrue(
                elapsedMs >= minimumDelayMs,
                "backoff appears not to have been applied: ${elapsedMs}ms elapsed, expected at least ${minimumDelayMs}ms",
            )
        }

    @Test
    fun `each backoff is at least the configured exponential delay`() =
        runBlocking<Unit> {
            val initialDelayMs = 10L
            val maxRetries = 3
            val config = RetryConfig(maxRetries = maxRetries, initialDelayMs = initialDelayMs, maxDelayMs = 1000)

            val delays = mutableListOf<Long>()
            var lastAt = System.currentTimeMillis()
            var attempt = 0

            runCatching {
                config.executeWithRetry<Unit> {
                    val now = System.currentTimeMillis()
                    if (attempt > 0) delays.add(now - lastAt)
                    lastAt = now
                    attempt++
                    throw java.net.ConnectException("Fail")
                }
            }

            assertEquals(maxRetries + 1, attempt, "the block should run once plus one per retry")
            assertEquals(maxRetries, delays.size, "one backoff should separate each pair of attempts")

            // executeWithRetry sleeps min(initial * 2^n, max) plus 1-20% jitter before retry n.
            // Asserting each delay is at least its own configured minimum verifies the schedule
            // grows exponentially, and does so in a form a slow machine cannot break: overhead
            // only ever pushes a measurement further above its lower bound.
            delays.forEachIndexed { index, measured ->
                val configured = (initialDelayMs * 2.0.pow(index)).toLong()
                assertTrue(
                    measured >= configured,
                    "backoff $index was ${measured}ms, below its configured ${configured}ms; delays were $delays",
                )
            }

            // And the schedule really is exponential rather than flat: the last wait is bounded
            // below by four times the first one's configuration.
            assertTrue(
                delays.last() >= initialDelayMs * 4,
                "the final backoff should be at least 4x the initial delay; delays were $delays",
            )
        }

    private companion object {
        const val OPERATIONS = 1000
        const val RETRY_OPERATIONS = 100
    }
}
