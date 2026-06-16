package org.trustweave.credential.pex

import java.util.concurrent.ExecutionException
import java.util.concurrent.Executors
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
 * thread. This guard (1) caps the pattern length, (2) compiles once, and (3) runs the match on a
 * worker bounded by a hard deadline, aborting a runaway match via an interruptible input view.
 * Any oversize / uncompilable / timed-out evaluation **fails closed** (returns `false`: the value
 * does not satisfy the filter) rather than blocking.
 */
internal object SafeRegex {

    private const val MAX_PATTERN_LENGTH = 1024
    private const val MATCH_TIMEOUT_MS = 1000L

    private val executor = Executors.newCachedThreadPool { r ->
        Thread(r, "pex-regex-guard").apply { isDaemon = true }
    }

    /** Partial match (`find`) of [input] against the untrusted [pattern]; never blocks indefinitely. */
    fun containsMatch(pattern: String, input: String): Boolean {
        if (pattern.length > MAX_PATTERN_LENGTH) return false
        val compiled = try {
            Pattern.compile(pattern)
        } catch (e: PatternSyntaxException) {
            return false
        }
        val future = executor.submit<Boolean> {
            compiled.matcher(InterruptibleCharSequence(input)).find()
        }
        return try {
            future.get(MATCH_TIMEOUT_MS, TimeUnit.MILLISECONDS)
        } catch (e: TimeoutException) {
            future.cancel(true)
            false
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
            future.cancel(true)
            false
        } catch (e: ExecutionException) {
            false
        }
    }

    /**
     * A view over [inner] that throws once its thread is interrupted, so a runaway `java.util.regex`
     * match (which reads its input through [get]) can be cancelled once the deadline is exceeded.
     */
    private class InterruptibleCharSequence(private val inner: CharSequence) : CharSequence {
        override val length: Int get() = inner.length

        override fun get(index: Int): Char {
            if (Thread.currentThread().isInterrupted) throw RuntimeException("regex match cancelled")
            return inner[index]
        }

        override fun subSequence(startIndex: Int, endIndex: Int): CharSequence =
            InterruptibleCharSequence(inner.subSequence(startIndex, endIndex))

        override fun toString(): String = inner.toString()
    }
}
