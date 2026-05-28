package org.trustweave.signatures.trustlists

import kotlinx.datetime.Instant

/**
 * Outcome of [TrustAnchorResolver.resolve].
 *
 * The three states encode the only distinctions that JAdES B-T verification needs:
 * - [QualifiedActive] : signature can be claimed as eIDAS-qualified at the asserted signing time.
 * - [QualifiedWithdrawn] : the issuing service is in the trust list but its CA was withdrawn —
 *   the verifier may downgrade the claim to "advanced" or reject outright depending on policy.
 * - [NotTrusted] : no chain to a CA/QC service in the supplied [TrustList] — the signature is
 *   not eIDAS-qualified.
 */
sealed class TrustAnchorMatch {

    /**
     * Signer chains to a CA/QC service whose current status is `GRANTED`.
     *
     * @property tspName     The matched TSP's legal name.
     * @property territory   ISO 3166-1 alpha-2 code of the Member State whose TSL listed the CA.
     * @property service     The matched [TspService] entry (CA/QC).
     * @property qcWithSscd  Convenience flag: `true` when the CA's qualifiers include
     *                       [QualifierUris.QC_WITH_SSCD] or [QualifierUris.QC_SSCD_STATUS_AS_IN_CERT],
     *                       which is what eIDAS Art. 25.2 requires for a Qualified Electronic
     *                       Signature (QES) as opposed to Advanced Electronic Signature (AES).
     * @property qcForESig   `true` when the CA's qualifiers explicitly include
     *                       [QualifierUris.QC_FOR_ESIG] (electronic signature use, as distinct
     *                       from electronic seal or website authentication).
     */
    data class QualifiedActive(
        val tspName: String,
        val territory: String,
        val service: TspService,
        val qcWithSscd: Boolean,
        val qcForESig: Boolean,
    ) : TrustAnchorMatch()

    /**
     * Signer chains to a CA/QC service but the service status is `WITHDRAWN` as of the
     * resolver call.
     *
     * @property tspName       Matched TSP's legal name.
     * @property withdrawnAt   Value of the matched service's `StatusStartingTime` at the moment
     *                         the status transitioned to `WITHDRAWN`.
     */
    data class QualifiedWithdrawn(
        val tspName: String,
        val withdrawnAt: Instant,
    ) : TrustAnchorMatch()

    /** No CA/QC service in the trust list could anchor the supplied certificate chain. */
    data object NotTrusted : TrustAnchorMatch()
}
