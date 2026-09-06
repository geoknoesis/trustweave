package org.trustweave.did.registrar.server.dto

import kotlinx.serialization.Serializable
import org.trustweave.did.registrar.model.DidState

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
    val didState: DidState? = null,
) {
    companion object {
        /**
         * Creates an error response from a message.
         */
        fun fromMessage(
            message: String,
            errorCode: String? = null,
        ): ErrorResponse =
            ErrorResponse(
                error = message,
                errorCode = errorCode,
                didState =
                    DidState(
                        state = org.trustweave.did.registrar.model.OperationState.FAILED,
                        reason = message,
                    ),
            )

        /**
         * Creates an error response from an exception.
         */
        fun fromException(
            e: Throwable,
            errorCode: String? = null,
        ): ErrorResponse {
            if (e is java.util.concurrent.CancellationException) throw e
            val message = if (errorCode == "INVALID_REQUEST") "Invalid registrar request" else "Unable to complete registrar operation"
            return ErrorResponse(
                error = message,
                errorCode = errorCode,
                didState =
                    DidState(
                        state = org.trustweave.did.registrar.model.OperationState.FAILED,
                        reason = message,
                    ),
            )
        }
    }
}
