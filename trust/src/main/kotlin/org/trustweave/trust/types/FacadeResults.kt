package org.trustweave.trust.types

import kotlin.time.Duration

/**
 * Sealed result of `TrustWeave.getKeyIdResult(did)`.
 *
 * Replaces the `kotlin.Result<String>` of `getKeyId`, so key selection follows the same
 * `when`-based handling as the other facade operations.
 */
sealed class KeyIdResult {
    /** [keyId] is the ID of the key the DID issues with. */
    data class Success(
        val keyId: String,
    ) : KeyIdResult()

    /** The DID could not be resolved, or its document has no usable signing key. */
    data class Failure(
        val reason: String,
        val cause: Throwable? = null,
    ) : KeyIdResult()
}

/** Unwraps [KeyIdResult.Success] or throws [IllegalStateException] carrying the failure reason. */
fun KeyIdResult.getOrThrow(): String =
    when (this) {
        is KeyIdResult.Success -> keyId
        is KeyIdResult.Failure -> throw IllegalStateException(reason, cause)
    }

/**
 * Sealed result of `TrustWeave.trustResult { }`.
 *
 * [Completed] only says the block ran to completion; it is not a trust verdict.
 */
sealed class TrustOperationResult {
    /** The trust block ran to completion. */
    data object Completed : TrustOperationResult()

    /** No trust registry is configured; the block was not run. */
    data class NotConfigured(
        val reason: String,
    ) : TrustOperationResult()

    /** The block or the registry threw (for example an invalid anchor DID). */
    data class Failure(
        val reason: String,
        val cause: Throwable,
    ) : TrustOperationResult()
}

/**
 * Sealed result of `TrustWeave.revokeResult { }`.
 *
 * Every outcome of `revoke { }` that is a thrown exception there has its own variant here.
 */
sealed class RevocationResult {
    /** The revocation manager revoked the credential. */
    data object Revoked : RevocationResult()

    /** The manager answered `false`: it did not change the entry (for example an unknown index). */
    data object NotRevoked : RevocationResult()

    /** No revocation manager is configured. */
    data class NotConfigured(
        val reason: String,
    ) : RevocationResult()

    /** The request was incomplete, for example `credential(...)` or `statusList(...)` is missing. */
    data class InvalidRequest(
        val reason: String,
    ) : RevocationResult()

    /** The operation exceeded [timeout]; its outcome is unknown, so re-check the status before retrying. */
    data class TimedOut(
        val timeout: Duration,
        val cause: Throwable? = null,
    ) : RevocationResult()

    /** The revocation manager failed. */
    data class Failure(
        val reason: String,
        val cause: Throwable,
    ) : RevocationResult()
}
