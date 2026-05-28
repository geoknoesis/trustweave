package org.trustweave.kms.results

import org.trustweave.core.identifiers.KeyId
import org.trustweave.kms.Algorithm
import org.trustweave.kms.KeyHandle

/**
 * Result of a key generation operation.
 */
sealed class GenerateKeyResult {
    data class Success(val keyHandle: KeyHandle) : GenerateKeyResult()

    sealed class Failure : GenerateKeyResult() {
        data class UnsupportedAlgorithm(
            val algorithm: Algorithm,
            val supportedAlgorithms: Set<Algorithm>,
            val reason: String? = null
        ) : Failure() {
            constructor(algorithm: Algorithm, supportedAlgorithms: Set<Algorithm>) : this(
                algorithm, supportedAlgorithms,
                "Algorithm '${algorithm.name}' is not supported by this KMS. Supported algorithms: ${supportedAlgorithms.joinToString(", ") { it.name }}"
            )
        }
        data class InvalidOptions(val algorithm: Algorithm, val reason: String, val invalidOptions: Map<String, Any?> = emptyMap(), val cause: Throwable? = null) : Failure()
        data class DuplicateKeyId(val keyId: KeyId) : Failure()
        data class Error(val algorithm: Algorithm, val reason: String, val cause: Throwable? = null) : Failure()
    }

    val isSuccess: Boolean get() = this is Success
    val isFailure: Boolean get() = this is Failure
    val keyHandleOrNull: KeyHandle? get() = (this as? Success)?.keyHandle
}

inline fun GenerateKeyResult.onSuccess(action: (KeyHandle) -> Unit): GenerateKeyResult {
    if (this is GenerateKeyResult.Success) action(keyHandle)
    return this
}

inline fun GenerateKeyResult.onFailure(action: (GenerateKeyResult.Failure) -> Unit): GenerateKeyResult {
    if (this is GenerateKeyResult.Failure) action(this)
    return this
}

fun GenerateKeyResult.getOrThrow(): KeyHandle = when (this) {
    is GenerateKeyResult.Success -> keyHandle
    is GenerateKeyResult.Failure.UnsupportedAlgorithm -> throw org.trustweave.kms.UnsupportedAlgorithmException(reason ?: "Algorithm '${algorithm.name}' is not supported")
    is GenerateKeyResult.Failure.InvalidOptions -> throw IllegalArgumentException(reason)
    is GenerateKeyResult.Failure.DuplicateKeyId -> throw IllegalArgumentException("Key with ID '${keyId.value}' already exists")
    is GenerateKeyResult.Failure.Error -> throw org.trustweave.core.exception.TrustWeaveException.Unknown(message = reason, cause = cause)
}

inline fun <R> GenerateKeyResult.fold(onFailure: (GenerateKeyResult.Failure) -> R, onSuccess: (KeyHandle) -> R): R = when (this) {
    is GenerateKeyResult.Success -> onSuccess(keyHandle)
    is GenerateKeyResult.Failure -> onFailure(this)
}
