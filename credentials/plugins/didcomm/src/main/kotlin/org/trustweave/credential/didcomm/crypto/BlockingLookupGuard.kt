package org.trustweave.credential.didcomm.crypto

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.slf4j.LoggerFactory
import java.util.concurrent.Executors
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * Bounded bridge for the synchronous callbacks didcomm-java makes (secret and DID document lookup).
 *
 * didcomm-java cannot suspend, so a lookup that was not satisfied from a preloaded cache has to
 * block. **Preloading from suspend code is the supported path** (`KmsSecretResolver.preload`,
 * `BlockingDidDocResolver.preload`); this guard is the safety net and is deliberately conservative:
 *
 * - fallback lookups run on a small dedicated daemon pool ([maxConcurrent] threads), never on
 *   `Dispatchers.IO`, so a storm of fallbacks cannot starve the shared IO pool or the callers;
 * - at most [maxConcurrent] fallbacks run at once; further callers wait up to the timeout for a slot
 *   and then fail loudly instead of queueing without bound;
 * - every lookup has a timeout ([DEFAULT_TIMEOUT_MS] = 5s);
 * - every fallback is logged at WARN so a missing preload is visible.
 *
 * Both limits are configurable per JVM: `-Dtrustweave.didcomm.fallbackLookup.timeoutMs=...` and
 * `-Dtrustweave.didcomm.fallbackLookup.maxConcurrent=...` (read once, at first use).
 */
internal object BlockingLookupGuard {
    const val DEFAULT_TIMEOUT_MS = 5_000L
    const val DEFAULT_MAX_CONCURRENT = 8
    const val TIMEOUT_PROPERTY = "trustweave.didcomm.fallbackLookup.timeoutMs"
    const val MAX_CONCURRENT_PROPERTY = "trustweave.didcomm.fallbackLookup.maxConcurrent"

    private val logger = LoggerFactory.getLogger(BlockingLookupGuard::class.java)

    val timeoutMs: Long =
        System.getProperty(TIMEOUT_PROPERTY)?.toLongOrNull()?.takeIf { it > 0 } ?: DEFAULT_TIMEOUT_MS

    val maxConcurrent: Int =
        System.getProperty(MAX_CONCURRENT_PROPERTY)?.toIntOrNull()?.takeIf { it > 0 } ?: DEFAULT_MAX_CONCURRENT

    private val semaphore = Semaphore(maxConcurrent)

    /** Dedicated, bounded dispatcher for fallback lookups. */
    val dispatcher: CoroutineDispatcher by lazy {
        val counter = AtomicInteger()
        Executors
            .newFixedThreadPool(maxConcurrent) { runnable ->
                Thread(runnable, "didcomm-fallback-lookup-${counter.incrementAndGet()}").apply { isDaemon = true }
            }.asCoroutineDispatcher()
    }

    /**
     * Runs [block] to completion on [on], blocking the caller, within [timeoutMs] and the
     * concurrency bound. [what] names the lookup for logs and errors.
     */
    fun <T> run(
        what: String,
        timeoutMs: Long = this.timeoutMs,
        on: CoroutineDispatcher = dispatcher,
        block: suspend () -> T,
    ): T {
        logger.warn(
            "DIDComm blocking fallback lookup for '{}': the value was not preloaded. Call preload(...) from suspend " +
                "code before pack/unpack to avoid blocking a thread.",
            what,
        )
        val acquired =
            try {
                semaphore.tryAcquire(timeoutMs, TimeUnit.MILLISECONDS)
            } catch (e: InterruptedException) {
                Thread.currentThread().interrupt()
                throw IllegalStateException("DIDComm fallback lookup for '$what' interrupted while waiting for a slot", e)
            }
        check(acquired) {
            "DIDComm fallback lookup for '$what' rejected: $maxConcurrent fallback lookups already in flight " +
                "(waited ${timeoutMs}ms). Preload secrets and DID documents instead of relying on the blocking fallback."
        }
        try {
            return runBlocking(on) {
                try {
                    withTimeout(timeoutMs) { block() }
                } catch (e: TimeoutCancellationException) {
                    throw IllegalStateException("DIDComm lookup for '$what' timed out after ${timeoutMs}ms", e)
                }
            }
        } finally {
            semaphore.release()
        }
    }
}
