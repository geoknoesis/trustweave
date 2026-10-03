package org.trustweave.core.plugin

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.trustweave.core.exception.PluginException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/** clear() during initialization followed by a re-register of the same ID must not mix registrations. */
class PluginRegistryGenerationTest {
    private fun meta(id: String) = createTestPluginMetadata(id = id, capabilities = PluginCapabilities(features = setOf("gen-capability")))

    /** Lifecycle whose initialize() parks until [release] is counted down. */
    private class GatedPlugin(
        val entered: CountDownLatch = CountDownLatch(1),
        val release: CountDownLatch = CountDownLatch(1),
    ) : PluginLifecycle {
        val torndown = AtomicInteger()

        override suspend fun initialize(config: Map<String, Any?>): Boolean {
            entered.countDown()
            check(release.await(20, TimeUnit.SECONDS)) { "test gate never released" }
            return true
        }

        override suspend fun start() = true

        override suspend fun stop(): Boolean {
            torndown.incrementAndGet()
            return true
        }

        override suspend fun cleanup() = Unit
    }

    @Test
    fun `a registration cleared mid-initialization cannot publish over or release a newer registration of the same id`() {
        val registry = DefaultPluginRegistry()
        val id = "aba-plugin"
        val first = GatedPlugin()
        val second = GatedPlugin()
        val firstError = AtomicReference<Throwable>()
        val secondError = AtomicReference<Throwable>()

        val t1 = thread { runCatching { registry.register(meta(id), first) }.onFailure { firstError.set(it) } }
        assertTrue(first.entered.await(10, TimeUnit.SECONDS))

        registry.clear() // wipes A's reservation while A is still initializing

        val t2 = thread { runCatching { registry.register(meta(id), second) }.onFailure { secondError.set(it) } }
        assertTrue(second.entered.await(10, TimeUnit.SECONDS))

        // A finishes first: it must fail (its reservation is gone) and must not consume B's reservation.
        first.release.countDown()
        t1.join(15_000)
        assertTrue(firstError.get() is PluginException.InitializationFailed, "first registration must fail: ${firstError.get()}")
        assertNull(registry.getMetadata(id), "A must not publish")
        assertEquals(1, first.torndown.get(), "A's lifecycle is torn down")
        // B's reservation is intact: a concurrent registration of the id is still rejected.
        assertFailsWith<PluginException.AlreadyRegistered> { registry.register(meta(id), "intruder") }

        second.release.countDown()
        t2.join(15_000)
        assertNull(secondError.get(), "second registration must succeed: ${secondError.get()}")
        assertSame(second, registry.getInstance(id, GatedPlugin::class.java))
        assertEquals(0, second.torndown.get())
    }

    @Test
    fun `suspending register unregister and clear drive the lifecycle`() =
        runBlocking {
            val registry = DefaultPluginRegistry()
            val plugin = GatedPlugin().also { it.release.countDown() }
            registry.registerSuspending(meta("susp"), plugin)
            assertSame(plugin, registry.getInstance("susp", GatedPlugin::class.java))
            registry.unregisterSuspending("susp")
            assertEquals(1, plugin.torndown.get())

            val other = GatedPlugin().also { it.release.countDown() }
            registry.registerSuspending(meta("susp2"), other)
            registry.clearSuspending()
            assertEquals(1, other.torndown.get())
            assertTrue(registry.getAllPlugins().isEmpty())
        }

    @Test
    fun `stress - concurrent register unregister and clear of one id leave a consistent registry`() {
        repeat(30) { round ->
            val registry = DefaultPluginRegistry()
            val id = "stress"
            val violation = AtomicReference<String>()
            val threads =
                (0 until 8).map { n ->
                    thread {
                        repeat(60) { i ->
                            try {
                                when ((n + i) % 3) {
                                    0 -> registry.register(meta(id), "inst-$n-$i")
                                    1 -> registry.clear()
                                    else -> registry.unregister(id)
                                }
                            } catch (_: PluginException) {
                                // AlreadyRegistered / InitializationFailed(cleared) are expected outcomes
                            }
                        }
                    }
                }
            threads.forEach { it.join(30_000) }
            // Quiescent state: metadata and instance (and capability index) agree.
            val metadata = registry.getMetadata(id)
            val instance = registry.getInstance(id, String::class.java)
            assertEquals(metadata != null, instance != null, "round $round: metadata/instance mismatch")
            assertEquals(metadata != null, registry.findByCapability("gen-capability").isNotEmpty(), "round $round: index mismatch")
            assertNull(violation.get(), violation.get() ?: "")
            // No reservation leaked: after clearing, the id can be registered again.
            registry.clear()
            registry.register(meta(id), "final")
            assertEquals("final", registry.getInstance(id, String::class.java))
        }
    }
}
