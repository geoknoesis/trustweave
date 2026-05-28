package org.trustweave.credential.jades

import kotlinx.datetime.Clock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import org.trustweave.core.identifiers.Iri
import org.trustweave.core.identifiers.KeyId
import org.trustweave.credential.format.ProofSuiteId
import org.trustweave.credential.model.vc.CredentialProof
import org.trustweave.credential.model.vc.VerifiableCredential
import org.trustweave.credential.requests.IssuanceRequest
import org.trustweave.credential.requests.VerificationOptions
import org.trustweave.credential.results.VerificationResult
import org.trustweave.credential.spi.proof.ProofEngine
import org.trustweave.credential.spi.proof.ProofEngineCapabilities
import org.trustweave.credential.spi.proof.ProofEngineConfig
import org.trustweave.kms.KeyManagementService
import org.trustweave.signatures.jades.DefaultJadesSigner
import org.trustweave.signatures.jades.DefaultJadesVerifier
import org.trustweave.signatures.jades.JadesProfile
import org.trustweave.signatures.jades.JadesSigningRequest
import org.trustweave.signatures.jades.JadesValidationResult
import org.trustweave.signatures.jades.JadesVerificationOptions
import org.trustweave.signatures.trustlists.TrustAnchorMatch
import org.trustweave.signatures.trustlists.TrustAnchorResolver
import org.trustweave.signatures.tsa.TsaConfig

/**
 * JAdES proof engine — implements the [ProofEngine] SPI for ETSI TS 119 182-1 signatures over
 * W3C Verifiable Credentials.
 *
 * The engine composes the existing TrustWeave proof-engine pipeline with the new `signatures:jades`
 * module: on issuance the credential JSON (with proof omitted) is signed as a JAdES JWS via
 * [DefaultJadesSigner], the resulting JWS string is stored in a [CredentialProof.JAdES] variant,
 * and the credential is returned. On verification the proof is reconstructed from the wire bytes
 * by [DefaultJadesVerifier], cryptographically checked, and the trust graph is consulted via the
 * supplied [TrustAnchorResolver].
 *
 * **Configuration knobs.**
 *
 * The engine looks for the following keys, in priority order:
 * - **Constructor-injected** [trustAnchorResolver] — set by the SPI provider when one is known
 *   at TrustWeave startup time.
 * - **`ProofEngineConfig.properties`** — `trustAnchorResolver` may also be injected here, which
 *   lets callers swap the resolver at runtime without re-instantiating the engine.
 * - **`IssuanceRequest.proofOptions.additionalOptions`** for issuance:
 *   - `"keyId"` (`String`) — KMS key ID, required.
 *   - `"signerCertificateChain"` (`List<ByteArray>`) — DER X.509 chain, signer first; required.
 *   - `"profile"` (`String`) — `"B-B"` (default) or `"B-T"`.
 *   - `"tsaConfig"` (`TsaConfig`) — required iff `profile == "B-T"`.
 *   - `"contentType"` (`String`) — optional JOSE `cty` parameter.
 * - **`VerificationOptions.additionalOptions`** for verification:
 *   - `"trustAnchorResolver"` (`TrustAnchorResolver`) — required unless one was injected at
 *     construction or via [ProofEngineConfig].
 *   - `"requiredProfile"` (`String`) — `"B-B"` (default) or `"B-T"`.
 *   - `"acceptedAlgorithms"` (`Set<String>`) — overrides the JAdES verifier's default allow-list.
 */
