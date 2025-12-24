package org.trustweave.kms.results

import org.trustweave.core.identifiers.KeyId
import org.trustweave.kms.Algorithm
import org.trustweave.kms.KeyHandle

/**
 * Result of a KMS operation.
 * 
 * Sealed hierarchy for exhaustive, type-safe error handling.
 * Follows the same pattern as [DidResolutionResult] and [IssuanceResult].
 * 
 * **Usage:**
 * ```kotlin
 * when (val result = kms.getPublicKey(keyId)) {
 *     is GetPublicKeyResult.Success -> {
 *         val handle = result.keyHandle
 *         // Use key handle
 *     }
 *     is GetPublicKeyResult.Failure.KeyNotFound -> {
 *         // Expected: Key doesn't exist
 *         println("Key not found: ${result.keyId}")
 *     }
 *     // Compiler ensures all cases handled
 * }
 * ```
 */

/**
 * Result of getting a public key.
 */
sealed class GetPublicKeyResult {
    /**
     * Public key retrieval succeeded.
     */
    data class Success(
        val keyHandle: KeyHandle
    ) : GetPublicKeyResult()
    
    /**
     * Public key retrieval failed.
     */
    sealed class Failure : GetPublicKeyResult() {
        /**
         * Key was not found.
         */
        data class KeyNotFound(
            val keyId: KeyId,
            val reason: String? = null
        ) : Failure() {
            constructor(keyId: KeyId) : this(keyId, null)
        }
        
        /**
         * Unexpected error during key retrieval.
         */
        data class Error(
            val keyId: KeyId,
            val reason: String,
            val cause: Throwable? = null
        ) : Failure()
    }
    
    /**
     * True if operation succeeded.
     */
    val isSuccess: Boolean
        get() = this is Success
    
    /**
     * True if operation failed.
     */
    val isFailure: Boolean
        get() = this is Failure
    
    /**
     * Get key handle if successful, otherwise null.
     */
    val keyHandleOrNull: KeyHandle?
        get() = (this as? Success)?.keyHandle
}

/**
 * Result of a signing operation.
 */
sealed class SignResult {
    /**
     * Signing succeeded.
     */
    data class Success(
        val signature: ByteArray
    ) : SignResult() {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is Success) return false
            return signature.contentEquals(other.signature)
        }
        
        override fun hashCode(): Int = signature.contentHashCode()
    }
    
    /**
     * Signing failed.
     */
    sealed class Failure : SignResult() {
        /**
         * Key was not found.
         */
        data class KeyNotFound(
            val keyId: KeyId,
            val reason: String? = null
        ) : Failure() {
            constructor(keyId: KeyId) : this(keyId, null)
        }
        
        /**
         * Algorithm is not supported or incompatible with the key.
         */
        data class UnsupportedAlgorithm(
            val keyId: KeyId,
            val requestedAlgorithm: Algorithm?,
            val keyAlgorithm: Algorithm,
            val reason: String? = null
        ) : Failure()
        
        /**
         * Unexpected error during signing.
         */
        data class Error(
            val keyId: KeyId,
            val reason: String,
            val cause: Throwable? = null
        ) : Failure()
    }
    
    /**
     * True if signing succeeded.
     */
    val isSuccess: Boolean
        get() = this is Success
    
    /**
     * True if signing failed.
     */
    val isFailure: Boolean
        get() = this is Failure
    
    /**
     * Get signature if successful, otherwise null.
     */
    val signatureOrNull: ByteArray?
        get() = (this as? Success)?.signature
}

/**
 * Result of a key generation operation.
 */
sealed class GenerateKeyResult {
    /**
     * Key generation succeeded.
     */
    data class Success(
        val keyHandle: KeyHandle
    ) : GenerateKeyResult()
    
