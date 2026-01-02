package org.trustweave.credential.internal

import org.trustweave.credential.model.vc.VerifiableCredential
import org.trustweave.credential.revocation.CredentialRevocationManager
import org.trustweave.credential.requests.RevocationFailurePolicy
import org.trustweave.credential.results.VerificationResult
import kotlinx.datetime.Clock

/**
 * Revocation checking utilities.
 * 
 * This utility object handles credential revocation status checks with proper error handling
 * and policy enforcement. It provides a centralized way to check if a credential has been
 * revoked or suspended, with configurable failure policies.
 * 
 * **Key Features:**
 * - Revocation status checking via CredentialRevocationManager
 * - Suspension status checking
 * - Policy-based error handling (fail-fast vs. fail-soft)
 * - Comprehensive error handling for network and I/O errors
 * - Proper cancellation support for coroutines
 * 
 * **Failure Policies:**
 * - `FAIL_FAST`: Return invalid result immediately on revocation check failure
 * - `FAIL_SOFT`: Return warnings but allow verification to proceed
 * 
 * **Usage:**
 * ```kotlin
 * val (invalidResult, warnings) = RevocationChecker.checkRevocationStatus(
 *     credential = credential,
 *     revocationManager = revocationManager,
 *     policy = RevocationFailurePolicy.FAIL_FAST
 * )
 * 
 * if (invalidResult != null) {
 *     return invalidResult
 * }
 * // Add warnings to verification result
 * ```
 * 
 * **Note:** This is an internal utility used by DefaultCredentialService during verification.
 */
internal object RevocationChecker {
    /**
     * Check revocation status with proper error handling.
     * 
     * @param credential The credential to check
     * @param revocationManager Revocation manager (if available)
     * @param policy Failure policy to apply
     * @return Pair of (VerificationResult?, List<String>) where first is failure result (if any),
     *         and second is warnings to add to successful verification
     */
    suspend fun checkRevocationStatus(
        credential: VerifiableCredential,
        revocationManager: CredentialRevocationManager?,
        policy: RevocationFailurePolicy
    ): Pair<VerificationResult.Invalid?, List<String>> {
        if (revocationManager == null || credential.credentialStatus == null) {
            return Pair(null, emptyList())
        }
        
        return try {
            val revocationStatus = revocationManager.checkRevocationStatus(credential)
            
            if (revocationStatus.revoked) {
                val errorMessage = "Credential has been revoked${revocationStatus.reason?.let { ": $it" } ?: ""}"
                Pair(
                    VerificationResult.Invalid.Revoked(
                        credential = credential,
                        revokedAt = Clock.System.now(),
                        errors = listOf(errorMessage),
                        warnings = emptyList(),
                        revocationReason = revocationStatus.reason
                    ),
                    emptyList()
                )
            } else if (revocationStatus.suspended) {
                val errorMessage = "Credential is suspended${revocationStatus.reason?.let { ": $it" } ?: ""}"
                Pair(
                    VerificationResult.Invalid.Revoked(
                        credential = credential,
                        revokedAt = Clock.System.now(),
                        errors = listOf(errorMessage),
                        warnings = emptyList(),
                        revocationReason = revocationStatus.reason
                    ),
                    emptyList()
                )
            } else {
                // Credential is not revoked
                Pair(null, emptyList())
            }
        } catch (e: java.util.concurrent.TimeoutException) {
            handleRevocationFailure(
                credential = credential,
                error = e,
                reason = "Revocation check timed out",
                policy = policy
            )
        } catch (e: java.net.UnknownHostException) {
            handleRevocationFailure(
                credential = credential,
                error = e,
                reason = "Revocation service unreachable: ${e.message}",
                policy = policy
            )
        } catch (e: java.net.ConnectException) {
            handleRevocationFailure(
                credential = credential,
                error = e,
                reason = "Revocation service connection refused: ${e.message}",
                policy = policy
            )
        } catch (e: java.io.IOException) {
            handleRevocationFailure(
                credential = credential,
                error = e,
                reason = "Revocation check I/O error: ${e.message}",
                policy = policy
            )
        } catch (e: IllegalStateException) {
            handleRevocationFailure(
                credential = credential,
                error = e,
                reason = "Revocation manager error: ${e.message}",
                policy = policy
            )
        } catch (e: IllegalArgumentException) {
            handleRevocationFailure(
                credential = credential,
                error = e,
                reason = "Invalid revocation check request: ${e.message}",
                policy = policy
            )
        } catch (e: kotlinx.coroutines.CancellationException) {
            // Re-throw cancellation exceptions
            throw e
        } catch (e: Exception) {
            handleRevocationFailure(
                credential = credential,
                error = e,
                reason = "Unexpected revocation check error: ${e.message}",
                policy = policy
            )
        }
    }
    
    /**
     * Handle revocation check failure according to policy.
     * 
     * Implements the revocation failure policy pattern:
     * - **FAIL_CLOSED**: Reject credential if revocation cannot be verified (most secure)
     * - **FAIL_WITH_WARNING**: Accept credential but add warning (balanced)
     * - **FAIL_OPEN**: Accept credential silently (most permissive, use with caution)
     * 
     * @param credential The credential being verified
     * @param error The exception that occurred during revocation check
     * @param reason Human-readable reason for the failure
     * @param policy The revocation failure policy to apply
     * @return Pair of (VerificationResult.Invalid?, List<String>) where first is failure result (if any),
     *         and second is warnings to add to successful verification
     */
    private fun handleRevocationFailure(
        credential: VerifiableCredential,
        error: Throwable,
        reason: String,
        policy: RevocationFailurePolicy
    ): Pair<VerificationResult.Invalid?, List<String>> {
        return when (policy) {
            RevocationFailurePolicy.FAIL_CLOSED -> {
                // Fail-closed: Reject credential if revocation cannot be checked
                Pair(
                    VerificationResult.Invalid.InvalidProof(
                        credential = credential,
                        reason = "Revocation check failed: $reason",
                        errors = listOf(
                            "Cannot verify credential revocation status: $reason",
                            "Credential rejected due to revocation check failure (fail-closed policy)"
                        ),
                        warnings = emptyList()
                    ),
                    emptyList()
                )
            }
            RevocationFailurePolicy.FAIL_WITH_WARNING -> {
                // Fail-with-warning: Continue verification but add warning
                Pair(
                    null,
                    listOf("Revocation check failed: $reason. Verification continues with warning.")
                )
            }
            RevocationFailurePolicy.FAIL_OPEN -> {
                // Fail-open: Continue verification silently
                Pair(null, emptyList())
            }
        }
    }
}

