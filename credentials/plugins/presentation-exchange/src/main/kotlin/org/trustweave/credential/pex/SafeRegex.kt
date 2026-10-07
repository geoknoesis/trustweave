package org.trustweave.credential.pex

import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.ExecutionException
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.regex.Pattern
import java.util.regex.PatternSyntaxException

/**
 * Bounded, ReDoS-resistant evaluation of an UNTRUSTED regex — a DIF Presentation Definition
 * `filter.pattern` supplied by the (untrusted) verifier in its request.
 *
 * `java.util.regex` uses a backtracking engine that can exhibit catastrophic (super-linear)
 * behaviour on adversarial patterns, so a verifier could otherwise hang the wallet's matching
 * thread. This guard (1) caps the pattern length, (2) compiles each distinct pattern once (bounded
 * LRU cache), and (3) runs the match on a bounded worker pool (fixed threads, bounded queue; a
 * full queue rejects, which fails closed) under a hard deadline, aborting a runaway match via an interruptible input view.
 * Any oversize / uncompilable / timed-out evaluation **fails closed** (returns `false`: the value
 * does not satisfy the filter) rather than blocking.
 */
internal object SafeRegex {
    private const val MAX_PATTERN_LENGTH = 1024
    private const val MATCH_TIMEOUT_MS = 1000L

    private const val MAX_WORKERS = 8
    private const val MAX_QUEUED = 32
    private const val MAX_CACHED_PATTERNS = 256

    private val executor =
        ThreadPoolExecutor(
            MAX_WORKERS,
            MAX_WORKERS,
            30L,
            TimeUnit.SECONDS,
            ArrayBlockingQueue(MAX_QUEUED),
            { r -> Thread(r, "pex-regex-guard").apply { isDaemon = true } },
            ThreadPoolExecutor.AbortPolicy(),
        ).apply { allowCoreThreadTimeOut(true) }

    /** Bounded LRU of compiled patterns; a pattern that does not compile is cached as `null`. */
    private val compiledCache =
        object : LinkedHashMap<String, Pattern?>(16, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Pattern?>): Boolean = size > MAX_CACHED_PATTERNS
        }

    private fun compile(pattern: String): Pattern? =
        synchronized(compiledCache) {
            if (compiledCache.containsKey(pattern)) {
                compiledCache[pattern]
            } else {
                val compiled =
                    try {
                        Pattern.compile(pattern)
                    } catch (e: PatternSyntaxException) {
                        null
                    }
                compiledCache[pattern] = compiled
                compiled
            }
        }

    /** Test hooks. */
    internal fun cachedPatternCount(): Int = synchronized(compiledCache) { compiledCache.size }

    internal fun guardThreadCount(): Int = executor.poolSize

    /** Partial match (`find`) of [input] against the untrusted [pattern]; never blocks indefinitely. */
    fun containsMatch(
        pattern: String,
        input: String,
    ): Boolean {
        if (pattern.length > MAX_PATTERN_LENGTH) return false
        val compiled = compile(pattern) ?: return false
        val future =
            try {
                executor.submit<Boolean> {
                    compiled.matcher(InterruptibleCharSequence(input)).find()
                }
            } catch (e: RejectedExecutionException) {
                return false // saturated: fail closed rather than queue without bound
            }
        return try {
            future.get(MATCH_TIMEOUT_MS, TimeUnit.MILLISECONDS)
        } catch (e: TimeoutException) {
            future.cancel(true)
            executor.remove(future as Runnable)
            false
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
            future.cancel(true)
            executor.remove(future as Runnable)
            false
        } catch (e: ExecutionException) {
            false
        }
    }

    /**
     * A view over [inner] that throws once its thread is interrupted, so a runaway `java.util.regex`
     * match (which reads its input through [get]) can be cancelled once the deadline is exceeded.
     */
    private class InterruptibleCharSequence(
        private val inner: CharSequence,
    ) : CharSequence {
        override val length: Int get() = inner.length

        override fun get(index: Int): Char {
            if (Thread.currentThread().isInterrupted) throw RuntimeException("regex match cancelled")
            return inner[index]
        }

        override fun subSequence(
            startIndex: Int,
            endIndex: Int,
        ): CharSequence = InterruptibleCharSequence(inner.subSequence(startIndex, endIndex))

        override fun toString(): String = inner.toString()
    }
}
