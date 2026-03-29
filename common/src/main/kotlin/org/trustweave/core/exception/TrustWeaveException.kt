package org.trustweave.core.exception

/**
 * Base exception hierarchy for all TrustWeave operations.
 *
 * All TrustWeave exceptions provide structured error codes and context
 * for better error handling and debugging.
 *
 * **Design Note:** This is an `open class` to allow domain-specific exception
 * hierarchies (DidException, WalletException, BlockchainException, etc.) in
 * other modules to extend it. Domain-specific exception classes are typically
 * `sealed` for exhaustive handling within their respective domains.
 *
 * **Exception Hierarchy:**
 * - [PluginException] — plugin lifecycle errors (not found, init failed, etc.)
 * - [ProviderException] — provider chain errors (none found, partial, all failed)
 * - [ConfigException] — configuration errors (not found, read failed, invalid format)
 * - [SerializationException] — JSON / serialization errors
 * - Core generic exceptions (in this class) — validation, encoding, digest, etc.
 * - Domain exceptions (in other modules) — DidException, WalletException, etc.
 */
open class TrustWeaveException(
    open val code: String,
    override val message: String,
    /**
     * Context map containing additional error information.
     *
     * **Null Handling:** Null values in the context map are typically filtered out
     * using `.filterValues { it != null }` to keep the context map clean and avoid
     * unnecessary null entries. However, some exceptions may intentionally include
     * null values if they represent meaningful state (e.g., optional fields that
     * were explicitly set to null vs. not provided).
     */
    open val context: Map<String, Any?> = emptyMap(),
    override val cause: Throwable? = null
) : Exception(message, cause) {

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
        private val baseContext: Map<String, Any?> = emptyMap(),
        override val cause: Throwable? = null
    ) : TrustWeaveException(
        code = "NOT_FOUND",
        message = message,
        context = buildMap {
            putAll(baseContext)  // Preserve any custom context passed
            resource?.let { put("resource", it) }  // Add resource if present
        },
        cause = cause
    ) {
        // Override context to return the computed value from parent
        override val context: Map<String, Any?>
            get() = super.context
    }

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
 * Checks if the exception is plugin-related (i.e., an instance of [PluginException]).
 */
fun TrustWeaveException.isPluginException(): Boolean = this is PluginException

/**
 * Checks if the exception is provider-related (i.e., an instance of [ProviderException]).
 */
fun TrustWeaveException.isProviderException(): Boolean = this is ProviderException

/**
 * Checks if the exception is configuration-related (i.e., an instance of [ConfigException]).
 */
fun TrustWeaveException.isConfigException(): Boolean = this is ConfigException

/**
 * Checks if the exception is serialization/JSON-related (i.e., an instance of [SerializationException]).
 */
fun TrustWeaveException.isJsonException(): Boolean = this is SerializationException

/**
 * Checks if the exception is validation-related.
 */
fun TrustWeaveException.isValidationException(): Boolean = this is TrustWeaveException.ValidationFailed

/**
 * Checks if the exception is encoding/digest-related.
 */
fun TrustWeaveException.isEncodingException(): Boolean =
    this is TrustWeaveException.DigestFailed || this is TrustWeaveException.EncodeFailed
