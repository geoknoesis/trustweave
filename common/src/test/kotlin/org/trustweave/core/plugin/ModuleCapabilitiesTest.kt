package org.trustweave.core.plugin

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ModuleCapabilitiesTest {
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
