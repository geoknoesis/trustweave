package com.trustweave.credential.didcomm.exception

import com.trustweave.credential.exchange.exception.ExchangeException

/**
 * DIDComm-specific exception types.
 * 
 * These exceptions extend ExchangeException and provide DIDComm-specific error information.
 * All DIDComm exceptions are part of the ExchangeException hierarchy for consistent handling.
 */
sealed class DidCommException(
    override val code: String,
    override val message: String,
    override val context: Map<String, Any?> = emptyMap(),
    override val cause: Throwable? = null
) : ExchangeException(code, message, context, cause) {
    
    /**
     * Exception thrown when DIDComm message packing fails.
     * 
     * @param reason The reason packing failed
     * @param messageId The ID of the message (if available)
     * @param cause The underlying exception
     */
    data class PackingFailed(
        val reason: String,
        val messageId: String? = null,
        override val cause: Throwable? = null
    ) : DidCommException(
        code = "DIDCOMM_PACKING_FAILED",
        message = "DIDComm message packing failed: $reason",
        context = mapOf(
            "reason" to reason,
            "messageId" to messageId
        ).filterValues { it != null },
        cause = cause
    )
    
    /**
     * Exception thrown when DIDComm message unpacking fails.
     * 
     * @param reason The reason unpacking failed
     * @param messageId The ID of the message (if available)
     * @param cause The underlying exception
     */
    data class UnpackingFailed(
        val reason: String,
        val messageId: String? = null,
        override val cause: Throwable? = null
    ) : DidCommException(
        code = "DIDCOMM_UNPACKING_FAILED",
        message = "DIDComm message unpacking failed: $reason",
        context = mapOf(
            "reason" to reason,
            "messageId" to messageId
        ).filterValues { it != null },
        cause = cause
    )
    
    /**
     * Exception thrown when DIDComm message encryption fails.
     * 
     * @param reason The reason encryption failed
     * @param fromDid The sender DID (if available)
     * @param toDid The recipient DID (if available)
     * @param cause The underlying exception
     */
    data class EncryptionFailed(
        val reason: String,
        val fromDid: String? = null,
        val toDid: String? = null,
        override val cause: Throwable? = null
    ) : DidCommException(
        code = "DIDCOMM_ENCRYPTION_FAILED",
        message = "DIDComm message encryption failed: $reason",
        context = mapOf(
            "reason" to reason,
            "fromDid" to fromDid,
            "toDid" to toDid
        ).filterValues { it != null },
        cause = cause
    )
    
    /**
     * Exception thrown when DIDComm message decryption fails.
     * 
     * @param reason The reason decryption failed
     * @param messageId The ID of the message (if available)
     * @param cause The underlying exception
     */
    data class DecryptionFailed(
        val reason: String,
        val messageId: String? = null,
        override val cause: Throwable? = null
    ) : DidCommException(
        code = "DIDCOMM_DECRYPTION_FAILED",
        message = "DIDComm message decryption failed: $reason",
        context = mapOf(
            "reason" to reason,
            "messageId" to messageId
        ).filterValues { it != null },
        cause = cause
    )
    
    /**
     * Exception thrown when a DIDComm protocol error occurs.
     * 
     * @param reason The reason for the protocol error
     * @param field The field that caused the error (if applicable)
     * @param cause The underlying exception
     */
    data class ProtocolError(
        val reason: String,
        val field: String? = null,
        override val cause: Throwable? = null
    ) : DidCommException(
        code = "DIDCOMM_PROTOCOL_ERROR",
        message = field?.let {
            "DIDComm protocol error in field '$it': $reason"
        } ?: "DIDComm protocol error: $reason",
        context = mapOf(
            "reason" to reason,
            "field" to field
        ).filterValues { it != null },
        cause = cause
    )
}

