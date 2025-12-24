package org.trustweave.did.validation

import org.trustweave.core.util.ValidationResult

/**
 * DID format and method validation.
 */
object DidValidator {
    /**
     * Error codes for DID validation.
     */
    object ErrorCodes {
        const val DID_EMPTY = "DID_EMPTY"
        const val INVALID_DID_FORMAT = "INVALID_DID_FORMAT"
        const val DID_METHOD_EXTRACTION_FAILED = "DID_METHOD_EXTRACTION_FAILED"
        const val UNSUPPORTED_DID_METHOD = "UNSUPPORTED_DID_METHOD"
    }

    /**
     * DID pattern matching the DID specification.
     * Format: did:<method>:<method-specific-id>
     */
    private val DID_PATTERN = Regex("^did:[a-z0-9]+:[a-zA-Z0-9._:%-]+$")

    /**
     * Validates DID format.
     *
     * @param did The DID string to validate
     * @return ValidationResult indicating if the DID format is valid
     */
    fun validateFormat(did: String): ValidationResult {
        if (did.isBlank()) {
            return ValidationResult.Invalid(
                code = ErrorCodes.DID_EMPTY,
                message = "DID cannot be empty",
                field = "did",
                value = did
            )
        }

        if (!DID_PATTERN.matches(did)) {
            return ValidationResult.Invalid(
                code = ErrorCodes.INVALID_DID_FORMAT,
                message = "DID must match format: did:<method>:<method-specific-id>",
                field = "did",
                value = did
            )
        }

        return ValidationResult.Valid
    }

    /**
     * Validates that the DID method is supported.
     *
     * @param did The DID string to validate
     * @param availableMethods List of available DID method names
     * @return ValidationResult indicating if the DID method is supported
     */
    fun validateMethod(did: String, availableMethods: List<String>): ValidationResult {
        val formatResult = validateFormat(did)
        if (!formatResult.isValid()) {
            return formatResult
        }

        val method = extractMethod(did)
        if (method == null) {
            return ValidationResult.Invalid(
                code = ErrorCodes.DID_METHOD_EXTRACTION_FAILED,
                message = "Failed to extract method from DID: $did",
                field = "did.method",
                value = did
            )
        }

        if (method !in availableMethods) {
            return ValidationResult.Invalid(
                code = ErrorCodes.UNSUPPORTED_DID_METHOD,
                message = "DID method '$method' is not supported. Available methods: $availableMethods",
                field = "did.method",
                value = method
            )
        }

        return ValidationResult.Valid
    }

    /**
     * Extracts the method name from a DID string.
     *
     * @param did The DID string
     * @return The method name, or null if extraction fails
     */
    fun extractMethod(did: String): String? {
        if (did.isBlank()) return null
        return did.split(":").getOrNull(1)
    }

    /**
     * Extracts the method-specific identifier from a DID string.
     *
     * @param did The DID string
     * @return The method-specific identifier, or null if extraction fails
     */
    fun extractMethodSpecificId(did: String): String? {
        val parts = did.split(":")
        if (parts.size < 3) return null
        return parts.drop(2).joinToString(":")
    }
}