class JAdESProofEngine(
    private val kms: KeyManagementService,
    private val trustAnchorResolver: TrustAnchorResolver? = null,
    private val config: ProofEngineConfig = ProofEngineConfig(),
) : ProofEngine {

    private val signer = DefaultJadesSigner(kms)
    private val verifier = DefaultJadesVerifier()

    override val format: ProofSuiteId = ProofSuiteId.JADES
    override val formatName: String = "ETSI TS 119 182-1 JAdES"
    override val formatVersion: String = "B-B, B-T"
    override val capabilities: ProofEngineCapabilities = ProofEngineCapabilities(
        selectiveDisclosure = false,
        zeroKnowledge = false,
        revocation = true,
        presentation = false,
        predicates = false,
    )

    private var resolvedAnchorResolver: TrustAnchorResolver? = trustAnchorResolver

    override suspend fun initialize(config: ProofEngineConfig) {
        super.initialize(config)
        (config.properties["trustAnchorResolver"] as? TrustAnchorResolver)?.let {
            resolvedAnchorResolver = it
        }
    }

    // ---------------------------------------------------------------- issue

    override suspend fun issue(request: IssuanceRequest): VerifiableCredential {
        val opts = request.proofOptions?.additionalOptions ?: emptyMap()
        val keyIdValue = (opts["keyId"] as? String)
            ?: request.issuerKeyId?.keyId?.value
            ?: throw JAdESEngineException(
                "JAdES issuance requires either request.issuerKeyId or proofOptions.additionalOptions[\"keyId\"]",
            )
        val signerCertChain = (opts["signerCertificateChain"] as? List<*>)
            ?.filterIsInstance<ByteArray>()
            ?: throw JAdESEngineException(
                "JAdES issuance requires proofOptions.additionalOptions[\"signerCertificateChain\"] " +
                    "as a non-empty List<ByteArray> of DER X.509 certs (signer first)",
            )
        if (signerCertChain.isEmpty()) {
            throw JAdESEngineException("signerCertificateChain is empty")
        }
        val profile = parseProfile(opts["profile"] as? String ?: PROFILE_B_B)
        val tsaConfig = opts["tsaConfig"] as? TsaConfig

        if (profile == JadesProfile.B_T && tsaConfig == null) {
            throw JAdESEngineException("profile = B-T requires proofOptions.additionalOptions[\"tsaConfig\"]")
        }

        // Build the credential without a proof, then take a stable JSON snapshot to use as the
        // JAdES payload. Adding the proof back into the returned credential creates an envelope
        // shape that downstream tooling can serialise verbatim: the proof.jws bytes are signed
        // over the proof-less credential and the redundant copy of the credential fields outside
        // the JWS is what makes the result greppable / human-inspectable.
        val unproven = request.toCredentialWithoutProof()
        val payloadJson = STABLE_JSON.encodeToJsonElement(
            VerifiableCredential.serializer(),
            unproven,
        )

        val signature = signer.sign(
            payloadJson = payloadJson,
            request = JadesSigningRequest(
                profile = profile,
                keyId = KeyId(keyIdValue),
                signerCertificateChain = signerCertChain,
                contentType = opts["contentType"] as? String,
                tsaConfig = tsaConfig,
            ),
        )

        // Prefer the JWS Compact form for B-B (smaller, single-line); fall back to JSON Flattened
        // (the only form that can carry the etsiU block) for B-T.
        val jws = signature.compact() ?: signature.serializedFlattened
        return unproven.copy(
            proof = CredentialProof.JAdES(jws = jws, profile = profile.asString()),
        )
    }

    // ---------------------------------------------------------------- verify

    override suspend fun verify(
        credential: VerifiableCredential,
        options: VerificationOptions,
    ): VerificationResult {
        val proof = credential.proof as? CredentialProof.JAdES
            ?: return invalidProof(credential, "credential.proof is not a JAdES variant")

        val anchorResolver = resolveAnchorResolver(options)
            ?: return invalidProof(credential, "no TrustAnchorResolver supplied; configure one in ProofEngineConfig or VerificationOptions")

        val requiredProfile = parseProfile(
            options.additionalOptions["requiredProfile"] as? String ?: proof.profile,
        )
        val acceptedAlgorithms =
            (options.additionalOptions["acceptedAlgorithms"] as? Set<*>)
                ?.filterIsInstance<String>()
                ?.toSet()
                ?: setOf("ES256", "ES384", "ES512", "EdDSA")

        val result = verifier.verify(
            jadesSerialized = proof.jws,
            options = JadesVerificationOptions(
                requiredProfile = requiredProfile,
                trustAnchorResolver = anchorResolver,
                acceptedAlgorithms = acceptedAlgorithms,
            ),
        )

        return mapVerifierResult(credential, result)
    }

    // ---------------------------------------------------------------- helpers

    private fun resolveAnchorResolver(options: VerificationOptions): TrustAnchorResolver? {
        (options.additionalOptions["trustAnchorResolver"] as? TrustAnchorResolver)
            ?.let { return it }
        return resolvedAnchorResolver
    }

    private fun mapVerifierResult(
        credential: VerifiableCredential,
        result: JadesValidationResult,
    ): VerificationResult = when (result) {
        is JadesValidationResult.Valid -> VerificationResult.Valid(
            credential = credential,
            issuerIri = Iri(credential.issuer.id.value),
            subjectIri = credential.credentialSubject.id?.let { Iri(it.value) },
            issuedAt = result.signingTime,
            expiresAt = credential.validUntil ?: credential.expirationDate,
            formatMetadata = buildFormatMetadata(result),
        )
        is JadesValidationResult.Invalid.BadSignature ->
            invalidProof(credential, "JAdES BadSignature: ${result.reason}")
        is JadesValidationResult.Invalid.UntrustedSigner ->
            invalidProof(credential, "JAdES UntrustedSigner: ${result.cert.subjectX500Principal}")
        is JadesValidationResult.Invalid.WrongProfile ->
            invalidProof(credential, "JAdES WrongProfile: found ${result.found}, required ${result.required}")
        is JadesValidationResult.Invalid.MissingTimeStamp ->
            invalidProof(credential, "JAdES MissingTimeStamp: ${result.reason}")
        is JadesValidationResult.Invalid.TimeStampMismatch ->
            invalidProof(credential, "JAdES TimeStampMismatch: ${result.reason}")
        is JadesValidationResult.Invalid.CertificateExpired ->
            invalidProof(credential, "JAdES CertificateExpired (notAfter=${result.notAfter})")
        is JadesValidationResult.Invalid.Malformed ->
            invalidProof(credential, "JAdES Malformed: ${result.reason}")
    }

    private fun buildFormatMetadata(valid: JadesValidationResult.Valid): Map<String, JsonElement> = buildJsonObject {
        put("alg", JsonPrimitive(valid.header.alg))
        put("sigT", JsonPrimitive(valid.header.sigT))
        valid.signatureTimeStamp?.let { put("sigTstGenTime", JsonPrimitive(it.toString())) }
        when (val trust = valid.trust) {
            is TrustAnchorMatch.QualifiedActive -> {
                put("trustStatus", JsonPrimitive("qualifiedActive"))
                put("trustTerritory", JsonPrimitive(trust.territory))
                put("trustTsp", JsonPrimitive(trust.tspName))
                put("qcWithSscd", JsonPrimitive(trust.qcWithSscd))
                put("qcForESig", JsonPrimitive(trust.qcForESig))
            }
            is TrustAnchorMatch.QualifiedWithdrawn -> {
                put("trustStatus", JsonPrimitive("qualifiedWithdrawn"))
                put("trustTsp", JsonPrimitive(trust.tspName))
                put("trustWithdrawnAt", JsonPrimitive(trust.withdrawnAt.toString()))
            }
            TrustAnchorMatch.NotTrusted -> {
                // Should not happen for a Valid result, but keep the branch exhaustive.
                put("trustStatus", JsonPrimitive("notTrusted"))
            }
        }
    }.let { obj: JsonObject -> obj.toMap() }

    private fun invalidProof(
        credential: VerifiableCredential?,
        reason: String,
    ): VerificationResult.Invalid.InvalidProof =
        VerificationResult.Invalid.InvalidProof(credential = credential, reason = reason)

    // ---------------------------------------------------------------- profile <-> string

    private fun parseProfile(s: String): JadesProfile = when (s.uppercase()) {
        PROFILE_B_B, "BB", "BASIC" -> JadesProfile.B_B
        PROFILE_B_T, "BT" -> JadesProfile.B_T
        else -> throw JAdESEngineException("Unknown JAdES profile '$s'; expected 'B-B' or 'B-T'")
    }

    private fun JadesProfile.asString(): String = when (this) {
        JadesProfile.B_B -> PROFILE_B_B
        JadesProfile.B_T -> PROFILE_B_T
        JadesProfile.B_LT -> "B-LT"
        JadesProfile.B_LTA -> "B-LTA"
    }

    companion object {
        const val PROFILE_B_B: String = "B-B"
        const val PROFILE_B_T: String = "B-T"

        /** JSON formatter used to produce the JAdES payload bytes. Stable across runs. */
        private val STABLE_JSON = Json {
            prettyPrint = false
            encodeDefaults = false
            ignoreUnknownKeys = true
        }
    }
}

/** Thrown when the JAdES engine cannot proceed because of a configuration or input error. */
class JAdESEngineException(message: String, cause: Throwable? = null) :
    RuntimeException(message, cause)
