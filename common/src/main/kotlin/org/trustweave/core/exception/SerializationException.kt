package org.trustweave.core.exception

/**
 * Sealed hierarchy for serialization and JSON-related exceptions.
 *
 * Extracted from [TrustWeaveException] to give serialization errors a focused home.
 * Named `TrustWeaveSerializationException` internally to avoid clashing with
 * `kotlinx.serialization.SerializationException` on the classpath.
 */
sealed class SerializationException(
    code: String,
    message: String,
    context: Map<String, Any?> = emptyMap(),
    cause: Throwable? = null
) : TrustWeaveException(code, message, context, cause) {

    data class InvalidJson(
        val jsonString: String? = null,
        val parseError: String,
        val position: String? = null
    ) : SerializationException(
        code = "INVALID_JSON",
        message = "Invalid JSON: $parseError${position?.let { " at $it" } ?: ""}",
        context = mapOf(
            "parseError" to parseError,
            "position" to position
        ).filterValues { it != null } + (jsonString?.let { mapOf("jsonString" to it.take(500)) } ?: emptyMap())
    )

    data class EncodeFailed(
        val element: String? = null,
        val reason: String
    ) : SerializationException(
        code = "JSON_ENCODE_FAILED",
        message = "Failed to encode JSON: $reason",
        context = mapOf(
            "reason" to reason,
            "element" to element
        ).filterValues { it != null }
    )
}
