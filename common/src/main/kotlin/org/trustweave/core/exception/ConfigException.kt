package org.trustweave.core.exception

/**
 * Sealed hierarchy for configuration-related exceptions.
 *
 * Extracted from [TrustWeaveException] to give config-related errors a focused home.
 */
sealed class ConfigException(
    code: String,
    message: String,
    context: Map<String, Any?> = emptyMap(),
    cause: Throwable? = null
) : TrustWeaveException(code, message, context, cause) {

    data class NotFound(
        val path: String
    ) : ConfigException(
        code = "CONFIG_NOT_FOUND",
        message = "Configuration file not found: $path",
        context = mapOf("path" to path)
    )

    data class ReadFailed(
        val path: String,
        val reason: String
    ) : ConfigException(
        code = "CONFIG_READ_FAILED",
        message = "Failed to read configuration file '$path': $reason",
        context = mapOf(
            "path" to path,
            "reason" to reason
        )
    )

    data class InvalidFormat(
        val jsonString: String? = null,
        val parseError: String,
        val field: String? = null
    ) : ConfigException(
        code = "INVALID_CONFIG_FORMAT",
        message = field?.let { "Invalid configuration format in field '$it': $parseError" }
            ?: "Invalid configuration format: $parseError",
        context = mapOf(
            "parseError" to parseError,
            "field" to field
        ).filterValues { it != null } + (jsonString?.let { mapOf("jsonString" to it) } ?: emptyMap())
    )
}
