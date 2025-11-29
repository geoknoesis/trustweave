package com.trustweave.core.exception

/**
 * Base sealed exception hierarchy for all TrustWeave operations.
 *
 * All TrustWeave exceptions provide structured error codes and context
 * for better error handling and debugging. Exceptions are organized by
 * naming convention (Plugin*, Provider*, Config*, etc.) for clarity.
 *
 * This sealed class ensures exhaustive handling in when expressions.
 */
open class TrustWeaveException(
    open val code: String,
    override val message: String,
    open val context: Map<String, Any?> = emptyMap(),
    override val cause: Throwable? = null
) : Exception(message, cause) {

    // ============================================================================
    // Plugin-related exceptions
    // ============================================================================

    data class PluginNotFound(
        val pluginId: String,
        val pluginType: String? = null
    ) : TrustWeaveException(
        code = "PLUGIN_NOT_FOUND",
        message = pluginType?.let { "Plugin '$pluginId' of type '$it' not found" }
            ?: "Plugin '$pluginId' not found",
        context = mapOf(
            "pluginId" to pluginId,
            "pluginType" to pluginType
        ).filterValues { it != null }
    )

    data class PluginInitializationFailed(
        val pluginId: String,
        val reason: String
    ) : TrustWeaveException(
        code = "PLUGIN_INITIALIZATION_FAILED",
        message = "Plugin '$pluginId' failed to initialize: $reason",
        context = mapOf(
            "pluginId" to pluginId,
            "reason" to reason
        )
    )

    class BlankPluginId : TrustWeaveException(
        code = "BLANK_PLUGIN_ID",
        message = "Plugin ID cannot be blank",
        context = emptyMap()
    )

    data class PluginAlreadyRegistered(
        val pluginId: String,
        val existingPlugin: String? = null
    ) : TrustWeaveException(
        code = "PLUGIN_ALREADY_REGISTERED",
        message = "Plugin with ID '$pluginId' is already registered",
        context = mapOf(
            "pluginId" to pluginId,
            "existingPlugin" to existingPlugin
        ).filterValues { it != null }
    )

    // ============================================================================
    // Provider-related exceptions
    // ============================================================================

    data class NoProvidersFound(
        val pluginIds: List<String>,
        val availablePlugins: List<String> = emptyList()
    ) : TrustWeaveException(
        code = "NO_PROVIDERS_FOUND",
        message = "No providers found for plugin IDs: ${pluginIds.joinToString(", ")}",
        context = mapOf(
            "pluginIds" to pluginIds,
            "availablePlugins" to availablePlugins
        )
    )

    data class PartialProvidersFound(
        val requestedIds: List<String>,
        val foundIds: List<String>,
        val missingIds: List<String>
    ) : TrustWeaveException(
        code = "PARTIAL_PROVIDERS_FOUND",
        message = "Only ${foundIds.size} of ${requestedIds.size} providers found. Missing: ${missingIds.joinToString(", ")}",
        context = mapOf(
            "requestedIds" to requestedIds,
            "foundIds" to foundIds,
            "missingIds" to missingIds
        )
    )

    data class AllProvidersFailed(
        val attemptedProviders: List<String>,
        val providerErrors: Map<String, String> = emptyMap(),
        val lastException: Throwable? = null
    ) : TrustWeaveException(
        code = "ALL_PROVIDERS_FAILED",
        message = "All providers in chain failed. Attempted: ${attemptedProviders.joinToString(", ")}",
        context = mapOf(
            "attemptedProviders" to attemptedProviders,
            "providerErrors" to providerErrors
        ),
        cause = lastException
    )

    // ============================================================================
    // Configuration exceptions
    // ============================================================================

    data class ConfigNotFound(
        val path: String
    ) : TrustWeaveException(
        code = "CONFIG_NOT_FOUND",
        message = "Configuration file not found: $path",
        context = mapOf("path" to path)
    )

    data class ConfigReadFailed(
        val path: String,
        val reason: String
    ) : TrustWeaveException(
        code = "CONFIG_READ_FAILED",
        message = "Failed to read configuration file '$path': $reason",
        context = mapOf(
            "path" to path,
            "reason" to reason
        )
    )

    data class InvalidConfigFormat(
        val jsonString: String? = null,
        val parseError: String,
        val field: String? = null
    ) : TrustWeaveException(
        code = "INVALID_CONFIG_FORMAT",
        message = field?.let { "Invalid configuration format in field '$it': $parseError" }
            ?: "Invalid configuration format: $parseError",
        context = mapOf(
            "parseError" to parseError,
            "field" to field
        ).filterValues { it != null } + (jsonString?.let { mapOf("jsonString" to it) } ?: emptyMap())
    )

    // ============================================================================
    // JSON-related exceptions
    // ============================================================================

    data class InvalidJson(
        val jsonString: String? = null,
        val parseError: String,
        val position: String? = null
    ) : TrustWeaveException(
        code = "INVALID_JSON",
        message = "Invalid JSON: $parseError${position?.let { " at $it" } ?: ""}",
        context = mapOf(
            "parseError" to parseError,
            "position" to position
        ).filterValues { it != null } + (jsonString?.let { mapOf("jsonString" to it.take(500)) } ?: emptyMap())
    )

    data class JsonEncodeFailed(
        val element: String? = null,
        val reason: String
    ) : TrustWeaveException(
        code = "JSON_ENCODE_FAILED",
        message = "Failed to encode JSON: $reason",
        context = mapOf(
            "reason" to reason,
            "element" to element
        ).filterValues { it != null }
    )

    // ============================================================================
    // Digest-related exceptions
    // ============================================================================

    data class DigestFailed(
        val algorithm: String,
        val reason: String
    ) : TrustWeaveException(
        code = "DIGEST_FAILED",
        message = "Digest computation failed for algorithm '$algorithm': $reason",
        context = mapOf(
            "algorithm" to algorithm,
            "reason" to reason
        )
    )

    // ============================================================================
    // Encoding-related exceptions
    // ============================================================================

    data class EncodeFailed(
        val operation: String,
        val reason: String
    ) : TrustWeaveException(
        code = "ENCODE_FAILED",
        message = "Encoding failed for operation '$operation': $reason",
        context = mapOf(
            "operation" to operation,
            "reason" to reason
        )
    )

    // ============================================================================
    // Validation exceptions
    // ============================================================================

    data class ValidationFailed(
        val field: String,
        val reason: String,
        val value: Any? = null
    ) : TrustWeaveException(
        code = "VALIDATION_FAILED",
        message = "Validation failed for field '$field': $reason",
        context = mapOf(
            "field" to field,
            "reason" to reason,
            "value" to value
        )
    )

    // ============================================================================
    // Generic exceptions
    // ============================================================================

    data class InvalidOperation(
        override val code: String = "INVALID_OPERATION",
        override val message: String,
        override val context: Map<String, Any?> = emptyMap(),
        override val cause: Throwable? = null
    ) : TrustWeaveException(code, message, context, cause)

    data class InvalidState(
        override val code: String = "INVALID_STATE",
        override val message: String,
        override val context: Map<String, Any?> = emptyMap(),
        override val cause: Throwable? = null
    ) : TrustWeaveException(code, message, context, cause)

    data class NotFound(
        val resource: String? = null,
        override val message: String = resource?.let { "Resource not found: $it" } ?: "Resource not found",
        override val context: Map<String, Any?> = emptyMap(),
        override val cause: Throwable? = null
    ) : TrustWeaveException(
        code = "NOT_FOUND",
        message = message,
        context = context + (resource?.let { mapOf("resource" to it) } ?: emptyMap()),
        cause = cause
    )

    data class UnsupportedAlgorithm(
        val algorithm: String,
        val supportedAlgorithms: List<String>
    ) : TrustWeaveException(
        code = "UNSUPPORTED_ALGORITHM",
        message = "Algorithm '$algorithm' is not supported. Supported algorithms: ${supportedAlgorithms.joinToString(", ")}",
        context = mapOf(
            "algorithm" to algorithm,
            "supportedAlgorithms" to supportedAlgorithms
        )
    )

    data class Unknown(
        override val code: String = "UNKNOWN_ERROR",
        override val message: String,
        override val context: Map<String, Any?> = emptyMap(),
        override val cause: Throwable? = null
    ) : TrustWeaveException(code, message, context, cause)
}

