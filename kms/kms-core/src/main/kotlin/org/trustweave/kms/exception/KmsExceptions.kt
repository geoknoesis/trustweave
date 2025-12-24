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
}

