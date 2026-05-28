package org.trustweave.trust.dsl.credential.jades

import org.trustweave.signatures.jades.JadesProfile
import org.trustweave.signatures.trustlists.TrustAnchorResolver
import org.trustweave.trust.dsl.credential.VerificationBuilder

/**
 * Ergonomic DSL extensions for verifying JAdES-protected credentials via
 * [org.trustweave.trust.dsl.credential.VerificationBuilder].
 *
 * These extensions wrap the low-level `additionalOptions` map that the JAdES proof engine
 * expects (`"requiredProfile"`, `"trustAnchorResolver"`, `"acceptedAlgorithms"`) so callers can
 * stay in the TrustWeave DSL idiom:
 *
 * ```kotlin
 * val result = trustWeave.verify {
 *     credential(jadesCred)
 *     requireJadesProfile(
 *         profile = JadesProfile.B_T,
 *         trustAnchorResolver = euLotlResolver,
 *     )
 * }
 * ```
 */

/** Additional-options map keys consumed by the JAdES proof engine for verification. */
internal object JadesVerificationOptionKeys {
    const val REQUIRED_PROFILE: String = "requiredProfile"
    const val TRUST_ANCHOR_RESOLVER: String = "trustAnchorResolver"
    const val ACCEPTED_ALGORITHMS: String = "acceptedAlgorithms"
}

/**
 * Require the credential's proof to be a JAdES proof matching [profile], and supply the
 * trust-anchor resolver the engine consults for signer-cert qualification.
 *
 * @param profile             Minimum JAdES profile to accept. The engine treats this as a floor:
 *                            `B-B` accepts any JAdES; `B-T` additionally requires a valid
 *                            signature-time-stamp.
 * @param trustAnchorResolver Resolves the signer cert against the caller-supplied trust graph
 *                            (e.g. an EU LOTL parsed via `signatures:trust-lists`). The engine
 *                            also accepts a resolver injected at TrustWeave construction time;
 *                            this parameter takes precedence when both are present.
 * @param acceptedAlgorithms  JOSE `alg` allow-list. When null, the engine uses its own default
 *                            (`ES256`, `ES384`, `ES512`, `EdDSA`).
 */
fun VerificationBuilder.requireJadesProfile(
    profile: JadesProfile,
    trustAnchorResolver: TrustAnchorResolver,
    acceptedAlgorithms: Set<String>? = null,
) {
    additionalOption(
        JadesVerificationOptionKeys.REQUIRED_PROFILE,
        when (profile) {
            JadesProfile.B_B -> JadesOptionKeys.PROFILE_B_B
            JadesProfile.B_T -> JadesOptionKeys.PROFILE_B_T
            JadesProfile.B_LT -> JadesOptionKeys.PROFILE_B_LT
            JadesProfile.B_LTA -> JadesOptionKeys.PROFILE_B_LTA
        },
    )
    additionalOption(JadesVerificationOptionKeys.TRUST_ANCHOR_RESOLVER, trustAnchorResolver)
    if (acceptedAlgorithms != null) {
        require(acceptedAlgorithms.isNotEmpty()) {
            "acceptedAlgorithms must be null or non-empty; an empty set would reject every signature"
        }
        additionalOption(
            JadesVerificationOptionKeys.ACCEPTED_ALGORITHMS,
            acceptedAlgorithms.toSet(),
        )
    }
}

/**
 * Supply the trust-anchor resolver without imposing a profile floor. Useful when the caller
 * trusts whatever profile the credential was issued with (typical for B-T credentials inspected
 * by a B-B-tolerant verifier).
 */
fun VerificationBuilder.withJadesTrustAnchorResolver(resolver: TrustAnchorResolver) {
    additionalOption(JadesVerificationOptionKeys.TRUST_ANCHOR_RESOLVER, resolver)
}
