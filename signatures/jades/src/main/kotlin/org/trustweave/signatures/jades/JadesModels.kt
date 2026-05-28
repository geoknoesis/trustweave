package org.trustweave.signatures.jades

import kotlinx.datetime.Instant
import kotlinx.serialization.json.JsonElement
import org.trustweave.core.identifiers.KeyId
import org.trustweave.signatures.trustlists.TrustAnchorResolver
import org.trustweave.signatures.tsa.TsaConfig
import kotlin.time.Duration

/**
 * JAdES profile level. Per ETSI TS 119 182-1 §5.
 *
 * The four baseline profiles form a strict inclusion chain: each level embeds everything the
 * lower level required and adds new `etsiU` entries on top. Verifiers compare the requested
 * minimum against what is actually present in the unsigned-properties block.
 */
enum class JadesProfile(val ordinalLevel: Int) {
    /** Basic signature — JWS + JAdES protected-header parameters only. No time-stamp. */
    B_B(0),

    /** Basic signature + at least one signature-time-stamp (`sigTst`) attribute. */
    B_T(1),

    /**
     * B-T + long-term validation material: complete signer certificate chain (`xVals`) and
     * revocation snapshots (`rVals`) embedded in the unsigned properties so verifiers can
     * re-validate the signature later without re-resolving the trust graph or re-fetching CRLs.
     */
    B_LT(2),

    /**
     * B-LT + archival time-stamps (`arcTst`) over the signature, `sigTst`, `xVals` and `rVals`.
     * Lets the signature remain validatable for decades even when the original cryptographic
     * algorithms or signing certificates expire or weaken.
     */
    B_LTA(3),
    ;

    /** `true` when this profile includes everything [other] does (and possibly more). */
    fun atLeast(other: JadesProfile): Boolean = this.ordinalLevel >= other.ordinalLevel
}

/**
 * JAdES protected header — the union of JWS RFC 7515 `protected` header parameters and the
 * JAdES TS 119 182-1 §5.2 parameters TrustWeave models explicitly.
 *
 * The verifier reconstructs this from the wire bytes; the signer builds it during issuance.
 *
 * @property alg        JOSE algorithm identifier (`ES256`, `ES384`, `ES512`, `EdDSA`).
 * @property kid        Optional JOSE `kid`. TrustWeave defaults to the [KeyId.value] of the
 *                      signing key.
 * @property typ        JOSE `typ`. Defaults to `"JAdES"` when the signer builds the header.
 * @property cty        JOSE `cty` content-type for the payload (e.g. `"application/json"`).
 * @property crit       Optional list of critical-header names from RFC 7515 §4.1.11.
 * @property sigT       ETSI `sigT` (TS 119 182-1 §5.2.5) — ISO 8601 claimed signing time.
 * @property x5tS256    Base64URL of SHA-256(signer-cert DER). Serialised as `"x5t#S256"`.
 * @property x5c        Optional X.509 cert chain as base64 (standard, NOT base64URL) DER strings,
 *                      signer first. RFC 7515 §4.1.6.
 * @property additional Free-form headers we do not model individually but still must include in
 *                      the protected JSON. Used by the round-trip serializer to preserve any
 *                      params the producer added that we do not understand.
 */
data class JadesHeader(
    val alg: String,
    val kid: String? = null,
    val typ: String? = "JAdES",
    val cty: String? = null,
    val crit: List<String>? = null,
    val sigT: String,
    val x5tS256: String,
    val x5c: List<String>? = null,
    val additional: Map<String, JsonElement> = emptyMap(),
)

