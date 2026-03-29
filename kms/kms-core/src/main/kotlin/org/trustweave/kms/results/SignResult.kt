package org.trustweave.kms.results

import org.trustweave.core.identifiers.KeyId
import org.trustweave.kms.Algorithm

/**
 * Result of a signing operation.
 */
sealed class SignResult {
    data class Success(val signature: ByteArray) : SignResult() {
        override fun equals(other: Any?): Boolean = this === other || (other is Success && signature.contentEquals(other.signature))
        override fun hashCode(): Int = signature.contentHashCode()
    }

    sealed class Failure : SignResult() {
        data class KeyNotFound(val keyId: KeyId, val reason: String? = null) : Failure() {
            constructor(keyId: KeyId) : this(keyId, null)
        }
        data class UnsupportedAlgorithm(
            val keyId: KeyId,
            val requestedAlgorithm: Algorithm?,
            val keyAlgorithm: Algorithm,
            val reason: String? = null
        ) : Failure()
        data class Error(val keyId: KeyId, val reason: String, val cause: Throwable? = null) : Failure()
    }

    val isSuccess: Boolean get() = this is Success
    val isFailure: Boolean get() = this is Failure
    val signatureOrNull: ByteArray? get() = (this as? Success)?.signature
}

inline fun SignResult.onSuccess(action: (ByteArray) -> Unit): SignResult {
    if (this is SignResult.Success) action(signature)
    return this
}

inline fun SignResult.onFailure(action: (SignResult.Failure) -> Unit): SignResult {
    if (this is SignResult.Failure) action(this)
    return this
}

fun SignResult.getOrThrow(): ByteArray = when (this) {
    is SignResult.Success -> signature
    is SignResult.Failure.KeyNotFound -> throw org.trustweave.kms.exception.KmsException.KeyNotFound(keyId = keyId.value)
    is SignResult.Failure.UnsupportedAlgorithm -> throw org.trustweave.kms.UnsupportedAlgorithmException(
        reason ?: "Algorithm '${requestedAlgorithm?.name ?: "null"}' is not compatible with key algorithm '${keyAlgorithm.name}'"
    )
    is SignResult.Failure.Error -> throw org.trustweave.core.exception.TrustWeaveException.Unknown(message = reason, cause = cause)
}

inline fun <R> SignResult.fold(onFailure: (SignResult.Failure) -> R, onSuccess: (ByteArray) -> R): R = when (this) {
    is SignResult.Success -> onSuccess(signature)
    is SignResult.Failure -> onFailure(this)
}
