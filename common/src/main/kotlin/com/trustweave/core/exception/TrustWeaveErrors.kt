package com.trustweave.core.exception

/**
 * Enhanced error types for TrustWeave API operations.
 * 
 * These errors provide structured error codes and context for better error handling
 * and debugging. All errors extend TrustWeaveException.
 */

/**
 * Sealed hierarchy for structured API errors with context.
 * 
 * This class contains only generic, domain-agnostic errors.
 * Domain-specific errors (DID, Credential, Blockchain, Wallet) are in their respective modules.
 */
sealed class TrustWeaveError(
    open val code: String,
    override val message: String,
    open val context: Map<String, Any?> = emptyMap(),
    override val cause: Throwable? = null
) : TrustWeaveException(message, cause) {
    
    // Key Management errors (generic, could be used by KMS)
    data class UnsupportedAlgorithm(
        val algorithm: String,
        val supportedAlgorithms: List<String>
    ) : TrustWeaveError(
        code = "UNSUPPORTED_ALGORITHM",
        message = "Algorithm '$algorithm' is not supported. Supported algorithms: ${supportedAlgorithms.joinToString(", ")}",
        context = mapOf(
            "algorithm" to algorithm,
            "supportedAlgorithms" to supportedAlgorithms
        )
    )
    
    // Plugin-related errors
    data class PluginNotFound(
        val pluginId: String,
        val pluginType: String? = null
    ) : TrustWeaveError(
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
    ) : TrustWeaveError(
        code = "PLUGIN_INITIALIZATION_FAILED",
        message = "Plugin '$pluginId' failed to initialize: $reason",
        context = mapOf(
            "pluginId" to pluginId,
            "reason" to reason
        )
    )
    
    class BlankPluginId : TrustWeaveError(
        code = "BLANK_PLUGIN_ID",
        message = "Plugin ID cannot be blank",
        context = emptyMap()
    )
    
    data class PluginAlreadyRegistered(
        val pluginId: String,
        val existingPlugin: String? = null
    ) : TrustWeaveError(
        code = "PLUGIN_ALREADY_REGISTERED",
        message = "Plugin with ID '$pluginId' is already registered",
        context = mapOf(
            "pluginId" to pluginId,
            "existingPlugin" to existingPlugin
        ).filterValues { it != null }
    )
    
    data class NoProvidersFound(
        val pluginIds: List<String>,
        val availablePlugins: List<String> = emptyList()
    ) : TrustWeaveError(
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
    ) : TrustWeaveError(
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
    ) : TrustWeaveError(
        code = "ALL_PROVIDERS_FAILED",
        message = "All providers in chain failed. Attempted: ${attemptedProviders.joinToString(", ")}",
        context = mapOf(
            "attemptedProviders" to attemptedProviders,
            "providerErrors" to providerErrors
        ),
        cause = lastException
    )
    
    // Configuration errors
    data class ConfigNotFound(
        val path: String
    ) : TrustWeaveError(
        code = "CONFIG_NOT_FOUND",
        message = "Configuration file not found: $path",
        context = mapOf("path" to path)
    )
    
    data class ConfigReadFailed(
        val path: String,
        val reason: String
    ) : TrustWeaveError(
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
    ) : TrustWeaveError(
        code = "INVALID_CONFIG_FORMAT",
        message = field?.let { "Invalid configuration format in field '$it': $parseError" }
            ?: "Invalid configuration format: $parseError",
        context = mapOf(
            "parseError" to parseError,
            "field" to field
        ).filterValues { it != null } + (jsonString?.let { mapOf("jsonString" to it) } ?: emptyMap())
    )
    
    // JSON/Digest errors
    data class InvalidJson(
        val jsonString: String? = null,
        val parseError: String,
        val position: String? = null
    ) : TrustWeaveError(
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
    ) : TrustWeaveError(
        code = "JSON_ENCODE_FAILED",
        message = "Failed to encode JSON: $reason",
        context = mapOf(
            "reason" to reason,
            "element" to element
        ).filterValues { it != null }
    )
    
    data class DigestFailed(
        val algorithm: String,
        val reason: String
    ) : TrustWeaveError(
        code = "DIGEST_FAILED",
        message = "Digest computation failed for algorithm '$algorithm': $reason",
        context = mapOf(
            "algorithm" to algorithm,
            "reason" to reason
        )
    )
    
    data class EncodeFailed(
        val operation: String,
        val reason: String
    ) : TrustWeaveError(
        code = "ENCODE_FAILED",
        message = "Encoding failed for operation '$operation': $reason",
        context = mapOf(
            "operation" to operation,
            "reason" to reason
        )
    )
    
    // Validation errors
    data class ValidationFailed(
        val field: String,
        val reason: String,
        val value: Any? = null
    ) : TrustWeaveError(
        code = "VALIDATION_FAILED",
        message = "Validation failed for field '$field': $reason",
        context = mapOf(
            "field" to field,
            "reason" to reason,
            "value" to value
        )
    )
    
    // Generic errors
    data class InvalidOperation(
        override val code: String = "INVALID_OPERATION",
        override val message: String,
        override val context: Map<String, Any?> = emptyMap(),
        override val cause: Throwable? = null
    ) : TrustWeaveError(code, message, context, cause)
    
    data class InvalidState(
        override val code: String = "INVALID_STATE",
        override val message: String,
        override val context: Map<String, Any?> = emptyMap(),
        override val cause: Throwable? = null
    ) : TrustWeaveError(code, message, context, cause)
    
    data class Unknown(
        override val code: String = "UNKNOWN_ERROR",
        override val message: String,
        override val context: Map<String, Any?> = emptyMap(),
        override val cause: Throwable? = null
    ) : TrustWeaveError(code, message, context, cause)
}

/**
 * Extension function to convert any Throwable to a TrustWeaveError.
 */
fun Throwable.toTrustWeaveError(): TrustWeaveError = when (this) {
    is TrustWeaveError -> this
    is IllegalArgumentException -> TrustWeaveError.InvalidOperation(
        code = "INVALID_ARGUMENT",
        message = message ?: "Invalid argument",
        context = emptyMap(),
        cause = this
    )
    is IllegalStateException -> TrustWeaveError.InvalidState(
        code = "INVALID_STATE",
        message = message ?: "Invalid state",
        context = emptyMap(),
        cause = this
    )
    is NotFoundException -> TrustWeaveError.InvalidOperation(
        code = "NOT_FOUND",
        message = message ?: "Resource not found",
        context = emptyMap(),
        cause = this
    )
    is InvalidOperationException -> TrustWeaveError.InvalidOperation(
        code = "INVALID_OPERATION",
        message = message ?: "Invalid operation",
        context = emptyMap(),
        cause = this
    )
    else -> TrustWeaveError.Unknown(
        code = "UNKNOWN_ERROR",
        message = message ?: "Unknown error: ${this::class.simpleName}",
        context = emptyMap(),
        cause = this
    )
}