/**
 * Unsigned-properties container — the JAdES `etsiU` array entries.
 *
 * Entries are profile-gated:
 * - `sigTst`  — populated for B-T and above.
 * - `xVals`   — populated for B-LT and above (complete signer certificate chain).
 * - `rVals`   — populated for B-LT and above (revocation snapshots: CRL bytes or OCSP responses).
 * - `arcTst`  — populated for B-LTA only (archival time-stamps over everything above).
 *
 * @property sigTst One or more encoded RFC 3161 time-stamp tokens covering the signature value.
 * @property xVals  Complete certificate chain for the signer, base64-encoded DER. The signer cert
 *                  itself MAY appear here too; verifiers tolerate both presence and absence.
 * @property rVals  Revocation data values: each entry is a base64-encoded CRL or OCSP response
 *                  proving the corresponding `xVals` certificate's status at signing time.
 * @property arcTst Archival time-stamps. Each token's message-imprint covers the SHA-256 of
 *                  `signatureB64u || serialized(sigTst) || serialized(xVals) || serialized(rVals)`
 *                  per TS 119 182-1 §5.3.6.
 */
data class JadesUnsignedProperties(
    val sigTst: List<EncodedTimeStampToken> = emptyList(),
    val xVals: List<EncodedCertificate> = emptyList(),
    val rVals: List<EncodedRevocationData> = emptyList(),
    val arcTst: List<EncodedTimeStampToken> = emptyList(),
)

/**
 * A single `xVals` entry — one base64-encoded DER X.509 certificate, plus an optional
 * algorithm/digest reference for future certificate-references modes.
 *
 * @property certB64 Base64 (standard, NOT base64URL) of the DER certificate bytes.
 */
data class EncodedCertificate(val certB64: String)

/**
 * A single `rVals` entry — base64-encoded revocation data with a type tag so verifiers know
 * whether to parse it as a CRL or as an OCSP response.
 *
 * @property type        `"CRL"` or `"OCSP"` per TS 119 182-1 §5.3.5.
 * @property dataB64     Base64-encoded DER bytes of the revocation artefact.
 * @property producedAt  Optional ISO 8601 `producedAt` time copied from the OCSP response or the
 *                       CRL's `thisUpdate` field; verifiers may use this for archival policies.
 */
data class EncodedRevocationData(
    val type: String,
    val dataB64: String,
    val producedAt: String? = null,
)

/**
 * A single `sigTst` entry: one or more base64-encoded RFC 3161 `TimeStampToken` DER blobs,
 * with an optional canonicalisation-algorithm URI (TS 119 182-1 §5.3.3).
 *
 * @property tstTokensB64 Base64 (standard, NOT base64URL) of the DER `TimeStampToken` bytes.
 *                       Multiple entries are allowed by ETSI for token redundancy.
 * @property canonAlg    Canonicalisation algorithm URI. MVP omits canonicalisation (the signed
 *                       data is the base64URL signature value itself, no canonicalisation needed),
 *                       so this is typically null.
 */
data class EncodedTimeStampToken(val tstTokensB64: List<String>, val canonAlg: String? = null)

/**
 * Input to [JadesSigner.sign].
 *
 * @property profile                 Target JAdES profile.
 * @property keyId                   Identifier of the signing key in the configured
 *                                   [org.trustweave.kms.KeyManagementService].
 * @property signerCertificateChain  DER-encoded X.509 certificates, signer first. Required to
 *                                   populate `x5c` and `x5t#S256`. MAY contain a single self-issued
 *                                   cert for development; production callers SHOULD include the
 *                                   issuing intermediates.
 * @property signingTime             Claimed signing time (`sigT`). Defaults to
 *                                   `kotlinx.datetime.Clock.System.now()` when null.
 * @property contentType             Optional JOSE `cty` (payload content type).
 * @property additionalHeaders       Free-form header parameters added to the protected header
 *                                   verbatim. Useful for application-specific JAdES extensions
 *                                   we do not model individually.
 * @property tsaConfig               Required iff [profile] == [JadesProfile.B_T]. The signer will
 *                                   request a `sigTst` against this TSA before serialising.
 */
