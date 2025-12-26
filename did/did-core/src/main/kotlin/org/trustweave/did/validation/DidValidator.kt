package org.trustweave.did.validation

import org.trustweave.core.util.ValidationResult

/**
 * DID format and method validation utilities.
 *
 * Provides validation functions following the W3C DID Core specification.
 * This validator checks DID format compliance and method availability.
 *
 * **Validation Rules:**
 * - DIDs must start with "did:"
 * - Method name must be lowercase alphanumeric
 * - Method-specific identifier can contain various characters (per W3C spec)
 * - Method must be registered if method validation is performed
 *
 * **Note:** The regex pattern is a simplified validation. For full W3C compliance,
 * a proper ABNF parser would be more accurate, but this pattern covers the
 * majority of valid DIDs in practice.
 *
 * **Example Usage:**
 * ```kotlin
 * // Validate format
 * val result = DidValidator.validateFormat("did:key:123")
 * if (result.isValid()) {
 *     // DID format is valid
 * }
 *
 * // Validate method availability
 * val methodResult = DidValidator.validateMethod(
 *     did = "did:key:123",
 *     availableMethods = listOf("key", "web")
 * )
 *
 * // Extract components
 * val method = DidValidator.extractMethod("did:key:123")  // "key"
 * val identifier = DidValidator.extractMethodSpecificId("did:key:123")  // "123"
 * ```
 */
object DidValidator {
    /**
     * Error codes for DID validation.
     *
     * These codes are used in [ValidationResult.Invalid] to identify
     * specific validation failure types.
     */
    object ErrorCodes {
        /** DID string is empty or blank */
        const val DID_EMPTY = "DID_EMPTY"
        
        /** DID does not match the required format pattern */
        const val INVALID_DID_FORMAT = "INVALID_DID_FORMAT"
        
        /** Failed to extract method name from DID string */
        const val DID_METHOD_EXTRACTION_FAILED = "DID_METHOD_EXTRACTION_FAILED"
        
        /** DID method is not in the list of available methods */
        const val UNSUPPORTED_DID_METHOD = "UNSUPPORTED_DID_METHOD"
    }

    /**
     * DID pattern matching the DID specification.
     * 
     * Format: `did:<method>:<method-specific-id>`
     * 
     * **Pattern Details:**
     * - `did:` - Required prefix
     * - `[a-z0-9]+` - Method name (lowercase alphanumeric, one or more)
     * - `:` - Separator
     * - `[a-zA-Z0-9._:%-]+` - Method-specific identifier (various allowed characters)
     * 
     * **Note:** This is a simplified regex. The W3C spec allows more characters
     * in method-specific-ids, but this pattern covers the majority of valid DIDs.
     * For full compliance, consider using an ABNF parser.
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

