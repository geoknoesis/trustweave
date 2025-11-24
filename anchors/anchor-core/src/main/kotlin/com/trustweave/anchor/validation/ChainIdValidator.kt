package com.trustweave.anchor.validation

import com.trustweave.core.util.ValidationResult

/**
 * Chain ID validation utilities.
 */
object ChainIdValidator {
    /**
     * Error codes for chain ID validation.
     */
    object ErrorCodes {
        const val CHAIN_ID_EMPTY = "CHAIN_ID_EMPTY"
        const val INVALID_CHAIN_ID_FORMAT = "INVALID_CHAIN_ID_FORMAT"
        const val CHAIN_NOT_REGISTERED = "CHAIN_NOT_REGISTERED"
    }
    
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
                code = ErrorCodes.CHAIN_ID_EMPTY,
                message = "Chain ID cannot be empty",
                field = "chainId",
                value = chainId
            )
        }
        
        // Support both CAIP-2 format (two parts) and extended format (three or more parts for Indy)
        if (!CAIP2_PATTERN.matches(chainId) && !EXTENDED_PATTERN.matches(chainId)) {
            return ValidationResult.Invalid(
                code = ErrorCodes.INVALID_CHAIN_ID_FORMAT,
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
                code = ErrorCodes.CHAIN_NOT_REGISTERED,
                message = "Chain ID '$chainId' is not registered. Available chains: $availableChains",
                field = "chainId",
                value = chainId
            )
        }
        
        return ValidationResult.Valid
    }
}

