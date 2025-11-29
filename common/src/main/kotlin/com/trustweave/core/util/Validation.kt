package com.trustweave.core.util

/**
 * Common validation utilities for TrustWeave.
 *
 * This module provides only generic validation infrastructure.
 * Domain-specific validators (DID, Chain ID) are in their respective modules:
 * - DID validation: `com.trustweave.did.validation.DidValidator`
 * - Chain ID validation: `com.trustweave.anchor.validation.ChainIdValidator`
 * - Credential validation: `credentials:core` module
 */

/**
 * Validation result for input validation.
 */
sealed class ValidationResult {
    object Valid : ValidationResult()

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
}