    /**
     * Key generation failed.
     */
    sealed class Failure : GenerateKeyResult() {
        /**
         * Algorithm is not supported by this KMS.
         */
        data class UnsupportedAlgorithm(
            val algorithm: Algorithm,
            val supportedAlgorithms: Set<Algorithm>,
            val reason: String? = null
        ) : Failure() {
            constructor(
                algorithm: Algorithm,
                supportedAlgorithms: Set<Algorithm>
            ) : this(
                algorithm = algorithm,
                supportedAlgorithms = supportedAlgorithms,
                reason = "Algorithm '${algorithm.name}' is not supported by this KMS. " +
                    "Supported algorithms: ${supportedAlgorithms.joinToString(", ") { it.name }}"
            )
        }
        
        /**
         * Invalid options provided for key generation.
         */
        data class InvalidOptions(
            val algorithm: Algorithm,
            val reason: String,
            val invalidOptions: Map<String, Any?> = emptyMap()
        ) : Failure()
        
        /**
         * Unexpected error during key generation.
         */
        data class Error(
            val algorithm: Algorithm,
            val reason: String,
            val cause: Throwable? = null
        ) : Failure()
    }
    
    /**
     * True if key generation succeeded.
     */
    val isSuccess: Boolean
        get() = this is Success
    
    /**
     * True if key generation failed.
     */
    val isFailure: Boolean
        get() = this is Failure
    
    /**
     * Get key handle if successful, otherwise null.
     */
    val keyHandleOrNull: KeyHandle?
        get() = (this as? Success)?.keyHandle
}

/**
 * Result of a key deletion operation.
 */
sealed class DeleteKeyResult {
    /**
     * Key deletion succeeded (key was deleted).
     */
    data object Deleted : DeleteKeyResult()
    
    /**
     * Key deletion succeeded (key did not exist, operation is idempotent).
     */
    data object NotFound : DeleteKeyResult()
    
    /**
     * Key deletion failed.
     */
    sealed class Failure : DeleteKeyResult() {
        /**
         * Unexpected error during key deletion.
         */
        data class Error(
            val keyId: KeyId,
            val reason: String,
            val cause: Throwable? = null
        ) : Failure()
    }
    
    /**
     * True if deletion succeeded (whether key existed or not).
     */
    val isSuccess: Boolean
        get() = this is Deleted || this is NotFound
    
    /**
     * True if key was actually deleted (existed before deletion).
     */
    val wasDeleted: Boolean
        get() = this is Deleted
    
    /**
     * True if operation failed.
     */
    val isFailure: Boolean
        get() = this is Failure
}

/**
 * Extension functions for fluent result handling.
 */

/**
 * Execute action if result is success.
 */
inline fun GetPublicKeyResult.onSuccess(action: (KeyHandle) -> Unit): GetPublicKeyResult {
    if (this is GetPublicKeyResult.Success) action(keyHandle)
    return this
}

inline fun GetPublicKeyResult.onFailure(action: (GetPublicKeyResult.Failure) -> Unit): GetPublicKeyResult {
    if (this is GetPublicKeyResult.Failure) action(this)
    return this
}

/**
 * Get value or throw exception.
 */
fun GetPublicKeyResult.getOrThrow(): KeyHandle {
    return when (this) {
        is GetPublicKeyResult.Success -> keyHandle
        is GetPublicKeyResult.Failure.KeyNotFound -> throw org.trustweave.kms.exception.KmsException.KeyNotFound(
            keyId = keyId.value
        )
        is GetPublicKeyResult.Failure.Error -> throw org.trustweave.core.exception.TrustWeaveException.Unknown(
            message = reason,
            cause = cause
        )
    }
}

/**
 * Fold (pattern matching as expression).
 */
inline fun <R> GetPublicKeyResult.fold(
    onFailure: (GetPublicKeyResult.Failure) -> R,
    onSuccess: (KeyHandle) -> R
): R {
    return when (this) {
        is GetPublicKeyResult.Success -> onSuccess(keyHandle)
        is GetPublicKeyResult.Failure -> onFailure(this)
    }
}

/**
 * Execute action if result is success.
 */
inline fun SignResult.onSuccess(action: (ByteArray) -> Unit): SignResult {
    if (this is SignResult.Success) action(signature)
    return this
}

inline fun SignResult.onFailure(action: (SignResult.Failure) -> Unit): SignResult {
    if (this is SignResult.Failure) action(this)
    return this
}

