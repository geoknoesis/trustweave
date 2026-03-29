package org.trustweave.trust.types

import org.trustweave.credential.model.vc.VerifiablePresentation

/**
 * Result of creating a [VerifiablePresentation] via the TrustWeave presentation DSL.
 *
 * Mirrors [org.trustweave.credential.results.IssuanceResult] / [org.trustweave.credential.results.VerificationResult]
 * so configuration and validation failures are handled with `when` instead of exceptions where possible.
 */
sealed class PresentationResult {
    /**
     * Presentation was created successfully.
     */
    data class Success(val presentation: VerifiablePresentation) : PresentationResult()

    /**
     * Presentation could not be created.
     */
    sealed class Failure : PresentationResult() {
        abstract val errors: List<String>
        abstract val warnings: List<String>

        /**
         * [org.trustweave.credential.CredentialService] is not configured.
         */
        data class AdapterNotReady(
            override val errors: List<String>,
            override val warnings: List<String> = emptyList(),
        ) : Failure() {
            constructor(reason: String? = null) : this(
                errors = listOf(
                    reason ?: "Credential service is not available. Configure it in TrustWeave.build { ... }",
                ),
            )
        }

        /**
         * DSL or request validation failed (e.g. missing holder, no credentials).
         */
        data class InvalidRequest(
            override val errors: List<String>,
            override val warnings: List<String> = emptyList(),
        ) : Failure()

        /**
         * The credential service failed while creating the presentation.
         */
        data class AdapterError(
            val cause: Throwable? = null,
            override val errors: List<String>,
            override val warnings: List<String> = emptyList(),
        ) : Failure() {
            constructor(message: String, cause: Throwable? = null) : this(
                cause = cause,
                errors = listOf(message),
            )
        }
    }

    val presentationOrNull: VerifiablePresentation?
        get() = (this as? Success)?.presentation

    val allErrors: List<String>
        get() = when (this) {
            is Success -> emptyList()
            is Failure -> errors
        }
}
