package org.trustweave.credential.vi.verification

import kotlinx.serialization.json.JsonObject

/**
 * Outcome of a Verifiable Intent delegation-chain verification.
 *
 * Fail-closed: [valid] is true only if every performed check passed. [errors] records the first
 * failure that aborted verification; [checksPerformed]/[checksSkipped] give an audit trail (e.g. a
 * skipped `aud` check when no expected value was supplied).
 *
 * Production note: this mirrors the reference impl's `ChainVerificationResult`. A later revision
 * should return TrustWeave's core `Result<T>` for consistency with the rest of the SDK.
 */
public data class ChainVerificationResult(
    val valid: Boolean,
    val errors: List<String> = emptyList(),
    val l1Claims: JsonObject? = null,
    val l2Claims: JsonObject? = null,
    val l3Claims: JsonObject? = null,
    val checksPerformed: List<String> = emptyList(),
    val checksSkipped: List<String> = emptyList(),
) {
    public companion object {
        internal fun failure(error: String, performed: List<String> = emptyList()): ChainVerificationResult =
            ChainVerificationResult(valid = false, errors = listOf(error), checksPerformed = performed)
    }
}
