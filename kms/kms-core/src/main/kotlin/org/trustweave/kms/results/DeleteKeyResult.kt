package org.trustweave.kms.results

import org.trustweave.core.identifiers.KeyId

/**
 * Result of a key deletion operation.
 */
sealed class DeleteKeyResult {
    data object Deleted : DeleteKeyResult()
    data object NotFound : DeleteKeyResult()

    sealed class Failure : DeleteKeyResult() {
        data class Error(val keyId: KeyId, val reason: String, val cause: Throwable? = null) : Failure()
    }

    val isSuccess: Boolean get() = this is Deleted || this is NotFound
    val wasDeleted: Boolean get() = this is Deleted
    val isFailure: Boolean get() = this is Failure
}

inline fun DeleteKeyResult.onSuccess(action: () -> Unit): DeleteKeyResult {
    if (isSuccess) action()
    return this
}

inline fun DeleteKeyResult.onFailure(action: (DeleteKeyResult.Failure) -> Unit): DeleteKeyResult {
    if (this is DeleteKeyResult.Failure) action(this)
    return this
}

fun DeleteKeyResult.getOrThrow(): Boolean = when (this) {
    is DeleteKeyResult.Deleted -> true
    is DeleteKeyResult.NotFound -> false
    is DeleteKeyResult.Failure.Error -> throw org.trustweave.core.exception.TrustWeaveException.Unknown(message = reason, cause = cause)
}

inline fun <R> DeleteKeyResult.fold(onFailure: (DeleteKeyResult.Failure) -> R, onSuccess: (Boolean) -> R): R = when (this) {
    is DeleteKeyResult.Deleted -> onSuccess(true)
    is DeleteKeyResult.NotFound -> onSuccess(false)
    is DeleteKeyResult.Failure -> onFailure(this)
}
