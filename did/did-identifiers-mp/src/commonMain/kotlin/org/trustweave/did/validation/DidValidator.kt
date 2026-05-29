package org.trustweave.did.validation

import org.trustweave.core.util.ValidationResult

/**
 * DID format and method validation utilities.
 *
 * Validates DIDs per **DID 1.1 §3.1 ABNF**:
 * - `did = "did:" method-name ":" method-specific-id`
 * - `method-name = 1*method-char` with `method-char = %x61-7A / DIGIT` (lowercase a-z, 0-9)
 * - `method-specific-id = *( *idchar ":" ) 1*idchar` with `idchar = ALPHA / DIGIT / "." / "-" / "_" / pct-encoded`
 * - `pct-encoded = "%" HEXDIG HEXDIG`
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

    /** Method name: 1+ method-char (lowercase a-z, digit) per ABNF. */
    private val METHOD_NAME_PATTERN = Regex("^[a-z0-9]+\$")

    /**
     * Method-specific-id per DID 1.1 §3.1: *( *idchar ":" ) 1*idchar.
     * idchar = ALPHA / DIGIT / "." / "-" / "_" / pct-encoded; pct-encoded = "%" HEXDIG HEXDIG.
     * Implemented as one or more segments of ([A-Za-z0-9._-]|%[0-9A-Fa-f]{2})+ separated by ":".
     */
    private val METHOD_SPECIFIC_ID_PATTERN = Regex("^([A-Za-z0-9._-]|%[0-9A-Fa-f]{2})+(:([A-Za-z0-9._-]|%[0-9A-Fa-f]{2})+)*\$")

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

        if (!did.startsWith("did:")) {
            return ValidationResult.Invalid(
                code = ErrorCodes.INVALID_DID_FORMAT,
                message = "DID must start with 'did:'",
                field = "did",
                value = did
            )
        }

        val afterScheme = did.removePrefix("did:")
        val firstColon = afterScheme.indexOf(':')
        if (firstColon < 0) {
            return ValidationResult.Invalid(
                code = ErrorCodes.INVALID_DID_FORMAT,
                message = "DID must have method and method-specific-id: did:<method>:<method-specific-id>",
                field = "did",
                value = did
            )
        }

        val methodName = afterScheme.substring(0, firstColon)
        val methodSpecificId = afterScheme.substring(firstColon + 1)

        if (!METHOD_NAME_PATTERN.matches(methodName)) {
            return ValidationResult.Invalid(
                code = ErrorCodes.INVALID_DID_FORMAT,
                message = "DID method name must be one or more lowercase letters or digits (a-z, 0-9)",
                field = "did.method",
                value = did
            )
        }

        if (methodSpecificId.isEmpty()) {
            return ValidationResult.Invalid(
                code = ErrorCodes.INVALID_DID_FORMAT,
                message = "DID method-specific-id cannot be empty",
                field = "did",
                value = did
            )
        }

        if (!METHOD_SPECIFIC_ID_PATTERN.matches(methodSpecificId)) {
            return ValidationResult.Invalid(
                code = ErrorCodes.INVALID_DID_FORMAT,
                message = "DID method-specific-id must match ABNF: idchar = ALPHA/DIGIT/'.'/'-'/'_'/pct-encoded; segments separated by ':'",
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
