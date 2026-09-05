package org.trustweave.core.plugin

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** Explicitly assessed capabilities; absence means unknown, never presumed supported. */
object ModuleCapabilities {
    data class Capability(
        val maturity: String,
        val operations: Set<String>,
        val formats: Set<String>,
    )

    private val catalog: Map<String, Capability> by lazy {
        val resource = requireNotNull(javaClass.getResourceAsStream("/trustweave-capabilities.json"))
        val root = resource.bufferedReader().use { Json.parseToJsonElement(it.readText()).jsonObject }
        root.mapValues { (_, entry) ->
            val value = entry.jsonObject
            Capability(
                value.getValue("maturity").jsonPrimitive.content,
                value
                    .getValue("operations")
                    .jsonArray
                    .map { it.jsonPrimitive.content }
                    .toSet(),
                value
                    .getValue("formats")
                    .jsonArray
                    .map { it.jsonPrimitive.content }
                    .toSet(),
            )
        }
    }

    fun get(module: String): Capability? = catalog[module]

    /** Construct runtime metadata from the same assessment used to generate documentation. */
    fun metadata(
        module: String,
        id: String,
        name: String,
        version: String,
        provider: String,
    ): PluginMetadata {
        val assessed = requireNotNull(get(module)) { "No assessed capabilities for $module" }
        return PluginMetadata(
            id = id,
            name = name,
            version = version,
            provider = provider,
            capabilities = PluginCapabilities(features = assessed.operations),
            maturity = PluginMaturity.valueOf(assessed.maturity.uppercase()),
            moduleId = module,
        )
    }

    fun requireFormats(
        module: String,
        required: Set<String>,
    ) {
        val assessed = requireNotNull(get(module)) { "No assessed capabilities for $module" }
        require(assessed.formats.containsAll(required)) { "$module does not support required formats $required" }
    }

    /** Call during startup, before constructing clients or issuing credentials. */
    fun requireOperations(
        module: String,
        required: Set<String>,
    ) {
        val capabilities = requireNotNull(get(module)) { "No assessed capabilities for $module" }
        require(capabilities.operations.containsAll(required)) {
            "$module (${capabilities.maturity}) does not support ${required - capabilities.operations}; supported: ${capabilities.operations}"
        }
    }
}