/**
 * Get value or throw exception.
 */
fun SignResult.getOrThrow(): ByteArray {
    return when (this) {
        is SignResult.Success -> signature
        is SignResult.Failure.KeyNotFound -> throw org.trustweave.kms.exception.KmsException.KeyNotFound(
            keyId = keyId.value
        )
        is SignResult.Failure.UnsupportedAlgorithm -> throw org.trustweave.kms.UnsupportedAlgorithmException(
            reason ?: "Algorithm '${requestedAlgorithm?.name ?: "null"}' is not compatible with key algorithm '${keyAlgorithm.name}'"
        )
        is SignResult.Failure.Error -> throw org.trustweave.core.exception.TrustWeaveException.Unknown(
            message = reason,
            cause = cause
        )
    }
}

/**
 * Fold (pattern matching as expression).
 */
inline fun <R> SignResult.fold(
    onFailure: (SignResult.Failure) -> R,
    onSuccess: (ByteArray) -> R
): R {
    return when (this) {
        is SignResult.Success -> onSuccess(signature)
        is SignResult.Failure -> onFailure(this)
    }
}

/**
 * Execute action if result is success.
 */
inline fun GenerateKeyResult.onSuccess(action: (KeyHandle) -> Unit): GenerateKeyResult {
    if (this is GenerateKeyResult.Success) action(keyHandle)
    return this
}

inline fun GenerateKeyResult.onFailure(action: (GenerateKeyResult.Failure) -> Unit): GenerateKeyResult {
    if (this is GenerateKeyResult.Failure) action(this)
    return this
}

/**
 * Get value or throw exception.
 */
fun GenerateKeyResult.getOrThrow(): KeyHandle {
    return when (this) {
        is GenerateKeyResult.Success -> keyHandle
        is GenerateKeyResult.Failure.UnsupportedAlgorithm -> throw org.trustweave.kms.UnsupportedAlgorithmException(
            reason ?: "Algorithm '${algorithm.name}' is not supported"
        )
        is GenerateKeyResult.Failure.InvalidOptions -> throw IllegalArgumentException(reason)
        is GenerateKeyResult.Failure.Error -> throw org.trustweave.core.exception.TrustWeaveException.Unknown(
            message = reason,
            cause = cause
        )
    }
}

/**
 * Fold (pattern matching as expression).
 */
inline fun <R> GenerateKeyResult.fold(
    onFailure: (GenerateKeyResult.Failure) -> R,
    onSuccess: (KeyHandle) -> R
): R {
    return when (this) {
        is GenerateKeyResult.Success -> onSuccess(keyHandle)
        is GenerateKeyResult.Failure -> onFailure(this)
    }
}

/**
 * Execute action if result is success.
 */
inline fun DeleteKeyResult.onSuccess(action: () -> Unit): DeleteKeyResult {
    if (isSuccess) action()
    return this
}

inline fun DeleteKeyResult.onFailure(action: (DeleteKeyResult.Failure) -> Unit): DeleteKeyResult {
    if (this is DeleteKeyResult.Failure) action(this)
    return this
}

/**
 * Get boolean indicating if key was deleted (true) or not found (false), or throw if error.
 */
fun DeleteKeyResult.getOrThrow(): Boolean {
    return when (this) {
        is DeleteKeyResult.Deleted -> true
        is DeleteKeyResult.NotFound -> false
        is DeleteKeyResult.Failure.Error -> throw org.trustweave.core.exception.TrustWeaveException.Unknown(
            message = reason,
            cause = cause
        )
    }
}

/**
 * Fold (pattern matching as expression).
 */
inline fun <R> DeleteKeyResult.fold(
    onFailure: (DeleteKeyResult.Failure) -> R,
    onSuccess: (Boolean) -> R  // true if deleted, false if not found
): R {
    return when (this) {
        is DeleteKeyResult.Deleted -> onSuccess(true)
        is DeleteKeyResult.NotFound -> onSuccess(false)
        is DeleteKeyResult.Failure -> onFailure(this)
    }
}

