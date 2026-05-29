package org.trustweave.credential.spi.status

import org.trustweave.credential.model.vc.VerifiableCredential

/**
 * SPI for checking the revocation/suspension status of a [VerifiableCredential].
 *
 * Implementations resolve the credential's [CredentialStatus] entry against a live
 * status list (Bitstring, Token, etc.) and return a typed result that proof engines
 * use to gate [VerificationResult].
 *
 * Inject via [org.trustweave.credential.spi.proof.ProofEngineConfig.properties]["statusChecker"].
 */
interface CredentialStatusChecker {
    suspend fun checkStatus(credential: VerifiableCredential): CredentialStatusCheckResult
}

sealed class CredentialStatusCheckResult {
    /** Status list confirms the credential is active. */
    object Valid : CredentialStatusCheckResult()

    /** Credential has been revoked. */
    data class Revoked(val reason: String? = null) : CredentialStatusCheckResult()

    /** Credential has been suspended (temporary hold). */
    data class Suspended(val reason: String? = null) : CredentialStatusCheckResult()

    /** The status check itself failed (network error, malformed list, etc.). */
    data class CheckFailed(val reason: String) : CredentialStatusCheckResult()

    /** Credential has no credentialStatus field — no status to check. */
    object NoStatus : CredentialStatusCheckResult()
}
