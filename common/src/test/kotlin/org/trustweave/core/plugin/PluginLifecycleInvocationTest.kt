package org.trustweave.core.plugin

import org.trustweave.core.exception.PluginException
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.*

/**
 * Tests that [DefaultPluginRegistry] actually drives [PluginLifecycle]:
 * initialize/start on register (with rollback on failure), stop/cleanup on
 * unregister/clear (failures logged, never propagated), plus dependency
 * version-range checks at registration time.
 */
class PluginLifecycleInvocationTest {

    private lateinit var registry: PluginRegistry

    @BeforeEach
    fun setup() {
        registry = DefaultPluginRegistry()
    }

    private fun metadata(
        id: String,
        version: String = "1.0.0",
        dependencies: List<PluginDependency> = emptyList(),
        configuration: Map<String, Any?> = emptyMap()
    ) = PluginMetadata(
        id = id,
        name = "Plugin $id",
        version = version,
        provider = "test",
        capabilities = PluginCapabilities(features = setOf("feature-$id")),
        dependencies = dependencies,
        configuration = configuration
    )

    /** Records every lifecycle call in order; each phase's outcome is scriptable. */
    private open class RecordingLifecyclePlugin(
        private val initializeResult: () -> Boolean = { true },
        private val startResult: () -> Boolean = { true },
        private val stopResult: () -> Boolean = { true },
        private val cleanupAction: () -> Unit = {}
    ) : PluginLifecycle {
        val calls = mutableListOf<String>()
        var receivedConfig: Map<String, Any?>? = null

        override suspend fun initialize(config: Map<String, Any?>): Boolean {
            calls.add("initialize")
            receivedConfig = config
            return initializeResult()
        }

        override suspend fun start(): Boolean {
            calls.add("start")
            return startResult()
        }

        override suspend fun stop(): Boolean {
            calls.add("stop")
            return stopResult()
        }

        override suspend fun cleanup() {
            calls.add("cleanup")
            cleanupAction()
        }
    }

    // ========== register ==========

    @Test
    fun `register invokes initialize then start with the metadata configuration`() {
        val plugin = RecordingLifecyclePlugin()
        val config = mapOf<String, Any?>("url" to "jdbc:test")

        registry.register(metadata("lc-plugin", configuration = config), plugin)

        assertEquals(listOf("initialize", "start"), plugin.calls)
        assertEquals(config, plugin.receivedConfig)
        assertTrue(registry.isRegistered("lc-plugin"))
        assertSame(plugin, registry.getInstance<RecordingLifecyclePlugin>("lc-plugin"))
    }

    @Test
    fun `register does not touch instances without PluginLifecycle`() {
        // Plain instance: registration must succeed exactly as before.
        registry.register(metadata("plain"), Any())
        assertTrue(registry.isRegistered("plain"))
    }

    @Test
    fun `initialize returning false fails registration and plugin is not visible`() {
        val plugin = RecordingLifecyclePlugin(initializeResult = { false })
        val meta = metadata("bad-init")

        val ex = assertFailsWith<PluginException.InitializationFailed> {
            registry.register(meta, plugin)
        }
        assertEquals("bad-init", ex.pluginId)

        // Rolled back: nothing visible through any read surface.
        assertFalse(registry.isRegistered("bad-init"))
        assertNull(registry.getMetadata("bad-init"))
        assertNull(registry.getInstance<RecordingLifecyclePlugin>("bad-init"))
        assertTrue(registry.findByCapability("feature-bad-init").isEmpty())
        assertTrue(registry.findByProvider("test").isEmpty())
        assertTrue(registry.getAllPlugins().isEmpty())

        // start() must not run after a failed initialize().
        assertEquals(listOf("initialize"), plugin.calls)
    }

    @Test
    fun `initialize throwing fails registration with cause and plugin is not visible`() {
        val boom = IllegalStateException("connection refused")
        val plugin = RecordingLifecyclePlugin(initializeResult = { throw boom })

        val ex = assertFailsWith<PluginException.InitializationFailed> {
            registry.register(metadata("throwing-init"), plugin)
        }
        assertSame(boom, ex.cause)
        assertFalse(registry.isRegistered("throwing-init"))
        assertNull(registry.getInstance<RecordingLifecyclePlugin>("throwing-init"))
    }

    @Test
    fun `start returning false fails registration and rolls back`() {
        val plugin = RecordingLifecyclePlugin(startResult = { false })

        assertFailsWith<PluginException.InitializationFailed> {
            registry.register(metadata("bad-start"), plugin)
        }
        assertEquals(listOf("initialize", "start"), plugin.calls)
        assertFalse(registry.isRegistered("bad-start"))
        assertNull(registry.getMetadata("bad-start"))
    }

    @Test
    fun `failed registration can be retried`() {
        var failFirst = true
        val plugin = RecordingLifecyclePlugin(initializeResult = {
            if (failFirst) {
                failFirst = false
                false
            } else {
                true
            }
        })
        val meta = metadata("retry")

        assertFailsWith<PluginException.InitializationFailed> { registry.register(meta, plugin) }
        // Rollback must leave no AlreadyRegistered residue.
        registry.register(meta, plugin)
        assertTrue(registry.isRegistered("retry"))
    }

