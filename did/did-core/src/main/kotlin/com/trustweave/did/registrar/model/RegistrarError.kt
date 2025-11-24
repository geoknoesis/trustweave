package com.trustweave.did.registrar.model

import kotlinx.serialization.Serializable

/**
 * Error information according to DID Registration specification.
 * 
 * The spec defines that errors should be returned in the `didState` when
 * the operation state is `FAILED`.
 * 
 * @see https://identity.foundation/did-registration/
 */
@Serializable
data class RegistrarError(
    /**
     * Error code identifying the type of error.
     * 
     * Common error codes:
     * - `invalidDid`: The DID format is invalid
     * - `methodNotSupported`: The DID method is not supported
     * - `invalidOptions`: The provided options are invalid
     * - `insufficientFunds`: Insufficient funds for blockchain operations
     * - `unauthorized`: Authorization failed
     * - `notFound`: DID not found
     * - `conflict`: Operation conflicts with existing state
     * - `internalError`: Internal server error
     */
    val code: String,
    
    /**
     * Human-readable error message.
     */
    val message: String,
    
    /**
     * Additional error details.
     * Can contain method-specific error information.
     */
    val details: Map<String, String>? = null
)

