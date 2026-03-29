package org.trustweave.kms.exception

import org.trustweave.core.exception.TrustWeaveException

/**
 * Key Management Service (KMS) related exception types.
 *
 * These exceptions provide structured error codes and context for KMS operations.
 */
sealed class KmsException(
    override val code: String,
    override val message: String,
    override val context: Map<String, Any?> = emptyMap(),
    override val cause: Throwable? = null
) : TrustWeaveException(code, message, context, cause) {

    data class KeyNotFound(
        val keyId: String,
        val keyType: String? = null
    ) : KmsException(
        code = "KEY_NOT_FOUND",
        message = "Key not found: $keyId",
        context = mapOf(
            "keyId" to keyId,
            "keyType" to keyType
        ).filterValues { it != null }
    )

    data class KeyGenerationFailed(
        val algorithm: String,
        val reason: String,
        override val cause: Throwable? = null
    ) : KmsException(
        code = "KEY_GENERATION_FAILED",
        message = "Key generation failed for algorithm '$algorithm': $reason",
        context = mapOf("algorithm" to algorithm, "reason" to reason),
        cause = cause
    )

    data class SigningFailed(
        val keyId: String,
        val reason: String,
        override val cause: Throwable? = null
    ) : KmsException(
        code = "SIGNING_FAILED",
        message = "Signing failed for key '$keyId': $reason",
        context = mapOf("keyId" to keyId, "reason" to reason),
        cause = cause
    )

    data class KeyDeletionFailed(
        val keyId: String,
        val reason: String,
        override val cause: Throwable? = null
    ) : KmsException(
        code = "KEY_DELETION_FAILED",
        message = "Key deletion failed for key '$keyId': $reason",
        context = mapOf("keyId" to keyId, "reason" to reason),
        cause = cause
    )
}