data class JadesSigningRequest(
    val profile: JadesProfile,
    val keyId: KeyId,
    val signerCertificateChain: List<ByteArray>,
    val signingTime: Instant? = null,
    val contentType: String? = null,
    val additionalHeaders: Map<String, JsonElement> = emptyMap(),
    val tsaConfig: TsaConfig? = null,
    val validationData: ValidationData? = null,
) {
    init {
        require(signerCertificateChain.isNotEmpty()) {
            "signerCertificateChain must include at least the signer's certificate"
        }
        if (profile.atLeast(JadesProfile.B_T)) {
            require(tsaConfig != null) {
                "tsaConfig is required for JadesProfile.${profile.name} (needed for sigTst and arcTst stamps)"
            }
        }
        if (profile.atLeast(JadesProfile.B_LT)) {
            require(validationData != null) {
                "validationData is required for JadesProfile.${profile.name} (needed for xVals/rVals)"
            }
            require(validationData.completeCertificateChain.isNotEmpty()) {
                "validationData.completeCertificateChain must be non-empty for B-LT/B-LTA"
            }
        }
    }
}

/**
 * Long-term validation data supplied by the caller for B-LT and B-LTA signing.
 *
 * TrustWeave does not fetch CRL/OCSP data in-tree — production callers provide a snapshot at
 * signing time, typically by querying the issuer's CRL distribution point or stapling an OCSP
 * response.
 *
 * @property completeCertificateChain DER-encoded certificates spanning the signer cert up to (but
 *                                    typically excluding) a Trusted-List root. Order matters for
 *                                    embedded verifiers: signer first.
 * @property revocationData           Revocation snapshots, one per cert that needs proving.
 */
data class ValidationData(
    val completeCertificateChain: List<ByteArray>,
    val revocationData: List<EncodedRevocationData> = emptyList(),
)

/**
 * A produced JAdES signature, in JWS JSON Serialization (flattened) form.
 *
 * @property protectedHeaderB64u  Base64URL-encoded JSON of the protected header.
 * @property payloadB64u          Base64URL-encoded payload (the JSON being signed).
 * @property signatureB64u        Base64URL-encoded signature bytes (raw R||S for ECDSA, raw bytes
 *                                otherwise).
 * @property unsigned             Unsigned-properties block. Empty for B-B; populated for B-T.
 * @property serializedFlattened  The full JWS JSON Flattened serialisation as a string. Always
 *                                non-null.
 */
data class JadesSignature(
    val protectedHeaderB64u: String,
    val payloadB64u: String,
    val signatureB64u: String,
    val unsigned: JadesUnsignedProperties = JadesUnsignedProperties(),
    val serializedFlattened: String,
) {
    /**
     * JWS Compact Serialization (RFC 7515 §3.1) — only available when there are no unsigned
     * properties (i.e. strict B-B). Returns null for B-T and beyond, where the unsigned `etsiU`
     * block can only ride along the JSON Flattened form.
     */
    fun compact(): String? =
        if (unsigned.sigTst.isEmpty()) "$protectedHeaderB64u.$payloadB64u.$signatureB64u" else null
}

/**
 * Verification policy supplied to [JadesVerifier.verify].
 *
 * @property requiredProfile                       Minimum JAdES profile to accept.
 * @property trustAnchorResolver                   Resolves the signer-cert chain against the
 *                                                 caller-supplied trust graph.
 * @property acceptedAlgorithms                    JOSE `alg` values the verifier accepts.
 *                                                 MVP defaults: `ES256`, `ES384`, `ES512`, `EdDSA`.
 *                                                 RSA-PSS (`PS256`/`384`/`512`) is intentionally
 *                                                 absent from the MVP defaults.
 * @property allowExpiredCertificateAtSigningTime  When true, certificate validity is not enforced
 *                                                 at the claimed signing time. Useful for B-LT
 *                                                 archival validation (future); the MVP default
 *                                                 is false.
 * @property maxClockSkew                          Tolerance applied to signing-time / TSA-time
 *                                                 comparisons. Default 5 minutes.
 */
data class JadesVerificationOptions(
    val requiredProfile: JadesProfile,
    val trustAnchorResolver: TrustAnchorResolver,
    val acceptedAlgorithms: Set<String> = setOf("ES256", "ES384", "ES512", "EdDSA"),
    val allowExpiredCertificateAtSigningTime: Boolean = false,
    val maxClockSkew: Duration = Duration.parse("PT5M"),
)
