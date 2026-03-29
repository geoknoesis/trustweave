package org.trustweave.core.exception

/**
 * Sealed hierarchy for plugin lifecycle exceptions.
 *
 * Extracted from [TrustWeaveException] to give plugin-related errors a focused home.
 * All subtypes extend [TrustWeaveException] so they remain compatible with existing
 * catch blocks that catch the base type.
 */
sealed class PluginException(
    code: String,
    message: String,
    context: Map<String, Any?> = emptyMap(),
    cause: Throwable? = null
) : TrustWeaveException(code, message, context, cause) {

    data class NotFound(
        val pluginId: String,
        val pluginType: String? = null
    ) : PluginException(
        code = "PLUGIN_NOT_FOUND",
        message = pluginType?.let { "Plugin '$pluginId' of type '$it' not found" }
            ?: "Plugin '$pluginId' not found",
        context = mapOf(
            "pluginId" to pluginId,
            "pluginType" to pluginType
        ).filterValues { it != null }
    )

    data class InitializationFailed(
        val pluginId: String,
        val reason: String
    ) : PluginException(
        code = "PLUGIN_INITIALIZATION_FAILED",
        message = "Plugin '$pluginId' failed to initialize: $reason",
        context = mapOf(
            "pluginId" to pluginId,
            "reason" to reason
        )
    )

    data class AlreadyRegistered(
        val pluginId: String,
        val existingPlugin: String? = null
    ) : PluginException(
        code = "PLUGIN_ALREADY_REGISTERED",
        message = "Plugin with ID '$pluginId' is already registered",
        context = mapOf(
            "pluginId" to pluginId,
            "existingPlugin" to existingPlugin
        ).filterValues { it != null }
    )

    /**
     * Singleton representing a blank/empty plugin ID — has no state.
     */
    object BlankId : PluginException(
        code = "BLANK_PLUGIN_ID",
        message = "Plugin ID cannot be blank"
    )
}
