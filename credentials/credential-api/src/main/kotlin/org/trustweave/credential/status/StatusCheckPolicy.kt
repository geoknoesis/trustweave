package org.trustweave.credential.status

import org.trustweave.credential.internal.RevocationChecker
import org.trustweave.credential.model.vc.VerifiableCredential
import org.trustweave.credential.requests.RevocationFailurePolicy
import org.trustweave.credential.results.VerificationResult
import org.trustweave.credential.spi.status.CredentialStatusCheckResult
import org.trustweave.credential.spi.status.CredentialStatusChecker

/**
 * What an engine-level status check concluded once the [RevocationFailurePolicy] was applied.
 *
 * @property status the checker's result, or `null` when the checker threw
 * @property failure set when the status could not be determined and the policy says to reject
 * @property warnings to attach to a successful verification when the policy only warns
 */
class StatusCheckVerdict(
    val status: CredentialStatusCheckResult?,
    val failure: VerificationResult.Invalid?,
    val warnings: List<String>,
)

/**
 * Applies the verifier's [RevocationFailurePolicy] to an engine-level status check, so proof-engine
 * plugins treat an undeterminable status (a [CredentialStatusCheckResult.CheckFailed] result or an
 * exception from the checker) the way the built-in engines do: fail closed by default, warn under
 * `FAIL_WITH_WARNING`, continue under `FAIL_OPEN`. A conclusive result (valid, revoked, suspended)
 * comes back in [StatusCheckVerdict.status] for the engine to map to its own typed result.
 */
object StatusCheckPolicy {
    suspend fun evaluate(
        credential: VerifiableCredential,
        checker: CredentialStatusChecker,
        policy: RevocationFailurePolicy,
    ): StatusCheckVerdict {
        val outcome = RevocationChecker.checkWithStatusChecker(credential, checker, policy)
        return StatusCheckVerdict(outcome.status, outcome.failure, outcome.warnings)
    }
}
