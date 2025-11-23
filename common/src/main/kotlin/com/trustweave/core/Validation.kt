package com.trustweave.core

/**
 * Common validation utilities for TrustWeave.
 * 
 * Provides validation for DIDs, chain IDs, and other common TrustWeave entities.
 * Credential-specific validation is in credentials:core module.
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

/**
 * DID format and method validation.
 */
object DidValidator {
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
                code = "DID_EMPTY",
                message = "DID cannot be empty",
                field = "did",
                value = did
            )
        }
        
        if (!DID_PATTERN.matches(did)) {
            return ValidationResult.Invalid(
                code = "INVALID_DID_FORMAT",
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
                code = "DID_METHOD_EXTRACTION_FAILED",
                message = "Failed to extract method from DID: $did",
                field = "did.method",
                value = did
            )
        }
        
        if (method !in availableMethods) {
            return ValidationResult.Invalid(
                code = "UNSUPPORTED_DID_METHOD",
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

/**
 * Chain ID validation utilities.
 */
object ChainIdValidator {
    /**
     * CAIP-2 chain ID pattern (basic format).
     * Format: <namespace>:<reference>
     */
    private val CAIP2_PATTERN = Regex("^[a-z0-9]+:[a-zA-Z0-9._-]+$")
    
    /**
     * Extended chain ID pattern (supports Indy and other multi-part formats).
     * Format: <namespace>:<reference>(:<additional>)*
     * Examples: "algorand:testnet", "indy:testnet:bcovrin"
     */
    private val EXTENDED_PATTERN = Regex("^[a-z0-9]+:[a-z0-9-]+(:[a-z0-9-]+)*$")
    
    /**
     * Validates chain ID format (supports CAIP-2 and extended formats like Indy).
     * 
     * @param chainId The chain ID to validate
     * @return ValidationResult indicating if the chain ID format is valid
     */
    fun validateFormat(chainId: String): ValidationResult {
        if (chainId.isBlank()) {
            return ValidationResult.Invalid(
                code = "CHAIN_ID_EMPTY",
                message = "Chain ID cannot be empty",
                field = "chainId",
                value = chainId
            )
        }
        
        // Support both CAIP-2 format (two parts) and extended format (three or more parts for Indy)
        if (!CAIP2_PATTERN.matches(chainId) && !EXTENDED_PATTERN.matches(chainId)) {
            return ValidationResult.Invalid(
                code = "INVALID_CHAIN_ID_FORMAT",
                message = "Chain ID must match format: <namespace>:<reference> or <namespace>:<reference>:<additional>",
                field = "chainId",
                value = chainId
            )
        }
        
        return ValidationResult.Valid
    }
    
    /**
     * Validates that the chain ID is registered.
     * 
     * @param chainId The chain ID to validate
     * @param availableChains List of available chain IDs
     * @return ValidationResult indicating if the chain ID is registered
     */
    fun validateRegistered(chainId: String, availableChains: List<String>): ValidationResult {
        val formatResult = validateFormat(chainId)
        if (!formatResult.isValid()) {
            return formatResult
        }
        
        if (chainId !in availableChains) {
            return ValidationResult.Invalid(
                code = "CHAIN_NOT_REGISTERED",
                message = "Chain ID '$chainId' is not registered. Available chains: $availableChains",
                field = "chainId",
                value = chainId
            )
        }
        
        return ValidationResult.Valid
    }
}

