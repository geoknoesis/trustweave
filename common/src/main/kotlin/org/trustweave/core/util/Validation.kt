package org.trustweave.core.util

/**
 * Common validation utilities for TrustWeave.
 *
 * This module provides only generic validation infrastructure.
 * Domain-specific validators (DID, Chain ID) are in their respective modules:
 * - DID validation: `org.trustweave.did.validation.DidValidator`
 * - Chain ID validation: `org.trustweave.anchor.validation.ChainIdValidator`
 * - Credential validation: `credentials:core` module
 */

/**
 * Validation result for input validation.
 *
 * This sealed class provides a type-safe way to represent validation outcomes.
 * Use [Valid] for successful validation and [Invalid] for validation failures.
 *
 * **Example:**
 * ```kotlin
 * fun validateEmail(email: String): ValidationResult {
 *     return if (email.contains("@")) {
 *         ValidationResult.Valid
 *     } else {
 *         ValidationResult.Invalid(
 *             code = "INVALID_EMAIL",
 *             message = "Email must contain @",
 *             field = "email",
 *             value = email
 *         )
 *     }
 * }
 * ```
 */
sealed class ValidationResult {
    /**
     * Represents a successful validation.
     */
    object Valid : ValidationResult()

    /**
     * Represents a failed validation with error details.
     *
     * @param code Error code for programmatic error handling
     * @param message Human-readable error message
     * @param field Field name that failed validation
     * @param value The value that failed validation (can be null)
     */
    data class Invalid(
        val code: String,
        val message: String,
        val field: String,
        val value: Any?
    ) : ValidationResult()

    /**
     * Returns true if validation passed.
     */
    fun isValid(): Boolean = this is Valid

    /**
     * Returns the error message if validation failed, null otherwise.
     */
    fun errorMessage(): String? = (this as? Invalid)?.message

    /**
     * Returns the error code if validation failed, null otherwise.
     */
    fun errorCode(): String? = (this as? Invalid)?.code

    /**
     * Converts this validation result to a Result<T>.
     *
     * @param value The value to return on success
     * @return Result.success(value) if valid, Result.failure(ValidationException) if invalid
     */
    fun <T> toResult(value: T): Result<T> = when (this) {
        is Valid -> Result.success(value)
        is Invalid -> Result.failure(
            IllegalArgumentException("Validation failed for field '${field}': $message")
        )
    }

    /**
     * Converts this validation result to a TrustWeaveException.
     *
     * @return null if valid, TrustWeaveException.ValidationFailed if invalid
     */
    fun toException(): org.trustweave.core.exception.TrustWeaveException.ValidationFailed? =
        when (this) {
            is Valid -> null
            is Invalid -> org.trustweave.core.exception.TrustWeaveException.ValidationFailed(
                field = field,
                reason = message,
                value = value
            )
        }

    companion object {
        /**
         * Combines multiple validation results into a single result.
         *
         * Returns [Valid] only if all validations passed, otherwise returns the first [Invalid] result.
         *
         * **Example:**
         * ```kotlin
         * val emailValidation = validateEmail(email)
         * val ageValidation = validateAge(age)
         * val combined = ValidationResult.combine(emailValidation, ageValidation)
         * ```
         *
         * @param results Validation results to combine
         * @return Combined validation result
         */
        fun combine(vararg results: ValidationResult): ValidationResult {
            return results.firstOrNull { !it.isValid() } ?: Valid
        }

        /**
         * Combines a list of validation results into a single result.
         *
         * Returns [Valid] only if all validations passed, otherwise returns the first [Invalid] result.
         *
         * @param results List of validation results to combine
         * @return Combined validation result
         */
        fun combine(results: List<ValidationResult>): ValidationResult {
            return results.firstOrNull { !it.isValid() } ?: Valid
        }
    }
}

