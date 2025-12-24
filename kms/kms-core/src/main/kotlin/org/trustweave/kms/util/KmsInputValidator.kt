package org.trustweave.kms.util

/**
 * Input validation utilities for KMS operations.
 * 
 * Provides standardized validation for key IDs, data sizes, and other inputs
 * to prevent security issues and invalid API calls.
 */
object KmsInputValidator {
    /**
     * Maximum key ID length (256 characters).
     * Prevents DoS attacks and ensures compatibility with most KMS providers.
     */
    private const val MAX_KEY_ID_LENGTH = 256
    
    /**
     * Maximum data size for signing (10 MB).
     * Prevents DoS attacks with extremely large data.
     */
    private const val MAX_SIGN_DATA_SIZE = 10 * 1024 * 1024 // 10 MB
    
    /**
     * Valid key ID characters: alphanumeric, dashes, underscores, slashes, colons.
     * This pattern is compatible with most KMS providers (AWS ARNs, Azure key IDs, etc.).
     */
    private val VALID_KEY_ID_PATTERN = Regex("^[a-zA-Z0-9_\\-/:]+$")
    
    /**
     * Validates a key ID.
     * 
     * @param keyId The key ID to validate
     * @return Error message if invalid, null if valid
     */
    fun validateKeyId(keyId: String): String? {
        if (keyId.isBlank()) {
            return "Key ID cannot be blank"
        }
        
        if (keyId.length > MAX_KEY_ID_LENGTH) {
            return "Key ID must be $MAX_KEY_ID_LENGTH characters or less, got ${keyId.length}"
        }
        
        if (!VALID_KEY_ID_PATTERN.matches(keyId)) {
            return "Key ID contains invalid characters. Only alphanumeric, dashes, underscores, slashes, and colons are allowed"
        }
        
        return null
    }
    
    /**
     * Validates data for signing operations.
     * 
     * @param data The data to validate
     * @return Error message if invalid, null if valid
     */
    fun validateSignData(data: ByteArray): String? {
        if (data.isEmpty()) {
            return "Cannot sign empty data"
        }
        
        if (data.size > MAX_SIGN_DATA_SIZE) {
            return "Data size (${data.size} bytes) exceeds maximum allowed size ($MAX_SIGN_DATA_SIZE bytes)"
        }
        
        return null
    }
    
    /**
     * Validates key ID and returns a normalized error message.
     * 
     * @param keyId The key ID to validate
     * @return Pair of (isValid: Boolean, errorMessage: String?)
     */
    fun validateKeyIdWithResult(keyId: String): Pair<Boolean, String?> {
        val error = validateKeyId(keyId)
        return (error == null) to error
    }
    
    /**
     * Validates signing data and returns a normalized error message.
     * 
     * @param data The data to validate
     * @return Pair of (isValid: Boolean, errorMessage: String?)
     */
    fun validateSignDataWithResult(data: ByteArray): Pair<Boolean, String?> {
        val error = validateSignData(data)
        return (error == null) to error
    }
}