    // ========== unregister / clear ==========

    @Test
    fun `unregister invokes stop then cleanup after retraction`() {
        val plugin = RecordingLifecyclePlugin()
        registry.register(metadata("lc-plugin"), plugin)

        registry.unregister("lc-plugin")

        assertEquals(listOf("initialize", "start", "stop", "cleanup"), plugin.calls)
        assertFalse(registry.isRegistered("lc-plugin"))
        assertNull(registry.getInstance<RecordingLifecyclePlugin>("lc-plugin"))
    }

    @Test
    fun `stop throwing does not break unregistration and cleanup still runs`() {
        val plugin = RecordingLifecyclePlugin(stopResult = { throw IllegalStateException("stop failed") })
        registry.register(metadata("bad-stop"), plugin)

        registry.unregister("bad-stop") // must not throw

        assertEquals(listOf("initialize", "start", "stop", "cleanup"), plugin.calls)
        assertFalse(registry.isRegistered("bad-stop"))
    }

    @Test
    fun `cleanup throwing does not break unregistration`() {
        val plugin = RecordingLifecyclePlugin(cleanupAction = { throw IllegalStateException("cleanup failed") })
        registry.register(metadata("bad-cleanup"), plugin)

        registry.unregister("bad-cleanup") // must not throw

        assertFalse(registry.isRegistered("bad-cleanup"))
        assertEquals(listOf("initialize", "start", "stop", "cleanup"), plugin.calls)
    }

    @Test
    fun `clear tears down every lifecycle plugin`() {
        val pluginA = RecordingLifecyclePlugin()
        val pluginB = RecordingLifecyclePlugin(stopResult = { throw IllegalStateException("boom") })
        registry.register(metadata("a"), pluginA)
        registry.register(metadata("b"), pluginB)
        registry.register(metadata("plain"), Any())

        registry.clear() // must not throw despite pluginB's stop() failure

        assertEquals(listOf("initialize", "start", "stop", "cleanup"), pluginA.calls)
        assertEquals(listOf("initialize", "start", "stop", "cleanup"), pluginB.calls)
        assertTrue(registry.getAllPlugins().isEmpty())
    }

    // ========== dependency version ranges ==========

    @Test
    fun `register succeeds when dependency version is within range`() {
        registry.register(metadata("dep", version = "1.5.0"), Any())

        registry.register(
            metadata(
                "consumer",
                dependencies = listOf(PluginDependency("dep", versionRange = ">=1.0.0,<2.0.0"))
            ),
            Any()
        )
        assertTrue(registry.isRegistered("consumer"))
    }

    @Test
    fun `register fails when required dependency version is out of range`() {
        registry.register(metadata("dep", version = "2.1.0"), Any())

        val ex = assertFailsWith<PluginException.DependencyVersionMismatch> {
            registry.register(
                metadata(
                    "consumer",
                    dependencies = listOf(PluginDependency("dep", versionRange = ">=1.0.0,<2.0.0"))
                ),
                Any()
            )
        }
        assertEquals("consumer", ex.pluginId)
        assertEquals("dep", ex.dependencyId)
        assertEquals("2.1.0", ex.actualVersion)
        assertFalse(registry.isRegistered("consumer"))
    }

    @Test
    fun `optional dependency out of range only warns`() {
        registry.register(metadata("dep", version = "3.0.0"), Any())

        registry.register(
            metadata(
                "consumer",
                dependencies = listOf(
                    PluginDependency("dep", versionRange = "<2.0.0", isOptional = true)
                )
            ),
            Any()
        )
        assertTrue(registry.isRegistered("consumer"))
    }

    @Test
    fun `dependency not yet registered is not checked`() {
        // The model has no resolution phase; registration order must stay unconstrained.
        registry.register(
            metadata(
                "consumer",
                dependencies = listOf(PluginDependency("absent", versionRange = ">=1.0.0"))
            ),
            Any()
        )
        assertTrue(registry.isRegistered("consumer"))
    }

    @Test
    fun `unparseable version range is skipped not enforced`() {
        registry.register(metadata("dep", version = "1.0.0"), Any())

        registry.register(
            metadata(
                "consumer",
                dependencies = listOf(PluginDependency("dep", versionRange = "~> nonsense"))
            ),
            Any()
        )
        assertTrue(registry.isRegistered("consumer"))
    }

    @Test
    fun `exact version constraint without operator`() {
        registry.register(metadata("dep", version = "1.2.0"), Any())

        // "1.2" == "1.2.0" (missing segments compare as zero)
        registry.register(
            metadata("ok", dependencies = listOf(PluginDependency("dep", versionRange = "1.2"))),
            Any()
        )
        assertTrue(registry.isRegistered("ok"))

        assertFailsWith<PluginException.DependencyVersionMismatch> {
            registry.register(
                metadata("bad", dependencies = listOf(PluginDependency("dep", versionRange = "1.3.0"))),
                Any()
            )
        }
    }
}
