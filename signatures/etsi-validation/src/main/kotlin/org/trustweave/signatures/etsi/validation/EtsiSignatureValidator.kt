package org.trustweave.signatures.etsi.validation

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.datetime.Instant
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.trustweave.signatures.etsi.validation.EtsiValidationReport.FinalVerdict
import org.trustweave.signatures.jades.DefaultJadesVerifier
import org.trustweave.signatures.jades.JadesProfile
import org.trustweave.signatures.jades.JadesValidationResult
import org.trustweave.signatures.jades.JadesVerificationOptions
import org.trustweave.signatures.trustlists.TrustAnchorMatch
import org.trustweave.signatures.trustlists.TrustAnchorResolver
import org.trustweave.signatures.trustlists.TspServiceStatus
import java.io.ByteArrayInputStream
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.util.Base64

/**
 * Orchestrates the ETSI EN 319 102-1 multi-step signature validation procedure.
 *
 * Each call to [validate] runs the 11 procedural steps in order against a JAdES envelope and
 * a caller-supplied [EtsiSignaturePolicy] + [TrustAnchorResolver], producing a structured
 * [EtsiValidationReport] with one [StepOutcome] per step plus an aggregated [FinalVerdict].
 *
 * The pipeline delegates the cryptographic heavy lifting — signature verification, X.509 path
 * building, time-stamp validation — to the existing [DefaultJadesVerifier]. The added value
 * here is the per-step report and the explicit policy enforcement layer on top.
 */
interface EtsiSignatureValidator {
    /**
     * Run the ETSI 102-1 procedure against [jadesSerialized].
     *
     * @param jadesSerialized      JWS JSON Flattened or JWS Compact serialization of the JAdES
     *                              signature.
     * @param policy               Caller-defined acceptance criteria (algorithms, skew, etc.).
     * @param trustAnchorResolver  Resolver into the caller's trust graph (ETSI TSL).
     */
    suspend fun validate(
        jadesSerialized: String,
        policy: EtsiSignaturePolicy,
        trustAnchorResolver: TrustAnchorResolver,
    ): EtsiValidationReport
}

/**
 * Default [EtsiSignatureValidator] backed by [DefaultJadesVerifier].
 *
 * Pipeline outline:
 *  1. Parse the envelope ourselves to attribute [EtsiValidationStep.FORMAT_CHECK] and
 *     [EtsiValidationStep.IDENTIFIER_CHECK] failures distinctly from cryptographic failures.
 *  2. Hand off to the JAdES verifier for steps SIGNATURE_ACCEPTANCE, SIGNING_TIME_VALIDITY,
 *     X509_CERT_PATH and TIME_STAMP_TOKEN.
 *  3. Layer policy checks on top: signature algorithm in allow-list, trust-status URI in
 *     allow-list, time-stamp required-by-policy.
 *  4. REVOCATION and LONG_TERM_VALIDATION return [StepOutcome.NotApplicable] until the
 *     B-LT/B-LTA verifier lands.
 *  5. Aggregate the per-step outcomes into the final verdict.
 */
