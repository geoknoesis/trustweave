package org.trustweave.signatures.etsi.validation

import kotlinx.datetime.Instant

/**
 * Full report produced by [EtsiSignatureValidator.validate].
 *
 * The report carries one [StepOutcome] per [EtsiValidationStep] plus the aggregated
 * [finalVerdict] derived from those outcomes. It is the structured analogue of the flat
 * pass/fail returned by the in-tree JAdES verifier.
 *
 * @property finalVerdict   Aggregate verdict (PASSED / FAILED / INDETERMINATE).
 * @property steps          Per-step outcome map. Missing entries are treated as
 *                          [StepOutcome.NotApplicable] by the [FinalVerdict] aggregation.
 * @property signerSubject  Distinguished name of the signer certificate, when parseable;
 *                          null when the format or identifier check failed early.
 * @property signingTime    Asserted `sigT` from the JAdES protected header, when parseable.
 */
data class EtsiValidationReport(
    val finalVerdict: FinalVerdict,
    val steps: Map<EtsiValidationStep, StepOutcome>,
    val signerSubject: String?,
    val signingTime: Instant?,
) {
    /**
     * Aggregate verdict as defined by ETSI EN 319 102-1 §5.6 (Signature Validation Algorithm).
     */
    enum class FinalVerdict {
        /** All applicable steps reported [StepOutcome.Passed]. */
        TOTAL_PASSED,

        /** At least one step reported [StepOutcome.Failed]. */
        TOTAL_FAILED,

        /**
         * No step reported [StepOutcome.Failed], but at least one reported
         * [StepOutcome.Inconclusive].
         */
        INDETERMINATE,
    }
}
