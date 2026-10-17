package org.trustweave.core.plugin

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import org.trustweave.core.exception.PluginException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

class PluginCleanupCancellationTest {
    private fun metadata(id: String) =
        PluginMetadata(
            id = id,
            name = id,
            version = "1.0.0",
            provider = "test",
            capabilities = PluginCapabilities(features = setOf("test")),
        )

    private open class Plugin : PluginLifecycle {
        val calls = mutableListOf<String>()

        override suspend fun initialize(config: Map<String, Any?>): Boolean = true

        override suspend fun start(): Boolean = true

        override suspend fun stop(): Boolean {
            calls.add("stop")
            return true
        }

        override suspend fun cleanup() {
            yield()
            calls.add("cleanup")
        }
    }

    @Test
    fun `unregister finishes cleanup before propagating stop cancellation`() {
        val registry = DefaultPluginRegistry()
        val cancellation = CancellationException("stop cancelled")
        val plugin =
            object : Plugin() {
                override suspend fun stop(): Boolean {
                    super.stop()
                    throw cancellation
                }
            }
        registry.register(metadata("cancel-stop"), plugin)

        assertSame(cancellation, assertFailsWith<CancellationException> { registry.unregister("cancel-stop") })
        assertEquals(listOf("stop", "cleanup"), plugin.calls)
        assertFalse(registry.isRegistered("cancel-stop"))
    }

    @Test
    fun `clear releases every plugin even when each stop cancels`() {
        val registry = DefaultPluginRegistry()
        val cancellations = (1..3).map { CancellationException("stop $it cancelled") }
        val plugins =
            cancellations.mapIndexed { index, cancellation ->
                object : Plugin() {
                    override suspend fun stop(): Boolean {
                        super.stop()
                        throw cancellation
                    }
                }.also { registry.register(metadata("plugin-$index"), it) }
            }

        val failure = assertFailsWith<CancellationException> { registry.clear() }
        assertTrue(cancellations.any { it === failure })
        assertEquals(2, failure.suppressed.size)
        assertTrue(cancellations.all { it === failure || it in failure.suppressed })
        plugins.forEach { assertEquals(listOf("stop", "cleanup"), it.calls) }
        assertTrue(registry.getAllPlugins().isEmpty())
    }

    @Test
    fun `rollback preserves startup failure when both teardown phases cancel`() {
        val registry = DefaultPluginRegistry()
        val startup = IllegalStateException("startup failed")
        val stop = CancellationException("stop cancelled")
        val cleanup = CancellationException("cleanup cancelled")
        val plugin =
            object : Plugin() {
                override suspend fun start(): Boolean = throw startup

                override suspend fun stop(): Boolean {
                    super.stop()
                    throw stop
                }

                override suspend fun cleanup() {
                    super.cleanup()
                    throw cleanup
                }
            }

        val failure = assertFailsWith<PluginException.InitializationFailed> { registry.register(metadata("rollback"), plugin) }
        assertSame(startup, failure.cause)
        assertEquals(listOf(stop, cleanup), startup.suppressed.toList())
        assertEquals(listOf("stop", "cleanup"), plugin.calls)
        assertFalse(registry.isRegistered("rollback"))
    }

    @Test
    fun `rollback tolerates a plugin rethrowing the same startup cancellation during stop`() {
        val registry = DefaultPluginRegistry()
        val cancellation = CancellationException("same cancellation")
        val plugin =
            object : Plugin() {
                override suspend fun start(): Boolean = throw cancellation

                override suspend fun stop(): Boolean {
                    super.stop()
                    throw cancellation
                }
            }

        assertSame(cancellation, assertFailsWith<CancellationException> { registry.register(metadata("same"), plugin) })
        assertEquals(listOf("stop", "cleanup"), plugin.calls)
        assertTrue(cancellation.suppressed.isEmpty())
    }

    @Test
    fun `caller cancellation during stop still allows suspending cleanup to finish`() =
        runBlocking {
            withTimeout(10_000) {
                val registry = DefaultPluginRegistry()
                val entered = CompletableDeferred<Unit>()
                val release = CompletableDeferred<Unit>()
                val plugin =
                    object : Plugin() {
                        override suspend fun stop(): Boolean {
                            super.stop()
                            entered.complete(Unit)
                            release.await()
                            return true
                        }
                    }
                registry.registerSuspending(metadata("caller-cancelled"), plugin)
                val job = launch { registry.unregisterSuspending("caller-cancelled") }
                entered.await()
                job.cancel()
                release.complete(Unit)
                job.join()

                assertTrue(job.isCancelled)
                assertEquals(listOf("stop", "cleanup"), plugin.calls)
                assertFalse(registry.isRegistered("caller-cancelled"))
            }
        }

    @Test
    fun `clear releases all plugins when the caller is cancelled during stop`() =
        runBlocking {
            withTimeout(10_000) {
                val registry = DefaultPluginRegistry()
                val entered = CompletableDeferred<Unit>()
                val release = CompletableDeferred<Unit>()
                val plugins =
                    (1..3).map { index ->
                        object : Plugin() {
                            override suspend fun stop(): Boolean {
                                super.stop()
                                entered.complete(Unit)
                                release.await()
                                return true
                            }
                        }.also { registry.registerSuspending(metadata("bulk-$index"), it) }
                    }
                val job = launch { registry.clearSuspending() }
                entered.await()
                job.cancel()
                release.complete(Unit)
                job.join()

                assertTrue(job.isCancelled)
                plugins.forEach { assertEquals(listOf("stop", "cleanup"), it.calls) }
                assertTrue(registry.getAllPlugins().isEmpty())
            }
        }

    @Test
    fun `cleanup cancellation retains an earlier ordinary stop failure`() {
        val registry = DefaultPluginRegistry()
        val stop = IllegalStateException("stop failed")
        val cleanup = CancellationException("cleanup cancelled")
        val plugin =
            object : Plugin() {
                override suspend fun stop(): Boolean {
                    super.stop()
                    throw stop
                }

                override suspend fun cleanup() {
                    super.cleanup()
                    throw cleanup
                }
            }
        registry.register(metadata("cleanup-cancelled"), plugin)

        assertSame(cleanup, assertFailsWith<CancellationException> { registry.unregister("cleanup-cancelled") })
        assertEquals(listOf(stop), cleanup.suppressed.toList())
        assertEquals(listOf("stop", "cleanup"), plugin.calls)
    }
}