class DefaultEtsiSignatureValidator(
    private val jadesVerifier: DefaultJadesVerifier = DefaultJadesVerifier(),
) : EtsiSignatureValidator {

    override suspend fun validate(
        jadesSerialized: String,
        policy: EtsiSignaturePolicy,
        trustAnchorResolver: TrustAnchorResolver,
    ): EtsiValidationReport = withContext(Dispatchers.IO) {
        val outcomes = linkedMapOf<EtsiValidationStep, StepOutcome>()

        // ------------------------------------------------------------ Step 1: FORMAT_CHECK
        val parsed = try {
            parseEnvelope(jadesSerialized)
        } catch (t: Throwable) {
            outcomes[EtsiValidationStep.FORMAT_CHECK] = StepOutcome.Failed(
                reason = t.message ?: "input is not well-formed JWS/JAdES",
                cause = t,
            )
            return@withContext finalize(outcomes, signerSubject = null, signingTime = null)
        }
        outcomes[EtsiValidationStep.FORMAT_CHECK] = StepOutcome.Passed("JWS envelope parsed")

        // ------------------------------------------------------------ Step 2: IDENTIFIER_CHECK
        val signerCert: X509Certificate
        val signerSubject: String
        try {
            signerCert = decodeSignerCertificate(parsed)
            signerSubject = signerCert.subjectX500Principal.name
        } catch (t: Throwable) {
            outcomes[EtsiValidationStep.IDENTIFIER_CHECK] = StepOutcome.Failed(
                reason = t.message ?: "signer certificate could not be decoded",
                cause = t,
            )
            outcomes.markInconclusiveRest(after = EtsiValidationStep.IDENTIFIER_CHECK)
            return@withContext finalize(outcomes, signerSubject = null, signingTime = null)
        }
        outcomes[EtsiValidationStep.IDENTIFIER_CHECK] = StepOutcome.Passed(
            "signer cert decoded: $signerSubject",
        )

        // Pre-compute signing-time presence for policy.requireSigningTime.
        val signingTime: Instant? = parsed.sigT?.let {
            try {
                Instant.parse(it)
            } catch (_: Throwable) {
                null
            }
        }

        // ------------------------------------------------------------ Hand off to JAdES verifier
        val jadesProfile = if (parsed.hasSigTst) JadesProfile.B_T else JadesProfile.B_B
        val jadesResult = jadesVerifier.verify(
            jadesSerialized,
            JadesVerificationOptions(
                requiredProfile = jadesProfile,
                trustAnchorResolver = trustAnchorResolver,
                acceptedAlgorithms = policy.acceptedSignatureAlgorithms +
                    // pass the asserted alg through too so the JAdES verifier doesn't pre-reject
                    // it on an alg-mismatch and mask the policy-level CRYPTO_CONSTRAINTS step.
                    setOfNotNull(parsed.alg),
                allowExpiredCertificateAtSigningTime = false,
                maxClockSkew = policy.maxClockSkew,
            ),
        )

        // ------------------------------------------------------------ Step 3: SIGNATURE_ACCEPTANCE
        outcomes[EtsiValidationStep.SIGNATURE_ACCEPTANCE] = when (jadesResult) {
            is JadesValidationResult.Valid -> StepOutcome.Passed("crypto verification ok")
            is JadesValidationResult.Invalid.BadSignature -> StepOutcome.Failed(
                "cryptographic verification failed: ${jadesResult.reason}",
            )
            is JadesValidationResult.Invalid.Malformed -> StepOutcome.Failed(
                "envelope rejected during crypto check: ${jadesResult.reason}",
            )
            else -> StepOutcome.Passed("crypto verification ok (further step failed)")
        }

        // ------------------------------------------------------------ Step 4: X509_CERT_PATH
        outcomes[EtsiValidationStep.X509_CERT_PATH] = when (jadesResult) {
            is JadesValidationResult.Valid -> when (val trust = jadesResult.trust) {
                is TrustAnchorMatch.QualifiedActive ->
                    StepOutcome.Passed("anchored at ${trust.tspName}")
                is TrustAnchorMatch.QualifiedWithdrawn ->
                    StepOutcome.Passed("anchored at withdrawn ${trust.tspName}")
                is TrustAnchorMatch.NotTrusted ->
                    StepOutcome.Failed("signer chain does not anchor at any trust-list CA")
            }
            is JadesValidationResult.Invalid.UntrustedSigner ->
                StepOutcome.Failed("signer chain does not anchor at any trust-list CA")
            is JadesValidationResult.Invalid.BadSignature,
            is JadesValidationResult.Invalid.Malformed,
            -> StepOutcome.Inconclusive("cert path not evaluated — earlier step failed")
            else -> StepOutcome.Passed("cert path validated")
        }

        // ------------------------------------------------------------ Step 5: REVOCATION (MVP placeholder)
        outcomes[EtsiValidationStep.REVOCATION] = StepOutcome.NotApplicable

        // ------------------------------------------------------------ Step 6: CRYPTO_CONSTRAINTS
        outcomes[EtsiValidationStep.CRYPTO_CONSTRAINTS] = when (val alg = parsed.alg) {
            null -> StepOutcome.Failed("protected header missing 'alg'")
            !in policy.acceptedSignatureAlgorithms -> StepOutcome.Failed(
                "signature alg '$alg' not in policy.acceptedSignatureAlgorithms " +
                    "(${policy.acceptedSignatureAlgorithms.joinToString()})",
            )
            else -> StepOutcome.Passed("alg=$alg accepted by policy")
        }

        // ------------------------------------------------------------ Step 7: SIGNATURE_POLICY
        outcomes[EtsiValidationStep.SIGNATURE_POLICY] = signaturePolicyOutcome(jadesResult, policy)

        // ------------------------------------------------------------ Step 8: SIGNING_TIME_VALIDITY
        outcomes[EtsiValidationStep.SIGNING_TIME_VALIDITY] = signingTimeOutcome(
            jadesResult = jadesResult,
            signingTime = signingTime,
            policy = policy,
        )

        // ------------------------------------------------------------ Step 9: TIME_STAMP_TOKEN
        outcomes[EtsiValidationStep.TIME_STAMP_TOKEN] = timeStampOutcome(jadesResult, policy, parsed)

        // ------------------------------------------------------------ Step 10: LONG_TERM_VALIDATION (MVP placeholder)
        outcomes[EtsiValidationStep.LONG_TERM_VALIDATION] = StepOutcome.NotApplicable

        // ------------------------------------------------------------ Step 11: FINAL_VERDICT
        return@withContext finalize(outcomes, signerSubject = signerSubject, signingTime = signingTime)
    }

    // ---------------------------------------------------------------- per-step helpers

    private fun signaturePolicyOutcome(
        jadesResult: JadesValidationResult,
        policy: EtsiSignaturePolicy,
    ): StepOutcome {
        if (jadesResult is JadesValidationResult.Valid) {
            val statusUri = when (val trust = jadesResult.trust) {
                is TrustAnchorMatch.QualifiedActive -> trust.service.status.uri
                is TrustAnchorMatch.QualifiedWithdrawn -> TspServiceStatus.WITHDRAWN.uri
                is TrustAnchorMatch.NotTrusted -> null
            }
            return when {
                statusUri == null ->
                    StepOutcome.Failed("no trust-list service status available")
                statusUri !in policy.allowedTrustStatusUris ->
                    StepOutcome.Failed(
                        "trust-status URI '$statusUri' not in policy.allowedTrustStatusUris",
                    )
                else -> StepOutcome.Passed("trust-status '$statusUri' accepted")
            }
        }
        if (jadesResult is JadesValidationResult.Invalid.UntrustedSigner) {
            return StepOutcome.Failed("signer is untrusted — no trust-list service status")
        }
        return StepOutcome.Inconclusive("signature-policy not evaluated — earlier step failed")
    }

    private fun signingTimeOutcome(
        jadesResult: JadesValidationResult,
        signingTime: Instant?,
        policy: EtsiSignaturePolicy,
    ): StepOutcome {
        if (policy.requireSigningTime && signingTime == null) {
            return StepOutcome.Failed("policy requires sigT but it was missing or unparseable")
        }
        return when (jadesResult) {
            is JadesValidationResult.Valid -> StepOutcome.Passed(
                "signer cert valid at sigT=${jadesResult.signingTime}",
            )
            is JadesValidationResult.Invalid.CertificateExpired ->
                StepOutcome.Failed("signer cert expired at sigT (notAfter=${jadesResult.notAfter})")
            else -> StepOutcome.Inconclusive("signing-time not evaluated — earlier step failed")
        }
    }

    private fun timeStampOutcome(
        jadesResult: JadesValidationResult,
        policy: EtsiSignaturePolicy,
        parsed: ParsedEnvelope,
    ): StepOutcome {
        if (policy.requireTimeStamp && !parsed.hasSigTst) {
            return StepOutcome.Failed("policy requires sigTst but the envelope has none")
        }
        if (!parsed.hasSigTst) {
            return StepOutcome.NotApplicable
        }
        return when (jadesResult) {
            is JadesValidationResult.Valid -> {
                val ts = jadesResult.signatureTimeStamp
                if (ts != null) {
                    StepOutcome.Passed("sigTst validated at $ts")
                } else {
                    StepOutcome.Inconclusive("sigTst present but no TSA gen-time recovered")
                }
            }
            is JadesValidationResult.Invalid.MissingTimeStamp ->
                StepOutcome.Failed("sigTst missing: ${jadesResult.reason}")
            is JadesValidationResult.Invalid.TimeStampMismatch ->
                StepOutcome.Failed("sigTst mismatch: ${jadesResult.reason}")
            else -> StepOutcome.Inconclusive("sigTst not evaluated — earlier step failed")
        }
    }

    // ---------------------------------------------------------------- final-verdict aggregation

    private fun finalize(
        outcomes: MutableMap<EtsiValidationStep, StepOutcome>,
        signerSubject: String?,
        signingTime: Instant?,
    ): EtsiValidationReport {
        // Fill any missing steps with NotApplicable so the report exposes all 11 entries.
        EtsiValidationStep.values().forEach { step ->
            outcomes.putIfAbsent(step, StepOutcome.NotApplicable)
        }

        // Compute aggregate verdict from the non-FINAL_VERDICT entries.
        val applicableOutcomes = outcomes
            .filterKeys { it != EtsiValidationStep.FINAL_VERDICT }
            .values

        val verdict = when {
            applicableOutcomes.any { it is StepOutcome.Failed } -> FinalVerdict.TOTAL_FAILED
            applicableOutcomes.any { it is StepOutcome.Inconclusive } -> FinalVerdict.INDETERMINATE
            else -> FinalVerdict.TOTAL_PASSED
        }

        outcomes[EtsiValidationStep.FINAL_VERDICT] = when (verdict) {
            FinalVerdict.TOTAL_PASSED -> StepOutcome.Passed("all applicable steps passed")
            FinalVerdict.TOTAL_FAILED -> StepOutcome.Failed("at least one step failed")
            FinalVerdict.INDETERMINATE -> StepOutcome.Inconclusive("at least one step is inconclusive")
        }

        return EtsiValidationReport(
            finalVerdict = verdict,
            steps = outcomes.toMap(),
            signerSubject = signerSubject,
            signingTime = signingTime,
        )
    }

    private fun MutableMap<EtsiValidationStep, StepOutcome>.markInconclusiveRest(
        after: EtsiValidationStep,
    ) {
        val afterOrdinal = after.ordinal
        EtsiValidationStep.values()
            .filter { it.ordinal > afterOrdinal && it != EtsiValidationStep.FINAL_VERDICT }
            .forEach { step ->
                putIfAbsent(step, StepOutcome.Inconclusive("not evaluated — earlier step failed"))
            }
    }

    // ---------------------------------------------------------------- envelope parsing

    private data class ParsedEnvelope(
        val protectedB64u: String,
        val payloadB64u: String,
        val signatureB64u: String,
        val alg: String?,
        val sigT: String?,
        val x5c: List<String>?,
        val hasSigTst: Boolean,
    )

    private fun parseEnvelope(serialized: String): ParsedEnvelope {
        val trimmed = serialized.trim()
        val (protectedB64u, payloadB64u, signatureB64u, hasSigTst) =
            if (trimmed.startsWith("{")) parseFlattened(trimmed) else parseCompact(trimmed)
        val headerJson = Json.parseToJsonElement(
            String(Base64.getUrlDecoder().decode(protectedB64u), Charsets.UTF_8),
        ).jsonObject
        val alg = headerJson["alg"]?.jsonPrimitive?.content
        val sigT = headerJson["sigT"]?.jsonPrimitive?.content
        val x5c = (headerJson["x5c"] as? JsonArray)?.map { it.jsonPrimitive.content }
        return ParsedEnvelope(
            protectedB64u = protectedB64u,
            payloadB64u = payloadB64u,
            signatureB64u = signatureB64u,
            alg = alg,
            sigT = sigT,
            x5c = x5c,
            hasSigTst = hasSigTst,
        )
    }

    /** Returns (protected, payload, signature, hasSigTst). */
    private fun parseFlattened(serialized: String): Quad {
        val obj = Json.parseToJsonElement(serialized).jsonObject
        val protectedB64u = obj["protected"]?.jsonPrimitive?.content
            ?: error("JAdES JSON Flattened: missing 'protected'")
        val payloadB64u = obj["payload"]?.jsonPrimitive?.content
            ?: error("JAdES JSON Flattened: missing 'payload'")
        val signatureB64u = obj["signature"]?.jsonPrimitive?.content
            ?: error("JAdES JSON Flattened: missing 'signature'")
        val unsignedHeader = obj["header"] as? JsonObject
        val etsiU = unsignedHeader?.get("etsiU") as? JsonArray
        val hasSigTst = etsiU?.any { (it as? JsonObject)?.get("sigTst") != null } == true
        return Quad(protectedB64u, payloadB64u, signatureB64u, hasSigTst)
    }

    private fun parseCompact(serialized: String): Quad {
        val parts = serialized.split('.')
        require(parts.size == 3) { "JWS Compact serialization must have three parts" }
        return Quad(parts[0], parts[1], parts[2], hasSigTst = false)
    }

    private data class Quad(
        val protectedB64u: String,
        val payloadB64u: String,
        val signatureB64u: String,
        val hasSigTst: Boolean,
    )

    private fun decodeSignerCertificate(parsed: ParsedEnvelope): X509Certificate {
        val x5c = parsed.x5c
            ?: error("protected header lacks 'x5c' — signer identity unknown")
        val signerDer = x5c.firstOrNull()
            ?: error("'x5c' was empty")
        val der = Base64.getDecoder().decode(signerDer)
        return CERT_FACTORY.generateCertificate(ByteArrayInputStream(der)) as X509Certificate
    }

    companion object {
        private val CERT_FACTORY: CertificateFactory = CertificateFactory.getInstance("X.509")
    }
}
