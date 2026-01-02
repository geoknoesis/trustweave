package org.trustweave.credential.internal

import org.trustweave.credential.format.ProofSuiteId
import org.trustweave.credential.results.IssuanceResult
import org.trustweave.credential.spi.proof.ProofEngine

/**
 * Error handling utilities for credential operations.
 * 
 * Extracts common error handling patterns to improve code readability and maintainability.
 * This module centralizes exception-to-result conversion logic.
 */
internal object ErrorHandling {
    
    /**
     * Handles exceptions during credential issuance operations.
     * 
     * Converts various exception types into appropriate [IssuanceResult.Failure] types,
     * preserving error context and ensuring proper cancellation handling.
     * 
     * @param format The proof suite format being used
     * @param operation A suspend function that performs the issuance operation
     * @return IssuanceResult representing success or failure
     */
    suspend fun handleIssuanceErrors(
        format: ProofSuiteId,
        operation: suspend () -> org.trustweave.credential.model.vc.VerifiableCredential
    ): IssuanceResult {
        return try {
            val credential = operation()
            IssuanceResult.Success(credential)
        } catch (e: IllegalArgumentException) {
            IssuanceResult.Failure.InvalidRequest(
                field = "request",
                reason = e.message ?: "Invalid request"
            )
        } catch (e: IllegalStateException) {
            IssuanceResult.Failure.AdapterNotReady(
                format = format,
                reason = e.message
            )
        } catch (e: kotlinx.coroutines.CancellationException) {
            // Re-throw cancellation exceptions to respect coroutine cancellation
            throw e
        } catch (e: java.util.concurrent.TimeoutException) {
            IssuanceResult.Failure.AdapterError(
                format = format,
                reason = "Issuance operation timed out: ${e.message}",
                cause = e
            )
        } catch (e: java.io.IOException) {
            IssuanceResult.Failure.AdapterError(
                format = format,
                reason = "I/O error during issuance: ${e.message}",
                cause = e
            )
        } catch (e: RuntimeException) {
            IssuanceResult.Failure.AdapterError(
                format = format,
                reason = "Runtime error during issuance: ${e.message ?: e.javaClass.simpleName}",
                cause = e
            )
        } catch (e: Exception) {
            IssuanceResult.Failure.AdapterError(
                format = format,
                reason = "Unexpected error during issuance: ${e.message ?: e.javaClass.simpleName}",
                cause = e
            )
        }
    }
    
    /**
     * Validates that a proof engine is available and ready for the given format.
     * 
     * @param format The proof suite format to check
     * @param engines Map of available proof engines
     * @return IssuanceResult.Failure if engine is unavailable or not ready, null otherwise
     */
    fun validateEngineAvailability(
        format: ProofSuiteId,
        engines: Map<ProofSuiteId, ProofEngine>
    ): IssuanceResult.Failure? {
        val engine = engines[format]
            ?: return IssuanceResult.Failure.UnsupportedFormat(
                format = format,
                supportedFormats = engines.keys.toList()
            )
        
        if (!engine.isReady()) {
            return IssuanceResult.Failure.AdapterNotReady(
                format = format,
                reason = "Proof engine not initialized"
            )
        }
        
        return null
    }
}

