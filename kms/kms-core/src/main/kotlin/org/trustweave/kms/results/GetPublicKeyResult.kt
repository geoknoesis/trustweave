package org.trustweave.kms.results

import org.trustweave.core.identifiers.KeyId
import org.trustweave.kms.KeyHandle

/**
 * Result of getting a public key.
 *
 * Sealed hierarchy for exhaustive, type-safe error handling.
 */
sealed class GetPublicKeyResult {
    data class Success(val keyHandle: KeyHandle) : GetPublicKeyResult()

    sealed class Failure : GetPublicKeyResult() {
        data class KeyNotFound(val keyId: KeyId, val reason: String? = null) : Failure() {
            constructor(keyId: KeyId) : this(keyId, null)
        }
        data class Error(val keyId: KeyId, val reason: String, val cause: Throwable? = null) : Failure()
    }

    val isSuccess: Boolean get() = this is Success
    val isFailure: Boolean get() = this is Failure
    val keyHandleOrNull: KeyHandle? get() = (this as? Success)?.keyHandle
}

inline fun GetPublicKeyResult.onSuccess(action: (KeyHandle) -> Unit): GetPublicKeyResult {
    if (this is GetPublicKeyResult.Success) action(keyHandle)
    return this
}

inline fun GetPublicKeyResult.onFailure(action: (GetPublicKeyResult.Failure) -> Unit): GetPublicKeyResult {
    if (this is GetPublicKeyResult.Failure) action(this)
    return this
}

fun GetPublicKeyResult.getOrThrow(): KeyHandle = when (this) {
    is GetPublicKeyResult.Success -> keyHandle
    is GetPublicKeyResult.Failure.KeyNotFound -> throw org.trustweave.kms.exception.KmsException.KeyNotFound(keyId = keyId.value)
    is GetPublicKeyResult.Failure.Error -> throw org.trustweave.core.exception.TrustWeaveException.Unknown(message = reason, cause = cause)
}

inline fun <R> GetPublicKeyResult.fold(onFailure: (GetPublicKeyResult.Failure) -> R, onSuccess: (KeyHandle) -> R): R = when (this) {
    is GetPublicKeyResult.Success -> onSuccess(keyHandle)
    is GetPublicKeyResult.Failure -> onFailure(this)
}
