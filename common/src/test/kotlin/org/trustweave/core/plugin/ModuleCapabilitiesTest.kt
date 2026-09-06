package org.trustweave.core.plugin

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ModuleCapabilitiesTest {
    @Test
    fun `production gate rejects experiments unknown providers and stubs even without requested operations`() {
        for (module in listOf("wallet:plugins:file", "unknown", "did:plugins:tezos")) {
            assertFailsWith<IllegalArgumentException> { ModuleCapabilities.requireDeployment(module, emptySet()) }
        }
        ModuleCapabilities.requireDeployment("wallet:plugins:file", setOf("store"), setOf("json-vc"), allowExperimental = true)
        for (module in listOf("unknown", "did:plugins:tezos")) {
            assertFailsWith<IllegalArgumentException> {
                ModuleCapabilities.requireDeployment(module, emptySet(), allowExperimental = true)
            }
        }
        assertFailsWith<IllegalArgumentException> {
            ModuleCapabilities.requireDeployment("wallet:plugins:file", setOf("page-records"), allowExperimental = true)
        }
        assertFailsWith<IllegalArgumentException> {
            ModuleCapabilities.requireDeployment("wallet:plugins:file", setOf("store"), setOf("mdoc"), allowExperimental = true)
        }
    }

    @Test
    fun `registration cannot promote maturity or evade a production policy`() {
        val assessed = ModuleCapabilities.metadata("wallet:plugins:file", "file", "File", "1.0.0", "native")
        val strict = DefaultPluginRegistry(requireSupportedProviders = true)
        assertFailsWith<IllegalArgumentException> { strict.register(assessed, Any()) }
        assertEquals(null, strict.getMetadata("file"))
        assertFailsWith<IllegalArgumentException> {
            strict.register(assessed.copy(moduleId = "unknown", maturity = PluginMaturity.SUPPORTED), Any())
        }
        assertFailsWith<IllegalArgumentException> {
            DefaultPluginRegistry().register(assessed.copy(maturity = PluginMaturity.SUPPORTED), Any())
        }
        DefaultPluginRegistry().register(assessed, Any())
    }

    @Test
    fun `runtime metadata comes from the catalog and application requirements fail closed`() {
        val metadata = ModuleCapabilities.metadata("wallet:plugins:file", "file", "File", "1.0.0", "native")
        val registry = DefaultPluginRegistry(mapOf("file" to setOf("page-records")))
        assertEquals(ModuleCapabilities.get("wallet:plugins:file")!!.operations, metadata.capabilities.features)
        assertFailsWith<IllegalArgumentException> { registry.register(metadata, Any()) }
        assertFailsWith<IllegalArgumentException> { ModuleCapabilities.requireFormats("wallet:plugins:file", setOf("mdoc")) }
        val stub = ModuleCapabilities.metadata("did:plugins:tezos", "tezos", "Tezos", "1.0.0", "native")
        assertFailsWith<IllegalArgumentException> { DefaultPluginRegistry().register(stub, Any()) }
    }

    @Test
    fun `stub and unknown capabilities fail before a workflow starts`() {
        assertFailsWith<IllegalArgumentException> { ModuleCapabilities.requireOperations("anchors:plugins:starknet", setOf("anchor")) }
        assertFailsWith<IllegalArgumentException> { ModuleCapabilities.requireOperations("unknown", setOf("receive")) }
        ModuleCapabilities.requireOperations("credentials:plugins:oidc4vci", setOf("receive"))
        assertEquals(setOf("ldp_vc"), ModuleCapabilities.get("credentials:plugins:oidc4vci")!!.formats)
    }
}
