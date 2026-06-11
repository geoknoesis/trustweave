package org.trustweave.core.plugin

import org.junit.jupiter.api.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Concurrency tests for [DefaultPluginRegistry].
 *
 * Invariant under test: a reader that observes `getMetadata(id) != null` must also be
 * able to observe the registered instance via `getInstance(id, ...)`. Registration
 * publishes metadata last; unregistration retracts metadata first — so the invariant
 * holds on both sides of the lifecycle.
 */
class PluginRegistryConcurrencyTest {

    private fun metadataFor(id: String) = createTestPluginMetadata(
        id = id,
        capabilities = PluginCapabilities(features = setOf("race-capability"))
    )

    @Test
    fun `concurrent register never exposes metadata without an instance`() {
        repeat(200) { round ->
            val registry = DefaultPluginRegistry()
            val id = "race-plugin-$round"
            val start = CountDownLatch(1)
            val violation = AtomicReference<String>(null)

            val reader = thread(name = "reader-$round") {
                start.await()
                // Spin (with a deadline) until the plugin becomes visible, then assert the invariant.
                val deadline = System.nanoTime() + 10_000_000_000L
                while (System.nanoTime() < deadline) {
                    val metadata = registry.getMetadata(id)
                    if (metadata != null) {
                        if (registry.getInstance(id, String::class.java) == null) {
                            violation.set("round $round: metadata visible but instance was null")
                        }
                        return@thread
                    }
                }
                violation.set("round $round: plugin never became visible to the reader")
            }
            val writer = thread(name = "writer-$round") {
                start.await()
                registry.register(metadataFor(id), "instance-$round")
            }

            start.countDown()
            reader.join(15_000)
            writer.join(15_000)

            assertNull(violation.get(), violation.get() ?: "")
        }
    }

    @Test
    fun `concurrent unregister never exposes metadata without an instance`() {
        repeat(200) { round ->
            val registry = DefaultPluginRegistry()
            val id = "race-plugin-$round"
            registry.register(metadataFor(id), "instance-$round")

            val start = CountDownLatch(1)
            val violation = AtomicReference<String>(null)

            val reader = thread(name = "reader-$round") {
                start.await()
                // Spin (with a deadline) until the plugin disappears; while metadata is
                // visible the instance must be visible too (metadata is retracted FIRST).
                val deadline = System.nanoTime() + 10_000_000_000L
                while (System.nanoTime() < deadline) {
                    val metadata = registry.getMetadata(id)
                    if (metadata == null) {
                        return@thread
                    }
                    if (registry.getInstance(id, String::class.java) == null) {
                        // The two reads are not atomic: unregister (metadata first, then
                        // instance) may have completed entirely between them. Re-read the
                        // metadata — only a STILL-visible metadata with a missing instance
                        // violates the publication invariant.
                        if (registry.getMetadata(id) != null) {
                            violation.set("round $round: metadata still visible but instance already removed")
                        }
                        return@thread
                    }
                }
                violation.set("round $round: plugin never disappeared for the reader")
            }
            val writer = thread(name = "writer-$round") {
                start.await()
                registry.unregister(id)
            }

            start.countDown()
            reader.join(15_000)
            writer.join(15_000)

            assertNull(violation.get(), violation.get() ?: "")
        }
    }

    @Test
    fun `unregister prunes capability and provider lookups`() {
        val registry = DefaultPluginRegistry()
        val first = createTestPluginMetadata(
            id = "plugin-a",
            provider = "shared-provider",
            capabilities = PluginCapabilities(features = setOf("shared-capability"))
        )
        val second = createTestPluginMetadata(
            id = "plugin-b",
            provider = "shared-provider",
            capabilities = PluginCapabilities(features = setOf("shared-capability"))
        )
        registry.register(first, "instance-a")
        registry.register(second, "instance-b")

        registry.unregister("plugin-a")
        assertEquals(listOf("plugin-b"), registry.findByCapability("shared-capability").map { it.id })
        assertEquals(listOf("plugin-b"), registry.findByProvider("shared-provider").map { it.id })

        registry.unregister("plugin-b")
        assertTrue(registry.findByCapability("shared-capability").isEmpty())
        assertTrue(registry.findByProvider("shared-provider").isEmpty())

        // Re-registration after full pruning must work normally.
        registry.register(first, "instance-a2")
        assertEquals(listOf("plugin-a"), registry.findByCapability("shared-capability").map { it.id })
    }
}
