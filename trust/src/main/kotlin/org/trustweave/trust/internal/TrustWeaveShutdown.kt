package org.trustweave.trust.internal

import kotlinx.coroutines.runBlocking
import org.slf4j.Logger
import org.trustweave.core.plugin.PluginLifecycle
import org.trustweave.trust.dsl.TrustWeaveConfig
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/**
 * Closes the components of a [TrustWeaveConfig] that the facade owns (see `TrustWeave.close`).
 *
 * Kept out of `TrustWeave.kt`: it is pure lifecycle plumbing with no part in the public facade surface.
 * Failures of individual components are logged and never stop the remaining components from being closed.
 */
internal class TrustWeaveShutdown(
    private val config: TrustWeaveConfig,
    private val logger: Logger,
) {
    /** Suspending close: awaits `stop()`/`cleanup()` of [PluginLifecycle] components. */
    suspend fun closeOwnedComponents() {
        logger.debug("Closing TrustWeave instance: ${config.name}")

        val ownership = config.ownership

        // DID method instances created during build (caller-registered methods are
        // intentionally absent from this snapshot).
        ownership.ownedDidMethods.forEach { method ->
            closeComponent("DID method ${method.javaClass.simpleName}", method)
        }

        if (ownership.ownsCredentialService) {
            closeComponent("credential service", config.credentialService)
        }
        if (ownership.ownsRevocationManager) {
            closeComponent("revocation manager", config.revocationManager)
        }
        if (ownership.ownsTrustRegistry) {
            closeComponent("trust registry", config.trustRegistry)
        }

        // Factory-built when domain { ... } is configured.
        closeComponent("trusted domain manager", config.trustedDomainManager)

        // Anchor clients are resolved by the factory from anchor { ... } configuration.
        try {
            config.blockchainRegistry.getAllClients().values.forEach { client ->
                closeComponent("blockchain client ${client.javaClass.simpleName}", client)
            }
        } catch (e: kotlin.coroutines.cancellation.CancellationException) {
            throw e
        } catch (e: Exception) {
            logger.warn("Error enumerating blockchain clients during close: ${e.message}", e)
        }

        if (ownership.ownsKms) {
            closeComponent("KMS", config.kms)
        }
        // The KmsService adapter is always factory-created (DefaultKmsService).
        closeComponent("KMS service", config.kmsService)

        logger.debug("TrustWeave instance closed: ${config.name}")
    }

    /**
     * Blocking close for `AutoCloseable.close()`.
     *
     * The blocking section runs on a dedicated daemon thread that drives its own `runBlocking` event
     * loop, and the caller only waits for it with a bounded `join`. It therefore never nests an event loop
     * inside the caller's dispatcher (a single-threaded or main dispatcher would deadlock under a plain
     * `runBlocking`), and a component whose `stop()` hangs cannot hang the caller beyond [timeoutMillis]:
     * the stuck close is logged as an error and abandoned (the thread is a daemon). `close()` never
     * throws, so an interrupted wait is remembered and re-asserted on the caller's thread afterwards.
     *
     * A component that needs the CALLER's own single thread to finish still cannot complete while that
     * thread waits here; that case ends at the timeout. Coroutine callers should use `closeAsync()`.
     */
    fun closeBlocking(timeoutMillis: Long = DEFAULT_CLOSE_TIMEOUT_MILLIS) {
        val failure = AtomicReference<Throwable?>(null)
        val worker =
            Thread({
                try {
                    runBlocking { closeOwnedComponents() }
                } catch (t: Throwable) {
                    failure.set(t)
                }
            }, "trustweave-close-${config.name}")
        worker.isDaemon = true
        worker.start()

        var interrupted = false
        val deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMillis)
        while (worker.isAlive) {
            val remaining = deadline - System.nanoTime()
            if (remaining <= 0) break
            try {
                TimeUnit.NANOSECONDS.timedJoin(worker, remaining)
            } catch (e: InterruptedException) {
                interrupted = true
            }
        }
        if (interrupted) Thread.currentThread().interrupt()

        if (worker.isAlive) {
            logger.error(
                "Closing TrustWeave instance '{}' did not finish within {} ms; abandoning it. A component's stop()/close() " +
                    "is stuck (or needs this thread): call closeAsync() from a coroutine instead.",
                config.name,
                timeoutMillis,
            )
        }
        failure.get()?.let { logger.warn("Closing TrustWeave instance '${config.name}' failed: ${it.message}", it) }
    }

    /**
     * Close a single owned component, preferring [AutoCloseable.close] and falling back
     * to [PluginLifecycle] `stop()` + `cleanup()`. Failures are logged, never propagated,
     * so every remaining component still gets closed.
     */
    private suspend fun closeComponent(
        name: String,
        component: Any?,
    ) {
        if (component == null) return
        try {
            when (component) {
                is AutoCloseable -> component.close()
                is PluginLifecycle ->
                    try {
                        component.stop()
                    } finally {
                        component.cleanup()
                    }
                else -> Unit
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            logger.warn("Error closing $name: ${e.message}", e)
        }
    }

    companion object {
        /** How long [closeBlocking] waits for the dedicated close thread before abandoning it. */
        const val DEFAULT_CLOSE_TIMEOUT_MILLIS: Long = 60_000L
    }
}
