package org.trustweave.core.plugin

import org.trustweave.core.exception.PluginException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PluginVersionConstraintTest {
    @Test
    fun `unsupported constraints take precedence over mismatches in every position`() {
        listOf("unsupported", "^1.0.0", ">=1..0", "<2147483648", ">=1.bad").forEach { unsupported ->
            assertNull(PluginVersions.satisfies("1.0.0", ">=2.0.0,$unsupported"), unsupported)
            assertNull(PluginVersions.satisfies("1.0.0", "$unsupported,>=2.0.0"), unsupported)
            assertNull(PluginVersions.satisfies("1.0.0", ">=2.0.0,$unsupported,<3.0.0"), unsupported)
        }
    }

    @Test
    fun `valid constraint results are independent of order`() {
        listOf(
            listOf(">=1.0.0", "<2.0.0") to true,
            listOf(">=2.0.0", "<3.0.0") to false,
            listOf(">=1.0.0", "<1.0.0") to false,
        ).forEach { (constraints, expected) ->
            assertEquals(expected, PluginVersions.satisfies("1.2.0", constraints.joinToString(",")))
            assertEquals(expected, PluginVersions.satisfies("1.2.0", constraints.reversed().joinToString(",")))
        }
    }

    @Test
    fun `comparison operators preserve inclusive and exclusive boundaries`() {
        mapOf(
            ">=1.2" to true,
            "<=1.2" to true,
            ">1.2" to false,
            "<1.2" to false,
            "=1.2" to true,
            "==1.2" to true,
            "1.2" to true,
            ">1.1" to true,
            "<1.3" to true,
            "=1.3" to false,
            ">=1.3" to false,
            "<=1.1" to false,
        ).forEach { (range, expected) ->
            assertEquals(expected, PluginVersions.satisfies("1.2.0", range), range)
        }
    }

    @Test
    fun `documented version normalization and unknown input behavior are preserved`() {
        assertEquals(true, PluginVersions.satisfies("1.2-beta+build", "==1.2.0"))
        assertEquals(true, PluginVersions.satisfies("1.2.0", "  >= 1.2 , < 2.0 "))
        assertNull(PluginVersions.satisfies("not-a-version", ">=1.0.0"))
        assertNull(PluginVersions.satisfies("1.2.0", ""))
        assertNull(PluginVersions.satisfies("1.2.0", ", ,"))
    }

    private fun metadata(
        id: String,
        dependencies: List<PluginDependency> = emptyList(),
    ) = PluginMetadata(
        id = id,
        name = id,
        version = "1.0.0",
        provider = "test",
        capabilities = PluginCapabilities(),
        dependencies = dependencies,
    )

    @Test
    fun `registry applies its unsupported range policy consistently for all constraint orders`() {
        listOf(">=2.0.0,unsupported", "unsupported,>=2.0.0").forEach { range ->
            val registry = DefaultPluginRegistry()
            registry.register(metadata("dependency"), Any())
            registry.register(metadata("consumer", listOf(PluginDependency("dependency", range))), Any())
            assertTrue(registry.isRegistered("consumer"), range)
        }
    }

    @Test
    fun `registry still rejects valid ranges when a required constraint fails`() {
        listOf(">=2.0.0,<3.0.0", "<3.0.0,>=2.0.0").forEach { range ->
            val registry = DefaultPluginRegistry()
            registry.register(metadata("dependency"), Any())
            assertFailsWith<PluginException.DependencyVersionMismatch> {
                registry.register(metadata("consumer", listOf(PluginDependency("dependency", range))), Any())
            }
            assertFalse(registry.isRegistered("consumer"))
        }
    }
}