/**
 * Extension function to convert any Throwable to a TrustWeaveException.
 *
 * This function provides automatic conversion of standard exceptions
 * to structured TrustWeaveException types for consistent error handling.
 */
fun Throwable.toTrustWeaveException(): TrustWeaveException = when (this) {
    is TrustWeaveException -> this
    is IllegalArgumentException -> TrustWeaveException.InvalidOperation(
        code = "INVALID_ARGUMENT",
        message = message ?: "Invalid argument",
        context = emptyMap(),
        cause = this
    )
    is IllegalStateException -> TrustWeaveException.InvalidState(
        code = "INVALID_STATE",
        message = message ?: "Invalid state",
        context = emptyMap(),
        cause = this
    )
    else -> TrustWeaveException.Unknown(
        code = "UNKNOWN_ERROR",
        message = message ?: "Unknown error: ${this::class.simpleName}",
        context = emptyMap(),
        cause = this
    )
}

/**
 * Helper function to create a TrustWeaveException from a simple message.
 *
 * This is a convenience function for cases where you need a generic exception
 * but don't have specific context. Prefer using specific exception types when possible.
 *
 * @param message The error message
 * @param cause Optional underlying exception
 * @return TrustWeaveException.Unknown with the provided message
 */
fun trustWeaveException(message: String, cause: Throwable? = null): TrustWeaveException {
    return TrustWeaveException.Unknown(
        message = message,
        context = emptyMap(),
        cause = cause
    )
}

/**
 * Helper functions for category-based exception handling.
 */
object TrustWeaveExceptionHelpers {
    /**
     * Checks if the exception is plugin-related.
     */
    fun TrustWeaveException.isPluginException(): Boolean = when (this) {
        is TrustWeaveException.PluginNotFound,
        is TrustWeaveException.PluginInitializationFailed,
        is TrustWeaveException.BlankPluginId,
        is TrustWeaveException.PluginAlreadyRegistered -> true
        else -> false
    }

    /**
     * Checks if the exception is provider-related.
     */
    fun TrustWeaveException.isProviderException(): Boolean = when (this) {
        is TrustWeaveException.NoProvidersFound,
        is TrustWeaveException.PartialProvidersFound,
        is TrustWeaveException.AllProvidersFailed -> true
        else -> false
    }

    /**
     * Checks if the exception is configuration-related.
     */
    fun TrustWeaveException.isConfigException(): Boolean = when (this) {
        is TrustWeaveException.ConfigNotFound,
        is TrustWeaveException.ConfigReadFailed,
        is TrustWeaveException.InvalidConfigFormat -> true
        else -> false
    }

    /**
     * Checks if the exception is JSON-related.
     */
    fun TrustWeaveException.isJsonException(): Boolean = when (this) {
        is TrustWeaveException.InvalidJson,
        is TrustWeaveException.JsonEncodeFailed -> true
        else -> false
    }

    /**
     * Checks if the exception is validation-related.
     */
    fun TrustWeaveException.isValidationException(): Boolean = when (this) {
        is TrustWeaveException.ValidationFailed -> true
        else -> false
    }
}
