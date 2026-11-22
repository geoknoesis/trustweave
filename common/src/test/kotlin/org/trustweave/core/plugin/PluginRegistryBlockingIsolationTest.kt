package org.trustweave.core.plugin

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.jupiter.api.Test
import org.trustweave.core.exception.TrustWeaveException
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicReference
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * Verifies that blocking [DefaultPluginRegistry] entry points (`register`, `unregister`, `clear`)
 * isolate suspending [PluginLifecycle] callbacks onto a dedicated daemon thread with a bounded
 * timeout rather than running `runBlocking` directly on the caller's thread.
 */
class PluginRegistryBlockingIsolationTest {
    private fun metadata(id: String) =
        PluginMetadata(
            id = id,
            name = "Plugin $id",
            version = "1.0.0",
            provider = "test",
            capabilities = PluginCapabilities(features = setOf("feature-$id")),
        )

    @Test
    fun `blocking lifecycle methods run on an isolated daemon thread instead of the caller thread`() {
        val registry = DefaultPluginRegistry()
        val callerThread = Thread.currentThread()
        val initThread = AtomicReference<Thread>()
        val stopThread = AtomicReference<Thread>()
        val cleanupThread = AtomicReference<Thread>()

        val plugin =
            object : PluginLifecycle {
                override suspend fun initialize(config: Map<String, Any?>): Boolean {
                    initThread.set(Thread.currentThread())
                    return true
                }

                override suspend fun start(): Boolean = true

                override suspend fun stop(): Boolean {
                    stopThread.set(Thread.currentThread())
                    return true
                }

                override suspend fun cleanup() {
                    cleanupThread.set(Thread.currentThread())
                }
            }

        registry.register(metadata("isolated"), plugin)
        assertNotEquals(callerThread, initThread.get())
        assertTrue(initThread.get().isDaemon)
        assertTrue(initThread.get().name.startsWith("trustweave-plugin-register-"))

        registry.unregister("isolated")
        assertNotEquals(callerThread, stopThread.get())
        assertTrue(stopThread.get().isDaemon)
        assertTrue(stopThread.get().name.startsWith("trustweave-plugin-unregister-"))
        assertEquals(stopThread.get(), cleanupThread.get())
    }

    @Test
    fun `blocking register unregister and clear complete cleanly from a single-threaded coroutine dispatcher`() {
        val executor =
            Executors.newSingleThreadExecutor { runnable ->
                Thread(runnable, "single-dispatcher-thread").apply { isDaemon = true }
            }
        val singleDispatcher = executor.asCoroutineDispatcher()
        try {
            val registry = DefaultPluginRegistry(blockingLifecycleTimeoutMillis = 5_000L)
            val lifecycleThreads = mutableListOf<String>()
            val plugin =
                object : PluginLifecycle {
                    override suspend fun initialize(config: Map<String, Any?>): Boolean {
                        lifecycleThreads += Thread.currentThread().name
                        withContext(Dispatchers.Default) { delay(10) }
                        return true
                    }

                    override suspend fun start(): Boolean = true

                    override suspend fun stop(): Boolean {
                        lifecycleThreads += Thread.currentThread().name
                        withContext(Dispatchers.Default) { delay(10) }
                        return true
                    }

                    override suspend fun cleanup() {
                        lifecycleThreads += Thread.currentThread().name
                    }
                }

            runBlocking {
                withContext(singleDispatcher) {
                    registry.register(metadata("single-thread-1"), plugin)
                    assertTrue(registry.isRegistered("single-thread-1"))
                    registry.unregister("single-thread-1")
                    assertFalse(registry.isRegistered("single-thread-1"))

                    registry.register(metadata("single-thread-2"), plugin)
                    registry.clear()
                    assertFalse(registry.isRegistered("single-thread-2"))
                }
            }

            assertTrue(lifecycleThreads.isNotEmpty())
            assertTrue(lifecycleThreads.none { it == "single-dispatcher-thread" })
        } finally {
            singleDispatcher.close()
        }
    }

    @Test
    fun `hung initialize times out with OperationTimedOut instead of hanging forever`() {
        val registry = DefaultPluginRegistry(blockingLifecycleTimeoutMillis = 150L)
        val hungPlugin =
            object : PluginLifecycle {
                override suspend fun initialize(config: Map<String, Any?>): Boolean {
                    awaitCancellation()
                }

                override suspend fun start(): Boolean = true

                override suspend fun stop(): Boolean = true

                override suspend fun cleanup() = Unit
            }

        val error =
            assertFailsWith<TrustWeaveException.OperationTimedOut> {
                registry.register(metadata("hung-init"), hungPlugin)
            }

        assertEquals("OPERATION_TIMED_OUT", error.code)
        assertTrue(error.operation.contains("hung-init"))
        assertFalse(registry.isRegistered("hung-init"))
    }

    @Test
    fun `hung clear times out with OperationTimedOut after retracting plugins`() {
        val registry = DefaultPluginRegistry(blockingLifecycleTimeoutMillis = 150L)
        val hungStopPlugin =
            object : PluginLifecycle {
                override suspend fun initialize(config: Map<String, Any?>): Boolean = true

                override suspend fun start(): Boolean = true

                override suspend fun stop(): Boolean {
                    awaitCancellation()
                }

                override suspend fun cleanup() = Unit
            }

        registry.register(metadata("hung-stop"), hungStopPlugin)
        assertTrue(registry.isRegistered("hung-stop"))

        val error =
            assertFailsWith<TrustWeaveException.OperationTimedOut> {
                registry.clear()
            }

        assertEquals("OPERATION_TIMED_OUT", error.code)
        assertFalse(registry.isRegistered("hung-stop"))
    }
}
