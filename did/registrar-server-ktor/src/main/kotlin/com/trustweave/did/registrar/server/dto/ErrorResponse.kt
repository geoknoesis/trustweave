package com.trustweave.did.registrar.server.dto

import com.trustweave.did.registrar.model.DidState
import kotlinx.serialization.Serializable

/**
 * Structured error response for API errors.
 *
 * Provides consistent error format across all endpoints.
 *
 * **Example:**
 * ```json
 * {
 *   "error": "Invalid DID format",
 *   "errorCode": "INVALID_DID_FORMAT",
 *   "didState": {
 *     "state": "failed",
 *     "reason": "Invalid DID format"
 *   }
 * }
 * ```
 */
@Serializable
data class ErrorResponse(
    /**
     * Human-readable error message.
     */
    val error: String,

    /**
     * Machine-readable error code for programmatic handling.
     */
    val errorCode: String? = null,

    /**
     * DID state indicating the operation failure details.
     */
    val didState: DidState? = null
) {
    companion object {
        /**
         * Creates an error response from a message.
         */
        fun fromMessage(message: String, errorCode: String? = null): ErrorResponse {
            return ErrorResponse(
                error = message,
                errorCode = errorCode,
                didState = DidState(
                    state = com.trustweave.did.registrar.model.OperationState.FAILED,
                    reason = message
                )
            )
        }

        /**
         * Creates an error response from an exception.
         */
        fun fromException(e: Throwable, errorCode: String? = null): ErrorResponse {
            return ErrorResponse(
                error = e.message ?: "Unknown error",
                errorCode = errorCode,
                didState = DidState(
                    state = com.trustweave.did.registrar.model.OperationState.FAILED,
                    reason = e.message ?: "Unknown error"
                )
            )
        }
    }
}

