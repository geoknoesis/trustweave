package org.trustweave.signatures.etsi.validation

/**
 * Outcome of a single [EtsiValidationStep].
 *
 * Mirrors the four-valued result space of ETSI EN 319 102-1:
 *  - [Passed]         — the step verified successfully.
 *  - [Failed]         — the step decisively rejected the signature.
 *  - [Inconclusive]   — the verifier could not produce a definitive answer (missing data,
 *                      transient resolver error, etc.). Maps to INDETERMINATE in the §5.6
 *                      Signature Validation Algorithm.
 *  - [NotApplicable]  — the step is not relevant for the current profile / policy (e.g.
 *                      [EtsiValidationStep.LONG_TERM_VALIDATION] for a B-B signature).
 */
sealed class StepOutcome {
    data class Passed(val notes: String = "") : StepOutcome()
    data class Failed(val reason: String, val cause: Throwable? = null) : StepOutcome()
    data class Inconclusive(val reason: String) : StepOutcome()
    object NotApplicable : StepOutcome()
}
