package org.trustweave.trust.dsl.credential.jades

import org.trustweave.credential.format.ProofSuiteId
import org.trustweave.signatures.jades.JadesProfile
import org.trustweave.signatures.tsa.TsaConfig
import org.trustweave.trust.dsl.credential.IssuanceBuilder

/**
 * Ergonomic DSL extensions for issuing JAdES-protected credentials via
 * [org.trustweave.trust.dsl.credential.IssuanceBuilder].
 *
 * These extensions wrap the low-level `additionalOptions` map that the JAdES proof engine
 * expects (`"keyId"`, `"signerCertificateChain"`, `"profile"`, `"tsaConfig"`, `"contentType"`)
 * so that callers can stay in the TrustWeave DSL idiom:
 *
 * ```kotlin
 * trustWeave.issue {
 *     credential { … }
 *     signedBy(issuerDid, "key-1")
 *     withJadesProfile(
 *         profile = JadesProfile.B_T,
 *         signerCertificateChain = listOf(signerCertDer, intermediateDer),
 *         tsaConfig = tsaConfig,
 *     )
 * }
 * ```
 *
 * Calling [withJadesProfile] always sets [IssuanceBuilder] proof suite to
 * [ProofSuiteId.JADES]; there is no useful way to combine JAdES with a different proof suite,
 * so the suite is fixed implicitly to reduce caller boilerplate.
 */

/** Additional-options map keys consumed by the JAdES proof engine. */
internal object JadesOptionKeys {
    const val KEY_ID: String = "keyId"
    const val SIGNER_CERT_CHAIN: String = "signerCertificateChain"
    const val PROFILE: String = "profile"
    const val TSA_CONFIG: String = "tsaConfig"
    const val CONTENT_TYPE: String = "contentType"

    /** Wire values the JAdES engine accepts for the `"profile"` option. */
    const val PROFILE_B_B: String = "B-B"
    const val PROFILE_B_T: String = "B-T"
    const val PROFILE_B_LT: String = "B-LT"
    const val PROFILE_B_LTA: String = "B-LTA"
}

/**
 * Configure the issuance to produce a JAdES B-B or B-T signature.
 *
 * @param profile                Target JAdES profile. [JadesProfile.B_B] omits the
 *                               signature-time-stamp; [JadesProfile.B_T] requires [tsaConfig].
 * @param signerCertificateChain DER-encoded X.509 chain, signer first. Must be non-empty —
 *                               at minimum the signer's own certificate.
 * @param tsaConfig              TSA configuration for the signature-time-stamp. Required iff
 *                               [profile] is [JadesProfile.B_T]; ignored for [JadesProfile.B_B].
 * @param contentType            Optional JOSE `cty` for the protected header (e.g.
 *                               `"application/json"`).
 *
 * @throws IllegalArgumentException if [signerCertificateChain] is empty, or if [profile] is
 *         [JadesProfile.B_T] without a [tsaConfig].
 */
fun IssuanceBuilder.withJadesProfile(
    profile: JadesProfile,
    signerCertificateChain: List<ByteArray>,
    tsaConfig: TsaConfig? = null,
    contentType: String? = null,
) {
    require(signerCertificateChain.isNotEmpty()) {
        "signerCertificateChain must contain at least the signer's certificate"
    }
    require(profile != JadesProfile.B_T || tsaConfig != null) {
        "JadesProfile.B_T requires a non-null tsaConfig (B-T must carry a signature-time-stamp)"
    }

    // Fix the proof suite to JAdES. The engine is selected by ProofSuiteId.
    setProofSuite(ProofSuiteId.JADES)

    additionalOption(
        JadesOptionKeys.PROFILE,
        when (profile) {
            JadesProfile.B_B -> JadesOptionKeys.PROFILE_B_B
            JadesProfile.B_T -> JadesOptionKeys.PROFILE_B_T
            JadesProfile.B_LT -> JadesOptionKeys.PROFILE_B_LT
            JadesProfile.B_LTA -> JadesOptionKeys.PROFILE_B_LTA
        },
    )
    additionalOption(JadesOptionKeys.SIGNER_CERT_CHAIN, signerCertificateChain.toList())
    tsaConfig?.let { additionalOption(JadesOptionKeys.TSA_CONFIG, it) }
    contentType?.let { additionalOption(JadesOptionKeys.CONTENT_TYPE, it) }
}

/**
 * Override the KMS key ID the JAdES signer should use. Optional — by default the engine reads
 * `IssuanceRequest.issuerKeyId` (populated by [IssuanceBuilder.signedBy]). Use this when the
 * signing key in the KMS is not the same as the verification method ID embedded in the DID
 * document.
 */
fun IssuanceBuilder.withJadesKeyId(keyId: String) {
    require(keyId.isNotBlank()) { "JAdES keyId cannot be blank" }
    additionalOption(JadesOptionKeys.KEY_ID, keyId)
}
